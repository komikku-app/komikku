package eu.kanade.presentation.browse

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.browse.components.BrowseSourceFloatingActionButton
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.browse.components.bulkSelectionButton
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

sealed class SourceFeedUI {
    abstract val id: Long

    abstract val title: Any

    abstract val results: List<Manga>?

    abstract fun withResults(results: List<Manga>?): SourceFeedUI

    data class Latest(override val results: List<Manga>?) : SourceFeedUI() {
        override val id: Long = -1
        override val title: StringResource
            get() = MR.strings.latest

        override fun withResults(results: List<Manga>?): SourceFeedUI {
            return copy(results = results)
        }
    }
    data class Browse(override val results: List<Manga>?) : SourceFeedUI() {
        override val id: Long = -2
        override val title: StringResource
            get() = MR.strings.browse

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
    // KMK -->
    // onClickDelete: (FeedSavedSearch) -> Unit,
    onLongClickFeed: (SourceFeedUI.SourceSavedSearch) -> Unit,
    // KMK <--
    onClickManga: (Manga) -> Unit,
    onClickSearch: (String) -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    getMangaState: @Composable (Manga) -> State<Manga>,
    // KMK -->
    navigateUp: () -> Unit,
    onWebViewClick: (() -> Unit)?,
    onToggleIncognito: () -> Unit,
    onSourceSettingClick: (() -> Unit?)?,
    onSortFeedClick: (() -> Unit)?,
    onLongClickManga: (Manga) -> Unit,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    // KMK <--
) {
    // KMK -->
    val scope = rememberCoroutineScope()
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    // KMK <--

    Scaffold(
        topBar = { scrollBehavior ->
            // KMK -->
            if (bulkFavoriteState.selectionMode) {
                BulkSelectionToolbar(
                    selectedCount = bulkFavoriteState.selection.size,
                    isRunning = bulkFavoriteState.isRunning,
                    onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                    onChangeCategoryClick = bulkFavoriteScreenModel::addFavorite,
                    onSelectAll = {
                        items.mapNotNull { it.results }
                            .flatten()
                            .let {
                                scope.launchIO {
                                    bulkFavoriteScreenModel.networkToLocalManga.getLocal(it)
                                        .forEach { bulkFavoriteScreenModel.select(it) }
                                }
                            }
                    },
                    onReverseSelection = {
                        items.mapNotNull { it.results }
                            .flatten()
                            .let {
                                scope.launchIO {
                                    bulkFavoriteScreenModel.reverseSelection(
                                        bulkFavoriteScreenModel.networkToLocalManga.getLocal(it),
                                    )
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
                    onToggleIncognito = onToggleIncognito,
                    onSourceSettingClick = onSourceSettingClick,
                    onSortFeedClick = onSortFeedClick,
                    toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                    isRunning = bulkFavoriteState.isRunning,
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
                        // KMK -->
                        // onClickDelete = onClickDelete,
                        onLongClickFeed = onLongClickFeed,
                        // KMK <--
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
    // KMK -->
    // onClickDelete: (FeedSavedSearch) -> Unit,
    onLongClickFeed: (SourceFeedUI.SourceSavedSearch) -> Unit,
    // KMK <--
    onClickManga: (Manga) -> Unit,
    // KMK -->
    onLongClickManga: (Manga) -> Unit,
    selection: List<Manga>,
    // KMK <--
) {
    ScrollbarLazyColumn(
        contentPadding = paddingValues + topSmallPaddingValues,
    ) {
        // KMK -->
        items(
            items,
            key = { "source-feed-${it.id}" },
        ) { item ->
            // KMK <--
            GlobalSearchResultItem(
                modifier = Modifier.animateItem(),
                title =
                // KMK -->
                if (item !is SourceFeedUI.SourceSavedSearch) {
                    stringResource(item.title as StringResource)
                } else {
                    // KMK <--
                    item.title
                },
                subtitle = null,
                onLongClick = if (item is SourceFeedUI.SourceSavedSearch) {
                    {
                        // KMK -->
                        onLongClickFeed(item)
                        // KMK <--
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
    onWebViewClick: (() -> Unit)?,
    onToggleIncognito: () -> Unit,
    onSourceSettingClick: (() -> Unit?)?,
    onSortFeedClick: (() -> Unit)?,
    toggleSelectionMode: () -> Unit,
    isRunning: Boolean,
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
                actions = persistentListOf<AppBar.AppBarAction>().builder()
                    .apply {
                        add(bulkSelectionButton(isRunning, toggleSelectionMode))

                        onWebViewClick?.let {
                            add(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_web_view),
                                    onClick = { onWebViewClick() },
                                    icon = Icons.Outlined.Public,
                                ),
                            )
                        }

                        // KMK -->
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.pref_incognito_mode),
                                onClick = onToggleIncognito,
                            ),
                        )
                        // KMK <--

                        onSortFeedClick?.let {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(KMR.strings.action_sort_feed),
                                    onClick = { onSortFeedClick() },
                                ),
                            )
                        }

                        onSourceSettingClick?.let {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.label_settings),
                                    onClick = { onSourceSettingClick() },
                                ),
                            )
                        }
                    }
                    .build(),
            )
        },
        // KMK <--
    )
}
