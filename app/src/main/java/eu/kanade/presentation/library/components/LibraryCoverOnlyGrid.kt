package eu.kanade.presentation.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.tachiyomi.ui.library.LibraryItem

@Composable
fun LibraryCoverOnlyGrid(
    items: List<LibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    // SY -->
    onOpenReader: (LibraryManga) -> Unit,
    // SY <--
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            contentType = { "library_only_cover_grid_item" },
        ) { libraryItem ->
            LibraryCoverOnlyGridItem(
                item = libraryItem,
                isSelected = libraryItem.libraryManga in selection,
                onClick = onClick,
                onLongClick = onLongClick,
                // SY -->
                onOpenReader = onOpenReader,
                // SY <--
            )
        }
    }
}

@Composable
fun LibraryCoverOnlyGridItem(
    item: LibraryItem,
    isSelected: Boolean,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    // SY -->
    onOpenReader: (LibraryManga) -> Unit,
    // SY <--
) {
    val libraryManga = item.libraryManga
    val manga = libraryManga.manga
    LibraryGridCover(
        modifier = Modifier
            .selectedOutline(isSelected)
            .combinedClickable(
                onClick = {
                    onClick(libraryManga)
                },
                onLongClick = {
                    onLongClick(libraryManga)
                },
            ),
        mangaCover = eu.kanade.domain.manga.model.MangaCover(
            manga.id,
            manga.source,
            manga.favorite,
            manga.thumbnailUrl,
            manga.coverLastModified,
        ),
        downloadCount = item.downloadCount,
        unreadCount = item.unreadCount,
        isLocal = item.isLocal,
        language = item.sourceLanguage,
        // SY -->
        showPlayButton = item.startReadingButton && item.libraryManga.unreadCount > 0,
        onOpenReader = {
            onOpenReader(item.libraryManga)
        },
        // SY <--
    )
}
