package eu.kanade.tachiyomi.ui.category.genre

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.interactor.CreateSortTag
import eu.kanade.domain.manga.interactor.DeleteSortTag
import eu.kanade.domain.manga.interactor.GetSortTag
import eu.kanade.domain.manga.interactor.ReorderSortTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
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
        screenModelScope.launchIO {
            getSortTag.subscribe()
                .collectLatest { tags ->
                    mutableState.update {
                        SortTagScreenState.Success(
                            tags = tags.toImmutableList(),
                        )
                    }
                }
        }
    }

    fun createTag(name: String) {
        screenModelScope.launchIO {
            when (createSortTag.await(name)) {
                is CreateSortTag.Result.TagExists -> _events.send(SortTagEvent.TagExists)
                else -> {}
            }
        }
    }

    fun delete(tag: String) {
        screenModelScope.launchIO {
            deleteSortTag.await(tag)
        }
    }

    fun moveUp(tag: String, index: Int) {
        screenModelScope.launchIO {
            when (reorderSortTag.await(tag, index - 1)) {
                is ReorderSortTag.Result.InternalError -> _events.send(SortTagEvent.InternalError)
                else -> {}
            }
        }
    }

    fun moveDown(tag: String, index: Int) {
        screenModelScope.launchIO {
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
    sealed class LocalizedMessage(val stringRes: StringResource) : SortTagEvent()
    data object TagExists : LocalizedMessage(SYMR.strings.error_tag_exists)
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed class SortTagDialog {
    data object Create : SortTagDialog()
    data class Delete(val tag: String) : SortTagDialog()
}

sealed class SortTagScreenState {

    @Immutable
    data object Loading : SortTagScreenState()

    @Immutable
    data class Success(
        val tags: ImmutableList<String>,
        val dialog: SortTagDialog? = null,
    ) : SortTagScreenState() {

        val isEmpty: Boolean
            get() = tags.isEmpty()
    }
}
