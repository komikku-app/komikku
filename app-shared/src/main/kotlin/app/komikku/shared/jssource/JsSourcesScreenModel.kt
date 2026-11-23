package app.komikku.shared.jssource

import app.komikku.sourcejs.repo.JsSourceStatus
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: state of the JavaScript sources screen (Android) / pane (desktop): the store's repositories and sources, which
 * entries are busy, and the open dialog. Install and update are two steps: the text is downloaded first and the user
 * confirms its host and SHA-256 ([Dialog.ConfirmInstall]); only that text is installed.
 */
class JsSourcesScreenModel(
    private val store: MangayomiSourceStore = Injekt.get(),
) : StateScreenModel<JsSourcesScreenModel.State>(State()) {

    data class State(
        /** `null` until the store's lists arrived. */
        val items: List<JsSourceItem>? = null,
        val repositories: List<JsSourceRepository> = emptyList(),
        val isRefreshing: Boolean = false,
        /** Mangayomi ids being downloaded, installed or removed. */
        val busy: Set<Long> = emptySet(),
        val dialog: Dialog? = null,
        /** The last failure, shown until dismissed. */
        val error: JsSourceStoreException? = null,
    ) {
        val updates: List<JsSourceItem> get() = items.orEmpty().filter { it.installed != null && it.hasUpdate }
        val installed: List<JsSourceItem> get() = items.orEmpty().filter { it.installed != null && !it.hasUpdate }
        val available: List<JsSourceItem> get() = items.orEmpty().filter { it.installed == null }
    }

    sealed interface Dialog {
        data object AddRepository : Dialog
        data class RemoveRepository(val repository: JsSourceRepository) : Dialog
        data class ConfirmInstall(val download: JsSourceDownload) : Dialog
        data class ConfirmUninstall(val item: JsSourceItem) : Dialog
    }

    init {
        screenModelScope.launch { store.items.collectLatest { items -> mutableState.update { it.copy(items = items) } } }
        screenModelScope.launch {
            store.repositories.collectLatest { repositories -> mutableState.update { it.copy(repositories = repositories) } }
        }
        screenModelScope.launch {
            store.isRefreshing.collectLatest { refreshing -> mutableState.update { it.copy(isRefreshing = refreshing) } }
        }
        refresh()
    }

    fun refresh() {
        screenModelScope.launch { store.refresh() }
    }

    fun showDialog(dialog: Dialog) = mutableState.update { it.copy(dialog = dialog) }

    fun dismissDialog() = mutableState.update { it.copy(dialog = null) }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    /** Adds the repository at [url]; returns the failure (also for the dialog), or `null` when it was added. */
    suspend fun addRepository(url: String): JsSourceStoreException? = try {
        store.addRepository(url)
        dismissDialog()
        null
    } catch (e: JsSourceStoreException) {
        e
    }

    fun removeRepository(repository: JsSourceRepository) {
        store.removeRepository(repository.url)
        dismissDialog()
    }

    /** Downloads the text to install (or update to) for [item] and asks for confirmation. */
    fun requestInstall(item: JsSourceItem) = busy(item.id) {
        val download = if (item.installed != null) store.downloadUpdate(item.id) else store.download(item.offer!!)
        showDialog(Dialog.ConfirmInstall(download))
    }

    /** Installs the confirmed [download] (its SHA-256 is checked again). */
    fun confirmInstall(download: JsSourceDownload) {
        dismissDialog()
        busy(download.offer.id) { store.install(download.offer, download.sha256) }
    }

    fun uninstall(item: JsSourceItem) {
        dismissDialog()
        busy(item.id) { store.uninstall(item.id) }
    }

    private fun busy(id: Long, block: suspend () -> Unit) {
        if (id in state.value.busy) return
        mutableState.update { it.copy(busy = it.busy + id) }
        screenModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: JsSourceStoreException) {
                mutableState.update { it.copy(error = e) }
            } finally {
                mutableState.update { it.copy(busy = it.busy - id) }
            }
        }
    }

    companion object {
        /** Whether [status] is worth showing next to the version. */
        fun isNotable(status: JsSourceStatus?): Boolean = status != null && status != JsSourceStatus.UP_TO_DATE
    }
}
