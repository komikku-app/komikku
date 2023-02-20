package eu.kanade.tachiyomi.ui.category.genre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

class SortTagScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { SortTagScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is SortTagScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as SortTagScreenState.Success

        eu.kanade.presentation.category.SortTagScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(SortTagDialog.Create) },
            onClickDelete = { screenModel.showDialog(SortTagDialog.Delete(it)) },
            onClickMoveUp = screenModel::moveUp,
            onClickMoveDown = screenModel::moveDown,
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            SortTagDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createTag(it) },
                    categories = successState.tags,
                    title = stringResource(R.string.add_tag),
                    extraMessage = stringResource(R.string.action_add_tags_message),
                    alreadyExistsError = R.string.error_tag_exists,
                )
            }
            is SortTagDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.delete(dialog.tag) },
                    title = stringResource(R.string.delete_tag),
                    text = stringResource(R.string.delete_tag_confirmation, dialog.tag),
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is SortTagEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
