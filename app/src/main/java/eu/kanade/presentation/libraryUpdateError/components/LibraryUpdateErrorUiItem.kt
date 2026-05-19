package eu.kanade.presentation.libraryUpdateError.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.material3.SwipeToDismissBoxValue.Settled
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorItem
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.presentation.core.util.selectedBackground

internal fun LazyListScope.libraryUpdateErrorUiItems(
    uiModels: List<LibraryUpdateErrorUiModel>,
    selectionMode: Boolean,
    onErrorSelected: (LibraryUpdateErrorItem, Boolean, Boolean) -> Unit,
    onClick: (LibraryUpdateErrorItem) -> Unit,
    onClickCover: (LibraryUpdateErrorItem) -> Unit,
    onDelete: (Long) -> Unit,
) {
    uiModels.forEach { uiModel ->
        when (uiModel) {
            is LibraryUpdateErrorUiModel.Header -> {
                stickyHeader(
                    key = "$STICKY_HEADER_KEY_PREFIX-errorHeader-${uiModel.hashCode()}",
                    contentType = "header",
                ) {
                    ListGroupHeader(
                        modifier = Modifier.animateItemFastScroll(),
                        text = uiModel.errorMessage,
                        tonalElevation = 1.dp,
                        count = uiModel.count,
                    )
                }
            }
            is LibraryUpdateErrorUiModel.Item -> {
                item(
                    key = "error-${uiModel.item.error.errorId}-${uiModel.item.error.mangaId}",
                    contentType = "item",
                ) {
                    val libraryUpdateErrorItem = uiModel.item
                    LibraryUpdateErrorUiItem(
                        modifier = Modifier.animateItemFastScroll(),
                        error = libraryUpdateErrorItem.error,
                        mangaCover = libraryUpdateErrorItem.mangaCover,
                        sourceName = libraryUpdateErrorItem.sourceName,
                        selected = libraryUpdateErrorItem.selected,
                        onClick = {
                            when {
                                selectionMode -> onErrorSelected(
                                    libraryUpdateErrorItem,
                                    !libraryUpdateErrorItem.selected,
                                    false,
                                )

                                else -> onClick(libraryUpdateErrorItem)
                            }
                        },
                        onLongClick = {
                            onErrorSelected(
                                libraryUpdateErrorItem,
                                !libraryUpdateErrorItem.selected,
                                true,
                            )
                        },
                        onClickCover = { onClickCover(libraryUpdateErrorItem) }.takeIf { !selectionMode },
                        onSwipe = { onDelete(libraryUpdateErrorItem.error.errorId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryUpdateErrorUiItem(
    modifier: Modifier,
    error: LibraryUpdateErrorWithRelations,
    mangaCover: tachiyomi.domain.manga.model.MangaCover,
    sourceName: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
    onSwipe: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                StartToEnd -> onSwipe()
                EndToStart -> onSwipe()
                else -> {}
            }
            return@rememberSwipeToDismissBoxState true
        },
        // Set threshold to 25% of the width
        positionalThreshold = { totalDistance -> totalDistance * 0.25f },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { DismissBackground(dismissState) },
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .selectedBackground(selected)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                )
                .padding(horizontal = MaterialTheme.padding.medium),
            verticalAlignment = Alignment.Top,
        ) {
            MangaCover.Square(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .height(48.dp),
                data = mangaCover,
                onClick = onClickCover,
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = 5.dp)
                    .weight(1f),
            ) {
                Text(
                    text = error.mangaTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    overflow = TextOverflow.Visible,
                )

                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Visible,
                        maxLines = 1,
                        modifier = Modifier
                            .secondaryItemAlpha()
                            .weight(weight = 1f, fill = false),
                    )
                }
            }
        }
    }
}

@Composable
fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val direction = dismissState.dismissDirection
    val targetState = dismissState.targetValue

    val backgroundColor by animateColorAsState(
        when (direction) {
            Settled ->
                MaterialTheme.colorScheme.surface
            StartToEnd ->
                MaterialTheme.colorScheme.errorContainer
                    .copy(alpha = if (targetState == Settled) 0.45f else 1f)
            EndToStart ->
                MaterialTheme.colorScheme.errorContainer
                    .copy(alpha = if (targetState == Settled) 0.45f else 1f)
        },
    )
    val alignment = when (direction) {
        StartToEnd -> Alignment.CenterStart
        EndToStart -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(MR.strings.action_delete),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

sealed class LibraryUpdateErrorUiModel {

    data class Header(val errorMessage: String, val count: Int) : LibraryUpdateErrorUiModel()

    data class Item(val item: LibraryUpdateErrorItem) : LibraryUpdateErrorUiModel()
}
