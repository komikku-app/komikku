package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryTopAppBar
import eu.kanade.presentation.category.components.genre.SortTagContent
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.category.genre.SortTagPresenter
import eu.kanade.tachiyomi.ui.category.genre.SortTagPresenter.Dialog
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SortTagScreen(
    presenter: SortTagPresenter,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val topAppBarScrollState = rememberTopAppBarScrollState()
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarScrollState)
    Scaffold(
        modifier = Modifier
            .statusBarsPadding()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            CategoryTopAppBar(
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                navigateUp = navigateUp,
                title = stringResource(id = R.string.action_edit_tags),
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = { presenter.dialog = Dialog.Create },
            )
        },
    ) { paddingValues ->
        val context = LocalContext.current
        val tags by presenter.tags.collectAsState(initial = emptyList())
        if (tags.isEmpty()) {
            EmptyScreen(textResource = R.string.information_empty_tags)
        } else {
            SortTagContent(
                categories = tags,
                lazyListState = lazyListState,
                paddingValues = paddingValues + topPaddingValues + PaddingValues(horizontal = horizontalPadding),
                onMoveUp = { tag, index -> presenter.moveUp(tag, index) },
                onMoveDown = { tag, index -> presenter.moveDown(tag, index) },
                onDelete = { presenter.dialog = Dialog.Delete(it) },
            )
        }
        val onDismissRequest = { presenter.dialog = null }
        when (val dialog = presenter.dialog) {
            Dialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = onDismissRequest,
                    onCreate = { presenter.createTag(it) },
                    title = stringResource(R.string.add_tag),
                    extraMessage = stringResource(R.string.action_add_tags_message),
                )
            }
            is Dialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { presenter.delete(dialog.tag) },
                    title = stringResource(R.string.delete_tag),
                    text = stringResource(R.string.delete_tag_confirmation, dialog.tag),
                )
            }
            else -> {}
        }
        LaunchedEffect(Unit) {
            presenter.events.collectLatest { event ->
                when (event) {
                    is SortTagPresenter.Event.TagExists -> {
                        context.toast(R.string.error_tag_exists)
                    }
                    is SortTagPresenter.Event.InternalError -> {
                        context.toast(R.string.internal_error)
                    }
                }
            }
        }
    }
}
