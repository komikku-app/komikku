package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.MissingSourceScreen
import eu.kanade.presentation.browse.components.BrowseSourceToolbar
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.browse.components.SavedSearchCreateDialog
import eu.kanade.presentation.browse.components.SavedSearchDeleteDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.AllowDuplicateDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.ChangeMangasCategoryDialog
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.md.follows.MangaDexFollowsScreen
import exh.source.isEhBasedSource
import exh.ui.ifSourcesLoaded
import exh.ui.smartsearch.SmartSearchScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.core.common.Constants
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BrowseSourceScreen(
    private val sourceId: Long,
    private val listingQuery: String?,
    // SY -->
    private val filtersJson: String? = null,
    private val savedSearch: Long? = null,
    /** being set when called from [SmartSearchScreen] or when click on a manga from this screen
     * which was previously opened from `SmartSearchScreen` */
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig? = null,
    // SY <--
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = rememberScreenModel {
            BrowseSourceScreenModel(
                sourceId = sourceId,
                listingQuery = listingQuery,
                // SY -->
                filtersJson = filtersJson,
                savedSearch = savedSearch,
                // SY <--
            )
        }
        val state by screenModel.state.collectAsState()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        // SY -->
        val context = LocalContext.current
        // SY <--

        // KMK -->
        screenModel.source.let {
            // KMK <--
            if (it is StubSource) {
                MissingSourceScreen(
                    source = it,
                    navigateUp = navigateUp,
                )
                return
            }
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        val onHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source as? HttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? HttpSource)?.baseUrl
        }

        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        BulkSelectionToolbar(
                            selectedCount = bulkFavoriteState.selection.size,
                            isRunning = bulkFavoriteState.isRunning,
                            onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                            onChangeCategoryClick = bulkFavoriteScreenModel::addFavorite,
                            onSelectAll = {
                                state.mangaDisplayingList.forEach { manga ->
                                    bulkFavoriteScreenModel.select(manga)
                                }
                            },
                            onReverseSelection = {
                                bulkFavoriteScreenModel.reverseSelection(state.mangaDisplayingList.toList())
                            },
                        )
                    } else {
                        // KMK <--
                        BrowseSourceToolbar(
                            searchQuery = state.toolbarQuery,
                            onSearchQueryChange = screenModel::setToolbarQuery,
                            source = screenModel.source,
                            displayMode = screenModel.displayMode
                                // KMK -->
                                .takeIf {
                                    !screenModel.source.isEhBasedSource() || !screenModel.ehentaiBrowseDisplayMode
                                },
                            // KMK <--
                            onDisplayModeChange = { screenModel.displayMode = it },
                            navigateUp = navigateUp,
                            onWebViewClick = onWebViewClick,
                            onHelpClick = onHelpClick,
                            onSettingsClick = { navigator.push(SourcePreferencesScreen(sourceId)) },
                            onSearch = screenModel::search,
                            // KMK -->
                            toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                            isRunning = bulkFavoriteState.isRunning,
                            // KMK <--
                        )
                    }

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((screenModel.source as CatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (/* SY --> */ state.filterable /* SY <-- */) {
                            FilterChip(
                                selected = state.listing is Listing.Search &&
                                    // KMK -->
                                    (state.listing as Listing.Search).savedSearchId == null,
                                // KMK <--
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    // SY -->
                                    Text(
                                        text = if (state.filters.isNotEmpty()) {
                                            stringResource(MR.strings.action_filter)
                                        } else {
                                            stringResource(MR.strings.action_search)
                                        },
                                    )
                                    // SY <--
                                },
                            )
                        }
                        // KMK -->
                        state.savedSearches.forEach { savedSearch ->
                            FilterChip(
                                selected = state.listing is Listing.Search &&
                                    (state.listing as Listing.Search).savedSearchId == savedSearch.id,
                                onClick = {
                                    screenModel.onSavedSearch(savedSearch) {
                                        context.toast(it)
                                    }
                                },
                                label = {
                                    Text(
                                        text = savedSearch.name,
                                    )
                                },
                            )
                        }
                        // KMK <--
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingFlow by screenModel.mangaPagerFlowFlow.collectAsState()

            BrowseSourceContent(
                source = screenModel.source,
                mangaList = pagingFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                // SY -->
                ehentaiBrowseDisplayMode = screenModel.ehentaiBrowseDisplayMode,
                // SY <--
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = onHelpClick,
                onMangaClick = {
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(it)
                    } else {
                        // KMK <--
                        navigator.push(
                            MangaScreen(
                                it.id,
                                // KMK -->
                                // Finding the entry to be merged to, so we don't want to expand description
                                // so that user can see the `Merge to another` button
                                smartSearchConfig != null,
                                // KMK <--
                                smartSearchConfig,
                            ),
                        )
                    }
                },
                onMangaLongClick = { manga ->
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        navigator.push(MangaScreen(manga.id, true))
                    } else {
                        // KMK <--
                        scope.launchIO {
                            val duplicateManga = screenModel.getDuplicateLibraryManga(manga)
                            when {
                                manga.favorite -> screenModel.setDialog(
                                    BrowseSourceScreenModel.Dialog.RemoveManga(manga),
                                )
                                duplicateManga != null -> screenModel.setDialog(
                                    BrowseSourceScreenModel.Dialog.AddDuplicateManga(
                                        manga,
                                        duplicateManga,
                                    ),
                                )
                                else -> screenModel.addFavorite(manga)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
                // KMK -->
                selection = bulkFavoriteState.selection,
                browseSourceState = state,
                // KMK <--
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseSourceScreenModel.Dialog.Filter -> {
                SourceFilterDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                    // SY -->
                    startExpanded = screenModel.startExpanded,
                    onSave = screenModel::onSaveSearch,
                    savedSearches = state.savedSearches,
                    onSavedSearch = { search ->
                        screenModel.onSavedSearch(search) {
                            context.toast(it)
                        }
                    },
                    onSavedSearchPress = screenModel::onSavedSearchPress,
                    // KMK -->
                    onSavedSearchPressDesc = stringResource(KMR.strings.saved_searches_delete),
                    // KMK <--
                    openMangaDexRandom = if (screenModel.sourceIsMangaDex) {
                        {
                            screenModel.onMangaDexRandom {
                                navigator.replace(
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
                            navigator.replace(MangaDexFollowsScreen(sourceId))
                        }
                    } else {
                        null
                    },
                    // SY <--
                )
            }

            is BrowseSourceScreenModel.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        // SY -->
                        PreMigrationScreen.navigateToMigration(
                            Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                            navigator,
                            dialog.duplicate.id,
                            dialog.manga.id,
                        )
                        // SY <--
                    },
                    // KMK -->
                    duplicate = dialog.duplicate,
                    // KMK <--
                )
            }

            is BrowseSourceScreenModel.Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeMangaFavorite(dialog.manga)
                    },
                    mangaToRemove = dialog.manga,
                )
            }

            is BrowseSourceScreenModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.changeMangaFavorite(dialog.manga)
                        screenModel.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            is BrowseSourceScreenModel.Dialog.CreateSavedSearch -> SavedSearchCreateDialog(
                onDismissRequest = onDismissRequest,
                currentSavedSearches = dialog.currentSavedSearches,
                saveSearch = screenModel::saveSearch,
            )
            is BrowseSourceScreenModel.Dialog.DeleteSavedSearch -> SavedSearchDeleteDialog(
                onDismissRequest = onDismissRequest,
                name = dialog.name,
                deleteSavedSearch = {
                    screenModel.deleteSearch(dialog.idToDelete)
                },
            )
            else -> {}
        }

        // KMK -->
        when (bulkFavoriteState.dialog) {
            is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory ->
                ChangeMangasCategoryDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.AllowDuplicate ->
                AllowDuplicateDialog(bulkFavoriteScreenModel)
            else -> {}
        }
        // KMK <--

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    when (it) {
                        is SearchType.Genre -> screenModel.searchGenre(it.txt)
                        is SearchType.Text -> screenModel.search(it.txt)
                    }
                }
        }
    }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
