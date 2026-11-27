package app.komikku.desktop.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.components.SingleLineText
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.SourceFeedRow
import eu.kanade.tachiyomi.ui.browse.feed.fetchSourceFeedPage
import eu.kanade.tachiyomi.ui.browse.feed.sourceFeedRows
import exh.log.xLogE
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceIdFeed
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

/**
 * P13: a source's landing page, Android's `SourceFeedScreen` (opened from the sources list while SY's "new source
 * navigation" is on, Android's default): rows for the latest updates (when the source has them), the popular entries
 * ("Browse") and the source's own feed saved searches (`feed_saved_search` rows with `global = false`), a search field
 * leading into the catalogue, managing the saved-search rows (order, delete). The filter button opens the catalogue with
 * its filter panel (Android's filter sheet: filter, the saved searches, "add to feed").
 */
class SourceFeedScreen(private val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { SourceFeedScreenModel(sourceId) }
        val state by model.state.collectAsState()
        val source = model.source
        var ordering by rememberSaveable { mutableStateOf(false) }
        val savedSearchRows = state.rows.orEmpty().mapNotNull { it.row as? SourceFeedRow.SavedSearchRow }
        // The last saved-search row deleted (or gone): nothing left to order, so order mode ends
        val nothingToOrder = state.rows != null && savedSearchRows.isEmpty()
        LaunchedEffect(ordering, nothingToOrder) { if (ordering && nothingToOrder) ordering = false }
        // P14: bulk favourites of the rows' entries
        val bulkModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkState by bulkModel.state.collectAsState()
        val bulkClicks = BulkClicks(bulkModel, bulkState.selectionMode) { navigator.push(MangaDetailsScreen(it.id)) }
        val allResults = { state.rows.orEmpty().flatMap { it.results.orEmpty() } }
        BrowseScaffold(
            title = if (bulkState.selectionMode) "${bulkState.selection.size}" else source?.name.orEmpty(),
            onBack = {
                when {
                    bulkState.selectionMode -> bulkModel.toggleSelectionMode(false)
                    ordering -> ordering = false
                    else -> navigator.pop()
                }
            },
            actions = {
                if (source !is CatalogueSource) return@BrowseScaffold
                if (bulkState.selectionMode) {
                    BulkSelectionActions(
                        state = bulkState,
                        onSelectAll = { allResults().forEach(bulkModel::select) },
                        onReverseSelection = { bulkModel.reverseSelection(allResults()) },
                        onAddToLibrary = { bulkModel.addFavorite() },
                    )
                    return@BrowseScaffold
                }
                if (ordering) {
                    IconButton(onClick = { ordering = false }, modifier = Modifier.testTag("source-feed-sort-done")) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(KMR.strings.action_sort_feed),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    return@BrowseScaffold
                }
                BulkSelectionButton(isRunning = bulkState.isRunning, onClick = { bulkModel.toggleSelectionMode() })
                IconButton(
                    onClick = { navigator.push(CatalogueScreen(sourceId, openFilters = true)) },
                    modifier = Modifier.testTag("source-feed-filter"),
                ) {
                    Icon(Icons.Outlined.FilterList, contentDescription = stringResource(MR.strings.action_filter))
                }
                if (savedSearchRows.isNotEmpty()) {
                    IconButton(onClick = { ordering = true }, modifier = Modifier.testTag("source-feed-sort")) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(KMR.strings.action_sort_feed))
                    }
                }
                IconButton(onClick = model::refresh, modifier = Modifier.testTag("source-feed-refresh")) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(MR.strings.action_webview_refresh))
                }
                if (source is ConfigurableSource) {
                    IconButton(onClick = { navigator.push(SourcePreferencesScreen(sourceId)) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.label_settings))
                    }
                }
            },
        ) {
            if (source !is CatalogueSource) {
                EmptyScreen(stringRes = KMR.strings.desktop_source_not_installed)
                return@BrowseScaffold
            }
            Column(modifier = Modifier.fillMaxSize()) {
                if (ordering) {
                    FeedOrderList(
                        rows = savedSearchRows.map { it.feed to it.savedSearch.name }.toImmutableList(),
                        onMove = model::changeOrder,
                        onDelete = model::openDeleteDialog,
                    )
                } else {
                    SourceFeedSearchField(onSearch = { query ->
                        navigator.push(CatalogueScreen(sourceId, initialQuery = query.takeIf { it.isNotBlank() }))
                    })
                    val rows = state.rows
                    when {
                        rows == null && state.failed -> EmptyScreen(MR.strings.internal_error)
                        rows == null -> LoadingScreen()
                        else -> SourceFeedRows(rows, model, navigator, bulkClicks, bulkState.selection)
                    }
                }
            }
        }
        BulkFavoriteDialogs(bulkModel, bulkState.dialog)
        state.deleting?.let { feed ->
            SourceFeedDeleteDialog(
                onDismissRequest = model::dismissDialog,
                deleteFeed = {
                    model.deleteFeed(feed)
                    model.dismissDialog()
                },
            )
        }
    }
}

