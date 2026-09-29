package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import mihon.domain.extension.interactor.AddExtensionStore
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.interactor.RemoveExtensionStore
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionStoresScreenModel(
    private val getExtensionStores: GetExtensionStores = Injekt.get(),
    private val addExtensionStore: AddExtensionStore = Injekt.get(),
    private val removeExtensionStore: RemoveExtensionStore = Injekt.get(),
    private val updateExtensionStores: UpdateExtensionStores = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    // KMK -->
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
) : StateScreenModel<ExtensionStoreScreenState>(ExtensionStoreScreenState.Loading) {

    private inline fun updateSuccessState(
        func: (ExtensionStoreScreenState.Success) -> ExtensionStoreScreenState.Success,
    ) {
        mutableState.update {
            when (it) {
                ExtensionStoreScreenState.Loading -> it
                is ExtensionStoreScreenState.Success -> func(it)
            }
        }
    }

    init {
        screenModelScope.launchIO {
            getExtensionStores.subscribe()
                .collectLatest { stores ->
                    mutableState.update {
                        when (it) {
                            ExtensionStoreScreenState.Loading -> ExtensionStoreScreenState.Success(
                                stores = stores,
                                // KMK -->
                                disabledRepos = sourcePreferences.disabledRepos().get(),
                                // KMK <--
                            )
                            is ExtensionStoreScreenState.Success -> it.copy(stores = stores)
                        }
                    }
                }
        }

        // KMK -->
        sourcePreferences.disabledRepos().changes()
            .onEach { disabledRepos ->
                mutableState.update {
                    when (it) {
                        is ExtensionStoreScreenState.Success -> it.copy(disabledRepos = disabledRepos)
                        else -> it
                    }
                }
            }
            .launchIn(screenModelScope)
        // KMK <--
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param indexUrl The baseUrl of the repo to create.
     */
    fun createRepo(indexUrl: String) {
        // KMK -->
        screenModelScope.launchIO {
            // KMK <--
            updateSuccessState {
                it.copy(
                    dialog = when (it.dialog) {
                        is ExtensionStoreDialog.Create -> it.dialog.copy(processing = true)
                        is ExtensionStoreDialog.Confirm -> it.dialog.copy(processing = true)
                        else -> it.dialog
                    },
                )
            }
            addExtensionStore(indexUrl)
                .onSuccess {
                    extensionManager.findAvailableExtensions()
                    dismissDialog()
                }
                .onFailure { throwable ->
                    updateSuccessState {
                        it.copy(
                            dialog = when (it.dialog) {
                                is ExtensionStoreDialog.Create -> it.dialog.copy(
                                    processing = false,
                                    errorMessage = throwable.message ?: "unknown error",
                                )
                                is ExtensionStoreDialog.Confirm -> it.dialog.copy(
                                    processing = false,
                                    errorMessage = throwable.message ?: "unknown error",
                                )
                                else -> it.dialog
                            },
                        )
                    }
                }
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        val status = state.value

        if (status is ExtensionStoreScreenState.Success) {
            screenModelScope.launchIO {
                updateExtensionStores()
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(indexUrl: String) {
        // KMK -->
        // Remove repo from disabled list
        enableStore(indexUrl)
        // KMK <--
        screenModelScope.launchIO {
            removeExtensionStore(indexUrl)
            extensionManager.findAvailableExtensions()
        }
    }

    // KMK -->
    fun enableStore(indexUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos().get()
        if (indexUrl in disabledRepos) {
            sourcePreferences.disabledRepos().set(
                disabledRepos.filterNot { it == indexUrl }.toSet(),
            )
        }
    }

    fun disableStore(indexUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos().get()
        if (indexUrl !in disabledRepos) {
            sourcePreferences.disabledRepos().set(
                disabledRepos + indexUrl,
            )
        }
    }

    fun refreshExtensionList() {
        screenModelScope.launchIO {
            extensionManager.findAvailableExtensions()
        }
    }
    // KMK <--

    fun addFromDeeplink(storeIndexUrl: String) {
        updateSuccessState { state ->
            state.copy(
                dialog = ExtensionStoreDialog.Confirm(
                    url = storeIndexUrl,
                    alreadyExists = state.stores.any { it.indexUrl == storeIndexUrl },
                ),
            )
        }
    }

    fun showDialog(dialog: ExtensionStoreDialog) {
        updateSuccessState { state ->
            state.copy(dialog = dialog)
        }
    }

    fun dismissDialog() {
        updateSuccessState {
            it.copy(dialog = null)
        }
    }
}

sealed class ExtensionStoreDialog {
    data class Create(val processing: Boolean = false, val errorMessage: String? = null) : ExtensionStoreDialog()
    data class Delete(val store: ExtensionStore) : ExtensionStoreDialog()
    data class Confirm(
        val url: String,
        val alreadyExists: Boolean = false,
        val processing: Boolean = false,
        val errorMessage: String? = null,
    ) : ExtensionStoreDialog()
}

sealed class ExtensionStoreScreenState {

    @Immutable
    data object Loading : ExtensionStoreScreenState()

    @Immutable
    data class Success(
        val stores: List<ExtensionStore>,
        val dialog: ExtensionStoreDialog? = null,
        // KMK -->
        val disabledRepos: Set<String> = emptySet(),
        // KMK <--
    ) : ExtensionStoreScreenState() {

        val isEmpty: Boolean
            get() = stores.isEmpty()
    }
}
