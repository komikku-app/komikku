package eu.kanade.tachiyomi.ui.chapterTag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.chapterTag.ChapterTagsScreen
import eu.kanade.presentation.chapterTag.components.ChapterTagRenameDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class ChapterTagsScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ChapterTagsScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is ChapterTagsScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as ChapterTagsScreenState.Success

        ChapterTagsScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(ChapterTagDialog.Create) },
            onClickRename = { screenModel.showDialog(ChapterTagDialog.Rename(it)) },
            onClickDelete = { screenModel.showDialog(ChapterTagDialog.Delete(it)) },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            ChapterTagDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = screenModel::createChapterTag,
                    categories = successState.chapterTags.fastMap { it.name }.toImmutableList(),
                    title = stringResource(KMR.strings.action_add_chapter_tag),
                    alreadyExistsError = KMR.strings.error_chapter_tag_exists,
                )
            }
            is ChapterTagDialog.Rename -> {
                ChapterTagRenameDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onRename = { screenModel.renameChapterTag(dialog.chapterTag, it) },
                    chapterTags = successState.chapterTags.fastMap { it.name }.toImmutableList(),
                    chapterTag = dialog.chapterTag.name,
                )
            }
            is ChapterTagDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteChapterTag(dialog.chapterTag.id) },
                    title = stringResource(KMR.strings.delete_chapter_tag),
                    text = stringResource(KMR.strings.delete_chapter_tag_confirmation, dialog.chapterTag.name),
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is ChapterTagsEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
