package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibraryDisplayItem
import eu.kanade.tachiyomi.ui.library.RemoteTrackerLibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

@Composable
internal fun LibraryList(
    items: List<LibraryDisplayItem>,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onClickRemoteTrack: (RemoteTrackerLibraryItem) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (!searchQuery.isNullOrEmpty()) {
                GlobalSearchItem(
                    modifier = Modifier.fillMaxWidth(),
                    searchQuery = searchQuery,
                    onClick = onGlobalSearchClicked,
                )
            }
        }

        items(
            items = items,
            key = { it.key },
            contentType = {
                when (it) {
                    is LibraryDisplayItem.Local -> "library_list_item"
                    is LibraryDisplayItem.RemoteTrack -> "remote_tracker_list_item"
                }
            },
        ) { item ->
            when (item) {
                is LibraryDisplayItem.Local -> {
                    val libraryItem = item.item
                    val manga = libraryItem.libraryManga.manga
                    MangaListItem(
                        isSelected = manga.id in selection,
                        title = manga.title,
                        coverData = MangaCover(
                            mangaId = manga.id,
                            sourceId = manga.source,
                            isMangaFavorite = manga.favorite,
                            ogUrl = manga.thumbnailUrl,
                            lastModified = manga.coverLastModified,
                        ),
                        badge = {
                            DownloadsBadge(count = libraryItem.downloadCount)
                            UnreadBadge(count = libraryItem.unreadCount)
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
                    )
                }
                is LibraryDisplayItem.RemoteTrack -> {
                    val remoteItem = item.item
                    MangaListItem(
                        title = remoteItem.track.title,
                        coverData = remoteItem.coverData,
                        badge = {
                            TrackerLabelBadge(remoteItem.trackerShortName)
                            TrackerStatusBadge(remoteItem.statusText)
                            TrackerProgressBadge(remoteItem.progressText)
                        },
                        onLongClick = {},
                        onClick = { onClickRemoteTrack(remoteItem) },
                        libraryColored = false,
                    )
                }
            }
        }
    }
}
