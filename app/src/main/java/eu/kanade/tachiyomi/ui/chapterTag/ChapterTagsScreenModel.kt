package eu.kanade.tachiyomi.ui.chapterTag

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.chapterTag.interactor.CreateChapterTagWithName
import tachiyomi.domain.chapterTag.interactor.DeleteChapterTag
import tachiyomi.domain.chapterTag.interactor.GetChapterTags
import tachiyomi.domain.chapterTag.interactor.RenameChapterTag
import tachiyomi.domain.chapterTag.model.ChapterTag
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ChapterTagsScreenModel(
    private val getChapterTags: GetChapterTags = Injekt.get(),
    private val createChapterTagWithName: CreateChapterTagWithName = Injekt.get(),
    private val renameChapterTag: RenameChapterTag = Injekt.get(),
    private val deleteChapterTag: DeleteChapterTag = Injekt.get(),
) : StateScreenModel<ChapterTagsScreenState>(ChapterTagsScreenState.Loading) {

    private val _events: Channel<ChapterTagsEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            getChapterTags.subscribe()
                .collectLatest { chapterTags ->
                    mutableState.update {
                        ChapterTagsScreenState.Success(
                            chapterTags = chapterTags.toImmutableList(),
                        )
                    }
                }
        }
    }

    fun createChapterTag(name: String) {
        screenModelScope.launch {
            when (createChapterTagWithName.await(name)) {
                is CreateChapterTagWithName.Result.InternalError -> _events.send(ChapterTagsEvent.InternalError)
                else -> {}
            }
        }
    }

    fun renameChapterTag(chapterTag: ChapterTag, name: String) {
        screenModelScope.launch {
            when (renameChapterTag.await(chapterTag, name)) {
                is RenameChapterTag.Result.InternalError -> _events.send(ChapterTagsEvent.InternalError)
                else -> {}
            }
        }
    }

    fun deleteChapterTag(tagId: Long) {
        screenModelScope.launch {
            when (deleteChapterTag.await(tagId)) {
                is DeleteChapterTag.Result.InternalError -> _events.send(ChapterTagsEvent.InternalError)
                else -> {}
            }
        }
    }

    fun showDialog(dialog: ChapterTagDialog) {
        mutableState.update {
            when (it) {
                ChapterTagsScreenState.Loading -> it
                is ChapterTagsScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                ChapterTagsScreenState.Loading -> it
                is ChapterTagsScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface ChapterTagDialog {
    data object Create : ChapterTagDialog
    data class Rename(val chapterTag: ChapterTag) : ChapterTagDialog
    data class Delete(val chapterTag: ChapterTag) : ChapterTagDialog
}

sealed interface ChapterTagsEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : ChapterTagsEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface ChapterTagsScreenState {

    @Immutable
    data object Loading : ChapterTagsScreenState

    @Immutable
    data class Success(
        val chapterTags: ImmutableList<ChapterTag>,
        val dialog: ChapterTagDialog? = null,
    ) : ChapterTagsScreenState {

        val isEmpty: Boolean
            get() = chapterTags.isEmpty()
    }
}
