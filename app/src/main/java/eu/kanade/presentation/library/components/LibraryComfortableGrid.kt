package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover

@Composable
internal fun LibraryComfortableGrid(
    items: List<LibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    // KMK -->
    usePanoramaCover: Boolean = false,
    // KMK <--
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            contentType = { "library_comfortable_grid_item" },
        ) { libraryItem ->
            val manga = libraryItem.libraryManga.manga
            val onClickItem = remember(libraryItem) { { onClick(libraryItem.libraryManga) } }
            val onLongClickItem = remember(libraryItem) { { onLongClick(libraryItem.libraryManga) } }
            val onClickContinueReadingItem = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                remember(libraryItem) { { onClickContinueReading(libraryItem.libraryManga) } }
            } else {
                null
            }
            MangaComfortableGridItem(
                isSelected = manga.id in selection,
                title = manga.title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    ogUrl = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnreadBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                        // KMK -->
                        useLangIcon = libraryItem.useLangIcon,
                        // KMK <--
                    )
                    // KMK -->
                    SourceIconBadge(source = libraryItem.source)
                    // KMK <--
                },
                onLongClick = onLongClickItem,
                onClick = onClickItem,
                onClickContinueReading = onClickContinueReadingItem,
                // KMK -->
                usePanoramaCover = usePanoramaCover,
                // KMK <--
            )
        }
    }
}
