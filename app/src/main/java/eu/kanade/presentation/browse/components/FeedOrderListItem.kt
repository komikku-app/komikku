package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import eu.kanade.presentation.browse.FeedItemUI
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun FeedOrderListItem(
    feed: FeedItemUI,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: (FeedSavedSearch) -> Unit,
    onMoveDown: (FeedSavedSearch) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Splitscreen,
                contentDescription = null,
            )
            Text(
                text = feed.title,
                modifier = Modifier
                    .padding(start = MaterialTheme.padding.medium),
            )
        }
        Row {
            IconButton(
                onClick = { onMoveUp(feed.feed) },
                enabled = canMoveUp,
            ) {
                Icon(imageVector = Icons.Outlined.ArrowDropUp, contentDescription = null)
            }
            IconButton(
                onClick = { onMoveDown(feed.feed) },
                enabled = canMoveDown,
            ) {
                Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.action_delete))
            }
        }
    }
}

@Preview
@Composable
private fun FeedOrderListItemPreview() {
    FeedOrderListItem(
        feed = FeedItemUI(
            feed = FeedSavedSearch(
                id = 1,
                source = 1,
                savedSearch = null,
                global = false,
                feedOrder = 0,
            ),
            title = "Feed 1",
            subtitle = "Source 1",
            results = null,
            savedSearch = null,
            source = null,
        ),
        canMoveUp = true,
        canMoveDown = true,
        onMoveUp = {},
        onMoveDown = {},
        onDelete = {},
    )
}
