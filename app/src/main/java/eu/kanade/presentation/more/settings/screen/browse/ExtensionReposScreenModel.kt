package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import mihon.domain.extensionrepo.interactor.CreateExtensionRepo
import mihon.domain.extensionrepo.interactor.DeleteExtensionRepo
import mihon.domain.extensionrepo.interactor.GetExtensionRepo
import mihon.domain.extensionrepo.interactor.ReplaceExtensionRepo
import mihon.domain.extensionrepo.interactor.UpdateExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionReposScreenModel(
    private val getExtensionRepo: GetExtensionRepo = Injekt.get(),
    private val createExtensionRepo: CreateExtensionRepo = Injekt.get(),
    private val deleteExtensionRepo: DeleteExtensionRepo = Injekt.get(),
    private val replaceExtensionRepo: ReplaceExtensionRepo = Injekt.get(),
    private val updateExtensionRepo: UpdateExtensionRepo = Injekt.get(),
    // KMK -->
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
) : StateScreenModel<RepoScreenState>(RepoScreenState.Loading) {

    private val _events: Channel<RepoEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getExtensionRepo.subscribeAll()
                .collectLatest { repos ->
                    mutableState.update {
                        RepoScreenState.Success(
                            repos = repos.toImmutableSet(),
                            // KMK -->
                            disabledRepos = sourcePreferences.disabledRepos().get(),
                            // KMK <--
                        )
                    }
                }
        }

        // KMK -->
        sourcePreferences.disabledRepos().changes()
            .onEach { disabledRepos ->
                mutableState.update {
                    when (it) {
                        is RepoScreenState.Success -> it.copy(disabledRepos = disabledRepos)
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
     * @param baseUrl The baseUrl of the repo to create.
     */
    fun createRepo(baseUrl: String) {
        screenModelScope.launchIO {
            when (val result = createExtensionRepo.await(baseUrl)) {
                CreateExtensionRepo.Result.InvalidUrl -> _events.send(RepoEvent.InvalidUrl)
                CreateExtensionRepo.Result.RepoAlreadyExists -> _events.send(RepoEvent.RepoAlreadyExists)
                is CreateExtensionRepo.Result.DuplicateFingerprint -> {
                    showDialog(RepoDialog.Conflict(result.oldRepo, result.newRepo))
                }

                else -> {}
            }
        }
    }

    /**
     * Inserts a repo to the database, replace a matching repo with the same signing key fingerprint if found.
     *
     * @param newRepo The repo to insert
     */
    fun replaceRepo(newRepo: ExtensionRepo) {
        screenModelScope.launchIO {
            replaceExtensionRepo.await(newRepo)
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        val status = state.value

        if (status is RepoScreenState.Success) {
            screenModelScope.launchIO {
                updateExtensionRepo.awaitAll()
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        // KMK -->
        // Remove repo from disabled list
        enableRepo(baseUrl)
        // KMK <--
        screenModelScope.launchIO {
            deleteExtensionRepo.await(baseUrl)
        }
    }

    // KMK -->
    fun enableRepo(baseUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos().get()
        if (baseUrl in disabledRepos) {
            sourcePreferences.disabledRepos().set(
                disabledRepos.filterNot { it == baseUrl }.toSet(),
            )
        }
    }

    fun disableRepo(baseUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos().get()
        if (baseUrl !in disabledRepos) {
            sourcePreferences.disabledRepos().set(
                disabledRepos + baseUrl,
            )
        }
    }
    // KMK <--

    fun showDialog(dialog: RepoDialog) {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class RepoEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : RepoEvent()
    data object InvalidUrl : LocalizedMessage(MR.strings.invalid_repo_name)
    data object RepoAlreadyExists : LocalizedMessage(MR.strings.error_repo_exists)
}

sealed class RepoDialog {
    data object Create : RepoDialog()
    data class Delete(val repo: String) : RepoDialog()
    data class Conflict(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : RepoDialog()
    data class Confirm(val url: String) : RepoDialog()
}

sealed class RepoScreenState {

    @Immutable
    data object Loading : RepoScreenState()

    @Immutable
    data class Success(
        val repos: ImmutableSet<ExtensionRepo>,
        val oldRepos: ImmutableSet<String>? = null,
        val dialog: RepoDialog? = null,
        // KMK -->
        val disabledRepos: Set<String> = emptySet(),
        // KMK <--
    ) : RepoScreenState() {

        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
