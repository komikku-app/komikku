package app.komikku.desktop.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.CoverImage
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Desktop counterpart of Android's `MangaCoverDialog` (P10): the cover large, "Save" (file chooser), "Edit cover" (an
 * image file; library and local entries) and "Delete" of a custom cover, with the last action's outcome below. No
 * "Share" on desktop.
 */
@Composable
internal fun MangaCoverDialog(
    manga: Manga,
    dialog: MangaDetailsScreenModel.Dialog.Cover,
    onSave: (dialogTitle: String) -> Unit,
    onEdit: (dialogTitle: String) -> Unit,
    onDeleteCustomCover: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val saveTitle = stringResource(MR.strings.action_save)
    val editTitle = stringResource(MR.strings.action_edit_cover)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("cover-dialog"),
        title = { Text(manga.title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverImage(
                    model = manga.asMangaCover(),
                    contentDescription = stringResource(MR.strings.manga_cover),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(480.dp).heightIn(max = 640.dp).testTag("cover-large"),
                )
                if (dialog.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSave(saveTitle) }, enabled = !dialog.busy, modifier = Modifier.testTag("cover-save")) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Text(saveTitle)
                    }
                    if (dialog.canEdit) {
                        OutlinedButton(onClick = { onEdit(editTitle) }, enabled = !dialog.busy, modifier = Modifier.testTag("cover-edit")) {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                            Text(editTitle)
                        }
                    }
                    if (dialog.isCustomCover) {
                        OutlinedButton(onClick = onDeleteCustomCover, enabled = !dialog.busy, modifier = Modifier.testTag("cover-delete")) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Text(stringResource(MR.strings.action_delete))
                        }
                    }
                }
                dialog.message?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("cover-message"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_close)) } },
    )
}