/** Android's source feed search bar: a search opens the source's catalogue with it. */
@Composable
private fun SourceFeedSearchField(onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { SingleLineText(stringResource(MR.strings.action_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        trailingIcon = {
            IconButton(onClick = { onSearch(query) }, modifier = Modifier.testTag("source-feed-search")) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(MR.strings.action_search))
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("source-feed-query"),
    )
}

@Composable
private fun SourceFeedRows(
    rows: ImmutableList<SourceFeedScreenModel.Row>,
    model: SourceFeedScreenModel,
    navigator: Navigator,
    bulkClicks: BulkClicks,
    selection: List<Manga>,
) {
    val sourceId = model.sourceId
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("source-feed-rows")) {
        items(rows, key = { "source-feed-${it.row.id}" }) { row ->
            val savedSearchRow = row.row as? SourceFeedRow.SavedSearchRow
            GlobalSearchResultItem(
                title = when (row.row) {
                    SourceFeedRow.Latest -> stringResource(MR.strings.latest)
                    SourceFeedRow.Popular -> stringResource(MR.strings.browse)
                    is SourceFeedRow.SavedSearchRow -> row.row.savedSearch.name
                },
                subtitle = null,
                onClick = {
                    navigator.push(
                        when (row.row) {
                            SourceFeedRow.Latest -> CatalogueScreen(sourceId, latest = true)
                            SourceFeedRow.Popular -> CatalogueScreen(sourceId)
                            is SourceFeedRow.SavedSearchRow -> CatalogueScreen(sourceId, savedSearchId = row.row.savedSearch.id)
                        },
                    )
                },
                // Android's long press on a saved-search row: its actions (delete)
                onLongClick = savedSearchRow?.let { { model.openDeleteDialog(it.feed) } },
                modifier = Modifier.testTag("source-feed-row-${row.row.id}"),
            ) {
                when {
                    row.results == null -> GlobalSearchLoadingResultItem()
                    row.error != null -> GlobalSearchErrorResultItem(message = row.error)
                    else -> GlobalSearchCardRow(
                        titles = row.results,
                        getManga = { manga ->
                            produceState(manga, manga.id) { model.mangaFlow(manga).collect { if (it != null) value = it } }
                        },
                        onClick = bulkClicks::onClick,
                        selection = selection,
                        onLongClick = bulkClicks::onLongClick,
                    )
                }
            }
        }
    }
}

/**
 * The model of [SourceFeedScreen], Android's `SourceFeedScreenModel` on the same rows and the shared logic (`FeedItems.kt`:
 * `sourceFeedRows`, `fetchSourceFeedPage`). Loads like Android's (up to five rows at a time); a failing row shows its
 * error (Android shows it as empty). Like the global feed (P13 review fixes): rows are built again when the source's feed
 * changes, unchanged rows keep what they loaded, a failure to build the rows keeps the feed followed, Refresh loads every
 * row again. A failure to read the feed rows themselves ends their subscription and shows the error state; Refresh then
 * follows them again (P13 rest / P14 review fixes).
 */
class SourceFeedScreenModel(
    val sourceId: Long,
    sourceManager: SourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    getIncognitoState: GetIncognitoState = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val reorderFeed: ReorderFeed = Injekt.get(),
    private val filterSerializer: FilterSerializer = Injekt.get(),
) : StateScreenModel<SourceFeedScreenModel.State>(State()) {

    /** One row with its first page (null while loading) or the error its load ended with. */
    data class Row(val row: SourceFeedRow, val results: List<Manga>? = null, val error: String? = null)

    data class State(
        /** The rows; null until the source's feed is read. */
        val rows: ImmutableList<Row>? = null,
        /** The rows could not be built (the last attempt failed). */
        val failed: Boolean = false,
        /** The saved-search row the user is asked to delete. */
        val deleting: FeedSavedSearch? = null,
    )

    val source = sourceManager.get(sourceId)
    private val catalogueSource = source as? CatalogueSource

    // Same as Android's: up to five rows load at a time, on Dispatchers.IO's threads
    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)

    private val hideInLibraryFeedItems = sourcePreferences.hideInLibraryFeedItems()

    /** The running load of each row, by row id. Only touched on the model's (main) dispatcher. */
    private val rowJobs = mutableMapOf<Long, Job>()

    private val buildMutex = Mutex()

    private var lastFeeds: List<FeedSavedSearch>? = null

    /** Follows the source's feed rows; ends only when reading them fails (then [refresh] follows them again). */
    private var subscription: Job? = null

    init {
        val source = catalogueSource
        if (source != null) {
            subscribe(source, reloadFirst = false)
            // Same as Android: a source opened outside incognito mode becomes the last used one
            getIncognitoState.subscribe(sourceId)
                .onEach { incognito -> if (!incognito) sourcePreferences.lastUsedSource().set(sourceId) }
                .launchIn(screenModelScope)
        }
    }

    /**
     * Follows the source's feed rows and builds the rows on each change; the first build reloads every row when
     * [reloadFirst]. A failure to read them (a database error) shows the error state and ends this subscription.
     */
    private fun subscribe(source: CatalogueSource, reloadFirst: Boolean) {
        subscription = screenModelScope.launch {
            var reload = reloadFirst
            getFeedSavedSearchBySourceId.subscribe(sourceId)
                .distinctUntilChanged()
                .catch { e ->
                    xLogE("Failed to read the feed of source $sourceId", e)
                    mutableState.update { it.copy(failed = true) }
                }
                .collect { feeds ->
                    buildRows(source, feeds, reload = reload)
                    reload = false
                }
        }
    }

    /**
     * Every row loads again. After a failure to read the feed rows (the subscription ended) they are followed again,
     * so the screen recovers instead of showing the error for good.
     */
    fun refresh() {
        val source = catalogueSource ?: return
        if (subscription?.isActive != true) {
            subscribe(source, reloadFirst = true)
            return
        }
        screenModelScope.launch {
            val feeds = lastFeeds ?: return@launch
            buildRows(source, feeds, reload = true)
        }
    }

    private suspend fun buildRows(source: CatalogueSource, feeds: List<FeedSavedSearch>, reload: Boolean) = buildMutex.withLock {
        try {
            lastFeeds = feeds
            val savedSearches = withIOContext { getSavedSearchBySourceIdFeed.await(sourceId) }
            // No suspension from here on: the rows, the kept loads and the state change together
            val previous = if (reload) emptyMap() else state.value.rows.orEmpty().associateBy { it.row.id }
            val kept = mutableSetOf<Long>()
            val rows = sourceFeedRows(source, feeds, savedSearches).map { row ->
                val old = previous[row.id]?.takeIf {
                    it.row.loadsSameAs(row) && (it.results != null || rowJobs[row.id]?.isActive == true)
                }
                if (old != null) kept += row.id
                Row(row = row, results = old?.results, error = old?.error)
            }
            (rowJobs.keys - kept).forEach { rowJobs.remove(it)?.cancel() }
            mutableState.update { it.copy(rows = rows.toImmutableList(), failed = false) }
            rows.filter { it.row.id !in kept }.forEach { row ->
                val job = screenModelScope.launch(start = CoroutineStart.LAZY) { loadRow(source, row.row) }
                rowJobs[row.row.id] = job
                job.start()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            xLogE("Failed to build the feed rows of source $sourceId", e)
            mutableState.update { it.copy(failed = true) }
        }
    }

    /** Whether this row loads the same page as [other] (a saved-search row: the same feed row and saved search). */
    private fun SourceFeedRow.loadsSameAs(other: SourceFeedRow): Boolean = when (this) {
        is SourceFeedRow.SavedSearchRow ->
            other is SourceFeedRow.SavedSearchRow && feed.id == other.feed.id && savedSearch == other.savedSearch
        else -> this == other
    }

    private suspend fun loadRow(source: CatalogueSource, row: SourceFeedRow) {
        val (results, error) = try {
            val page = withContext(coroutineDispatcher) { fetchSourceFeedPage(source, row, filterSerializer) }.mangas
            val manga = withIOContext {
                page.map { it.toDomainManga(source.id) }
                    .distinctBy { it.url }
                    .let { networkToLocalManga(it) }
                    .filter { !hideInLibraryFeedItems.get() || !it.favorite }
            }
            manga to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Extension code can throw errors too (e.g. a missing Android class): the row's error
            xLogE("Failed to load the feed row ${row.id} of source $sourceId", e)
            emptyList<Manga>() to (e.message ?: e::class.simpleName.orEmpty())
        }
        if (rowJobs[row.id] !== currentCoroutineContext()[Job]) return
        rowJobs.remove(row.id)
        mutableState.update { state ->
            state.copy(
                rows = state.rows
                    ?.map { if (it.row.id == row.id) it.copy(results = results, error = error) else it }
                    ?.toImmutableList(),
            )
        }
    }

    fun openDeleteDialog(feed: FeedSavedSearch) {
        mutableState.update { it.copy(deleting = feed) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(deleting = null) }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        screenModelScope.launchNonCancellable { deleteFeedSavedSearchById.await(feed.id) }
    }

    /** Android's source feed order screen: moves [feed] to [newIndex] among the saved-search rows. */
    fun changeOrder(feed: FeedSavedSearch, newIndex: Int) {
        val count = state.value.rows.orEmpty().count { it.row is SourceFeedRow.SavedSearchRow }
        if (newIndex !in 0..<count) return
        screenModelScope.launch { reorderFeed.changeOrder(feed, newIndex, global = false) }
    }

    /** The entry as stored, kept current (Android's `SourceFeedScreenModel.getManga`). */
    fun mangaFlow(manga: Manga) = getManga.subscribe(manga.url, manga.source)
}
