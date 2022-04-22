package eu.kanade.tachiyomi.ui.browse.source.feed

import android.os.Bundle
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.exh.feedSavedSearchMapper
import eu.kanade.data.exh.savedSearchMapper
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
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
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.logcat
import exh.log.xLogE
import exh.savedsearches.EXHSavedSearch
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import logcat.LogPriority
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.lang.RuntimeException

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
 * @param database manages the database calls.
 * @param preferences manages the preference calls.
 */
open class SourceFeedPresenter(
    val source: CatalogueSource,
    val database: DatabaseHandler = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
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

        database.subscribeToList { feed_saved_searchQueries.selectSourceFeedSavedSearch(source.id, savedSearchMapper) }
            .onEach {
                getFeed()
            }
            .launchIn(presenterScope)
    }

    override fun onDestroy() {
        fetchSourcesSubscription?.unsubscribe()
        fetchImageSubscription?.unsubscribe()
        super.onDestroy()
    }

    suspend fun hasTooManyFeeds(): Boolean {
        return withIOContext {
            database.awaitList {
                feed_saved_searchQueries.selectSourceFeedSavedSearch(source.id)
            }.size > 10
        }
    }

    suspend fun getSourceSavedSearches(): List<SavedSearch> {
        return database.awaitList { saved_searchQueries.selectBySource(source.id, savedSearchMapper) }
    }

    fun createFeed(savedSearchId: Long) {
        launchIO {
            database.await {
                feed_saved_searchQueries.insertFeedSavedSearch(
                    _id = null,
                    source = source.id,
                    saved_search = savedSearchId,
                    global = false
                )
            }
        }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        launchIO {
            database.await { feed_saved_searchQueries.deleteById(feed.id ?: return@await) }
        }
    }

    private suspend fun getSourcesToGetFeed(): List<SourceFeed> {
        val savedSearches = database.awaitList { feed_saved_searchQueries.selectSourceFeedSavedSearch(source.id, savedSearchMapper) }
            .associateBy { it.id!! }

        return listOfNotNull(
            if (source.supportsLatest) {
                SourceFeed.Latest
            } else null,
            SourceFeed.Browse,
        ) + database.awaitList { feed_saved_searchQueries.selectBySource(source.id, feedSavedSearchMapper) }
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
    suspend fun getFeed() {
        // Create image fetch subscription
        initializeFetchImageSubscription()

        // Create items with the initial state
        val initialItems = getSourcesToGetFeed().map {
            createCatalogueSearchItem(
                it,
                null,
            )
        }
        var items = initialItems

        fetchSourcesSubscription?.unsubscribe()
        fetchSourcesSubscription = Observable.from(getSourcesToGetFeed())
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
            db.insertManga(manga).executeAsBlocking()
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
    protected open fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        var localManga = db.getManga(sManga.url, sourceId).executeAsBlocking()
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            val result = db.insertManga(newManga).executeAsBlocking()
            newManga.id = result.insertedId()
            localManga = newManga
        }
        return localManga
    }

    suspend fun loadSearch(searchId: Long): EXHSavedSearch? {
        return withIOContext {
            val search = database.awaitOneOrNull {
                saved_searchQueries.selectById(searchId, savedSearchMapper)
            } ?: return@withIOContext null
            EXHSavedSearch(
                id = search.id!!,
                name = search.name,
                query = search.query.orEmpty(),
                filterList = runCatching {
                    val originalFilters = source.getFilterList()
                    filterSerializer.deserialize(
                        filters = originalFilters,
                        json = search.filtersJson
                            ?.let { Json.decodeFromString<JsonArray>(it) }
                            ?: return@runCatching null,
                    )
                    originalFilters
                }.getOrNull(),
            )
        }
    }

    suspend fun loadSearches(): List<EXHSavedSearch> {
        return withIOContext {
            database.awaitList { saved_searchQueries.selectBySource(source.id, savedSearchMapper) }.map {
                val filtersJson = it.filtersJson ?: return@map EXHSavedSearch(
                    id = it.id!!,
                    name = it.name,
                    query = it.query.orEmpty(),
                    filterList = null,
                )
                val filters = try {
                    Json.decodeFromString<JsonArray>(filtersJson)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                } ?: return@map EXHSavedSearch(
                    id = it.id!!,
                    name = it.name,
                    query = it.query.orEmpty(),
                    filterList = null,
                )

                try {
                    val originalFilters = source.getFilterList()
                    filterSerializer.deserialize(originalFilters, filters)
                    EXHSavedSearch(
                        id = it.id!!,
                        name = it.name,
                        query = it.query.orEmpty(),
                        filterList = originalFilters,
                    )
                } catch (t: RuntimeException) {
                    // Load failed
                    xLogE("Failed to load saved search!", t)
                    EXHSavedSearch(
                        id = it.id!!,
                        name = it.name,
                        query = it.query.orEmpty(),
                        filterList = null,
                    )
                }
            }
        }
    }
}
