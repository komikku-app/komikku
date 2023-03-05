package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.copyFrom
import eu.kanade.domain.manga.model.toDomainManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.interactor.CountFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.DeleteFeedSavedSearchById
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.GetSavedSearchBySourceIdFeed
import eu.kanade.domain.source.interactor.InsertFeedSavedSearch
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.source.getMainSource
import exh.source.mangaDexSourceIds
import exh.util.nullIfBlank
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.util.lang.awaitSingle
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
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
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed = Injekt.get(),
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
) : StateScreenModel<SourceFeedState>(SourceFeedState()) {

    val source = sourceManager.getOrStub(sourceId) as CatalogueSource

    val sourceIsMangaDex = sourceId in mangaDexSourceIds

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    val startExpanded by uiPreferences.expandFilters().asState(coroutineScope)

    init {
        setFilters(source.getFilterList())

        coroutineScope.launchIO {
            val searches = loadSearches()
            mutableState.update { it.copy(savedSearches = searches) }
        }

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
            .launchIn(coroutineScope)
    }

    fun setFilters(filters: FilterList) {
        mutableState.update { it.copy(filters = filters) }
    }

    private suspend fun hasTooManyFeeds(): Boolean {
        return countFeedSavedSearchBySourceId.await(source.id) > 10
    }

    fun createFeed(savedSearchId: Long) {
        coroutineScope.launchNonCancellable {
            insertFeedSavedSearch.await(
                FeedSavedSearch(
                    id = -1,
                    source = source.id,
                    savedSearch = savedSearchId,
                    global = false,
                ),
            )
        }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        coroutineScope.launchNonCancellable {
            deleteFeedSavedSearchById.await(feed.id)
        }
    }

    private suspend fun getSourcesToGetFeed(feedSavedSearch: List<FeedSavedSearch>): List<SourceFeedUI> {
        val savedSearches = getSavedSearchBySourceIdFeed.await(source.id)
            .associateBy { it.id }

        return listOfNotNull(
            if (source.supportsLatest) {
                SourceFeedUI.Latest(null)
            } else {
                null
            },
            SourceFeedUI.Browse(null),
        ) + feedSavedSearch
            .map { SourceFeedUI.SourceSavedSearch(it, savedSearches[it.savedSearch]!!, null) }
    }

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(feedSavedSearch: List<SourceFeedUI>) {
        coroutineScope.launch {
            feedSavedSearch.map { sourceFeed ->
                async {
                    val page = try {
                        withContext(coroutineDispatcher) {
                            when (sourceFeed) {
                                is SourceFeedUI.Browse -> source.fetchPopularManga(1)
                                is SourceFeedUI.Latest -> source.fetchLatestUpdates(1)
                                is SourceFeedUI.SourceSavedSearch -> source.fetchSearchManga(
                                    page = 1,
                                    query = sourceFeed.savedSearch.query.orEmpty(),
                                    filters = getFilterList(sourceFeed.savedSearch, source),
                                )
                            }.awaitSingle()
                        }.mangas
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val titles = withIOContext {
                        page.map {
                            networkToLocalManga.await(it.toDomainManga(source.id))
                        }
                    }

                    mutableState.update { state ->
                        state.copy(
                            items = state.items.map { item -> if (item.id == sourceFeed.id) sourceFeed.withResults(titles) else item },
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
                    if (manga == null) return@collectLatest
                    withIOContext {
                        initializeManga(manga)
                    }
                    value = manga
                }
        }
    }

    /**
     * Initialize a manga.
     *
     * @param manga to initialize.
     */
    private suspend fun initializeManga(manga: DomainManga) {
        if (manga.thumbnailUrl != null || manga.initialized) return
        withNonCancellableContext {
            try {
                val networkManga = source.getMangaDetails(manga.toSManga())
                val updatedManga = manga.copyFrom(networkManga)
                    .copy(initialized = true)

                updateManga.await(updatedManga.toMangaUpdate())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private suspend fun loadSearches() =
        getExhSavedSearch.await(source.id, source::getFilterList)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name))

    fun onFilter(onBrowseClick: (query: String?, filters: String?) -> Unit) {
        coroutineScope.launchIO {
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

    fun onSavedSearch(
        search: EXHSavedSearch,
        onBrowseClick: (query: String?, searchId: Long) -> Unit,
        onToast: (Int) -> Unit,
    ) {
        coroutineScope.launchIO {
            if (search.filterList == null && state.value.filters.isNotEmpty()) {
                withUIContext {
                    onToast(R.string.save_search_invalid)
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
        onToast: (Int) -> Unit,
    ) {
        coroutineScope.launchIO {
            if (hasTooManyFeeds()) {
                withUIContext {
                    onToast(R.string.too_many_in_feed)
                }
                return@launchIO
            }
            openAddFeed(search.id, search.name)
        }
    }

    fun onMangaDexRandom(onRandomFound: (String) -> Unit) {
        coroutineScope.launchIO {
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

    fun openAddFeed(feedId: Long, name: String) {
        mutableState.update { it.copy(dialog = Dialog.AddFeed(feedId, name)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed class Dialog {
        object Filter : Dialog()
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog()
        data class AddFeed(val feedId: Long, val name: String) : Dialog()
    }

    override fun onDispose() {
        super.onDispose()
        coroutineDispatcher.close()
    }
}

@Immutable
data class SourceFeedState(
    val searchQuery: String? = null,
    val items: List<SourceFeedUI> = emptyList(),
    val filters: FilterList = FilterList(),
    val savedSearches: List<EXHSavedSearch> = emptyList(),
    val dialog: SourceFeedScreenModel.Dialog? = null,
) {
    val isLoading
        get() = items.isEmpty()
}
