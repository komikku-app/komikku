package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.library.LibraryDisplayItem
import eu.kanade.tachiyomi.ui.library.RemoteTrackerLibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover

@Composable
internal fun LibraryComfortableGrid(
    items: List<LibraryDisplayItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onClickRemoteTrack: (RemoteTrackerLibraryItem) -> Unit,
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
            key = { it.key },
            contentType = {
                when (it) {
                    is LibraryDisplayItem.Local -> "library_comfortable_grid_item"
                    is LibraryDisplayItem.RemoteTrack -> "remote_tracker_comfortable_grid_item"
                }
            },
        ) { item ->
            when (item) {
                is LibraryDisplayItem.Local -> {
                    val libraryItem = item.item
                    val manga = libraryItem.libraryManga.manga
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
                                useLangIcon = libraryItem.useLangIcon,
                            )
                            SourceIconBadge(source = libraryItem.source)
                        },
                        onLongClick = { onLongClick(libraryItem.libraryManga) },
                        onClick = { onClick(libraryItem.libraryManga) },
                        onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                            { onClickContinueReading(libraryItem.libraryManga) }
                        } else {
                            null
                        },
                        usePanoramaCover = usePanoramaCover,
                    )
                }
                is LibraryDisplayItem.RemoteTrack -> {
                    val remoteItem = item.item
                    MangaComfortableGridItem(
                        title = remoteItem.track.title,
                        coverData = remoteItem.coverData,
                        coverBadgeStart = {
                            TrackerStatusBadge(remoteItem.statusText)
                            TrackerProgressBadge(remoteItem.progressText)
                        },
                        coverBadgeEnd = {
                            TrackerLabelBadge(remoteItem.trackerShortName)
                        },
                        onLongClick = {},
                        onClick = { onClickRemoteTrack(remoteItem) },
                        libraryColored = false,
                        usePanoramaCover = usePanoramaCover,
                    )
                }
            }
        }
    }
}
