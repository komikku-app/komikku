package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.preference.asState
import eu.kanade.domain.manga.model.toDomainManga
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.feed.MaxFeedItems
import exh.source.getMainSource
import exh.source.mangaDexSourceIds
import exh.util.nullIfBlank
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.interactor.CountFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceIdFeed
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.util.concurrent.Executors
import tachiyomi.domain.manga.model.Manga as DomainManga

open class SourceFeedScreenModel(
    val sourceId: Long,
    uiPreferences: UiPreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed = Injekt.get(),
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
    // KMK -->
    private val reorderFeed: ReorderFeed = Injekt.get(),
    // KMK <--
) : StateScreenModel<SourceFeedState>(SourceFeedState()) {

    var source = sourceManager.getOrStub(sourceId)

    val sourceIsMangaDex = sourceId in mangaDexSourceIds

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    val startExpanded by uiPreferences.expandFilters().asState(screenModelScope)

    init {
        // KMK -->
        screenModelScope.launch {
            var retry = 10
            while (source !is CatalogueSource && retry-- > 0) {
                // Sometime source is late to load, so we need to wait a bit
                delay(100)
                source = sourceManager.getOrStub(sourceId)
            }
            val source = source
            if (source !is CatalogueSource) return@launch
            // KMK <--

            setFilters(source.getFilterList())
            // KMK -->
            reloadSavedSearches()
            // KMK <--
            getFeedSavedSearchBySourceId.subscribe(source.id)
                .onEach {
                    val items = getSourcesToGetFeed(it)
                    mutableState.update { state ->
                        state.copy(
                            items = items,
                        )
                    }
                    getFeed(items)
                }
                .launchIn(screenModelScope)
        }
    }

    // KMK-->
    fun resetFilters() {
        val source = source
        if (source !is CatalogueSource) return

        setFilters(source.getFilterList())

        reloadSavedSearches()
    }
    // KMK <--

    fun setFilters(filters: FilterList) {
        mutableState.update { it.copy(filters = filters) }
    }

    private suspend fun hasTooManyFeeds(): Boolean {
        return countFeedSavedSearchBySourceId.await(source.id) > MaxFeedItems
    }

    fun createFeed(savedSearchId: Long) {
        screenModelScope.launchNonCancellable {
            insertFeedSavedSearch.await(
                FeedSavedSearch(
                    id = -1,
                    source = source.id,
                    savedSearch = savedSearchId,
                    global = false,
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
    fun changeOrder(feed: FeedSavedSearch, newOrder: Int) {
        screenModelScope.launch {
            reorderFeed.changeOrder(feed, newOrder, false)
        }
    }

    fun sortAlphabetically() {
        screenModelScope.launchNonCancellable {
            reorderFeed.sortAlphabetically(
                state.value.items
                    .filterIsInstance<SourceFeedUI.SourceSavedSearch>()
                    .sortedBy { feed -> feed.title }
                    .mapIndexed { index, feed ->
                        FeedSavedSearchUpdate(
                            id = feed.feed.id,
                            feedOrder = index.toLong(),
                        )
                    },
            )
        }
    }
    // KMK <--

    private suspend fun getSourcesToGetFeed(feedSavedSearch: List<FeedSavedSearch>): ImmutableList<SourceFeedUI> {
        // KMK -->
        val source = source
        // KMK <--
        if (source !is CatalogueSource) return persistentListOf()
        val savedSearches = getSavedSearchBySourceIdFeed.await(source.id)
            .associateBy { it.id }

        return (
            listOfNotNull(
                if (source.supportsLatest) {
                    SourceFeedUI.Latest(null)
                } else {
                    null
                },
                SourceFeedUI.Browse(null),
            ) + feedSavedSearch
                .map { SourceFeedUI.SourceSavedSearch(it, savedSearches[it.savedSearch]!!, null) }
            )
            .toImmutableList()
    }

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(feedSavedSearch: List<SourceFeedUI>) {
        // KMK -->
        val source = source
        // KMK <--
        if (source !is CatalogueSource) return
        screenModelScope.launch {
            feedSavedSearch.map { sourceFeed ->
                async {
                    val page = try {
                        withContext(coroutineDispatcher) {
                            when (sourceFeed) {
                                is SourceFeedUI.Browse -> source.getPopularManga(1)
                                is SourceFeedUI.Latest -> source.getLatestUpdates(1)
                                is SourceFeedUI.SourceSavedSearch -> source.getSearchManga(
                                    page = 1,
                                    query = sourceFeed.savedSearch.query.orEmpty(),
                                    filters = getFilterList(sourceFeed.savedSearch, source),
                                )
                            }
                        }.mangas
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val titles = withIOContext {
                        page.map {
                            // KMK -->
                            it.toDomainManga(source.id)
                            // KMK <--
                        }
                    }

                    mutableState.update { state ->
                        state.copy(
                            items = state.items.map { item ->
                                if (item.id == sourceFeed.id) sourceFeed.withResults(titles) else item
                            }.toImmutableList(),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private val filterSerializer = FilterSerializer()

    private fun getFilterList(savedSearch: SavedSearch, source: CatalogueSource): FilterList {
        val filters = savedSearch.filtersJson ?: return FilterList()
        return runCatching {
            val originalFilters = source.getFilterList()
            filterSerializer.deserialize(
                filters = originalFilters,
                json = Json.decodeFromString(filters),
            )
            originalFilters
        }.getOrElse { FilterList() }
    }

    @Composable
    fun getManga(initialManga: DomainManga): State<DomainManga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .collectLatest { manga ->
                    value = manga
                        // KMK -->
                        ?: initialManga
                    // KMK <--
                }
        }
    }

    // KMK -->
    private fun reloadSavedSearches() {
        screenModelScope.launchIO {
            val searches = loadSearches()
            mutableState.update { it.copy(savedSearches = searches) }
        }
    }
    // KMK <--

    private suspend fun loadSearches() =
        getExhSavedSearch.await(source.id, (source as CatalogueSource)::getFilterList)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name))
            .toImmutableList()

    fun onFilter(onBrowseClick: (query: String?, filters: String?) -> Unit) {
        // KMK -->
        val source = source
        // KMK <--
        if (source !is CatalogueSource) return
        screenModelScope.launchIO {
            val allDefault = state.value.filters == source.getFilterList()
            dismissDialog()
            if (allDefault) {
                onBrowseClick(
                    state.value.searchQuery?.nullIfBlank(),
                    null,
                )
            } else {
                onBrowseClick(
                    state.value.searchQuery?.nullIfBlank(),
                    Json.encodeToString(filterSerializer.serialize(state.value.filters)),
                )
            }
        }
    }

    /** Open a saved search */
    fun onSavedSearch(
        // KMK -->
        loadedSearch: EXHSavedSearch,
        // KMK <--
        onBrowseClick: (query: String?, searchId: Long) -> Unit,
        onToast: (StringResource) -> Unit,
    ) {
        // KMK -->
        val source = source
        // KMK <--
        if (source !is CatalogueSource) return
        screenModelScope.launchIO {
            // KMK -->
            val search = getExhSavedSearch.awaitOne(loadedSearch.id, source::getFilterList) ?: loadedSearch
            // KMK <--

            if (search.filterList == null && state.value.filters.isNotEmpty()) {
                withUIContext {
                    onToast(SYMR.strings.save_search_invalid)
                }
                return@launchIO
            }

            val allDefault = search.filterList != null && search.filterList == source.getFilterList()
            dismissDialog()

            if (!allDefault) {
                onBrowseClick(
                    state.value.searchQuery?.nullIfBlank(),
                    search.id,
                )
            }
        }
    }

    fun onSavedSearchAddToFeed(
        search: EXHSavedSearch,
        onToast: (StringResource) -> Unit,
    ) {
        screenModelScope.launchIO {
            if (hasTooManyFeeds()) {
                withUIContext {
                    onToast(KMR.strings.too_many_in_feed)
                }
                return@launchIO
            }
            openAddFeed(search.id, search.name)
        }
    }

    fun onMangaDexRandom(onRandomFound: (String) -> Unit) {
        screenModelScope.launchIO {
            val random = source.getMainSource<MangaDex>()?.fetchRandomMangaUrl()
                ?: return@launchIO
            onRandomFound(random)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openFilterSheet() {
        mutableState.update { it.copy(dialog = Dialog.Filter) }
    }

    fun openDeleteFeed(feed: FeedSavedSearch) {
        mutableState.update { it.copy(dialog = Dialog.DeleteFeed(feed)) }
    }

    // KMK -->
    fun openActionsDialog(
        feed: SourceFeedUI.SourceSavedSearch,
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

    fun showDialog(dialog: Dialog) {
        if (!state.value.isLoading) {
            mutableState.update {
                it.copy(dialog = dialog)
            }
        }
    }
    // KMK <--

    private fun openAddFeed(feedId: Long, name: String) {
        mutableState.update { it.copy(dialog = Dialog.AddFeed(feedId, name)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed class Dialog {
        data object Filter : Dialog()
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog()
        data class AddFeed(val feedId: Long, val name: String) : Dialog()

        // KMK -->
        data class FeedActions(
            val feedItem: SourceFeedUI.SourceSavedSearch,
        ) : Dialog()

        data object SortAlphabetically : Dialog()
        // KMK <--
    }

    override fun onDispose() {
        super.onDispose()
        coroutineDispatcher.close()
    }
}

@Immutable
data class SourceFeedState(
    val searchQuery: String? = null,
    val items: ImmutableList<SourceFeedUI> = persistentListOf(),
    val filters: FilterList = FilterList(),
    val savedSearches: ImmutableList<EXHSavedSearch> = persistentListOf(),
    val dialog: SourceFeedScreenModel.Dialog? = null,
) {
    val isLoading
        get() = items.isEmpty()
}
