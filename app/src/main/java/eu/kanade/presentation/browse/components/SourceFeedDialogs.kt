package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import eu.kanade.presentation.browse.SourceFeedUI.SourceSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

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

// KMK -->
@Composable
fun FeedActionsDialog(
    feedItem: SourceSavedSearch,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onMoveUp: (FeedSavedSearch) -> Unit,
    onMoveDown: (FeedSavedSearch) -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(SYMR.strings.feed))
        },
        text = {
            Text(text = feedItem.title)
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { onMoveUp(feedItem.feed) }, enabled = canMoveUp) {
                    Text(text = stringResource(KMR.strings.action_move_up))
                }
                TextButton(onClick = { onMoveDown(feedItem.feed) }, enabled = canMoveDown) {
                    Text(text = stringResource(KMR.strings.action_move_down))
                }
                TextButton(onClick = { onClickDelete(feedItem.feed) }) {
                    Text(text = stringResource(MR.strings.action_delete))
                }
            }
        },
    )
}

@Composable
fun FeedSortAlphabeticallyDialog(
    onDismissRequest: () -> Unit,
    onSort: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onSort()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(KMR.strings.action_sort_feed))
        },
        text = {
            Text(text = stringResource(KMR.strings.sort_feed_confirmation))
        },
    )
}
// KMK <--
