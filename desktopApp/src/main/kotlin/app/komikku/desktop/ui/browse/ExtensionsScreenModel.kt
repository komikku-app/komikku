package app.komikku.desktop.ui.browse

import android.app.Application
import app.komikku.desktop.extensions.DesktopExtensionManager
import app.komikku.desktop.extensions.UnsupportedExtension
import app.komikku.shared.extension.GetPlatformExtensionsByType
import app.komikku.shared.extension.PlatformExtensionManager
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.model.Extensions
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.extension.interactor.AddExtensionStore
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.interactor.RemoveExtensionStore
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * State of the desktop Extensions tab: extensions grouped like Android (updates / installed /
 * untrusted / available, through [GetPlatformExtensionsByType]), install progress per entry, the
 * saved extension stores; actions go to [PlatformExtensionManager] and the store interactors.
 */
class ExtensionsScreenModel(
    private val manager: PlatformExtensionManager = Injekt.get(),
    private val getExtensions: GetPlatformExtensionsByType = Injekt.get(),
    private val getStores: GetExtensionStores = Injekt.get(),
    private val addExtensionStore: AddExtensionStore = Injekt.get(),
    private val removeExtensionStore: RemoveExtensionStore = Injekt.get(),
    private val context: Application = Injekt.get(),
) : StateScreenModel<ExtensionsScreenModel.State>(State()) {

    data class State(
        val extensions: Extensions? = null,
        /** Install / update progress by [Extension.pkgName] of the listed entry. */
        val steps: Map<String, InstallStep> = emptyMap(),
        val stores: List<ExtensionStore> = emptyList(),
        val isRefreshing: Boolean = false,
        /** A failed install whose cause is known, shown in a dialog until dismissed. */
        val installFailure: InstallFailure? = null,
        /** Installed extensions that need what desktop cannot do ("unsupported on desktop: needs …"). */
        val unsupported: List<UnsupportedExtension> = emptyList(),
    )

    /** [name] could not be installed because of [reason] (the first line of the cause; the log has the rest). */
    data class InstallFailure(val name: String, val reason: String)

    init {
        screenModelScope.launch {
            getExtensions.subscribe().collectLatest { extensions -> mutableState.update { it.copy(extensions = extensions) } }
        }
        screenModelScope.launch {
            getStores.subscribe().collectLatest { stores -> mutableState.update { it.copy(stores = stores) } }
        }
        (manager as? DesktopExtensionManager)?.let { desktop ->
            screenModelScope.launch {
                desktop.unsupportedExtensionsFlow.collectLatest { list -> mutableState.update { it.copy(unsupported = list) } }
            }
        }
        refresh()
    }

    fun refresh() {
        if (state.value.isRefreshing) return
        mutableState.update { it.copy(isRefreshing = true) }
        screenModelScope.launch {
            try {
                manager.findAvailableExtensions()
            } finally {
                mutableState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun install(extension: Extension.Available) = track(extension, manager.installExtension(extension))

    fun update(extension: Extension.Installed) = track(extension, manager.updateExtension(extension))

    fun updateAll() = state.value.extensions?.updates.orEmpty().forEach(::update)

    fun cancel(extension: Extension) {
        manager.cancelInstallUpdateExtension(extension)
        mutableState.update { it.copy(steps = it.steps - extension.pkgName) }
    }

    fun uninstall(extension: Extension) = manager.uninstallExtension(extension)

    fun uninstall(extension: UnsupportedExtension) {
        val desktop = manager as? DesktopExtensionManager ?: return
        screenModelScope.launch { desktop.uninstall(extension.pkgName) }
    }

    fun dismissInstallFailure() = mutableState.update { it.copy(installFailure = null) }

    fun trust(extension: Extension.Untrusted) {
        screenModelScope.launch { manager.trust(extension) }
    }

    /** Adds the store at [indexUrl]; returns false when it could not be fetched or parsed. */
    suspend fun addStore(indexUrl: String): Boolean {
        val added = addExtensionStore(indexUrl.trim()).isSuccess
        if (added) refresh()
        return added
    }

    fun removeStore(store: ExtensionStore) {
        screenModelScope.launch {
            removeExtensionStore(store.indexUrl)
            refresh()
        }
    }

    private fun track(extension: Extension, steps: Flow<InstallStep>) {
        screenModelScope.launch {
            var last = InstallStep.Idle
            steps.collect { step ->
                last = step
                mutableState.update { it.copy(steps = it.steps + (extension.pkgName to step)) }
            }
            mutableState.update { it.copy(steps = it.steps - extension.pkgName) }
            if (last == InstallStep.Error) {
                val desktop = manager as? DesktopExtensionManager
                val unsupported = desktop?.unsupportedExtensionsFlow?.value?.find { it.pkgName == extension.pkgName }
                val reason = unsupported?.let { context.stringResource(KMR.strings.desktop_ext_unsupported_needs, it.needs) }
                    ?: desktop?.installError(extension.pkgName)
                if (reason != null) {
                    mutableState.update { it.copy(installFailure = InstallFailure(extension.name, reason)) }
                } else {
                    context.toast(context.stringResource(KMR.strings.desktop_ext_install_failed, extension.name))
                }
            }
        }
    }
}
