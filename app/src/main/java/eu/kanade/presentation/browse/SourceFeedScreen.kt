package eu.kanade.presentation.browse

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.BrowseSourceFloatingActionButton
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.browse.components.SourceSettingsButton
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.SelectionToolbar
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.bulkSelectionButton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.LocalSource

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
            get() = stringResource(MR.strings.latest)

        override fun withResults(results: List<Manga>?): SourceFeedUI {
            return copy(results = results)
        }
    }
    data class Browse(override val results: List<Manga>?) : SourceFeedUI() {
        override val id: Long = -2
        override val title: String
            @Composable
            @ReadOnlyComposable
            get() = stringResource(MR.strings.browse)

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
    items: ImmutableList<SourceFeedUI>,
    hasFilters: Boolean,
    onFabClick: () -> Unit,
    onClickBrowse: () -> Unit,
    onClickLatest: () -> Unit,
    onClickSavedSearch: (SavedSearch) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
    onClickSearch: (String) -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    getMangaState: @Composable (Manga) -> State<Manga>,
    // KMK -->
    navigateUp: () -> Unit,
    onWebViewClick: () -> Unit,
    sourceId: Long,
    onLongClickManga: (Manga) -> Unit,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    // KMK <--
) {
    // KMK -->
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    // KMK <--

    Scaffold(
        topBar = { scrollBehavior ->
            // KMK -->
            if (bulkFavoriteState.selectionMode) {
                SelectionToolbar(
                    selectedCount = bulkFavoriteState.selection.size,
                    onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                    onChangeCategoryClicked = bulkFavoriteScreenModel::addFavorite,
                    onSelectAll = {
                        items.forEach {
                            it.results?.forEach { manga ->
                                if (!bulkFavoriteState.selection.contains(manga)) {
                                    bulkFavoriteScreenModel.select(manga)
                                }
                            }
                        }
                    },
                )
            } else {
                // KMK <--
                SourceFeedToolbar(
                    title = name,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    scrollBehavior = scrollBehavior,
                    onClickSearch = onClickSearch,
                    // KMK -->
                    navigateUp = navigateUp,
                    onWebViewClick = onWebViewClick,
                    sourceId = sourceId,
                    toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                    // KMK <--
                )
            }
        },
        floatingActionButton = {
            BrowseSourceFloatingActionButton(
                isVisible = hasFilters,
                onFabClick = onFabClick,
            )
        },
    ) { paddingValues ->
        Crossfade(targetState = isLoading, label = "source_feed") { state ->
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
                        // KMK -->
                        onLongClickManga = onLongClickManga,
                        selection = bulkFavoriteState.selection,
                        // KMK <--
                    )
                }
            }
        }
    }
}

@Composable
fun SourceFeedList(
    items: ImmutableList<SourceFeedUI>,
    paddingValues: PaddingValues,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onClickBrowse: () -> Unit,
    onClickLatest: () -> Unit,
    onClickSavedSearch: (SavedSearch) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
    // KMK -->
    onLongClickManga: (Manga) -> Unit,
    selection: List<Manga>,
    // KMK <--
) {
    ScrollbarLazyColumn(
        contentPadding = paddingValues + topSmallPaddingValues,
    ) {
        items(
            items,
            key = { "source-feed-${it.id}" },
        ) { item ->
            GlobalSearchResultItem(
                modifier = Modifier.animateItem(),
                title = item.title,
                subtitle = null,
                onLongClick = if (item is SourceFeedUI.SourceSavedSearch) {
                    {
                        onClickDelete(item.feed)
                    }
                } else {
                    null
                },
                onClick = when (item) {
                    is SourceFeedUI.Browse -> onClickBrowse
                    is SourceFeedUI.Latest -> onClickLatest
                    is SourceFeedUI.SourceSavedSearch -> {
                        { onClickSavedSearch(item.savedSearch) }
                    }
                },
            ) {
                SourceFeedItem(
                    item = item,
                    getMangaState = { getMangaState(it) },
                    onClickManga = onClickManga,
                    // KMK -->
                    onLongClickManga = onLongClickManga,
                    selection = selection,
                    // KMK <--
                )
            }
        }
    }
}

@Composable
fun SourceFeedItem(
    item: SourceFeedUI,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onClickManga: (Manga) -> Unit,
    // KMK -->
    onLongClickManga: (Manga) -> Unit,
    selection: List<Manga>,
    // KMK <--
) {
    val results = item.results
    when {
        results == null -> {
            GlobalSearchLoadingResultItem()
        }
        results.isEmpty() -> {
            GlobalSearchErrorResultItem(message = stringResource(MR.strings.no_results_found))
        }
        else -> {
            GlobalSearchCardRow(
                titles = item.results.orEmpty(),
                getManga = getMangaState,
                onClick = onClickManga,
                // KMK -->
                onLongClick = onLongClickManga,
                selection = selection,
                // KMK <--
            )
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
    // KMK -->
    navigateUp: () -> Unit,
    onWebViewClick: () -> Unit,
    sourceId: Long,
    toggleSelectionMode: () -> Unit,
    // KMK <--
) {
    SearchToolbar(
        titleContent = { AppBarTitle(title) },
        searchQuery = searchQuery,
        onChangeSearchQuery = onSearchQueryChange,
        onSearch = onClickSearch,
        // KMK -->
        navigateUp = navigateUp,
        onClickCloseSearch = navigateUp,
        // KMK <--
        scrollBehavior = scrollBehavior,
        placeholderText = stringResource(MR.strings.action_search_hint),
        // KMK -->
        actions = {
            AppBarActions(
                actions = persistentListOf(
                    bulkSelectionButton(toggleSelectionMode),
                ),
            )
            persistentListOf(
                if (sourceId != LocalSource.ID) {
                    IconButton(
                        onClick = onWebViewClick,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = stringResource(MR.strings.action_web_view),
                        )
                    }
                } else {
                    null
                },
                SourceSettingsButton(sourceId),
            )
        },
        // KMK <--
    )
}
