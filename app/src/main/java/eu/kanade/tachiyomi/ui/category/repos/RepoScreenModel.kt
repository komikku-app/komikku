package eu.kanade.tachiyomi.ui.category.repos

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.source.interactor.CreateSourceRepo
import eu.kanade.domain.source.interactor.DeleteSourceRepos
import eu.kanade.domain.source.interactor.GetSourceRepos
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RepoScreenModel(
    private val getSourceRepos: GetSourceRepos = Injekt.get(),
    private val createSourceRepo: CreateSourceRepo = Injekt.get(),
    private val deleteSourceRepos: DeleteSourceRepos = Injekt.get(),
) : StateScreenModel<RepoScreenState>(RepoScreenState.Loading) {

    private val _events: Channel<RepoEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        coroutineScope.launchIO {
            getSourceRepos.subscribe()
                .collectLatest { repos ->
                    mutableState.update {
                        RepoScreenState.Success(
                            repos = repos,
                        )
                    }
                }
        }
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param name The name of the repo to create.
     */
    fun createRepo(name: String) {
        coroutineScope.launchIO {
            when (createSourceRepo.await(name)) {
                is CreateSourceRepo.Result.InvalidName -> _events.send(RepoEvent.InvalidName)
                else -> {}
            }
        }
    }

    /**
     * Deletes the given repos from the database.
     *
     * @param repos The list of repos to delete.
     */
    fun deleteRepos(repos: List<String>) {
        coroutineScope.launchIO {
            deleteSourceRepos.await(repos)
        }
    }

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
    sealed class LocalizedMessage(@StringRes val stringRes: Int) : RepoEvent()
    object InvalidName : LocalizedMessage(R.string.invalid_repo_name)
    object InternalError : LocalizedMessage(R.string.internal_error)
}

sealed class RepoDialog {
    object Create : RepoDialog()
    data class Delete(val repo: String) : RepoDialog()
}

sealed class RepoScreenState {

    @Immutable
    object Loading : RepoScreenState()

    @Immutable
    data class Success(
        val repos: List<String>,
        val dialog: RepoDialog? = null,
    ) : RepoScreenState() {

        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
