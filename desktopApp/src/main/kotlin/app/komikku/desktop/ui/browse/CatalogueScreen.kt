package app.komikku.desktop.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.CoverImage
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.desktop.ui.migration.MigrationListScreen
import app.komikku.desktop.webview.WebViewDialogs
import app.komikku.desktop.webview.rememberWebViewLauncher
import app.komikku.shared.jssource.JsSourceNotesBanner
import app.komikku.sourcejs.mihon.MangayomiJsSource
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.components.SavedSearchCreateDialog
import eu.kanade.presentation.browse.components.SavedSearchDeleteDialog
import eu.kanade.presentation.browse.components.SourceFeedAddDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.isFeedFull
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterItem
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogE
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.interactor.CountFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import tachiyomi.core.common.i18n.stringResource as contextString

/**
 * Popular / latest / search listing of one source, as a cover grid. With [initialQuery] (global search: "open this
 * source's own search") it starts with that search, like Android's `BrowseSourceScreen(sourceId, query)`; with
 * [savedSearchId] (a feed row) with that saved search, with [latest] (a feed row) with the latest updates when the
 * source has them, like Android's `BrowseSourceScreen(sourceId, listingQuery, savedSearch)`; with [openFilters] (the
 * source feed's filter button) with the filter panel open.
 *
 * P20: with [migrationFor] (a row header of the migration search, Android's `MigrateSourceSearchScreen`) a click on an
 * entry makes it that library entry's match in the migration list below and shows the list again (the entry itself is
 * ignored); like Android there is no bulk selection there, a long press opens the entry's details.
 */
