package app.komikku.desktop.ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.CoverImage
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup

/**
 * The library page in the user's display mode (`LibraryPreferences.displayMode()`, Android's modes): compact
 * grid (title over the cover), comfortable grid (title below; also used for the KMK panorama variant),
 * cover-only grid, list. Grid columns from the column preference (0 = automatic, like Android's 128 dp
 * adaptive cells). Badges as on Android (`LibraryItem` counts are 0 / empty when a badge preference is off).
 */
@Composable
internal fun LibraryItems(
    items: List<LibraryItem>,
    displayMode: LibraryDisplayMode,
    columns: Int,
    selection: Set<Long>,
    onClick: (Long, LibraryClickModifiers) -> Unit,
    onLongClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (displayMode == LibraryDisplayMode.List) {
        LazyColumn(modifier = modifier.fillMaxSize().testTag("library-list"), contentPadding = PaddingValues(8.dp)) {
            items(items = items, key = { it.id }) { item ->
                LibraryListItem(item, Modifier.libraryItemClicks(item.id, item.id in selection, onClick, onLongClick))
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = if (columns > 0) GridCells.Fixed(columns) else GridCells.Adaptive(128.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize().testTag("library-grid"),
    ) {
        items(items = items, key = { it.id }) { item ->
            LibraryGridItem(item, displayMode, Modifier.libraryItemClicks(item.id, item.id in selection, onClick, onLongClick))
        }
    }
}

/**
 * Click handling of a library entry: click, long press (Android's range selection) and the keyboard modifiers held at
 * the press (Shift: range, Ctrl / Cmd: toggle); selected entries are tinted like Android's selected items.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.libraryItemClicks(
    id: Long,
    selected: Boolean,
    onClick: (Long, LibraryClickModifiers) -> Unit,
    onLongClick: (Long) -> Unit,
): Modifier {
    val pressModifiers = remember { arrayOf(LibraryClickModifiers()) }
    return this
        .clip(RoundedCornerShape(6.dp))
        .then(
            if (selected) Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = SELECTED_ALPHA)) else Modifier,
        )
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        val keys = event.keyboardModifiers
                        pressModifiers[0] = LibraryClickModifiers(
                            shift = keys.isShiftPressed,
                            toggle = keys.isCtrlPressed || keys.isMetaPressed,
                        )
                    }
                }
            }
        }
        .combinedClickable(
            onClick = { onClick(id, pressModifiers[0]).also { pressModifiers[0] = LibraryClickModifiers() } },
            // A long press ends the press too: forget its modifiers, or the next (touch) click would reuse them.
            onLongClick = { onLongClick(id).also { pressModifiers[0] = LibraryClickModifiers() } },
        )
        .semantics { this.selected = selected }
}

@Composable
private fun LibraryGridItem(item: LibraryItem, displayMode: LibraryDisplayMode, clicks: Modifier) {
    val manga = item.libraryManga.manga
    val titleBelow = displayMode == LibraryDisplayMode.ComfortableGrid ||
        displayMode == LibraryDisplayMode.ComfortableGridPanorama
    Column(
        modifier = clicks
            .padding(4.dp)
            .testTag("library-item-${item.id}"),
    ) {
        Box {
            LibraryCover(item, Modifier.fillMaxWidth().aspectRatio(MANGA_COVER_RATIO))
            LibraryBadges(item, Modifier.padding(4.dp))
            if (displayMode == LibraryDisplayMode.CompactGrid) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                        .padding(6.dp),
                )
            }
        }
        if (titleBelow) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LibraryListItem(item: LibraryItem, clicks: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(clicks)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("library-item-${item.id}"),
    ) {
        LibraryCover(item, Modifier.height(56.dp).aspectRatio(MANGA_COVER_RATIO))
        Text(
            text = item.libraryManga.manga.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        LibraryBadges(item, Modifier)
    }
}

@Composable
private fun LibraryCover(item: LibraryItem, modifier: Modifier) {
    val manga = item.libraryManga.manga
    val cover = remember(manga) { manga.asMangaCover() }
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        CoverImage(
            model = cover,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
        )
    }
}

/** Android's library badges: downloads + unread (start), local folder or source language (end). */
@Composable
private fun LibraryBadges(item: LibraryItem, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item.downloadCount > 0 || item.unreadCount > 0) {
            BadgeGroup {
                if (item.downloadCount > 0) {
                    Badge(
                        text = "${item.downloadCount}",
                        color = MaterialTheme.colorScheme.tertiary,
                        textColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.testTag("download-badge-${item.id}"),
                    )
                }
                if (item.unreadCount > 0) {
                    Badge(text = "${item.unreadCount}", modifier = Modifier.testTag("unread-badge-${item.id}"))
                }
            }
        }
        if (item.isLocal || item.sourceLanguage.isNotEmpty()) {
            BadgeGroup {
                if (item.isLocal) {
                    Badge(
                        imageVector = Icons.Outlined.Folder,
                        color = MaterialTheme.colorScheme.tertiary,
                        iconColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.testTag("local-badge-${item.id}"),
                    )
                } else {
                    // Desktop has no flag icons: the language code as text (Android's `useLangIcon = false` look).
                    Badge(
                        text = item.sourceLanguage.uppercase(),
                        color = MaterialTheme.colorScheme.tertiary,
                        textColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.testTag("language-badge-${item.id}"),
                    )
                }
            }
        }
    }
}

private const val MANGA_COVER_RATIO = 2f / 3f

/** Tint of selected entries (Android's `GridSelectedCoverAlpha`-like highlight). */
private const val SELECTED_ALPHA = 0.35f
