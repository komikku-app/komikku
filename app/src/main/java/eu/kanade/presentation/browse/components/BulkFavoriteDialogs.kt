package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel.Dialog
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateDialog
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateDialogScreenModel
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Compose to shows the bulk favorite dialogs.
 *
 * @param bulkFavoriteScreenModel the screen model.
 * @param dialog the dialog to show.
 */
@Composable
fun BulkFavoriteDialogs(
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    dialog: Dialog?,
) {
    when (dialog) {
        /* Bulk-favorite actions */
        is Dialog.ChangeMangasCategory ->
            ChangeMangasCategoryDialog(
                bulkFavoriteScreenModel,
                onConfirm = { include, exclude ->
                    bulkFavoriteScreenModel.setMangasCategories(dialog.mangas, include, exclude)
                },
            )

        is Dialog.BulkAllowDuplicate ->
            BulkAllowDuplicateDialog(bulkFavoriteScreenModel)

        /* Single-favorite actions for screens originally don't have it */
        is Dialog.AddDuplicateManga ->
            AddDuplicateMangaDialog(bulkFavoriteScreenModel)

        is Dialog.RemoveManga ->
            RemoveMangaDialog(bulkFavoriteScreenModel)

        is Dialog.Migrate ->
            ShowMigrateDialog(bulkFavoriteScreenModel)

        else -> {}
    }
}

@Composable
private fun ShowMigrateDialog(bulkFavoriteScreenModel: BulkFavoriteScreenModel) {
    val navigator = LocalNavigator.current
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as Dialog.Migrate

    bulkFavoriteScreenModel.stopRunning()

    MigrateDialog(
        oldManga = dialog.oldManga,
        newManga = dialog.newManga,
        screenModel = MigrateDialogScreenModel(),
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onClickTitle = { navigator?.push(MangaScreen(dialog.oldManga.id)) },
        onPopScreen = {
            bulkFavoriteScreenModel.toggleSelection(dialog.newManga, toSelectedState = false)
            bulkFavoriteScreenModel.dismissDialog()
            when {
                // `selectionMode` is current state before calling above `toggleSelection`
                !bulkFavoriteState.selectionMode -> {
                    navigator?.push(MangaScreen(dialog.newManga.id))
                }
                // `selection.size` is at current value before calling above `toggleSelection`
                bulkFavoriteState.selection.size > 1 -> {
                    // Continue adding favorites
                    bulkFavoriteScreenModel.addFavorite()
                }
            }
        },
    )
}

/**
 * Shows dialog to add a single manga to library when there are duplicates.
 *
 * @param bulkFavoriteScreenModel the screen model.
 */
@Composable
private fun AddDuplicateMangaDialog(bulkFavoriteScreenModel: BulkFavoriteScreenModel) {
    val navigator = LocalNavigator.current
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as Dialog.AddDuplicateManga

    bulkFavoriteScreenModel.stopRunning()

    DuplicateMangaDialog(
        duplicates = dialog.duplicates,
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onConfirm = {
            if (bulkFavoriteState.selectionMode) {
                bulkFavoriteScreenModel.toggleSelectionMode()
            }
            bulkFavoriteScreenModel.addFavorite(dialog.manga)
        },
        onOpenManga = { navigator?.push(MangaScreen(it.id)) },
        onMigrate = {
            bulkFavoriteScreenModel.showMigrateDialog(
                manga = dialog.manga,
                duplicate = it,
            )
        },
    )
}

@Composable
private fun RemoveMangaDialog(bulkFavoriteScreenModel: BulkFavoriteScreenModel) {
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as Dialog.RemoveManga

    RemoveMangaDialog(
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onConfirm = {
            bulkFavoriteScreenModel.changeMangaFavorite(dialog.manga)
        },
        mangaToRemove = dialog.manga,
    )
}

@Composable
private fun ChangeMangasCategoryDialog(
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    onConfirm: (List<Long>, List<Long>) -> Unit,
) {
    val navigator = LocalNavigator.current
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as Dialog.ChangeMangasCategory

    ChangeCategoryDialog(
        initialSelection = dialog.initialSelection,
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onEditCategories = { navigator?.push(CategoryScreen()) },
        onConfirm = onConfirm,
    )
}

/**
 * Shows dialog to bulk allow/skip or migrate multiple manga to library when there are duplicates.
 *
 * @param bulkFavoriteScreenModel the screen model.
 */
@Composable
private fun BulkAllowDuplicateDialog(bulkFavoriteScreenModel: BulkFavoriteScreenModel) {
    val navigator = LocalNavigator.current
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as Dialog.BulkAllowDuplicate

    DuplicateMangaDialog(
        duplicates = dialog.duplicates,
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onConfirm = {
            bulkFavoriteScreenModel.addFavorite(startIdx = dialog.currentIdx + 1)
        },
        onOpenManga = { navigator?.push(MangaScreen(it.id)) },
        onMigrate = {
            bulkFavoriteScreenModel.showMigrateDialog(
                manga = dialog.manga,
                duplicate = it,
            )
        },
        bulkFavoriteManga = dialog.manga,
        onAllowAllDuplicate = bulkFavoriteScreenModel::addFavoriteDuplicate,
        onSkipAllDuplicate = {
            bulkFavoriteScreenModel.addFavoriteDuplicate(skipAllDuplicates = true)
        },
        onSkipDuplicate = {
            bulkFavoriteScreenModel.removeDuplicateSelectedManga(index = dialog.currentIdx)
            bulkFavoriteScreenModel.addFavorite(startIdx = dialog.currentIdx)
        },
        stopRunning = bulkFavoriteScreenModel::stopRunning,
    )
}

@Composable
fun bulkSelectionButton(
    isRunning: Boolean,
    toggleSelectionMode: () -> Unit,
) = AppBar.Action(
    title = stringResource(KMR.strings.action_bulk_select),
    icon = Icons.Outlined.Checklist,
    iconTint = MaterialTheme.colorScheme.primary.takeIf { isRunning },
    onClick = toggleSelectionMode,
)
