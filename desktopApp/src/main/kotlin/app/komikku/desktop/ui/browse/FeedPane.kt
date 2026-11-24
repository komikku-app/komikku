package app.komikku.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.FeedAddSearchDialog
import eu.kanade.presentation.browse.RadioSelectorSearchable
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.feed.feedItemTitles
import eu.kanade.tachiyomi.ui.browse.feed.feedSourceOptions
import eu.kanade.tachiyomi.ui.browse.feed.fetchFeedPage
import eu.kanade.tachiyomi.ui.browse.feed.isFeedFull
import eu.kanade.tachiyomi.ui.browse.feed.pairFeedWithSavedSearches
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogE
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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
import tachiyomi.domain.source.interactor.CountFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetSavedSearchGlobalFeed
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

/**
 * P13: the global feed of desktop Browse, Android's `FeedScreenModel` on the same `feed_saved_search` rows and the
 * shared feed logic (`FeedItems.kt`: titles, the sources to add a row for, a row's first page, the row limit). Rows load
 * one at a time like Android's (one-thread dispatcher); a failing row shows its error (Android shows it as empty).
 *
 * The rows are built again (source, titles) whenever the feed rows or the source manager's sources change, and on
 * Refresh; a row whose feed row, saved search and source are unchanged keeps what it loaded (or its running load), so
 * only new or changed rows load. Refresh loads every row again.
 */
class FeedPaneModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal = Injekt.get(),
    private val getSavedSearchGlobalFeed: GetSavedSearchGlobalFeed = Injekt.get(),
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val reorderFeed: ReorderFeed = Injekt.get(),
    private val filterSerializer: FilterSerializer = Injekt.get(),
) : StateScreenModel<FeedPaneModel.State>(State()) {

    /** One feed row: Android's `FeedItemUI` with the error a failed load shows. */
    data class Row(
        val feed: FeedSavedSearch,
        val savedSearch: SavedSearch?,
        val source: Source?,
        val title: String,
        val subtitle: String,
        /** The first page's entries; null while loading. */
        val results: List<Manga>? = null,
        /** Why the row could not be loaded (then [results] is empty). */
        val error: String? = null,
    ) {
        /** Whether this row loads the same page as a row of [feed], [savedSearch] and [source] would. */
        fun loadsSameAs(feed: FeedSavedSearch, savedSearch: SavedSearch?, source: Source?): Boolean =
            this.feed.id == feed.id &&
                this.feed.source == feed.source &&
                this.feed.savedSearch == feed.savedSearch &&
                this.savedSearch == savedSearch &&
                this.source === source
    }

    data class State(
        /** The rows in feed order; null until the feed is read. */
        val rows: ImmutableList<Row>? = null,
        /** The feed's rows could not be built (the last attempt failed); [rows] keeps what was shown before. */
        val failed: Boolean = false,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class AddFeed(val options: ImmutableList<Source>) : Dialog
        data class AddFeedSearch(val source: Source, val options: ImmutableList<SavedSearch?>) : Dialog
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
        data object TooManyFeeds : Event
    }

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    // Same as Android: the rows' sources are asked one at a time, on Dispatchers.IO's threads
    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val hideInLibraryFeedItems = sourcePreferences.hideInLibraryFeedItems()

    /** The running load of each row, by feed row id. Only touched on the model's (main) dispatcher. */
    private val rowJobs = mutableMapOf<Long, Job>()

    /** One build of the rows at a time (the feed / source changes and Refresh). */
    private val buildMutex = Mutex()

    /** The feed rows last read, built again on Refresh. */
    private var lastFeeds: List<FeedSavedSearch>? = null

    init {
        screenModelScope.launch {
            sourceManager.isInitialized.first { it }
            combine(
                getFeedSavedSearchGlobal.subscribe().distinctUntilChanged(),
                // An extension installed, updated or removed: its rows get their (new) source
                sourceManager.sources,
            ) { feeds, _ -> feeds }
                .catch { e ->
                    xLogE("Failed to read the feed", e)
                    onBuildFailed()
                }
                .collect { feeds -> buildRows(feeds, reload = false) }
        }
    }

    /** Android's refresh (`FeedScreenModel.init`): the rows are built again (sources looked up afresh) and all load. */
    fun refresh() {
        screenModelScope.launch {
            val feeds = lastFeeds ?: return@launch
            buildRows(feeds, reload = true)
        }
    }

    /**
     * Builds the rows of [feeds] and starts the loads of the new or changed ones (of all with [reload]). A failure (e.g.
     * extension code throwing while a title is made) shows the error state and keeps the feed followed.
     */
    private suspend fun buildRows(feeds: List<FeedSavedSearch>, reload: Boolean) = buildMutex.withLock {
        try {
            lastFeeds = feeds
            val savedSearches = withIOContext { getSavedSearchGlobalFeed.await() }
            // No suspension from here on: the rows, the kept loads and the state change together
            val previous = if (reload) emptyMap() else state.value.rows.orEmpty().associateBy { it.feed.id }
            val kept = mutableSetOf<Long>()
            val rows = pairFeedWithSavedSearches(feeds, savedSearches).map { (feed, savedSearch) ->
                val source = sourceManager.get(feed.source)
                val (title, subtitle) = feedItemTitles(feed, savedSearch, source)
                val old = previous[feed.id]?.takeIf {
                    it.loadsSameAs(feed, savedSearch, source) && (it.results != null || rowJobs[feed.id]?.isActive == true)
                }
                if (old != null) kept += feed.id
                Row(
                    feed = feed,
                    savedSearch = savedSearch,
                    source = source,
                    title = title,
                    subtitle = subtitle,
                    results = old?.results,
                    error = old?.error,
                )
            }
            (rowJobs.keys - kept).forEach { rowJobs.remove(it)?.cancel() }
            mutableState.update { it.copy(rows = rows.toImmutableList(), failed = false) }
            rows.filter { it.feed.id !in kept }.forEach { row ->
                // Registered before it runs: a load that finishes without suspending must find itself
                val job = screenModelScope.launch(start = CoroutineStart.LAZY) { loadRow(row) }
                rowJobs[row.feed.id] = job
                job.start()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            xLogE("Failed to build the feed rows", e)
            onBuildFailed()
        }
    }

    private suspend fun onBuildFailed() {
        mutableState.update { it.copy(failed = true) }
        _events.send(Event.FailedFetchingSources)
    }

    private suspend fun loadRow(row: Row) {
        val (results, error) = try {
            val source = row.source
            if (source == null) {
                emptyList<Manga>() to null
            } else {
                val page = withContext(coroutineDispatcher) {
                    fetchFeedPage(source, row.savedSearch, filterSerializer)
                }.mangas
                val manga = withIOContext {
                    page.map { it.toDomainManga(source.id) }
                        .distinctBy { it.url }
                        .let { networkToLocalManga(it) }
                        .filter { !hideInLibraryFeedItems.get() || !it.favorite }
                }
                manga to null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Extension code can throw errors too (e.g. a missing Android class): the row's error
            xLogE("Failed to load the feed row of source ${row.feed.source}", e)
            emptyList<Manga>() to (e.message ?: e::class.simpleName.orEmpty())
        }
        // Only while this is still the row's load (a newer build may have replaced it)
        if (rowJobs[row.feed.id] !== currentCoroutineContext()[Job]) return
        rowJobs.remove(row.feed.id)
        mutableState.update { state ->
            state.copy(
                rows = state.rows
                    ?.map { if (it.feed.id == row.feed.id) it.copy(results = results, error = error) else it }
                    ?.toImmutableList(),
            )
        }
    }

    /** Android's "Add": refused once the feed holds its limit (`MaxFeedItems`). */
    fun openAddDialog() {
        screenModelScope.launch {
            if (isFeedFull(withIOContext { countFeedSavedSearchGlobal.await() })) {
                _events.send(Event.TooManyFeeds)
                return@launch
            }
            mutableState.update { it.copy(dialog = Dialog.AddFeed(feedSourceOptions(sourceManager, sourcePreferences))) }
        }
    }

    /** The second step: latest / popular (`null`) or one of the source's saved searches. */
    fun openAddSearchDialog(source: Source) {
        screenModelScope.launch {
            val savedSearches = withIOContext { getSavedSearchBySourceId.await(source.id) }
            mutableState.update {
                it.copy(dialog = Dialog.AddFeedSearch(source, (persistentListOf(null) + savedSearches).toImmutableList()))
            }
        }
    }

    fun openDeleteDialog(feed: FeedSavedSearch) {
        mutableState.update { it.copy(dialog = Dialog.DeleteFeed(feed)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun createFeed(source: Source, savedSearch: SavedSearch?) {
        screenModelScope.launchNonCancellable {
            insertFeedSavedSearch.await(
                FeedSavedSearch(
                    id = -1,
                    source = source.id,
                    savedSearch = savedSearch?.id,
                    global = true,
                    feedOrder = 0,
                ),
            )
        }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        screenModelScope.launchNonCancellable {
            deleteFeedSavedSearchById.await(feed.id)
        }
    }

    /** Android's feed order screen: moves [feed] to [newIndex] (`ReorderFeed`). */
    fun changeOrder(feed: FeedSavedSearch, newIndex: Int) {
        val count = state.value.rows?.size ?: return
        if (newIndex !in 0..<count) return
        screenModelScope.launch {
            reorderFeed.changeOrder(feed, newIndex)
        }
    }

    /** The entry as stored, kept current (Android's `FeedScreenModel.getManga`). */
    fun mangaFlow(manga: Manga) = getManga.subscribe(manga.url, manga.source)
}

/** P13: the Feed tab of desktop Browse (Android's `feedTab`): rows, add / delete / reorder, refresh. */
@Composable
fun FeedPane(model: FeedPaneModel) {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val state by model.state.collectAsState()
    var ordering by rememberSaveable { mutableStateOf(false) }
    val tooManyFeeds = stringResource(KMR.strings.too_many_in_feed)
    val internalError = stringResource(MR.strings.internal_error)
    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                FeedPaneModel.Event.TooManyFeeds -> context.toast(tooManyFeeds)
                FeedPaneModel.Event.FailedFetchingSources -> context.toast(internalError)
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            if (ordering) {
                IconButton(onClick = { ordering = false }, modifier = Modifier.testTag("feed-sort-done")) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(KMR.strings.action_sort_feed),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                IconButton(onClick = model::openAddDialog, modifier = Modifier.testTag("feed-add")) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(MR.strings.action_add))
                }
                IconButton(onClick = { ordering = true }, modifier = Modifier.testTag("feed-sort")) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(KMR.strings.action_sort_feed))
                }
                IconButton(onClick = model::refresh, modifier = Modifier.testTag("feed-refresh")) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(MR.strings.action_webview_refresh))
                }
            }
        }
        val rows = state.rows
        when {
            rows == null && state.failed -> EmptyScreen(MR.strings.internal_error)
            rows == null -> LoadingScreen()
            rows.isEmpty() -> EmptyScreen(SYMR.strings.feed_tab_empty)
            ordering -> FeedOrderList(
                rows = rows.map { it.feed to it.title }.toImmutableList(),
                onMove = model::changeOrder,
                onDelete = model::openDeleteDialog,
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize().testTag("feed-rows")) {
                itemsIndexed(rows, key = { _, row -> "feed-${row.feed.id}" }) { _, row ->
                    GlobalSearchResultItem(
                        title = row.title,
                        subtitle = row.subtitle,
                        onClick = {
                            val source = row.source ?: return@GlobalSearchResultItem
                            navigator.push(
                                if (row.savedSearch != null) {
                                    CatalogueScreen(source.id, savedSearchId = row.savedSearch.id)
                                } else {
                                    CatalogueScreen(source.id, latest = true)
                                },
                            )
                        },
                        onLongClick = { model.openDeleteDialog(row.feed) },
                        modifier = Modifier.testTag("feed-row-${row.feed.id}"),
                    ) {
                        when {
                            row.results == null -> GlobalSearchLoadingResultItem()
                            row.error != null -> GlobalSearchErrorResultItem(message = row.error)
                            else -> GlobalSearchCardRow(
                                titles = row.results,
                                getManga = { manga ->
                                    produceState(manga, manga.id) { model.mangaFlow(manga).collect { if (it != null) value = it } }
                                },
                                onClick = { navigator.push(MangaDetailsScreen(it.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
    when (val dialog = state.dialog) {
        is FeedPaneModel.Dialog.AddFeed -> FeedAddSourceDialog(
            sources = dialog.options,
            onDismiss = model::dismissDialog,
            onClickAdd = { source ->
                model.dismissDialog()
                model.openAddSearchDialog(source)
            },
        )
        is FeedPaneModel.Dialog.AddFeedSearch -> FeedAddSearchDialog(
            source = dialog.source,
            savedSearches = dialog.options,
            onDismiss = model::dismissDialog,
            onClickAdd = { source, savedSearch ->
                model.createFeed(source, savedSearch)
                model.dismissDialog()
            },
        )
        is FeedPaneModel.Dialog.DeleteFeed -> SourceFeedDeleteDialog(
            onDismissRequest = model::dismissDialog,
            deleteFeed = {
                model.deleteFeed(dialog.feed)
                model.dismissDialog()
            },
        )
        null -> Unit
    }
}

/**
 * Android's feed order screen (global feed and a source's): the rows' titles with move up / down (instead of dragging)
 * and delete. [rows]: each feed row with its title.
 */
@Composable
internal fun FeedOrderList(
    rows: ImmutableList<Pair<FeedSavedSearch, String>>,
    onMove: (FeedSavedSearch, Int) -> Unit,
    onDelete: (FeedSavedSearch) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("feed-order"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        itemsIndexed(rows, key = { _, row -> "feed-order-${row.first.id}" }) { index, (feed, title) ->
            ElevatedCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = title, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    IconButton(
                        onClick = { onMove(feed, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.testTag("feed-up-${feed.id}"),
                    ) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(KMR.strings.action_move_up))
                    }
                    IconButton(
                        onClick = { onMove(feed, index + 1) },
                        enabled = index < rows.lastIndex,
                        modifier = Modifier.testTag("feed-down-${feed.id}"),
                    ) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(KMR.strings.action_move_down))
                    }
                    IconButton(onClick = { onDelete(feed) }, modifier = Modifier.testTag("feed-delete-${feed.id}")) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.action_delete))
                    }
                }
            }
        }
    }
}

/**
 * Android's `FeedAddDialog` (pick the source of a new row, searchable by name or id) with the desktop's plain rows:
 * Android's draws the extension icon (`SourceIcon`, Android-only).
 */
@Composable
private fun FeedAddSourceDialog(
    sources: ImmutableList<Source>,
    onDismiss: () -> Unit,
    onClickAdd: (Source) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sourceList = remember(sources, query) {
        sources.filter { source ->
            if (query.isBlank()) return@filter true
            query.split(",").any {
                val input = it.trim()
                if (input.isEmpty()) return@any false
                source.name.contains(input, ignoreCase = true) || source.id == input.toLongOrNull()
            }
        }.toImmutableList()
    }
    var selected by remember(sourceList) { mutableStateOf<Int?>(null) }
    val selectedSource = selected?.let { sourceList.getOrNull(it) }
    AlertDialog(
        title = { Text(text = stringResource(SYMR.strings.feed)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("feed-add-search"),
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    RadioSelectorSearchable(
                        options = sourceList,
                        optionStrings = sourceList.map { "${it.name} (${languageName(it.lang)})" }.toImmutableList(),
                        selected = selected,
                    ) { selected = it }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            // Nothing to add until a source is picked
            TextButton(onClick = { selectedSource?.let(onClickAdd) }, enabled = selectedSource != null) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