class CatalogueScreen(
    private val sourceId: Long,
    private val initialQuery: String? = null,
    private val savedSearchId: Long? = null,
    private val latest: Boolean = false,
    private val openFilters: Boolean = false,
    private val migrationFor: Long? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CatalogueScreenModel(sourceId, initialQuery, savedSearchId, latest, openFilters) }
        val state by model.state.collectAsState()
        val source = model.source
        // P7: Android's WebView action (`BrowseSourceScreen.onWebViewClick`)
        val webView = rememberWebViewLauncher()
        val onWebViewClick = (source as? HttpSource)?.let { http -> { webView.open(http.getHomeUrl(), http.id) } }
        val context = LocalContext.current
        var savingSearch by remember { mutableStateOf(false) }
        var deletingSearch by remember { mutableStateOf<EXHSavedSearch?>(null) }
        val tooManyInFeed = stringResource(KMR.strings.too_many_in_feed)
        val onSourceError: (String) -> Unit = { context.toast(context.contextString(KMR.strings.desktop_source_error, it)) }
        // Back from the source's settings: its filters may have changed (a JavaScript source's follow its preferences)
        LaunchedEffect(model) { model.onResumed() }
        // P14: Komikku's bulk favourites (selection mode)
        val bulkModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkState by bulkModel.state.collectAsState()
        val bulkClicks = BulkClicks(bulkModel, bulkState.selectionMode) { manga -> navigator.push(MangaDetailsScreen(manga.id)) }
        // P20: Android's `MigrateSourceSearchScreen` has no bulk selection: a click picks the match, a long press opens
        // the details
        val migrationList = migrationFor?.let { navigator.items.filterIsInstance<MigrationListScreen>().lastOrNull() }
        val onClick: (Manga) -> Unit = if (migrationFor == null || migrationList == null) {
            bulkClicks::onClick
        } else {
            { manga ->
                if (manga.id != migrationFor) {
                    migrationList.addMatchOverride(current = migrationFor, target = manga.id)
                    navigator.popUntil { it === migrationList }
                }
            }
        }
        val onLongClick: (Manga) -> Unit = if (migrationList == null) {
            bulkClicks::onLongClick
        } else {
            { manga -> navigator.push(MangaDetailsScreen(manga.id)) }
        }
        // Another listing (a search, the Popular / Latest chips, applied filters or a saved search): the selected entries
        // are no longer the ones shown, so the selection ends with it
        LaunchedEffect(model, bulkModel) {
            model.state.map { it.listing }.distinctUntilChanged().drop(1).collect { bulkModel.toggleSelectionMode(false) }
        }
        BrowseScaffold(
            title = if (bulkState.selectionMode) "${bulkState.selection.size}" else source?.name.orEmpty(),
            // While selecting, "back" ends the selection (Android's action mode)
            onBack = { if (bulkState.selectionMode) bulkModel.toggleSelectionMode(false) else navigator.pop() },
            actions = {
                if (bulkState.selectionMode) {
                    BulkSelectionActions(
                        state = bulkState,
                        onSelectAll = { state.manga.forEach(bulkModel::select) },
                        onReverseSelection = { bulkModel.reverseSelection(state.manga) },
                        onAddToLibrary = { bulkModel.addFavorite() },
                    )
                    return@BrowseScaffold
                }
                if (source is CatalogueSource) {
                    if (migrationList == null) {
                        BulkSelectionButton(isRunning = bulkState.isRunning, onClick = { bulkModel.toggleSelectionMode() })
                    }
                    // P13: Android's filter sheet (saved searches included) as a side panel; the badge shows that the
                    // search runs with filters other than the source's defaults
                    IconButton(
                        onClick = { if (state.filterPanelOpen) model.closeFilters() else model.openFilters() },
                        modifier = Modifier.testTag("catalogue-filter"),
                    ) {
                        BadgedBox(
                            badge = { if (state.filtersActive) Badge(modifier = Modifier.testTag("catalogue-filter-active")) },
                        ) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(MR.strings.action_filter))
                        }
                    }
                }
                if (onWebViewClick != null) {
                    IconButton(onClick = onWebViewClick, modifier = Modifier.testTag("catalogue-webview")) {
                        Icon(Icons.Outlined.Public, contentDescription = stringResource(MR.strings.action_open_in_web_view))
                    }
                }
                if (source is ConfigurableSource) {
                    IconButton(
                        onClick = {
                            model.onSourceSettingsOpened()
                            navigator.push(SourcePreferencesScreen(sourceId))
                        },
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.label_settings))
                    }
                }
            },
        ) {
            if (source !is CatalogueSource) {
                EmptyScreen(stringRes = KMR.strings.desktop_source_not_installed)
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        (source as? MangayomiJsSource)?.descriptor?.notes?.takeIf { it.isNotBlank() }?.let { JsSourceNotesBanner(it) }
                        CatalogueContent(
                            state = state,
                            supportsLatest = source.supportsLatest,
                            onMode = model::setMode,
                            onSearch = model::search,
                            onLoadMore = model::loadMore,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            selection = bulkState.selection,
                            onWebViewClick = onWebViewClick,
                        )
                    }
                    if (state.filterPanelOpen) {
                        VerticalDivider()
                        CatalogueFilterPanel(
                            filters = state.editingFilters,
                            error = state.filtersError,
                            startExpanded = model.startExpanded,
                            savedSearches = state.savedSearches,
                            onReset = model::resetFilters,
                            onFilter = { model.applyFilters(onSourceError) },
                            onUpdate = model::updateFilters,
                            onSave = { savingSearch = true },
                            onSavedSearch = {
                                model.applySavedSearch(it, onInvalid = { context.toast(SYMR.strings.save_search_invalid) }, onError = onSourceError)
                            },
                            onDeleteSavedSearch = { deletingSearch = it },
                            onAddSavedSearchToFeed = { model.askAddToFeed(it, onFull = { context.toast(tooManyInFeed) }) },
                            onClose = model::closeFilters,
                        )
                    }
                }
            }
        }
        WebViewDialogs(webView)
        BulkFavoriteDialogs(bulkModel, bulkState.dialog)
        // P13: Android's saved search dialogs (BrowseSourceDialogs), a duplicate or blank name is rejected there
        if (savingSearch) {
            SavedSearchCreateDialog(
                onDismissRequest = { savingSearch = false },
                currentSavedSearches = state.savedSearches.map { it.name }.toImmutableList(),
                saveSearch = { model.saveSearch(it, onSourceError) },
            )
        }
        deletingSearch?.let { search ->
            SavedSearchDeleteDialog(
                onDismissRequest = { deletingSearch = null },
                name = search.name,
                deleteSavedSearch = { model.deleteSearch(search.id) },
            )
        }
        // P13: Android's "add to feed" confirmation (the source feed's filter sheet), into this source's own feed
        state.addingToFeed?.let { search ->
            SourceFeedAddDialog(
                onDismissRequest = model::dismissAddToFeed,
                name = search.name,
                addFeed = { model.addToFeed(search) },
            )
        }
    }
}

