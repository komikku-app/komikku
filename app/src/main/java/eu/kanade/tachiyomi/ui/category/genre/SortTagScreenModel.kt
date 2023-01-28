package eu.kanade.tachiyomi.ui.category.genre

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.manga.interactor.CreateSortTag
import eu.kanade.domain.manga.interactor.DeleteSortTag
import eu.kanade.domain.manga.interactor.GetSortTag
import eu.kanade.domain.manga.interactor.ReorderSortTag
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SortTagScreenModel(
    private val getSortTag: GetSortTag = Injekt.get(),
    private val createSortTag: CreateSortTag = Injekt.get(),
    private val deleteSortTag: DeleteSortTag = Injekt.get(),
    private val reorderSortTag: ReorderSortTag = Injekt.get(),
) : StateScreenModel<SortTagScreenState>(SortTagScreenState.Loading) {

    private val _events: Channel<SortTagEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        coroutineScope.launchIO {
            getSortTag.subscribe()
                .collectLatest { tags ->
                    mutableState.update {
                        SortTagScreenState.Success(
                            tags = tags,
                        )
                    }
                }
        }
    }

    fun createTag(name: String) {
        coroutineScope.launchIO {
            when (createSortTag.await(name)) {
                is CreateSortTag.Result.TagExists -> _events.send(SortTagEvent.TagExists)
                else -> {}
            }
        }
    }

    fun delete(tag: String) {
        coroutineScope.launchIO {
            deleteSortTag.await(tag)
        }
    }

    fun moveUp(tag: String, index: Int) {
        coroutineScope.launchIO {
            when (reorderSortTag.await(tag, index - 1)) {
                is ReorderSortTag.Result.InternalError -> _events.send(SortTagEvent.InternalError)
                else -> {}
            }
        }
    }

    fun moveDown(tag: String, index: Int) {
        coroutineScope.launchIO {
            when (reorderSortTag.await(tag, index + 1)) {
                is ReorderSortTag.Result.InternalError -> _events.send(SortTagEvent.InternalError)
                else -> {}
            }
        }
    }

    fun showDialog(dialog: SortTagDialog) {
        mutableState.update {
            when (it) {
                SortTagScreenState.Loading -> it
                is SortTagScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                SortTagScreenState.Loading -> it
                is SortTagScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class SortTagEvent {
    sealed class LocalizedMessage(@StringRes val stringRes: Int) : SortTagEvent()
    object TagExists : LocalizedMessage(R.string.error_tag_exists)
    object InternalError : LocalizedMessage(R.string.internal_error)
}

sealed class SortTagDialog {
    object Create : SortTagDialog()
    data class Delete(val tag: String) : SortTagDialog()
}

sealed class SortTagScreenState {

    @Immutable
    object Loading : SortTagScreenState()

    @Immutable
    data class Success(
        val tags: List<String>,
        val dialog: SortTagDialog? = null,
    ) : SortTagScreenState() {

        val isEmpty: Boolean
            get() = tags.isEmpty()
    }
}
