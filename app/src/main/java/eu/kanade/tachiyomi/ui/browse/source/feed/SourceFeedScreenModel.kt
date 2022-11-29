package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.core.prefs.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.NetworkToLocalManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toDomainManga
import eu.kanade.domain.manga.model.toMangaUpdate
import eu.kanade.domain.source.interactor.CountFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.DeleteFeedSavedSearchById
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.GetSavedSearchBySourceIdFeed
import eu.kanade.domain.source.interactor.InsertFeedSavedSearch
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.toItems
import eu.kanade.tachiyomi.util.lang.awaitSingle
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.lang.withNonCancellableContext
import eu.kanade.tachiyomi.util.system.logcat
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.util.concurrent.Executors
import eu.kanade.domain.manga.model.Manga as DomainManga

/**
 * Presenter of [SourceFeedController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param source the source.
 */
open class SourceFeedScreenModel(
    val sourceId: Long,
    private val sourceManager: SourceManager = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
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

    val isDownloadOnly: Boolean by preferences.downloadedOnly().asState(coroutineScope)
    val isIncognitoMode: Boolean by preferences.incognitoMode().asState(coroutineScope)

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    init {
        setFilters(source.getFilterList())

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

    suspend fun hasTooManyFeeds(): Boolean {
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
            feedSavedSearch.forEach { sourceFeed ->
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

                val titles = page.map {
                    withIOContext {
                        networkToLocalManga.await(it.toDomainManga(source.id))
                    }
                }

                mutableState.update { state ->
                    state.copy(
                        items = state.items.map { item -> if (item.id == sourceFeed.id) sourceFeed.withResults(titles) else item },
                    )
                }
            }
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

    suspend fun loadSearch(searchId: Long) =
        getExhSavedSearch.awaitOne(searchId, source::getFilterList)

    suspend fun loadSearches() =
        getExhSavedSearch.await(source.id, source::getFilterList)

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openDeleteFeed(feed: FeedSavedSearch) {
        mutableState.update { it.copy(dialog = Dialog.DeleteFeed(feed)) }
    }

    fun openAddFeed(feedId: Long, name: String) {
        mutableState.update { it.copy(dialog = Dialog.AddFeed(feedId, name)) }
    }

    fun openFailedToLoadSavedSearch() {
        mutableState.update { it.copy(dialog = Dialog.FailedToLoadSavedSearch) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed class Dialog {
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog()
        data class AddFeed(val feedId: Long, val name: String) : Dialog()
        object FailedToLoadSavedSearch : Dialog()
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
    val dialog: SourceFeedScreenModel.Dialog? = null,
) {
    val filterItems: List<IFlexible<*>> by lazy { filters.toItems() }

    val isLoading
        get() = items.isEmpty()
}
