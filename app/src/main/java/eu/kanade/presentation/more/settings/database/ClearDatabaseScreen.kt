package eu.kanade.presentation.more.settings.database

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseContent
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseDeleteDialog
import eu.kanade.presentation.more.settings.database.components.ClearDatabaseToolbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.setting.database.ClearDatabasePresenter
import eu.kanade.tachiyomi.util.system.toast

@Composable
fun ClearDatabaseScreen(
    presenter: ClearDatabasePresenter,
    navigateUp: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = { scrollBehavior ->
            ClearDatabaseToolbar(
                state = presenter,
                navigateUp = navigateUp,
                onClickSelectAll = { presenter.selectAll() },
                onClickInvertSelection = { presenter.invertSelection() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        ClearDatabaseContent(
            state = presenter,
            contentPadding = paddingValues,
            onClickSelection = { source ->
                presenter.toggleSelection(source)
            },
            onClickDelete = {
                presenter.dialog = ClearDatabasePresenter.Dialog.Delete(presenter.selection)
            },
        )
    }
    val dialog = presenter.dialog
    if (dialog is ClearDatabasePresenter.Dialog.Delete) {
        ClearDatabaseDeleteDialog(
            onDismissRequest = { presenter.dialog = null },
            onDelete = {
                presenter.removeMangaBySourceId(dialog.sourceIds, /* SY --> */ it /* SY <-- */)
                presenter.clearSelection()
                presenter.dialog = null
                context.toast(R.string.clear_database_completed)
            },
        )
    }
}
