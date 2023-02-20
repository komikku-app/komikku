package eu.kanade.tachiyomi.ui.category.repos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.SourceRepoScreen
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

class RepoScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RepoScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is RepoScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as RepoScreenState.Success

        SourceRepoScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(RepoDialog.Create) },
            onClickDelete = { screenModel.showDialog(RepoDialog.Delete(it)) },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            RepoDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(it) },
                    categories = successState.repos,
                    title = stringResource(R.string.action_add_repo),
                    extraMessage = stringResource(R.string.action_add_repo_message),
                    alreadyExistsError = R.string.error_repo_exists,
                )
            }
            is RepoDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteRepos(listOf(dialog.repo)) },
                    title = stringResource(R.string.delete_repo),
                    text = stringResource(R.string.delete_repo_confirmation, dialog.repo),
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
