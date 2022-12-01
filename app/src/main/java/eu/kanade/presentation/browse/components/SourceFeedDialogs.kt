package eu.kanade.presentation.browse.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.R

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
                Text(text = stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        title = {
            Text(text = stringResource(R.string.feed))
        },
        text = {
            Text(text = stringResource(R.string.feed_add, name))
        },
    )
}

@Composable
fun SourceFeedDeleteDialog(
    onDismissRequest: () -> Unit,
    deleteFeed: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = deleteFeed) {
                Text(text = stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        title = {
            Text(text = stringResource(R.string.feed))
        },
        text = {
            Text(text = stringResource(R.string.feed_delete))
        },
    )
}
