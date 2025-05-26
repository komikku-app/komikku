package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel.Dialog
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateDialog
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateDialogScreenModel
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Compose to shows the bulk favorite dialogs.
 *
 * @param bulkFavoriteScreenModel the screen model.
 * @param dialog the dialog to show.
 */
@Composable
fun Screen.BulkFavoriteDialogs(
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    dialog: Dialog?,
) {
    val navigator = LocalNavigator.current
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

    when (dialog) {
        /* Bulk-favorite actions */
        is Dialog.ChangeMangasCategory ->
            ChangeMangasCategoryDialog(
                dialog = dialog,
                navigator = navigator,
                onDismiss = bulkFavoriteScreenModel::dismissDialog,
                onConfirm = { include, exclude ->
                    bulkFavoriteScreenModel.setMangasCategories(dialog.mangas, include, exclude)
                },
            )

        is Dialog.BulkAllowDuplicate ->
            BulkAllowDuplicateDialog(
                dialog = dialog,
                navigator = navigator,
                onDismiss = bulkFavoriteScreenModel::dismissDialog,
                stopRunning = bulkFavoriteScreenModel::stopRunning,
                addFavorite = bulkFavoriteScreenModel::addFavorite,
                showMigrateDialog = bulkFavoriteScreenModel::showMigrateDialog,
                addFavoriteDuplicate = bulkFavoriteScreenModel::addFavoriteDuplicate,
                removeDuplicateSelectedManga = bulkFavoriteScreenModel::removeDuplicateSelectedManga,
            )

        /* Single-favorite actions for screens originally don't have it */
        is Dialog.AddDuplicateManga ->
            AddDuplicateMangaDialog(
                dialog = dialog,
                navigator = navigator,
                state = bulkFavoriteState,
                onDismiss = bulkFavoriteScreenModel::dismissDialog,
                stopRunning = bulkFavoriteScreenModel::stopRunning,
                toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                addFavorite = bulkFavoriteScreenModel::addFavorite,
                showMigrateDialog = bulkFavoriteScreenModel::showMigrateDialog,
            )

        is Dialog.RemoveManga ->
            RemoveMangaDialog(
                dialog = dialog,
                onDismiss = bulkFavoriteScreenModel::dismissDialog,
                changeMangaFavorite = bulkFavoriteScreenModel::changeMangaFavorite,
            )

        is Dialog.Migrate ->
            ShowMigrateDialog(
                dialog = dialog,
                navigator = navigator,
                state = bulkFavoriteState,
                migrateScreenModel = rememberScreenModel { MigrateDialogScreenModel() },
                onDismiss = bulkFavoriteScreenModel::dismissDialog,
                stopRunning = bulkFavoriteScreenModel::stopRunning,
                toggleSelection = bulkFavoriteScreenModel::toggleSelection,
                addFavorite = bulkFavoriteScreenModel::addFavorite,
            )

        else -> {}
    }
}

@Composable
private fun ShowMigrateDialog(
    dialog: Dialog.Migrate,
    navigator: Navigator?,
    state: BulkFavoriteScreenModel.State,
    migrateScreenModel: MigrateDialogScreenModel,
    onDismiss: () -> Unit,
    stopRunning: () -> Unit,
    toggleSelection: (Manga, toSelectedState: Boolean) -> Unit,
    addFavorite: () -> Unit,
) {
    stopRunning()

    MigrateDialog(
        oldManga = dialog.oldManga,
        newManga = dialog.newManga,
        screenModel = migrateScreenModel,
        onDismissRequest = onDismiss,
        onClickTitle = { navigator?.push(MangaScreen(dialog.oldManga.id)) },
        onPopScreen = {
            toggleSelection(dialog.newManga, false)
            onDismiss()
            // `selection.size` is at current value before calling above `toggleSelection`
            if (state.selection.size > 1) {
                // Continue adding favorites
                addFavorite()
            }
        },
    )
}

/**
 * Shows dialog to add a single manga to library when there are duplicates.
 */
@Composable
private fun AddDuplicateMangaDialog(
    dialog: Dialog.AddDuplicateManga,
    navigator: Navigator?,
    state: BulkFavoriteScreenModel.State,
    onDismiss: () -> Unit,
    stopRunning: () -> Unit,
    toggleSelectionMode: () -> Unit,
    addFavorite: (Manga) -> Unit,
    showMigrateDialog: (manga: Manga, duplicate: Manga) -> Unit,
) {
    stopRunning()

    DuplicateMangaDialog(
        duplicates = dialog.duplicates,
        onDismissRequest = onDismiss,
        onConfirm = {
            if (state.selectionMode) {
                toggleSelectionMode()
            }
            addFavorite(dialog.manga)
        },
        onOpenManga = { navigator?.push(MangaScreen(it.id)) },
        onMigrate = { showMigrateDialog(dialog.manga, it) },
        targetManga = dialog.manga,
    )
}

@Composable
private fun RemoveMangaDialog(
    dialog: Dialog.RemoveManga,
    onDismiss: () -> Unit,
    changeMangaFavorite: (Manga) -> Unit,
) {
    RemoveMangaDialog(
        onDismissRequest = onDismiss,
        onConfirm = { changeMangaFavorite(dialog.manga) },
        mangaToRemove = dialog.manga,
    )
}

@Composable
private fun ChangeMangasCategoryDialog(
    dialog: Dialog.ChangeMangasCategory,
    navigator: Navigator?,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>, List<Long>) -> Unit,
) {
    ChangeCategoryDialog(
        initialSelection = dialog.initialSelection,
        onDismissRequest = onDismiss,
        onEditCategories = { navigator?.push(CategoryScreen()) },
        onConfirm = onConfirm,
    )
}

/**
 * Shows dialog to bulk allow/skip or migrate multiple manga to library when there are duplicates.
 */
@Composable
private fun BulkAllowDuplicateDialog(
    dialog: Dialog.BulkAllowDuplicate,
    navigator: Navigator?,
    onDismiss: () -> Unit,
    stopRunning: () -> Unit,
    addFavorite: (startIdx: Int) -> Unit,
    showMigrateDialog: (manga: Manga, duplicate: Manga) -> Unit,
    addFavoriteDuplicate: (skipAllDuplicates: Boolean) -> Unit,
    removeDuplicateSelectedManga: (index: Int) -> Unit,
) {
    DuplicateMangaDialog(
        duplicates = dialog.duplicates,
        onDismissRequest = onDismiss,
        onConfirm = { addFavorite(dialog.currentIdx + 1) },
        onOpenManga = { navigator?.push(MangaScreen(it.id)) },
        onMigrate = { showMigrateDialog(dialog.manga, it) },
        targetManga = dialog.manga,
        bulkFavoriteManga = dialog.manga,
        onAllowAllDuplicate = { addFavoriteDuplicate(false) },
        onSkipAllDuplicate = { addFavoriteDuplicate(true) },
        onSkipDuplicate = {
            removeDuplicateSelectedManga(dialog.currentIdx)
            addFavorite(dialog.currentIdx)
        },
        stopRunning = stopRunning,
    )
}

@Composable
fun bulkSelectionButton(
    isRunning: Boolean,
    toggleSelectionMode: () -> Unit,
): AppBar.AppBarAction {
    val title = stringResource(KMR.strings.action_bulk_select)
    return if (isRunning) {
        AppBar.ActionCompose(
            title = title,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    } else {
        AppBar.Action(
            title = title,
            icon = Icons.Outlined.Checklist,
            onClick = toggleSelectionMode,
        )
    }
}
