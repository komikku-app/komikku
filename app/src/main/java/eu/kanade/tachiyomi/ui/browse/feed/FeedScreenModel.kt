@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.util.fastAny
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.FeedItemUI
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import tachiyomi.domain.manga.model.Manga as DomainManga

/**
 * Presenter of [feedTab]
 */
open class FeedScreenModel(
    val sourceManager: SourceManager = Injekt.get(),
    val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal = Injekt.get(),
    private val getSavedSearchGlobalFeed: GetSavedSearchGlobalFeed = Injekt.get(),
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    // KMK -->
    private val reorderFeed: ReorderFeed = Injekt.get(),
    // KMK <--
) : StateScreenModel<FeedScreenState>(FeedScreenState()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    // KMK -->
    // Shares Dispatchers.IO's threads instead of owning a pool, so nothing has to be closed on dispose
    // (mihonapp/mihon#4036)
    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    // KMK <--
    var pushed: Boolean = false

    init {
        getFeedSavedSearchGlobal.subscribe()
            .distinctUntilChanged()
            .onEach {
                sourceManager.isInitialized.first { it }
                val items = getSourcesToGetFeed(it).map { (feed, savedSearch) ->
                    createCatalogueSearchItem(
                        feed = feed,
                        savedSearch = savedSearch,
                        source = sourceManager.get(feed.source),
                        results = null,
                    )
                }
                mutableState.update { state ->
                    state.copy(
                        items = items
                            // KMK -->
                            .toImmutableList(),
                        // KMK <--
                    )
                }
                getFeed(items)
            }
            .catch { _events.send(Event.FailedFetchingSources) }
            .launchIn(screenModelScope)
    }

    fun init() {
        pushed = false
        screenModelScope.launchIO {
            val newItems = state.value.items?.map { it.copy(results = null) } ?: return@launchIO
            mutableState.update { state ->
                state.copy(
                    items = newItems
                        // KMK -->
                        .toImmutableList(),
                    // KMK <--
                )
            }
            getFeed(newItems)
        }
    }

    fun openAddDialog() {
        screenModelScope.launchIO {
            if (hasTooManyFeeds()) {
                _events.send(Event.TooManyFeeds)
                return@launchIO
            }
            mutableState.update { state ->
                state.copy(
                    dialog = Dialog.AddFeed(getEnabledSources()),
                )
            }
        }
    }

    fun openAddSearchDialog(source: Source) {
        screenModelScope.launchIO {
            mutableState.update { state ->
                state.copy(
                    dialog = Dialog.AddFeedSearch(
                        source,
                        (
                            // KMK -->
                            // (if (source.supportsLatest) persistentListOf(null) else persistentListOf()) +
                            persistentListOf(null) +
                                // KMK <-->
                                getSourceSavedSearches(source.id)
                            ).toImmutableList(),
                    ),
                )
            }
        }
    }

    fun openDeleteDialog(feed: FeedSavedSearch) {
        screenModelScope.launchIO {
            mutableState.update { state ->
                state.copy(
                    dialog = Dialog.DeleteFeed(feed),
                )
            }
        }
    }

    // KMK -->
    fun openActionsDialog(
        feed: FeedItemUI,
    ) {
        screenModelScope.launchIO {
            mutableState.update { state ->
                state.copy(
                    dialog = Dialog.FeedActions(
                        feedItem = feed,
                    ),
                )
            }
        }
    }
    // KMK <--

    // KMK --> bodies moved to FeedItems.kt (shared with the desktop feed)
    private suspend fun hasTooManyFeeds(): Boolean {
        return isFeedFull(countFeedSavedSearchGlobal.await())
    }

    private fun getEnabledSources(): ImmutableList<Source> {
        return feedSourceOptions(sourceManager, sourcePreferences)
    }
    // KMK <--

    private suspend fun getSourceSavedSearches(sourceId: Long): ImmutableList<SavedSearch> {
        return getSavedSearchBySourceId.await(sourceId).toImmutableList()
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

    // KMK -->
    fun changeOrder(feed: FeedSavedSearch, newIndex: Int) {
        screenModelScope.launch {
            reorderFeed.changeOrder(feed, newIndex)
        }
    }
    // KMK <--

    private suspend fun getSourcesToGetFeed(feedSavedSearch: List<FeedSavedSearch>): List<Pair<FeedSavedSearch, SavedSearch?>> {
        // KMK -->
        return pairFeedWithSavedSearches(feedSavedSearch, getSavedSearchGlobalFeed.await())
        // KMK <--
    }

    /**
     * Creates a catalogue search item
     */
    private fun createCatalogueSearchItem(
        feed: FeedSavedSearch,
        savedSearch: SavedSearch?,
        source: Source?,
        results: List<DomainManga>?,
    ): FeedItemUI {
        // KMK -->
        val (title, subtitle) = feedItemTitles(feed, savedSearch, source)
        // KMK <--
        return FeedItemUI(
            feed,
            savedSearch,
            source,
            // KMK -->
            title,
            subtitle,
            // KMK <--
            results,
        )
    }

    // KMK -->
    private val hideInLibraryFeedItems = sourcePreferences.hideInLibraryFeedItems()
    // KMK <--

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(feedSavedSearch: List<FeedItemUI>) {
        screenModelScope.launch {
            feedSavedSearch.map { itemUI ->
                async {
                    val page = try {
                        if (itemUI.source != null) {
                            withContext(coroutineDispatcher) {
                                // KMK -->
                                fetchFeedPage(itemUI.source, itemUI.savedSearch, filterSerializer)
                                // KMK <--
                            }.mangas
                        } else {
                            emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val result = withIOContext {
                        itemUI.copy(
                            results = page
                                .map { it.toDomainManga(itemUI.source!!.id) }
                                .distinctBy { it.url }
                                .let { networkToLocalManga(it) }
                                // KMK -->
                                .filter { !hideInLibraryFeedItems.get() || !it.favorite },
                            // KMK <--
                        )
                    }

                    mutableState.update { state ->
                        state.copy(
                            items = state.items?.map { if (it.feed.id == result.feed.id) result else it }
                                // KMK -->
                                ?.toImmutableList(),
                            // KMK <--
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private val filterSerializer = FilterSerializer()
    // KMK --> getFilterList moved to FeedItems.kt (feedFilterList)
    // KMK <--

    @Composable
    fun getManga(initialManga: DomainManga): State<DomainManga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .collectLatest { manga ->
                    if (manga == null) return@collectLatest
                    value = manga
                }
        }
    }

    // KMK -->
    fun showDialog(dialog: Dialog) {
        if (!state.value.isLoading) {
            mutableState.update {
                it.copy(dialog = dialog)
            }
        }
    }
    // KMK <--

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed class Dialog {
        data class AddFeed(val options: ImmutableList<Source>) : Dialog()
        data class AddFeedSearch(val source: Source, val options: ImmutableList<SavedSearch?>) : Dialog()
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog()

        // KMK -->
        data class FeedActions(
            val feedItem: FeedItemUI,
        ) : Dialog()
        // KMK <--
    }

    sealed class Event {
        data object FailedFetchingSources : Event()
        data object TooManyFeeds : Event()
    }
}

data class FeedScreenState(
    val dialog: FeedScreenModel.Dialog? = null,
    val items: ImmutableList<FeedItemUI>? = null,
) {
    val isLoading
        get() = items == null

    val isEmpty
        get() = items.isNullOrEmpty()

    val isLoadingItems
        get() = items?.fastAny { it.results == null } != false
}

// KMK --> MaxFeedItems moved to FeedItems.kt
// KMK <--
