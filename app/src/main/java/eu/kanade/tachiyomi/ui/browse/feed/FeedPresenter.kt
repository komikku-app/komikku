package eu.kanade.tachiyomi.ui.browse.feed

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.runAsObservable
import eu.kanade.tachiyomi.util.system.logcat
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
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

/**
 * Presenter of [FeedController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param sourceManager manages the different sources.
 * @param db manages the database calls.
 * @param preferences manages the preference calls.
 */
open class FeedPresenter(
    val sourceManager: SourceManager = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
) : BasePresenter<FeedController>() {

    /**
     * Fetches the different sources by user settings.
     */
    private var fetchSourcesSubscription: Subscription? = null

    /**
     * Subject which fetches image of given manga.
     */
    private val fetchImageSubject = PublishSubject.create<Triple<List<Manga>, Source, FeedSavedSearch>>()

    /**
     * Subscription for fetching images of manga.
     */
    private var fetchImageSubscription: Subscription? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        db.getGlobalFeedSavedSearches()
            .asRxObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnEach {
                getFeed()
            }
            .subscribe()
            .let(::add)
    }

    override fun onDestroy() {
        fetchSourcesSubscription?.unsubscribe()
        fetchImageSubscription?.unsubscribe()
        super.onDestroy()
    }

    fun hasTooManyFeeds(): Boolean {
        return db.getGlobalFeedSavedSearches().executeAsBlocking().size > 10
    }

    fun getEnabledSources(): List<CatalogueSource> {
        val languages = preferences.enabledLanguages().get()
        val pinnedSources = preferences.pinnedSources().get()

        val list = sourceManager.getVisibleCatalogueSources()
            .filter { it.lang in languages }
            .sortedBy { "(${it.lang}) ${it.name}" }

        return list.sortedBy { it.id.toString() !in pinnedSources }
    }

    fun getSourceSavedSearches(source: CatalogueSource): List<SavedSearch> {
        return db.getSavedSearches(source.id).executeAsBlocking()
    }

    fun createFeed(source: CatalogueSource, savedSearch: SavedSearch?) {
        launchIO {
            db.insertFeedSavedSearch(
                FeedSavedSearch(
                    id = null,
                    source = source.id,
                    savedSearch = savedSearch?.id,
                    global = true,
                ),
            ).executeAsBlocking()
        }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        launchIO {
            db.deleteFeedSavedSearch(feed).executeAsBlocking()
        }
    }

    private fun getSourcesToGetFeed(): List<Pair<FeedSavedSearch, SavedSearch?>> {
        val savedSearches = db.getGlobalSavedSearchesFeed().executeAsBlocking()
            .associateBy { it.id!! }
        return db.getGlobalFeedSavedSearches().executeAsBlocking()
            .map { it to savedSearches[it.savedSearch] }
    }

    /**
     * Creates a catalogue search item
     */
    protected open fun createCatalogueSearchItem(
        feed: FeedSavedSearch,
        savedSearch: SavedSearch?,
        source: CatalogueSource?,
        results: List<FeedCardItem>?,
    ): FeedItem {
        return FeedItem(feed, savedSearch, source, results)
    }

    /**
     * Initiates get manga per feed.
     */
    fun getFeed() {
        // Create image fetch subscription
        initializeFetchImageSubscription()

        // Create items with the initial state
        val initialItems = getSourcesToGetFeed().map { (feed, savedSearch) ->
            createCatalogueSearchItem(
                feed,
                savedSearch,
                sourceManager.get(feed.source) as? CatalogueSource,
                null,
            )
        }
        var items = initialItems

        fetchSourcesSubscription?.unsubscribe()
        fetchSourcesSubscription = Observable.from(getSourcesToGetFeed())
            .flatMap(
                { (feed, savedSearch) ->
                    val source = sourceManager.get(feed.source) as? CatalogueSource
                    if (source != null) {
                        Observable.defer {
                            if (savedSearch == null) {
                                source.fetchLatestUpdates(1)
                            } else {
                                source.fetchSearchManga(1, savedSearch.query.orEmpty(), getFilterList(savedSearch, source))
                            }
                        }
                            .subscribeOn(Schedulers.io())
                            .onErrorReturn { MangasPage(emptyList(), false) } // Ignore timeouts or other exceptions
                            .map { it.mangas } // Get manga from search result.
                            .map { list -> list.map { networkToLocalManga(it, source.id) } } // Convert to local manga.
                            .doOnNext { fetchImage(it, source, feed) } // Load manga covers.
                            .map { list -> createCatalogueSearchItem(feed, savedSearch, source, list.map { FeedCardItem(it) }) }
                    } else {
                        Observable.just(createCatalogueSearchItem(feed, null, null, emptyList()))
                    }
                },
                5,
            )
            .observeOn(AndroidSchedulers.mainThread())
            // Update matching source with the obtained results
            .map { result ->
                items.map { item -> if (item.feed == result.feed) result else item }
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
    private fun fetchImage(manga: List<Manga>, source: CatalogueSource, feed: FeedSavedSearch) {
        fetchImageSubject.onNext(Triple(manga, source, feed))
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
                { (feed, manga) ->
                    @Suppress("DEPRECATION")
                    view?.onMangaInitialized(feed, manga)
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
}
