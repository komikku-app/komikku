package eu.kanade.presentation.browse

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.components.BrowseSourceFloatingActionButton
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topSmallPaddingValues
import eu.kanade.tachiyomi.R
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch

sealed class SourceFeedUI {
    abstract val id: Long

    abstract val title: String
        @Composable
        @ReadOnlyComposable
        get

    abstract val results: List<Manga>?

    abstract fun withResults(results: List<Manga>?): SourceFeedUI

    data class Latest(override val results: List<Manga>?) : SourceFeedUI() {
        override val id: Long = -1
        override val title: String
            @Composable
            @ReadOnlyComposable
            get() = stringResource(R.string.latest)

        override fun withResults(results: List<Manga>?): SourceFeedUI {
            return copy(results = results)
        }
    }
    data class Browse(override val results: List<Manga>?) : SourceFeedUI() {
        override val id: Long = -2
        override val title: String
            @Composable
            @ReadOnlyComposable
            get() = stringResource(R.string.browse)

        override fun withResults(results: List<Manga>?): SourceFeedUI {
            return copy(results = results)
        }
    }
    data class SourceSavedSearch(
        val feed: FeedSavedSearch,
        val savedSearch: SavedSearch,
        override val results: List<Manga>?,
    ) : SourceFeedUI() {
        override val id: Long
            get() = feed.id

        override val title: String
            @Composable
            @ReadOnlyComposable
            get() = savedSearch.name

        override fun withResults(results: List<Manga>?): SourceFeedUI {
            return copy(results = results)
        }
    }
}

@Composable
fun SourceFeedScreen(
    name: String,
    isLoading: Boolean,
    items: List<SourceFeedUI>,
    onFabClick: (() -> Unit)?,
    onClickBrowse: () -> Unit,
    onClickLatest: () -> Unit,
    onClickSavedSearch: (SavedSearch) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
    onClickSearch: (String) -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    getMangaState: @Composable (Manga) -> State<Manga>,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            SourceFeedToolbar(
                title = name,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                scrollBehavior = scrollBehavior,
                onClickSearch = onClickSearch,
            )
        },
        floatingActionButton = {
            BrowseSourceFloatingActionButton(
                isVisible = onFabClick != null,
                onFabClick = onFabClick ?: {},
            )
        },
    ) { paddingValues ->
        Crossfade(targetState = isLoading) { state ->
            when (state) {
                true -> LoadingScreen()
                false -> {
                    SourceFeedList(
                        items = items,
                        paddingValues = paddingValues,
                        getMangaState = getMangaState,
                        onClickBrowse = onClickBrowse,
                        onClickLatest = onClickLatest,
                        onClickSavedSearch = onClickSavedSearch,
                        onClickDelete = onClickDelete,
                        onClickManga = onClickManga,
                    )
                }
            }
        }
    }
}

@Composable
fun SourceFeedList(
    items: List<SourceFeedUI>,
    paddingValues: PaddingValues,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onClickBrowse: () -> Unit,
    onClickLatest: () -> Unit,
    onClickSavedSearch: (SavedSearch) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
) {
    ScrollbarLazyColumn(
        contentPadding = paddingValues + topSmallPaddingValues,
    ) {
        items(
            items.orEmpty(),
            key = { it.id },
        ) { item ->
            SourceFeedItem(
                modifier = Modifier.animateItemPlacement(),
                item = item,
                getMangaState = getMangaState,
                onClickTitle = when (item) {
                    is SourceFeedUI.Browse -> onClickBrowse
                    is SourceFeedUI.Latest -> onClickLatest
                    is SourceFeedUI.SourceSavedSearch -> {
                        { onClickSavedSearch(item.savedSearch) }
                    }
                },
                onClickDelete = onClickDelete,
                onClickManga = onClickManga,
            )
        }
    }
}

@Composable
fun SourceFeedItem(
    modifier: Modifier,
    item: SourceFeedUI,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onClickTitle: () -> Unit,
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
                .let {
                    if (item is SourceFeedUI.SourceSavedSearch) {
                        it.combinedClickable(
                            onLongClick = {
                                onClickDelete(item.feed)
                            },
                            onClick = onClickTitle,
                        )
                    } else {
                        it.clickable(onClick = onClickTitle)
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward_24dp),
                contentDescription = stringResource(R.string.label_more),
                modifier = Modifier.padding(16.dp),
            )
        }
        val results = item.results
        when {
            results == null -> {
                CircularProgressIndicator()
            }
            results.isEmpty() -> {
                Text(stringResource(R.string.no_results_found), modifier = Modifier.padding(bottom = 16.dp))
            }
            else -> {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    items(results) {
                        val manga by getMangaState(it)
                        FeedCardItem(
                            manga = manga,
                            onClickManga = onClickManga,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SourceFeedToolbar(
    title: String,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onClickSearch: (String) -> Unit,
) {
    SearchToolbar(
        titleContent = { AppBarTitle(title) },
        searchQuery = searchQuery,
        onChangeSearchQuery = onSearchQueryChange,
        onSearch = onClickSearch,
        onClickCloseSearch = { onSearchQueryChange(null) },
        scrollBehavior = scrollBehavior,
        placeholderText = stringResource(R.string.action_search_hint),
    )
}
