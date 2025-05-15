package eu.kanade.presentation.libraryUpdateError.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorItem
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal fun LazyListScope.libraryUpdateErrorUiItems(
    uiModels: List<LibraryUpdateErrorUiModel>,
    selectionMode: Boolean,
    onErrorSelected: (LibraryUpdateErrorItem, Boolean, Boolean, Boolean) -> Unit,
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
                        selected = libraryUpdateErrorItem.selected,
                        onClick = {
                            when {
                                selectionMode -> onErrorSelected(
                                    libraryUpdateErrorItem,
                                    !libraryUpdateErrorItem.selected,
                                    true,
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
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
    onSwipe: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    val swipeAction = swipeAction(
        icon = Icons.Outlined.Delete,
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = onSwipe,
    )

    SwipeableActionsBox(
        modifier = Modifier.clipToBounds(),
        startActions = listOfNotNull(swipeAction),
        endActions = listOfNotNull(swipeAction),
        swipeThreshold = swipeActionThreshold,
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = modifier
                .selectedBackground(selected)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        onLongClick()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
                .padding(horizontal = MaterialTheme.padding.medium),
            verticalAlignment = Alignment.Top,
        ) {
            MangaCover.Square(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .height(48.dp),
                data = error.mangaCover,
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
                        text = Injekt.get<SourceManager>().getOrStub(error.mangaSource).name,
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

private fun swipeAction(
    onSwipe: () -> Unit,
    icon: ImageVector,
    background: Color,
    isUndo: Boolean = false,
): me.saket.swipe.SwipeAction {
    return me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = icon,
                tint = contentColorFor(background),
                contentDescription = null,
            )
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = isUndo,
    )
}

private val swipeActionThreshold = 56.dp

sealed class LibraryUpdateErrorUiModel {

    data class Header(val errorMessage: String) : LibraryUpdateErrorUiModel()

    data class Item(val item: LibraryUpdateErrorItem) : LibraryUpdateErrorUiModel()
}
