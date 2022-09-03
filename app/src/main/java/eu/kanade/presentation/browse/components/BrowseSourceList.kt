package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.items
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.library.components.MangaListItem
import eu.kanade.presentation.library.components.MangaListItemContent
import eu.kanade.presentation.util.verticalPadding
import eu.kanade.tachiyomi.R
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata

@Composable
fun BrowseSourceList(
    mangaList: LazyPagingItems</* SY --> */Pair<Manga, RaisedSearchMetadata?>/* SY <-- */>,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    // SY -->
    getMetadataState: @Composable ((Manga, RaisedSearchMetadata?) -> State<RaisedSearchMetadata?>),
    // SY <--
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        if (header != null) {
            item {
                header()
            }
        }

        item {
            if (mangaList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(mangaList) { initialManga ->
            initialManga ?: return@items
            val manga by getMangaState(initialManga.first)
            // SY -->
            val metadata by getMetadataState(initialManga.first, initialManga.second)
            // SY <--
            BrowseSourceListItem(
                manga = manga,
                // SY -->
                metadata = metadata,
                // SY <--
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
            )
        }

        item {
            if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
fun BrowseSourceListItem(
    manga: Manga,
    // SY -->
    metadata: RaisedSearchMetadata?,
    // SY <--
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    val overlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.66f)
    MangaListItem(
        coverContent = {
            MangaCover.Square(
                modifier = Modifier
                    .padding(vertical = verticalPadding)
                    .fillMaxHeight()
                    .drawWithContent {
                        drawContent()
                        if (manga.favorite) {
                            drawRect(overlayColor)
                        }
                    },
                data = manga.thumbnailUrl,
            )
        },
        onClick = onClick,
        onLongClick = onLongClick,
        badges = {
            if (manga.favorite) {
                Badge(text = stringResource(id = R.string.in_library))
            }
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
        content = {
            MangaListItemContent(text = manga.title)
        },
    )
}