class CatalogueScreenModel(
    sourceId: Long,
    initialQuery: String? = null,
    savedSearchId: Long? = null,
    latest: Boolean = false,
    openFilters: Boolean = false,
    sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    getIncognitoState: GetIncognitoState = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
    private val insertSavedSearch: InsertSavedSearch = Injekt.get(),
    private val deleteSavedSearchById: DeleteSavedSearchById = Injekt.get(),
    private val filterSerializer: FilterSerializer = Injekt.get(),
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
) : StateScreenModel<CatalogueScreenModel.State>(
    State(
        mode = when {
            !initialQuery.isNullOrBlank() -> Mode.Search(initialQuery)
            latest && sourceManager.get(sourceId)?.supportsLatest == true -> Mode.Latest
            else -> Mode.Popular
        },
        // A saved search to open: its listing starts once it is loaded
        openingSavedSearch = savedSearchId != null,
    ),
) {

    sealed interface Mode {
        data object Popular : Mode
        data object Latest : Mode

        /**
         * A search for [query]. [filters]: the filters the user applied (filter panel, saved search) when they differ
         * from the source's defaults; null = the defaults, taken fresh from the source for each request.
         */
        data class Search(val query: String, val filters: FilterList? = null) : Mode
    }

    data class State(
        val mode: Mode = Mode.Popular,
        val manga: List<Manga> = emptyList(),
        val page: Int = 0,
        val hasNextPage: Boolean = true,
        val isLoading: Boolean = false,
        val error: String? = null,
        /**
         * A feed row's saved search is being opened (`CatalogueScreen(savedSearchId)`): no listing yet, it starts once
         * the search is loaded. Tracked apart from [isLoading]: anything that supersedes the opening clears it.
         */
        val openingSavedSearch: Boolean = false,
        /** Whether the filter panel (Android's filter sheet) is shown. */
        val filterPanelOpen: Boolean = false,
        /**
         * The filters edited in the open panel: a copy of the applied ones (or the source's fresh defaults), so editing
         * changes no running search until "Filter"; null while they load and while the panel is closed.
         */
        val editingFilters: FilterList? = null,
        /** Why the panel's filters could not be loaded (the source's error). */
        val filtersError: String? = null,
        /** The source's saved searches, sorted by name like Android's filter sheet. */
        val savedSearches: ImmutableList<EXHSavedSearch> = persistentListOf(),
        /** Bumped whenever the model sets the search query itself (a saved search applied): the search field shows it. */
        val queryEvent: Int = 0,
        /** The saved search the user is asked to add to the source's feed. */
        val addingToFeed: EXHSavedSearch? = null,
        /** Bumped whenever another listing starts (mode, query or filters): the bulk selection ends with the old one. */
        val listing: Int = 0,
    ) {
        /** The search runs with filters other than the source's defaults. */
        val filtersActive: Boolean get() = (mode as? Mode.Search)?.filters != null

        /** The query of the current search, empty for popular / latest. */
        val query: String get() = (mode as? Mode.Search)?.query.orEmpty()
    }

    val source: Source? = sourceManager.get(sourceId)
    private val catalogueSource = source as? CatalogueSource

    /** Android's "expand all filters" preference (`UiPreferences.expandFilters`). */
    val startExpanded: Boolean = uiPreferences.expandFilters().get()

    /** The page load of the current listing. */
    private var listingJob: Job? = null

    /** A saved search or the panel's filters being applied; a newer apply, search or mode change cancels it. */
    private var applyJob: Job? = null

    /** The panel's filters being loaded. */
    private var filtersJob: Job? = null

    private var sourceSettingsOpened = false

    init {
        if (catalogueSource != null) {
            if (savedSearchId == null) loadMore() else openSavedSearch(catalogueSource, savedSearchId)
            // The saved searches' own filter lists are not used here (only their names are listed; applying one loads
            // it again), so a failing filter list lists them all the same
            getExhSavedSearch.subscribe(sourceId) { filterListOrEmpty(catalogueSource) }
                .map { it.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name)) }
                .onEach { searches -> mutableState.update { it.copy(savedSearches = searches.toImmutableList()) } }
                .catch { xLogE("Failed to load the saved searches of source $sourceId", it) }
                .launchIn(screenModelScope)
            // Same as Android's BrowseSourceScreenModel: a source opened outside incognito mode becomes the last used
            // one. A source that is not installed (nothing to browse) is not recorded.
            getIncognitoState.subscribe(sourceId)
                .onEach { incognito -> if (!incognito) sourcePreferences.lastUsedSource().set(sourceId) }
                .launchIn(screenModelScope)
            if (openFilters) openFilters()
        }
    }

    /**
     * Popular, latest or a search. Popular and latest reset the filters like Android's chips (`resetFilters`); the
     * panel, if open, shows the source's defaults again.
     */
    fun setMode(mode: Mode) {
        // No cancelApply(): the new listing starts right away (and ends a pending saved-search opening)
        applyJob?.cancel()
        startListing(mode)
        if (mode !is Mode.Search && state.value.filterPanelOpen) loadEditingFilters(applied = null)
    }

    /** A typed search: keeps the filters the current search runs with, like Android's `search(query)`. */
    fun search(query: String) {
        setMode(Mode.Search(query, (state.value.mode as? Mode.Search)?.filters))
    }

    /** Starts [mode]'s listing from its first page (also when only its filters changed). */
    private fun startListing(mode: Mode, queryApplied: Boolean = false) {
        listingJob?.cancel()
        mutableState.update {
            it.copy(
                mode = mode,
                manga = emptyList(),
                page = 0,
                hasNextPage = true,
                isLoading = false,
                error = null,
                openingSavedSearch = false,
                queryEvent = if (queryApplied) it.queryEvent + 1 else it.queryEvent,
                listing = it.listing + 1,
            )
        }
        loadMore()
    }

    fun openFilters() {
        mutableState.update { it.copy(filterPanelOpen = true) }
        loadEditingFilters((state.value.mode as? Mode.Search)?.filters)
    }

    fun closeFilters() {
        filtersJob?.cancel()
        mutableState.update { it.copy(filterPanelOpen = false, editingFilters = null, filtersError = null) }
    }

    /** The panel's filters changed (Android's `setFilters`): only the edited copy, the running search keeps its own. */
    fun updateFilters(filters: FilterList) {
        if (state.value.editingFilters == null) return
        mutableState.update { it.copy(editingFilters = filters) }
    }

    /** Android's `resetFilters`: the source's defaults, taken fresh, in the panel ("Filter" applies them). */
    fun resetFilters() = loadEditingFilters(applied = null)

    /** Loads the panel's filters: the source's fresh list, with [applied]'s states carried over when given. */
    private fun loadEditingFilters(applied: FilterList?) {
        val source = catalogueSource ?: return
        filtersJob?.cancel()
        mutableState.update { it.copy(editingFilters = null, filtersError = null) }
        filtersJob = screenModelScope.launch {
            sourceCall { freshFilters(source, applied) }
                .onSuccess { filters -> mutableState.update { it.copy(editingFilters = filters) } }
                .onFailure { e -> mutableState.update { it.copy(filtersError = e.errorText()) } }
        }
    }

    /** Android's "Filter" button: searches the current query with the panel's filters and closes the panel. */
    fun applyFilters(onError: (String) -> Unit) {
        val source = catalogueSource ?: return
        val filters = state.value.editingFilters ?: return
        cancelApply()
        applyJob = screenModelScope.launch {
            val defaults = sourceCall { source.getFilterList() }.getOrElse {
                onError(it.errorText())
                return@launch
            }
            closeFilters()
            startListing(Mode.Search(state.value.query, filters.takeUnless { it.list == defaults.list }))
        }
    }

    /**
     * Same as Android's `BrowseSourceScreenModel.onSavedSearch`: reloads the saved search, refuses one whose filters no
     * longer match the source's, otherwise searches its query with its filters (the source's defaults when it has none
     * or they are the defaults). The last one applied wins: a newer apply, search or mode change cancels this one.
     */
    fun applySavedSearch(loaded: EXHSavedSearch, onInvalid: () -> Unit, onError: (String) -> Unit) {
        val source = catalogueSource ?: return
        cancelApply()
        applyJob = screenModelScope.launch {
            val (defaults, search) = sourceCall {
                source.getFilterList() to (getExhSavedSearch.awaitOne(loaded.id, source::getFilterList) ?: loaded)
            }.getOrElse {
                onError(it.errorText())
                return@launch
            }
            if (search.filterList == null && defaults.isNotEmpty()) {
                onInvalid()
                return@launch
            }
            closeFilters()
            startListing(
                Mode.Search(search.query.orEmpty(), search.filterList?.takeUnless { it.list == defaults.list }),
                queryApplied = true,
            )
        }
    }

    /**
     * Opens with a saved search (a feed row), like Android's `BrowseSourceScreenModel` with `savedSearch`: its query
     * with its filters, the source's defaults when they no longer load; popular when it is gone or fails.
     */
    private fun openSavedSearch(source: CatalogueSource, savedSearchId: Long) {
        applyJob = screenModelScope.launch {
            val (defaults, search) = sourceCall {
                source.getFilterList() to getExhSavedSearch.awaitOne(savedSearchId, source::getFilterList)
            }.getOrElse { null to null }
            if (defaults == null || search == null) {
                mutableState.update { it.copy(openingSavedSearch = false) }
                loadMore()
                return@launch
            }
            startListing(
                Mode.Search(search.query.orEmpty(), search.filterList?.takeUnless { it.list == defaults.list }),
                queryApplied = true,
            )
        }
    }

    /**
     * Same as Android's `BrowseSourceScreenModel.saveSearch`: the current search query (if any) and the filter panel's
     * filters (where "Save" is, like Android's sheet), else the applied filters or the source's defaults.
     */
    fun saveSearch(name: String, onError: (String) -> Unit) {
        val source = catalogueSource ?: return
        val current = state.value
        screenModelScope.launch {
            withContext(NonCancellable) {
                val query = current.query.takeUnless { it.isBlank() }?.trim()
                val filterList = current.editingFilters
                    ?: (current.mode as? Mode.Search)?.filters
                    ?: sourceCall { source.getFilterList() }.getOrElse {
                        onError(it.errorText())
                        return@withContext
                    }
                withIOContext {
                    insertSavedSearch.await(
                        SavedSearch(
                            id = -1,
                            source = source.id,
                            name = name.trim(),
                            query = query,
                            filtersJson = runCatching {
                                filterSerializer.serialize(filterList).ifEmpty { null }?.let { Json.encodeToString(it) }
                            }.getOrNull(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Android's "add to feed" of a saved search (`SourceFeedScreenModel.onSavedSearchAddToFeed`): refused with [onFull]
     * once the source's feed holds its limit, else asks for confirmation ([State.addingToFeed]).
     */
    fun askAddToFeed(search: EXHSavedSearch, onFull: () -> Unit) {
        val source = catalogueSource ?: return
        screenModelScope.launch {
            if (isFeedFull(withIOContext { countFeedSavedSearchBySourceId.await(source.id) })) {
                onFull()
                return@launch
            }
            mutableState.update { it.copy(addingToFeed = search) }
        }
    }

    fun dismissAddToFeed() {
        mutableState.update { it.copy(addingToFeed = null) }
    }

    /** Adds [search] to the source's own feed (Android's `SourceFeedScreenModel.createFeed`, not the global feed). */
    fun addToFeed(search: EXHSavedSearch) {
        val source = catalogueSource ?: return
        dismissAddToFeed()
        screenModelScope.launch {
            withContext(NonCancellable) {
                withIOContext {
                    insertFeedSavedSearch.await(
                        FeedSavedSearch(id = -1, source = source.id, savedSearch = search.id, global = false, feedOrder = 0),
                    )
                }
            }
        }
    }

    fun deleteSearch(savedSearchId: Long) {
        screenModelScope.launch {
            withContext(NonCancellable) { withIOContext { deleteSavedSearchById.await(savedSearchId) } }
        }
    }

    /** The source's settings are being opened: [onResumed] refreshes the filters when the catalogue is shown again. */
    fun onSourceSettingsOpened() {
        sourceSettingsOpened = true
    }

    /**
     * The catalogue is shown (again). Back from the source's settings, the filters are refreshed: a JavaScript
     * source's filter list can follow its preferences, so the applied and the edited filters are carried over onto
     * the source's new list (and the search restarted with them); without applied filters each request takes the new
     * list anyway.
     */
    fun onResumed() {
        if (!sourceSettingsOpened) return
        sourceSettingsOpened = false
        val source = catalogueSource ?: return
        if (state.value.filterPanelOpen) loadEditingFilters(state.value.editingFilters)
        val mode = state.value.mode as? Mode.Search ?: return
        val applied = mode.filters ?: return
        cancelApply()
        applyJob = screenModelScope.launch {
            val (filters, defaults) = sourceCall { freshFilters(source, applied) to source.getFilterList() }
                .getOrElse { return@launch }
            startListing(Mode.Search(mode.query, filters.takeUnless { it.list == defaults.list }))
        }
    }

    /**
     * Cancels the running apply (P13 review fixes). When that was the opening of a feed row's saved search, which has
     * no listing yet, the current listing (popular / latest) starts now: an apply that then fails or is refused leaves
     * the catalogue usable instead of loading forever, and one that succeeds replaces the listing.
     */
    private fun cancelApply() {
        applyJob?.cancel()
        if (state.value.openingSavedSearch) {
            mutableState.update { it.copy(openingSavedSearch = false) }
            loadMore()
        }
    }

    fun loadMore() {
        val source = catalogueSource ?: return
        val current = state.value
        if (current.isLoading || current.openingSavedSearch || !current.hasNextPage) return
        mutableState.update { it.copy(isLoading = true, error = null) }
        listingJob = screenModelScope.launch {
            try {
                val page = current.page + 1
                val result = withIOContext {
                    when (val mode = current.mode) {
                        Mode.Popular -> source.getPopularManga(page)
                        Mode.Latest -> source.getLatestUpdates(page)
                        // A fresh filter list per request unless the user applied filters (P13 review fixes)
                        is Mode.Search -> source.getSearchManga(page, mode.query, mode.filters ?: source.getFilterList())
                    }
                }
                // Same as the Android browse paging source: store the entries so they have ids
                val manga = withIOContext { networkToLocalManga(result.mangas.map { it.toDomainManga(source.id) }) }
                mutableState.update {
                    it.copy(
                        manga = (it.manga + manga).distinctBy { entry -> entry.id },
                        page = page,
                        hasNextPage = result.hasNextPage,
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Extension code can throw errors too (e.g. a missing Android class); they are the source's error
                xLogE("Failed to load page of source ${source.id}", e)
                mutableState.update { it.copy(isLoading = false, error = e.errorText()) }
            }
        }
    }

    /** The source's filter list with [applied]'s states carried over (Android's saved search deserialisation). */
    private fun freshFilters(source: CatalogueSource, applied: FilterList?): FilterList {
        val filters = source.getFilterList()
        if (applied != null) {
            runCatching { filterSerializer.deserialize(filters, filterSerializer.serialize(applied)) }
                .onFailure { xLogE("Failed to carry the filters of source ${source.id} over", it) }
        }
        return filters
    }

    private fun filterListOrEmpty(source: CatalogueSource): FilterList = try {
        source.getFilterList()
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        xLogE("Failed to get the filters of source ${source.id}", e)
        FilterList()
    }

    /**
     * Runs extension code ([block], e.g. `getFilterList()`, which may be slow) off the UI thread; any throwable but a
     * cancellation (extension code can throw errors, e.g. a missing Android class) becomes a failure.
     */
    private suspend fun <T> sourceCall(block: suspend () -> T): Result<T> = try {
        Result.success(withIOContext { block() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // A superseded call (its job cancelled while the source was busy) ends here instead of acting on the failure
        currentCoroutineContext().ensureActive()
        xLogE("Source call of ${source?.id} failed", e)
        Result.failure(e)
    }

    private fun Throwable.errorText(): String = message ?: this::class.simpleName.orEmpty()
}

/**
 * P13: Android's filter sheet (`SourceFilterDialog`) as a side panel of the catalogue: Reset, Save (the current search
 * as a saved search), Filter (apply), the source's saved searches and its filters (Android's rows, `SourceFilterItem`).
 */
@Composable
private fun CatalogueFilterPanel(
    filters: FilterList?,
    error: String?,
    startExpanded: Boolean,
    savedSearches: ImmutableList<EXHSavedSearch>,
    onReset: () -> Unit,
    onFilter: () -> Unit,
    onUpdate: (FilterList) -> Unit,
    onSave: () -> Unit,
    onSavedSearch: (EXHSavedSearch) -> Unit,
    onDeleteSavedSearch: (EXHSavedSearch) -> Unit,
    onAddSavedSearchToFeed: (EXHSavedSearch) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.width(360.dp).fillMaxHeight().testTag("catalogue-filter-panel")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onReset, enabled = error == null || filters != null) {
                Text(stringResource(MR.strings.action_reset))
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSave, modifier = Modifier.testTag("catalogue-save-search")) {
                Icon(Icons.Outlined.Save, contentDescription = stringResource(MR.strings.action_save))
            }
            Button(onClick = onFilter, enabled = filters != null, modifier = Modifier.testTag("catalogue-apply-filters")) {
                Text(stringResource(MR.strings.action_filter))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_close))
            }
        }
        HorizontalDivider()
        // Same as Android's sheet: edits change the filter objects in place, then hand the list back
        val updateFilters = { filters?.let(onUpdate) ?: Unit }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "saved-searches") {
                SavedSearchesRow(
                    savedSearches = savedSearches,
                    onApply = onSavedSearch,
                    onDelete = onDeleteSavedSearch,
                    onAddToFeed = onAddSavedSearchToFeed,
                )
            }
            when {
                error != null -> item(key = "error") {
                    Text(
                        text = stringResource(KMR.strings.desktop_source_error, error),
                        modifier = Modifier.padding(16.dp),
                    )
                }
                filters == null -> item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> items(filters) { SourceFilterItem(it, updateFilters, startExpanded) }
            }
        }
    }
}

/**
 * P13: the source's saved searches (Android lists them at the top of the filter sheet): apply, add to the source's feed
 * (Android's source feed sheet: long press), delete (Android's browse sheet: long press).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedSearchesRow(
    savedSearches: ImmutableList<EXHSavedSearch>,
    onApply: (EXHSavedSearch) -> Unit,
    onDelete: (EXHSavedSearch) -> Unit,
    onAddToFeed: (EXHSavedSearch) -> Unit,
) {
    if (savedSearches.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(SYMR.strings.saved_searches), style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            savedSearches.forEach { search ->
                InputChip(
                    selected = false,
                    onClick = { onApply(search) },
                    label = { Text(search.name) },
                    leadingIcon = {
                        IconButton(onClick = { onAddToFeed(search) }, modifier = Modifier.testTag("saved-search-feed-${search.name}")) {
                            Icon(Icons.Outlined.DynamicFeed, contentDescription = stringResource(KMR.strings.desktop_saved_search_add_to_feed))
                        }
                    },
                    trailingIcon = {
                        IconButton(onClick = { onDelete(search) }, modifier = Modifier.testTag("saved-search-delete-${search.name}")) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(SYMR.strings.save_search_delete))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogueContent(
    state: CatalogueScreenModel.State,
    supportsLatest: Boolean,
    onMode: (CatalogueScreenModel.Mode) -> Unit,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
    selection: List<Manga>,
    onWebViewClick: (() -> Unit)?,
) {
    // The field keeps what the user typed (also across a visit to the details); it takes the query only when the
    // model sets one itself (a saved search applied, `State.queryEvent`)
    var query by rememberSaveable { mutableStateOf(state.query) }
    var seenQueryEvent by rememberSaveable { mutableIntStateOf(state.queryEvent) }
    LaunchedEffect(state.queryEvent) {
        if (state.queryEvent != seenQueryEvent) {
            seenQueryEvent = state.queryEvent
            query = state.query
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.mode == CatalogueScreenModel.Mode.Popular,
                onClick = { onMode(CatalogueScreenModel.Mode.Popular) },
                label = { Text(stringResource(MR.strings.popular)) },
            )
            if (supportsLatest) {
                FilterChip(
                    selected = state.mode == CatalogueScreenModel.Mode.Latest,
                    onClick = { onMode(CatalogueScreenModel.Mode.Latest) },
                    label = { Text(stringResource(MR.strings.latest)) },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                trailingIcon = {
                    IconButton(onClick = { onSearch(query) }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(MR.strings.action_search))
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        when {
            state.manga.isEmpty() && (state.isLoading || state.openingSavedSearch) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.manga.isEmpty() && state.error != null ->
                EmptyScreen(
                    message = stringResource(KMR.strings.desktop_source_error, state.error),
                    // Android's browse error actions (retry, WebView), e.g. to pass a Cloudflare check (P7)
                    actions = listOfNotNull(
                        EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, onLoadMore),
                        onWebViewClick?.let { EmptyScreenAction(MR.strings.action_open_in_web_view, Icons.Outlined.Public, it) },
                    ).toImmutableList(),
                )
            state.manga.isEmpty() && !state.hasNextPage -> EmptyScreen(stringRes = MR.strings.no_results_found)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.manga, key = { it.id }) { manga ->
                    CoverItem(
                        manga = manga,
                        onClick = { onClick(manga) },
                        selected = selection.any { it.id == manga.id },
                        onLongClick = { onLongClick(manga) },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            state.isLoading -> CircularProgressIndicator()
                            state.error != null -> Text(stringResource(KMR.strings.desktop_source_error, state.error))
                            state.hasNextPage -> Button(onClick = onLoadMore) { Text(stringResource(KMR.strings.desktop_load_more)) }
                        }
                    }
                }
            }
        }
    }
}

/** A cover with its title; [selected] (P14 bulk selection) marks it like Android's selected items. */
@Composable
internal fun CoverItem(manga: Manga, onClick: () -> Unit, selected: Boolean = false, onLongClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (selected) Modifier.testTag("cover-selected-${manga.id}") else Modifier),
    ) {
        Box {
            CoverImage(
                model = manga.asMangaCover(),
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
            )
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp),
        )
    }
}
