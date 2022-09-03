package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.library.components.MangaGridCompactText
import eu.kanade.presentation.library.components.MangaGridCover
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata

@Composable
fun BrowseSourceCompactGrid(
    mangaList: LazyPagingItems</* SY --> */Pair<Manga, RaisedSearchMetadata?>/* SY <-- */>,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    // SY -->
    getMetadataState: @Composable ((Manga, RaisedSearchMetadata?) -> State<RaisedSearchMetadata?>),
    // SY <--
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(8.dp, 4.dp) + contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                header()
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            if (mangaList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(mangaList.itemCount) { index ->
            val initialManga = mangaList[index] ?: return@items
            val manga by getMangaState(initialManga.first)
            // SY -->
            val metadata by getMetadataState(initialManga.first, initialManga.second)
            // SY <--
            BrowseSourceCompactGridItem(
                manga = manga,
                // SY -->
                metadata = metadata,
                // SY <--
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
fun BrowseSourceCompactGridItem(
    manga: Manga,
    // SY -->
    metadata: RaisedSearchMetadata?,
    // SY <--
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    val overlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.66f)
    MangaGridCover(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        cover = {
            MangaCover.Book(
                modifier = Modifier
                    .fillMaxHeight()
                    .drawWithContent {
                        drawContent()
                        if (manga.favorite) {
                            drawRect(overlayColor)
                        }
                    },
                data = eu.kanade.domain.manga.model.MangaCover(
                    manga.id,
                    manga.source,
                    manga.favorite,
                    manga.thumbnailUrl,
                    manga.coverLastModified,
                ),
            )
        },
        badgesStart = {
            if (manga.favorite) {
                Badge(text = stringResource(id = R.string.in_library))
            }
        },
        // SY -->
        badgesEnd = {
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
        content = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xAA000000),
                        ),
                    )
                    .fillMaxHeight(0.33f)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
            MangaGridCompactText(manga.title)
        },
    )
}
