package eu.kanade.tachiyomi.ui.browse.source.feed

import android.os.Bundle
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.InsertManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.manga.model.toMangaUpdate
import eu.kanade.domain.source.interactor.CountFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.DeleteFeedSavedSearchById
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.GetSavedSearchBySourceId
import eu.kanade.domain.source.interactor.GetSavedSearchBySourceIdFeed
import eu.kanade.domain.source.interactor.InsertFeedSavedSearch
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter.Companion.toItems
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.runAsObservable
import eu.kanade.tachiyomi.util.system.logcat
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

sealed class SourceFeed {
    object Latest : SourceFeed()
    object Browse : SourceFeed()
    data class SourceSavedSearch(val feed: FeedSavedSearch, val savedSearch: SavedSearch) : SourceFeed()
}

/**
 * Presenter of [SourceFeedController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param source the source.
 * @param handler manages the database calls.
 * @param preferences manages the preference calls.
 */
open class SourceFeedPresenter(
    val source: CatalogueSource,
    val preferences: PreferencesHelper = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val insertManga: InsertManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed = Injekt.get(),
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
) : BasePresenter<SourceFeedController>() {

    /**
     * Fetches the different sources by user settings.
     */
    private var fetchSourcesSubscription: Subscription? = null

    /**
     * Subject which fetches image of given manga.
     */
    private val fetchImageSubject = PublishSubject.create<Triple<List<Manga>, Source, SourceFeed>>()

    /**
     * Subscription for fetching images of manga.
     */
    private var fetchImageSubscription: Subscription? = null

    /**
     * Modifiable list of filters.
     */
    var sourceFilters = FilterList()
        set(value) {
            field = value
            filterItems = value.toItems()
        }

    var filterItems: List<IFlexible<*>> = emptyList()

    init {
        query = ""
    }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        sourceFilters = source.getFilterList()

        getFeedSavedSearchBySourceId.subscribe(source.id)
            .onEach {
                getFeed(it)
            }
            .launchIn(presenterScope)
    }

    override fun onDestroy() {
        fetchSourcesSubscription?.unsubscribe()
        fetchImageSubscription?.unsubscribe()
        super.onDestroy()
    }

    suspend fun hasTooManyFeeds(): Boolean {
        return countFeedSavedSearchBySourceId.await(source.id) > 10
    }

    suspend fun getSourceSavedSearches(): List<SavedSearch> {
        return getSavedSearchBySourceId.await(source.id)
    }

    fun createFeed(savedSearchId: Long) {
        launchIO {
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
        launchIO {
            deleteFeedSavedSearchById.await(feed.id)
        }
    }

    private suspend fun getSourcesToGetFeed(feedSavedSearch: List<FeedSavedSearch>): List<SourceFeed> {
        val savedSearches = getSavedSearchBySourceIdFeed.await(source.id)
            .associateBy { it.id }

        return listOfNotNull(
            if (source.supportsLatest) {
                SourceFeed.Latest
            } else null,
            SourceFeed.Browse,
        ) + feedSavedSearch
            .map { SourceFeed.SourceSavedSearch(it, savedSearches[it.savedSearch]!!) }
    }

    /**
     * Creates a catalogue search item
     */
    protected open fun createCatalogueSearchItem(
        sourceFeed: SourceFeed,
        results: List<SourceFeedCardItem>?,
    ): SourceFeedItem {
        return SourceFeedItem(sourceFeed, results)
    }

    /**
     * Initiates get manga per feed.
     */
    private suspend fun getFeed(feedSavedSearch: List<FeedSavedSearch>) {
        // Create image fetch subscription
        initializeFetchImageSubscription()

        // Create items with the initial state
        val initialItems = getSourcesToGetFeed(feedSavedSearch).map {
            createCatalogueSearchItem(
                it,
                null,
            )
        }
        var items = initialItems

        fetchSourcesSubscription?.unsubscribe()
        fetchSourcesSubscription = Observable.from(getSourcesToGetFeed(feedSavedSearch))
            .flatMap(
                { sourceFeed ->
                    Observable.defer {
                        when (sourceFeed) {
                            SourceFeed.Browse -> source.fetchPopularManga(1)
                            SourceFeed.Latest -> source.fetchLatestUpdates(1)
                            is SourceFeed.SourceSavedSearch -> source.fetchSearchManga(
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
                        .doOnNext { fetchImage(it, source, sourceFeed) } // Load manga covers.
                        .map { list -> createCatalogueSearchItem(sourceFeed, list.map { SourceFeedCardItem(it) }) }
                },
                5,
            )
            .observeOn(AndroidSchedulers.mainThread())
            // Update matching source with the obtained results
            .map { result ->
                items.map { item -> if (item.sourceFeed == result.sourceFeed) result else item }
            }
            // Update current state
            .doOnNext { items = it }
            // Deliver initial state
            .startWith(initialItems)
            .subscribeLatestCache(
                { view, manga ->
                    view.setItems(manga)
                },
                { _, error ->
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

    /**
     * Initialize a list of manga.
     *
     * @param manga the list of manga to initialize.
     */
    private fun fetchImage(manga: List<Manga>, source: Source, sourceFeed: SourceFeed) {
        fetchImageSubject.onNext(Triple(manga, source, sourceFeed))
    }

    /**
     * Subscribes to the initializer of manga details and updates the view if needed.
     */
    private fun initializeFetchImageSubscription() {
        fetchImageSubscription?.unsubscribe()
        fetchImageSubscription = fetchImageSubject.observeOn(Schedulers.io())
            .flatMap { pair ->
                val source = pair.second
                Observable.from(pair.first).filter { it.thumbnail_url == null && !it.initialized }
                    .map { Pair(it, source) }
                    .concatMap { getMangaDetailsObservable(it.first, it.second) }
                    .map { Pair(pair.third, it) }
            }
            .onBackpressureBuffer()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { (sourceFeed, manga) ->
                    @Suppress("DEPRECATION")
                    view?.onMangaInitialized(sourceFeed, manga)
                },
                { error ->
                    logcat(LogPriority.ERROR, error)
                },
            )
    }

    /**
     * Returns an observable of manga that initializes the given manga.
     *
     * @param manga the manga to initialize.
     * @return an observable of the manga to initialize
     */
    private fun getMangaDetailsObservable(manga: Manga, source: Source): Observable<Manga> {
        return runAsObservable {
            val networkManga = source.getMangaDetails(manga.toMangaInfo())
            manga.copyFrom(networkManga.toSManga())
            manga.initialized = true
            updateManga.await(manga.toDomainManga()!!.toMangaUpdate())
            manga
        }
            .onErrorResumeNext { Observable.just(manga) }
    }

    /**
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
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

    suspend fun loadSearch(searchId: Long) =
        getExhSavedSearch.awaitOne(searchId, source::getFilterList)

    suspend fun loadSearches() =
        getExhSavedSearch.await(source.id, source::getFilterList)
}
