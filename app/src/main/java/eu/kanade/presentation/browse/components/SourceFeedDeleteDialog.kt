package eu.kanade.presentation.browse.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

/*
 * KMK: the feed's delete confirmation, moved out of SourceFeedDialogs.kt unchanged so the desktop Feed tab uses it too
 * (a `shared` borrowed source, see app-shared/borrowed-app-sources.gradle.kts).
 */

@Composable
fun SourceFeedDeleteDialog(
    onDismissRequest: () -> Unit,
    deleteFeed: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = deleteFeed) {
                Text(text = stringResource(MR.strings.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(SYMR.strings.feed))
        },
        text = {
            Text(text = stringResource(SYMR.strings.feed_delete))
        },
    )
}
