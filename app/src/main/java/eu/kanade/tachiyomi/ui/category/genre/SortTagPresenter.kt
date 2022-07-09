package eu.kanade.tachiyomi.ui.category.genre

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.manga.interactor.CreateSortTag
import eu.kanade.domain.manga.interactor.DeleteSortTag
import eu.kanade.domain.manga.interactor.GetSortTag
import eu.kanade.domain.manga.interactor.ReorderSortTag
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [SortTagController]. Used to manage the categories of the library.
 */
class SortTagPresenter(
    private val getSortTag: GetSortTag = Injekt.get(),
    private val createSortTag: CreateSortTag = Injekt.get(),
    private val deleteSortTag: DeleteSortTag = Injekt.get(),
    private val reorderSortTag: ReorderSortTag = Injekt.get(),
) : BasePresenter<SortTagController>() {

    var dialog: Dialog? by mutableStateOf(null)

    /**
     * List containing categories.
     */
    val tags = getSortTag.subscribe()

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events = _events.consumeAsFlow()

    fun createTag(name: String) {
        presenterScope.launchIO {
            when (createSortTag.await(name)) {
                is CreateSortTag.Result.TagExists -> _events.send(Event.TagExists)
                else -> {}
            }
        }
    }

    fun delete(tag: String) {
        presenterScope.launchIO {
            deleteSortTag.await(tag)
        }
    }

    fun moveUp(tag: String, index: Int) {
        presenterScope.launchIO {
            when (reorderSortTag.await(tag, index - 1)) {
                is ReorderSortTag.Result.InternalError -> _events.send(Event.InternalError)
                else -> {}
            }
        }
    }

    fun moveDown(tag: String, index: Int) {
        presenterScope.launchIO {
            when (reorderSortTag.await(tag, index + 1)) {
                is ReorderSortTag.Result.InternalError -> _events.send(Event.InternalError)
                else -> {}
            }
        }
    }

    sealed class Event {
        object TagExists : Event()
        object InternalError : Event()
    }

    sealed class Dialog {
        object Create : Dialog()
        data class Delete(val tag: String) : Dialog()
    }
}
