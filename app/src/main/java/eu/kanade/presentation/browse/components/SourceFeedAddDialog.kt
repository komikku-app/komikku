package eu.kanade.presentation.browse.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

/*
 * KMK: the confirmation to add a saved search to a source's feed, moved out of SourceFeedDialogs.kt unchanged so the
 * desktop catalogue uses it too (a `shared` borrowed source, see app-shared/borrowed-app-sources.gradle.kts).
 */

@Composable
fun SourceFeedAddDialog(
    onDismissRequest: () -> Unit,
    name: String,
    addFeed: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = addFeed) {
                Text(text = stringResource(MR.strings.action_add))
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
            Text(text = stringResource(SYMR.strings.feed_add, name))
        },
    )
}
