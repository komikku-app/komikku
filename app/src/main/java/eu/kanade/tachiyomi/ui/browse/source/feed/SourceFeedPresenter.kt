package eu.kanade.tachiyomi.ui.browse.source.feed

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.InsertManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.manga.model.toMangaUpdate
import eu.kanade.domain.source.interactor.CountFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.DeleteFeedSavedSearchById
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.GetSavedSearchBySourceIdFeed
import eu.kanade.domain.source.interactor.InsertFeedSavedSearch
import eu.kanade.presentation.browse.SourceFeedState
import eu.kanade.presentation.browse.SourceFeedStateImpl
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchNonCancellableIO
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.logcat
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import eu.kanade.domain.manga.model.Manga as DomainManga

/**
 * Presenter of [SourceFeedController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param source the source.
 */
open class SourceFeedPresenter(
    private val state: SourceFeedStateImpl = SourceFeedState() as SourceFeedStateImpl,
    val source: CatalogueSource,
    val preferences: PreferencesHelper = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val insertManga: InsertManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed = Injekt.get(),
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
) : BasePresenter<SourceFeedController>(), SourceFeedState by state {

    val isDownloadOnly: Boolean by preferences.downloadedOnly().asState()
    val isIncognitoMode: Boolean by preferences.incognitoMode().asState()

    /**
     * Fetches the different sources by user settings.
     */
    private var fetchSourcesSubscription: Subscription? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        setFilters(source.getFilterList())

        getFeedSavedSearchBySourceId.subscribe(source.id)
            .onEach {
                val items = getSourcesToGetFeed(it)
                state.items = items
                state.isLoading = false
                getFeed(items)
            }
            .launchIn(presenterScope)
    }

    override fun onDestroy() {
        fetchSourcesSubscription?.unsubscribe()
        super.onDestroy()
    }

    fun setFilters(filters: FilterList) {
        state.filters = filters
    }

    suspend fun hasTooManyFeeds(): Boolean {
        return countFeedSavedSearchBySourceId.await(source.id) > 10
    }

    fun createFeed(savedSearchId: Long) {
        presenterScope.launchNonCancellableIO {
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
        presenterScope.launchNonCancellableIO {
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
        fetchSourcesSubscription?.unsubscribe()
        fetchSourcesSubscription = Observable.from(feedSavedSearch)
            .flatMap(
                { sourceFeed ->
                    Observable.defer {
                        when (sourceFeed) {
                            is SourceFeedUI.Browse -> source.fetchPopularManga(1)
                            is SourceFeedUI.Latest -> source.fetchLatestUpdates(1)
                            is SourceFeedUI.SourceSavedSearch -> source.fetchSearchManga(
                                page = 1,
                                query = sourceFeed.savedSearch.query.orEmpty(),
                                filters = getFilterList(sourceFeed.savedSearch, source),
                            )
                        }
                    }
                        .subscribeOn(Schedulers.io())
                        .onErrorReturn { MangasPage(emptyList(), false) } // Ignore timeouts or other exceptions
                        .map { it.mangas } // Get manga from search result.
                        .map { list -> list.map { networkToLocalManga(it, source.id) } } // Convert to local manga.
                        .map { list -> sourceFeed.withResults(list.mapNotNull { it.toDomainManga() }) }
                },
                5,
            )
            .observeOn(AndroidSchedulers.mainThread())
            // Update matching source with the obtained results
            .doOnNext { result ->
                synchronized(state) {
                    state.items = state.items?.map { item -> if (item.id == result.id) result else item }
                }
            }
            // Deliver initial state
            .subscribe(
                {},
                { error ->
                    logcat(LogPriority.ERROR, error)
                },
            )
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
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
    @Synchronized
    private fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        var localManga = runBlocking { getManga.await(sManga.url, sourceId) }
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            newManga.id = -1
            val result = runBlocking {
                val id = insertManga.await(newManga.toDomainManga()!!)
                getManga.await(id!!)
            }
            localManga = result
        } else if (!localManga.favorite) {
            // if the manga isn't a favorite, set its display title from source
            // if it later becomes a favorite, updated title will go to db
            localManga = localManga.copy(ogTitle = sManga.title)
        }
        return localManga?.toDbManga()!!
    }

    /**
     * Initialize a manga.
     *
     * @param manga to initialize.
     */
    private suspend fun initializeManga(manga: DomainManga) {
        if (manga.thumbnailUrl != null || manga.initialized) return
        withContext(NonCancellable) {
            val db = manga.toDbManga()
            try {
                val networkManga = source.getMangaDetails(db.copy())
                db.copyFrom(networkManga)
                db.initialized = true
                updateManga.await(
                    db
                        .toDomainManga()
                        ?.toMangaUpdate()!!,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    suspend fun loadSearch(searchId: Long) =
        getExhSavedSearch.awaitOne(searchId, source::getFilterList)

    suspend fun loadSearches() =
        getExhSavedSearch.await(source.id, source::getFilterList)
}
