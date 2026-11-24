package app.komikku.desktop.ui.browse

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.manga.DuplicateMangaDialog
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.desktop.ui.more.CategoriesScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

/*
 * P14: Komikku's bulk favourites of browse results (Android's `BulkFavoriteScreenModel`, borrowed, with the desktop's
 * dialogs): a selection mode in the catalogue and in global search; "Add to library" checks every selected entry for
 * library duplicates (P10's dialog, with Android's allow all / skip all / skip this one), then asks for categories like
 * Android (default category, none, or the category dialog with the entries' common / mixed categories preselected).
 */

/** Android's `bulkSelectionButton`: enters / leaves the selection mode; a spinner while entries are being added. */
@Composable
internal fun BulkSelectionButton(isRunning: Boolean, onClick: () -> Unit) {
    if (isRunning) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp).testTag("bulk-running"), strokeWidth = 2.dp)
    } else {
        IconButton(onClick = onClick, modifier = Modifier.testTag("bulk-select")) {
            Icon(Icons.Outlined.Checklist, contentDescription = stringResource(KMR.strings.action_bulk_select))
        }
    }
}

/** Android's `BulkSelectionToolbar` actions: select all, select inverse, add to library (a spinner while adding). */
@Composable
internal fun RowScope.BulkSelectionActions(
    state: BulkFavoriteScreenModel.State,
    onSelectAll: () -> Unit,
    onReverseSelection: () -> Unit,
    onAddToLibrary: () -> Unit,
) {
    IconButton(onClick = onSelectAll, modifier = Modifier.testTag("bulk-select-all")) {
        Icon(Icons.Filled.SelectAll, contentDescription = stringResource(MR.strings.action_select_all))
    }
    IconButton(onClick = onReverseSelection, modifier = Modifier.testTag("bulk-select-inverse")) {
        Icon(Icons.Outlined.FlipToBack, contentDescription = stringResource(MR.strings.action_select_inverse))
    }
    if (state.isRunning) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp).testTag("bulk-running"), strokeWidth = 2.dp)
    } else {
        IconButton(
            // Like Android's toolbar: nothing to add without a selection
            onClick = { if (state.selection.isNotEmpty()) onAddToLibrary() },
            modifier = Modifier.testTag("bulk-add"),
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = stringResource(MR.strings.add_to_library))
        }
    }
}

/** The selection's click rules (Android's): a click toggles while selecting, else opens; a long press starts selecting. */
internal class BulkClicks(
    private val model: BulkFavoriteScreenModel,
    private val selectionMode: Boolean,
    private val open: (Manga) -> Unit,
) {
    fun onClick(manga: Manga) {
        if (selectionMode) model.toggleSelection(manga) else open(manga)
    }

    /** Android opens the details on a long press while selecting (its long press otherwise adds / removes one entry). */
    fun onLongClick(manga: Manga) {
        if (selectionMode) open(manga) else model.select(manga)
    }
}

/** Android's `BulkFavoriteDialogs` with the desktop's dialogs (no migration on desktop). */
@Composable
internal fun BulkFavoriteDialogs(model: BulkFavoriteScreenModel, dialog: BulkFavoriteScreenModel.Dialog?) {
    val navigator = LocalNavigator.currentOrThrow
    when (dialog) {
        is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory -> ChangeCategoryDialog(
            initialSelection = dialog.initialSelection,
            onDismissRequest = model::dismissDialog,
            onEditCategories = { navigator.push(CategoriesScreen()) },
            onConfirm = { include, exclude -> model.setMangasCategories(dialog.mangas, include, exclude) },
        )
        is BulkFavoriteScreenModel.Dialog.BulkAllowDuplicate -> DuplicateMangaDialog(
            targetManga = dialog.manga,
            duplicates = dialog.duplicates,
            onDismissRequest = {
                model.dismissDialog()
                model.stopRunning()
            },
            // "Add anyway": this one stays selected, the next duplicate is looked for
            onConfirm = { model.addFavorite(dialog.currentIdx + 1) },
            onOpenManga = { navigator.push(MangaDetailsScreen(it.id)) },
            onAllowAllDuplicate = { model.addFavoriteDuplicate(skipAllDuplicates = false) },
            onSkipAllDuplicate = { model.addFavoriteDuplicate(skipAllDuplicates = true) },
            onSkipDuplicate = {
                model.removeDuplicateSelectedManga(dialog.currentIdx)
                model.addFavorite(dialog.currentIdx)
            },
        )
        is BulkFavoriteScreenModel.Dialog.AddDuplicateManga -> {
            LaunchedEffect(dialog) { model.stopRunning() }
            DuplicateMangaDialog(
                targetManga = dialog.manga,
                duplicates = dialog.duplicates,
                onDismissRequest = model::dismissDialog,
                onConfirm = {
                    model.toggleSelectionMode(false)
                    model.addFavorite(dialog.manga)
                },
                onOpenManga = { navigator.push(MangaDetailsScreen(it.id)) },
            )
        }
        // Not reached on desktop: removing (Android's long press on a library entry) and migrating are not offered
        is BulkFavoriteScreenModel.Dialog.RemoveManga, is BulkFavoriteScreenModel.Dialog.Migrate -> {
            LaunchedEffect(dialog) { model.dismissDialog() }
        }
        null -> Unit
    }
}
