package app.komikku.desktop.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DownloadDropdownMenu
import eu.kanade.presentation.manga.DownloadAction
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal

/** Keyboard modifiers held when a library entry was clicked. */
data class LibraryClickModifiers(val shift: Boolean = false, val toggle: Boolean = false)

/**
 * Android's `LibrarySelectionToolbar`: the selection count, unselect all, select all and invert selection (both on the
 * shown page, like Android).
 */
@Composable
internal fun LibrarySelectionToolbar(
    count: Int,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("library-selection-toolbar"),
    ) {
        IconButton(onClick = onClickUnselectAll) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_cancel))
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("library-selection-count"),
        )
        IconButton(onClick = onClickSelectAll) {
            Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(MR.strings.action_select_all))
        }
        IconButton(onClick = onClickInvertSelection) {
            Icon(Icons.Outlined.FlipToBack, contentDescription = stringResource(MR.strings.action_select_inverse))
        }
    }
}

/**
 * Android's `LibraryBottomActionMenu` for the actions the desktop has: change categories, mark read / unread, download
 * (Android's download amounts; not offered when a local entry is selected, like Android), update the selected entries
 * (KMK) and remove. Not on desktop: migrate, merge, recommendations and the SY title cleaning / MangaDex follow / info
 * reset actions.
 */
@Composable
internal fun LibrarySelectionBar(
    selectedManga: List<Manga>,
    onChangeCategoryClicked: () -> Unit,
    onMarkAsReadClicked: () -> Unit,
    onMarkAsUnreadClicked: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    onUpdateClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("library-selection-bar")) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            SelectionAction(stringResource(MR.strings.action_move_category), Icons.AutoMirrored.Outlined.Label, onChangeCategoryClicked)
            SelectionAction(stringResource(MR.strings.action_mark_as_read), Icons.Outlined.DoneAll, onMarkAsReadClicked)
            SelectionAction(stringResource(MR.strings.action_mark_as_unread), Icons.Outlined.RemoveDone, onMarkAsUnreadClicked)
            if (selectedManga.none { it.isLocal() }) {
                var downloadExpanded by remember { mutableStateOf(false) }
                Box {
                    SelectionAction(stringResource(MR.strings.action_download), Icons.Outlined.Download) {
                        downloadExpanded = !downloadExpanded
                    }
                    DownloadDropdownMenu(
                        expanded = downloadExpanded,
                        onDismissRequest = { downloadExpanded = false },
                        onDownloadClicked = onDownloadClicked,
                    )
                }
            }
            SelectionAction(stringResource(KMR.strings.action_update), Icons.Outlined.Refresh, onUpdateClicked)
            SelectionAction(stringResource(MR.strings.action_delete), Icons.Outlined.Delete, onDeleteClicked)
        }
    }
}

@Composable
private fun SelectionAction(title: String, icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = title)
    }
}
