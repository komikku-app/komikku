package eu.kanade.presentation.errors.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.errors.LibraryUpdateErrorItem
import tachiyomi.domain.error.model.ErrorManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal fun LazyListScope.errorUiItems(
    uiModels: List<LibraryUpdateErrorUiModel>,
    selectionMode: Boolean,
    onErrorSelected: (LibraryUpdateErrorItem, Boolean, Boolean, Boolean) -> Unit,
    onClick: (LibraryUpdateErrorItem) -> Unit,
    onClickCover: (LibraryUpdateErrorItem) -> Unit,
) {
    uiModels.forEach {
        when (it) {
            is LibraryUpdateErrorUiModel.Header -> {
                stickyHeader(
                    key = "$STICKY_HEADER_KEY_PREFIX-errorHeader-${it.hashCode()}",
                    contentType = "header",
                ) {
                    ListGroupHeader(
                        modifier = Modifier.animateItemFastScroll(),
                        text = it.errorMessage,
                    )
                }
            }
            is LibraryUpdateErrorUiModel.Item -> {
                item(
                    key = "error-${it.item.error.errorId}-${it.item.error.manga.mangaId}",
                    contentType = "item",
                ) {
                    val libraryUpdateErrorItem = it.item
                    LibraryUpdateErrorUiItem(
                        modifier = Modifier.animateItemFastScroll(),
                        manga = libraryUpdateErrorItem.error.manga,
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
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryUpdateErrorUiItem(
    modifier: Modifier,
    manga: ErrorManga,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
) {
    val haptic = LocalHapticFeedback.current

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
            data = manga.mangaCover,
            onClick = onClickCover,
        )

        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium, vertical = 5.dp)
                .weight(1f),
        ) {
            Text(
                text = manga.mangaTitle,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Visible,
            )

            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Injekt.get<SourceManager>().getOrStub(manga.mangaSource).name,
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

sealed class LibraryUpdateErrorUiModel {

    data class Header(val errorMessage: String) : LibraryUpdateErrorUiModel()

    data class Item(val item: LibraryUpdateErrorItem) : LibraryUpdateErrorUiModel()
}
