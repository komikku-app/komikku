package eu.kanade.presentation.library.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.R

@Composable
fun SyncFavoritesConfirmDialog(
    onDismissRequest: () -> Unit,
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        title = {
            Text(stringResource(R.string.favorites_sync))
        },
        text = {
            Text(text = stringResource(R.string.favorites_sync_conformation_message))
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}
