package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.source.comment.SourceComment
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * Variant A comments bottom sheet. Fetch is owned by [MangaScreenModel]; this only renders state.
 */
@Composable
fun CommentsSheet(
    state: MangaScreenModel.CommentsState,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = TabbedDialogPaddings.Vertical),
        ) {
            CommentsSheetHeader(
                state = state,
                onDismissRequest = onDismissRequest,
            )
            HorizontalDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 520.dp),
            ) {
                when (state) {
                    MangaScreenModel.CommentsState.Idle,
                    MangaScreenModel.CommentsState.Loading,
                    -> CommentsLoadingBody()
                    is MangaScreenModel.CommentsState.Empty -> CommentsEmptyBody(
                        subtitle = state.subtitle,
                    )
                    is MangaScreenModel.CommentsState.Error -> CommentsErrorBody(
                        message = state.message,
                        onRetry = onRetry,
                    )
                    is MangaScreenModel.CommentsState.Loaded -> CommentsListBody(
                        state = state,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentsSheetHeader(
    state: MangaScreenModel.CommentsState,
    onDismissRequest: () -> Unit,
) {
    val count = when (state) {
        is MangaScreenModel.CommentsState.Loaded -> state.totalCount
        is MangaScreenModel.CommentsState.Empty -> state.totalCount
        else -> null
    }
    val subtitle = when (state) {
        is MangaScreenModel.CommentsState.Loaded -> state.subtitle
        is MangaScreenModel.CommentsState.Empty -> state.subtitle
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TabbedDialogPaddings.Horizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count != null) {
                    stringResource(KMR.strings.action_comments) + " ($count)"
                } else {
                    stringResource(KMR.strings.action_comments)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        TextButton(onClick = onDismissRequest) {
            Text(text = stringResource(MR.strings.action_close))
        }
    }
}

@Composable
private fun CommentsLoadingBody() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CommentsEmptyBody(subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TabbedDialogPaddings.Horizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(KMR.strings.information_no_comments),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.secondaryItemAlpha(),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            )
        }
    }
}

@Composable
private fun CommentsErrorBody(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TabbedDialogPaddings.Horizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.secondaryItemAlpha(),
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(MR.strings.action_retry))
        }
    }
}

@Composable
private fun CommentsListBody(
    state: MangaScreenModel.CommentsState.Loaded,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = TabbedDialogPaddings.Horizontal,
            vertical = MaterialTheme.padding.small,
        ),
    ) {
        items(
            items = state.comments,
            key = { it.id },
        ) { comment ->
            CommentListItem(comment = comment)
            HorizontalDivider(modifier = Modifier.secondaryItemAlpha())
        }
        if (state.hasNextPage) {
            item(key = "load-more") {
                OutlinedButton(
                    onClick = onLoadMore,
                    enabled = !state.isLoadingMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.padding.small),
                ) {
                    if (state.isLoadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = stringResource(KMR.strings.action_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentListItem(
    comment: SourceComment,
    nested: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (nested) MaterialTheme.padding.large else 0.dp,
                top = MaterialTheme.padding.small,
                bottom = MaterialTheme.padding.small,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = comment.username.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
            Column {
                Text(
                    text = comment.username,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                comment.dateEpochMillis?.let { epoch ->
                    Text(
                        text = relativeTimeSpanString(epoch),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        CommentBody(comment = comment)
        comment.replies.forEach { reply ->
            CommentListItem(comment = reply, nested = true)
        }
    }
}

/** Spoiler-flagged bodies stay hidden behind a tap-to-reveal placeholder. */
@Composable
private fun CommentBody(comment: SourceComment) {
    var revealed by remember(comment.id) { mutableStateOf(!comment.isSpoiler) }
    val bodyModifier = Modifier.padding(
        start = 40.dp,
        top = MaterialTheme.padding.extraSmall,
    )
    if (revealed) {
        Text(
            text = comment.body,
            style = MaterialTheme.typography.bodyMedium,
            modifier = bodyModifier,
        )
    } else {
        Text(
            text = stringResource(KMR.strings.information_spoiler_tap_to_reveal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = bodyModifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { revealed = true }
                .padding(horizontal = MaterialTheme.padding.small, vertical = MaterialTheme.padding.extraSmall),
        )
    }
}
