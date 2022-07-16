package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.repo.SourceRepoContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.category.repos.RepoPresenter
import eu.kanade.tachiyomi.ui.category.repos.RepoPresenter.Dialog
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SourceRepoScreen(
    presenter: RepoPresenter,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        modifier = Modifier
            .statusBarsPadding(),
        topBar = {
            AppBar(
                navigateUp = navigateUp,
                title = stringResource(R.string.action_edit_repos),
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
        when {
            presenter.isLoading -> LoadingScreen()
            presenter.isEmpty -> EmptyScreen(textResource = R.string.information_empty_category)
            else -> {
                SourceRepoContent(
                    state = presenter,
                    lazyListState = lazyListState,
                    paddingValues = paddingValues + topPaddingValues + PaddingValues(horizontal = horizontalPadding),
                )
            }
        }

        val onDismissRequest = { presenter.dialog = null }
        when (val dialog = presenter.dialog) {
            Dialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = onDismissRequest,
                    onCreate = { presenter.createRepo(it) },
                    title = stringResource(R.string.action_add_repo),
                    extraMessage = stringResource(R.string.action_add_repo_message),
                )
            }
            is Dialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { presenter.deleteRepos(listOf(dialog.repo)) },
                    title = stringResource(R.string.delete_repo),
                    text = stringResource(R.string.delete_repo_confirmation, dialog.repo),
                )
            }
            else -> {}
        }
        LaunchedEffect(Unit) {
            presenter.events.collectLatest { event ->
                when (event) {
                    is RepoPresenter.Event.RepoExists -> {
                        context.toast(R.string.error_repo_exists)
                    }
                    is RepoPresenter.Event.InternalError -> {
                        context.toast(R.string.internal_error)
                    }
                    is RepoPresenter.Event.InvalidName -> {
                        context.toast(R.string.invalid_repo_name)
                    }
                }
            }
        }
    }
}
