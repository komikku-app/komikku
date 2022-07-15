package eu.kanade.tachiyomi.ui.category.repos

import android.os.Bundle
import eu.kanade.domain.source.interactor.CreateSourceRepo
import eu.kanade.domain.source.interactor.DeleteSourceRepos
import eu.kanade.domain.source.interactor.GetSourceRepos
import eu.kanade.presentation.category.SourceRepoState
import eu.kanade.presentation.category.SourceRepoStateImpl
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [RepoController]. Used to manage the repos for the extensions.
 */
class RepoPresenter(
    private val state: SourceRepoStateImpl = SourceRepoState() as SourceRepoStateImpl,
    private val getSourceRepos: GetSourceRepos = Injekt.get(),
    private val createSourceRepo: CreateSourceRepo = Injekt.get(),
    private val deleteSourceRepos: DeleteSourceRepos = Injekt.get(),
) : BasePresenter<RepoController>(), SourceRepoState by state {

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events = _events.consumeAsFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            getSourceRepos.subscribe()
                .collectLatest {
                    state.isLoading = false
                    state.repos = it
                }
        }
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param name The name of the repo to create.
     */
    fun createRepo(name: String) {
        presenterScope.launchIO {
            when (createSourceRepo.await(name)) {
                is CreateSourceRepo.Result.RepoExists -> _events.send(Event.RepoExists)
                is CreateSourceRepo.Result.InvalidName -> _events.send(Event.InvalidName)
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
        presenterScope.launchIO {
            deleteSourceRepos.await(repos)
        }
    }

    sealed class Event {
        object RepoExists : Event()
        object InvalidName : Event()
        object InternalError : Event()
    }

    sealed class Dialog {
        object Create : Dialog()
        data class Delete(val repo: String) : Dialog()
    }
}
