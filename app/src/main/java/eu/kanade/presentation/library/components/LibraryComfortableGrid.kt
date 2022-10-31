package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.presentation.components.MangaComfortableGridItem
import eu.kanade.tachiyomi.ui.library.LibraryItem

@Composable
fun LibraryComfortableGrid(
    items: List<LibraryItem>,
    showDownloadBadges: Boolean,
    showUnreadBadges: Boolean,
    showLocalBadges: Boolean,
    showLanguageBadges: Boolean,
    // SY -->
    showStartReadingButton: Boolean,
    // SY <--
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
            contentType = { "library_comfortable_grid_item" },
        ) { libraryItem ->
            val manga = libraryItem.libraryManga.manga
            MangaComfortableGridItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryManga.id },
                title = manga.title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(
                        enabled = showDownloadBadges,
                        item = libraryItem,
                    )
                    UnreadBadge(
                        enabled = showUnreadBadges,
                        item = libraryItem,
                    )
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        showLanguage = showLanguageBadges,
                        showLocal = showLocalBadges,
                        item = libraryItem,
                    )
                },
                // SY -->
                buttonBottom = if (showStartReadingButton && libraryItem.unreadCount > 0) {
                    { StartReadingButton(onOpenReader = { onOpenReader(libraryItem.libraryManga) }) }
                } else {
                    null
                },
                // SY <--
                onLongClick = { onLongClick(libraryItem.libraryManga) },
                onClick = { onClick(libraryItem.libraryManga) },
            )
        }
    }
}
