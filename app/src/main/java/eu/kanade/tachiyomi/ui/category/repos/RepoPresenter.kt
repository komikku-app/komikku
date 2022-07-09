package eu.kanade.tachiyomi.ui.category.repos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.source.interactor.CreateSourceRepo
import eu.kanade.domain.source.interactor.DeleteSourceRepos
import eu.kanade.domain.source.interactor.GetSourceRepos
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [RepoController]. Used to manage the repos for the extensions.
 */
class RepoPresenter(
    private val getSourceRepos: GetSourceRepos = Injekt.get(),
    private val createSourceRepo: CreateSourceRepo = Injekt.get(),
    private val deleteSourceRepos: DeleteSourceRepos = Injekt.get(),
) : BasePresenter<RepoController>() {

    var dialog: Dialog? by mutableStateOf(null)

    val repos = getSourceRepos.subscribe()

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events = _events.consumeAsFlow()

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
