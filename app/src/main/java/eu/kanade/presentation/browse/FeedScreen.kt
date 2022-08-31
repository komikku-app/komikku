package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.BadgeGroup
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.util.bottomNavPaddingValues
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.feed.FeedPresenter
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import eu.kanade.domain.manga.model.MangaCover as MangaCoverData

data class FeedItemUI(
    val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
    val source: CatalogueSource?,
    val title: String,
    val subtitle: String,
    val results: List<Manga>?,
)

@Composable
fun FeedScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: FeedPresenter,
    onClickSavedSearch: (SavedSearch, CatalogueSource) -> Unit,
    onClickSource: (CatalogueSource) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
) {
    when {
        presenter.isLoading -> LoadingScreen()
        presenter.isEmpty -> EmptyScreen(R.string.feed_tab_empty)
        else -> {
            FeedList(
                nestedScrollConnection = nestedScrollInterop,
                state = presenter,
                onClickSavedSearch = onClickSavedSearch,
                onClickSource = onClickSource,
                onClickDelete = onClickDelete,
                onClickManga = onClickManga,
            )
        }
    }
}

@Composable
fun FeedList(
    nestedScrollConnection: NestedScrollConnection,
    state: FeedState,
    onClickSavedSearch: (SavedSearch, CatalogueSource) -> Unit,
    onClickSource: (CatalogueSource) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
) {
    ScrollbarLazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        contentPadding = bottomNavPaddingValues + WindowInsets.navigationBars.asPaddingValues() + topPaddingValues,
    ) {
        items(
            state.items.orEmpty(),
            key = { it.feed.id },
        ) { item ->
            FeedItem(
                modifier = Modifier.animateItemPlacement(),
                item = item,
                onClickSavedSearch = onClickSavedSearch,
                onClickSource = onClickSource,
                onClickDelete = onClickDelete,
                onClickManga = onClickManga,
            )
        }
    }
}

@Composable
fun FeedItem(
    modifier: Modifier,
    item: FeedItemUI,
    onClickSavedSearch: (SavedSearch, CatalogueSource) -> Unit,
    onClickSource: (CatalogueSource) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
) {
    Column(
        modifier then Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onLongClick = {
                        onClickDelete(item.feed)
                    },
                    onClick = {
                        if (item.savedSearch != null && item.source != null) {
                            onClickSavedSearch(item.savedSearch, item.source)
                        } else if (item.source != null) {
                            onClickSource(item.source)
                        }
                    },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = LocalContentColor.current.copy(alpha = ContentAlpha.high),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward_24dp),
                contentDescription = stringResource(R.string.label_more),
                modifier = Modifier.padding(16.dp),
            )
        }
        when {
            item.results == null -> {
                CircularProgressIndicator()
            }
            item.results.isEmpty() -> {
                Text(stringResource(R.string.no_results_found), modifier = Modifier.padding(bottom = 16.dp))
            }
            else -> {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    items(item.results) {
                        FeedCardItem(
                            manga = it,
                            onClickManga = onClickManga,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeedCardItem(
    modifier: Modifier = Modifier,
    manga: Manga,
    onClickManga: (Manga) -> Unit,
) {
    Column(
        modifier
            .padding(vertical = 4.dp)
            .width(112.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = { onClickManga(manga) })
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MangaCover.Book.ratio),
        ) {
            MangaCover.Book(
                modifier = Modifier.fillMaxWidth()
                    .alpha(
                        if (manga.favorite) 0.3f else 1.0f,
                    ),
                data = MangaCoverData(
                    manga.id,
                    manga.source,
                    manga.favorite,
                    manga.thumbnailUrl,
                    manga.coverLastModified,
                ),
            )
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
            ) {
                if (manga.favorite) {
                    Badge(text = stringResource(R.string.in_library))
                }
            }
        }

        Text(
            modifier = Modifier.padding(4.dp),
            text = manga.title,
            fontSize = 12.sp,
            maxLines = 2,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
