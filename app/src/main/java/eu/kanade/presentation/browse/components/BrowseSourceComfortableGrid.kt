package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.CommonMangaItemDefaults
import eu.kanade.presentation.components.MangaComfortableGridItem
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata

@Composable
fun BrowseSourceComfortableGrid(
    mangaList: LazyPagingItems</* SY --> */Pair<Manga, RaisedSearchMetadata?>/* SY <-- */>,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    // SY -->
    getMetadataState: @Composable ((Manga, RaisedSearchMetadata?) -> State<RaisedSearchMetadata?>),
    // SY <--
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
    ) {
        if (mangaList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(mangaList.itemCount) { index ->
            val initialManga = mangaList[index] ?: return@items
            val manga by getMangaState(initialManga.first)
            // SY -->
            val metadata by getMetadataState(initialManga.first, initialManga.second)
            // SY <--
            BrowseSourceComfortableGridItem(
                manga = manga,
                // SY -->
                metadata = metadata,
                // SY <--
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
            )
        }

        if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
fun BrowseSourceComfortableGridItem(
    manga: Manga,
    // SY -->
    metadata: RaisedSearchMetadata?,
    // SY <--
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    MangaComfortableGridItem(
        title = manga.title,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            if (manga.favorite) {
                Badge(text = stringResource(R.string.in_library))
            }
        },
        // SY -->
        coverBadgeEnd = {
            if (metadata is MangaDexSearchMetadata) {
                metadata.followStatus?.let { followStatus ->
                    val text = LocalContext.current
                        .resources
                        .let {
                            remember {
                                it.getStringArray(R.array.md_follows_options)
                                    .getOrNull(followStatus)
                            }
                        }
                        ?: return@let
                    Badge(
                        text = text,
                        color = MaterialTheme.colorScheme.tertiary,
                        textColor = MaterialTheme.colorScheme.onTertiary,
                    )
                }
                metadata.relation?.let {
                    Badge(
                        text = stringResource(it.resId),
                        color = MaterialTheme.colorScheme.tertiary,
                        textColor = MaterialTheme.colorScheme.onTertiary,
                    )
                }
            }
        },
        // SY <--
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
