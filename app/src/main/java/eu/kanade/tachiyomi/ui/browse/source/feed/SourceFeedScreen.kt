package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.MissingSourceScreen
import eu.kanade.presentation.browse.SourceFeedOrderScreen
import eu.kanade.presentation.browse.SourceFeedScreen
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.presentation.browse.components.FeedActionsDialog
import eu.kanade.presentation.browse.components.SourceFeedAddDialog
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.presentation.more.settings.screen.SettingsEhScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteDialogs
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.md.follows.MangaDexFollowsScreen
import exh.source.anyIs
import exh.source.isEhBasedSource
import exh.util.nullIfBlank
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceFeedScreen(val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = rememberScreenModel { SourceFeedScreenModel(sourceId) }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        // KMK -->
        screenModel.source.let {
            if (it is StubSource) {
                MissingSourceScreen(
                    source = it,
                    navigateUp = navigator::pop,
                )
                return
            }
        }

        LaunchedEffect(navigator.lastItem) {
            // Reset filters & reload saved-searches when screen is navigated back to
            screenModel.resetFilters()
        }

        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
        val showingFeedOrderScreen = rememberSaveable { mutableStateOf(false) }

        val isHentaiEnabled: Boolean = Injekt.get<UnsortedPreferences>().isHentaiEnabled().get()
        val isConfigurableSource = screenModel.source.anyIs<ConfigurableSource>() ||
            screenModel.source.isEhBasedSource() &&
            isHentaiEnabled

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode || showingFeedOrderScreen.value) {
            when {
                bulkFavoriteState.selectionMode -> bulkFavoriteScreenModel.backHandler()
                showingFeedOrderScreen.value -> showingFeedOrderScreen.value = false
            }
        }
        Crossfade(
            targetState = showingFeedOrderScreen.value,
            label = "feed_order_crossfade",
        ) { targetState ->
            if (targetState) {
                SourceFeedOrderScreen(
                    state = state,
                    onClickDelete = screenModel::openDeleteFeed,
                    onChangeOrder = screenModel::changeOrder,
                    navigateUp = { showingFeedOrderScreen.value = false },
                )
            } else {
                // KMK <--
                SourceFeedScreen(
                    name = screenModel.source.name,
                    isLoading = state.isLoading,
                    items = state.items,
                    hasFilters = state.filters.isNotEmpty(),
                    onFabClick = screenModel::openFilterSheet,
                    onClickBrowse = { onBrowseClick(navigator, screenModel.source) },
                    onClickLatest = { onLatestClick(navigator, screenModel.source) },
                    onClickSavedSearch = { onSavedSearchClick(navigator, screenModel.source, it) },
                    // KMK -->
                    // onClickDelete = screenModel::openDeleteFeed,
                    onLongClickFeed = screenModel::openActionsDialog,
                    // KMK <--
                    onClickManga = { manga ->
                        // KMK -->
                        if (bulkFavoriteState.selectionMode) {
                            bulkFavoriteScreenModel.toggleSelection(manga)
                        } else {
                            // KMK <--
                            onMangaClick(navigator, manga)
                        }
                    },
                    onClickSearch = { onSearchClick(navigator, screenModel.source, it) },
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = screenModel::search,
                    getMangaState = { screenModel.getManga(initialManga = it) },
                    // KMK -->
                    navigateUp = { navigator.pop() },
                    onWebViewClick = {
                        val source = screenModel.source as HttpSource
                        navigator.push(
                            WebViewScreen(
                                url = source.baseUrl,
                                initialTitle = source.name,
                                sourceId = source.id,
                            ),
                        )
                    }.takeIf { screenModel.source is HttpSource },
                    onSourceSettingClick = {
                        when {
                            screenModel.source.isEhBasedSource() && isHentaiEnabled ->
                                navigator.push(SettingsEhScreen)
                            screenModel.source.anyIs<ConfigurableSource>() ->
                                navigator.push(SourcePreferencesScreen(screenModel.source.id))
                            else -> {}
                        }
                    }.takeIf { isConfigurableSource },
                    onSortFeedClick = { showingFeedOrderScreen.value = true }
                        .takeIf {
                            screenModel.state.value.items
                                .filterIsInstance<SourceFeedUI.SourceSavedSearch>()
                                .isNotEmpty()
                        },
                    onLongClickManga = { manga ->
                        if (!bulkFavoriteState.selectionMode) {
                            bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                        } else {
                            navigator.push(MangaScreen(manga.id, true))
                        }
                    },
                    bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                    // KMK <--
                )
            }
        }

        val onDismissRequest = screenModel::dismissDialog
        when (val dialog = state.dialog) {
            is SourceFeedScreenModel.Dialog.AddFeed -> {
                SourceFeedAddDialog(
                    onDismissRequest = onDismissRequest,
                    name = dialog.name,
                    addFeed = {
                        screenModel.createFeed(dialog.feedId)
                        onDismissRequest()
                    },
                )
            }
            is SourceFeedScreenModel.Dialog.DeleteFeed -> {
                SourceFeedDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    deleteFeed = {
                        screenModel.deleteFeed(dialog.feed)
                        onDismissRequest()
                    },
                )
            }
            // KMK -->
            is SourceFeedScreenModel.Dialog.FeedActions -> {
                FeedActionsDialog(
                    feed = dialog.feedItem.feed,
                    title = dialog.feedItem.title,
                    onDismissRequest = screenModel::dismissDialog,
                    onClickDelete = { screenModel.openDeleteFeed(it) },
                )
            }
            // KMK <--
            is SourceFeedScreenModel.Dialog.Filter -> {
                SourceFilterDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    // KMK -->
                    onReset = screenModel::resetFilters,
                    // KMK <--
                    onFilter = {
                        screenModel.onFilter { query, filters ->
                            onBrowseClick(
                                navigator = navigator,
                                sourceId = sourceId,
                                search = query,
                                filters = filters,
                            )
                        }
                    },
                    onUpdate = screenModel::setFilters,
                    startExpanded = screenModel.startExpanded,
                    onSave = {},
                    savedSearches = state.savedSearches,
                    onSavedSearch = { search ->
                        screenModel.onSavedSearch(
                            search,
                            onBrowseClick = { query, searchId ->
                                onBrowseClick(
                                    navigator = navigator,
                                    sourceId = sourceId,
                                    search = query,
                                    savedSearch = searchId,
                                )
                            },
                            onToast = {
                                context.toast(it)
                            },
                        )
                    },
                    onSavedSearchPress = { search ->
                        screenModel.onSavedSearchAddToFeed(search) {
                            context.toast(it)
                        }
                    },
                    // KMK -->
                    onSavedSearchPressDesc = stringResource(KMR.strings.saved_searches_add_feed),
                    shouldShowSavingButton = false,
                    // KMK <--
                    openMangaDexRandom = if (screenModel.sourceIsMangaDex) {
                        {
                            screenModel.onMangaDexRandom {
                                // KMK -->
                                // navigator.replace(
                                navigator.push(
                                    // KMK <--
                                    BrowseSourceScreen(
                                        sourceId,
                                        "id:$it",
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    openMangaDexFollows = if (screenModel.sourceIsMangaDex) {
                        {
                            // KMK -->
                            // navigator.replace(MangaDexFollowsScreen(sourceId))
                            navigator.push(MangaDexFollowsScreen(sourceId))
                            // KMK <--
                        }
                    } else {
                        null
                    },
                )
            }
            null -> Unit
        }

        // KMK -->
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }

    private fun onMangaClick(navigator: Navigator, manga: Manga) {
        navigator.push(MangaScreen(manga.id, true))
    }

    private fun onBrowseClick(navigator: Navigator, sourceId: Long, search: String? = null, savedSearch: Long? = null, filters: String? = null) {
        // KMK -->
        // navigator.replace(BrowseSourceScreen(sourceId, search, savedSearch = savedSearch, filtersJson = filters))
        navigator.push(BrowseSourceScreen(sourceId, search, savedSearch = savedSearch, filtersJson = filters))
        // KMK <--
    }

    private fun onLatestClick(navigator: Navigator, source: Source) {
        // KMK -->
        // navigator.replace(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_LATEST))
        navigator.push(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_LATEST))
        // KMK <--
    }

    private fun onBrowseClick(navigator: Navigator, source: Source) {
        // KMK -->
        // navigator.replace(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_POPULAR))
        navigator.push(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_POPULAR))
        // KMK <--
    }

    private fun onSavedSearchClick(navigator: Navigator, source: Source, savedSearch: SavedSearch) {
        // KMK -->
        // navigator.replace(BrowseSourceScreen(source.id, listingQuery = null, savedSearch = savedSearch.id))
        navigator.push(BrowseSourceScreen(source.id, listingQuery = null, savedSearch = savedSearch.id))
        // KMK <--
    }

    private fun onSearchClick(navigator: Navigator, source: Source, query: String) {
        onBrowseClick(navigator, source.id, query.nullIfBlank())
    }
}
