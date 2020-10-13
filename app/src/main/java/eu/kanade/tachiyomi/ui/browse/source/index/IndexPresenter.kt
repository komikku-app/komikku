package eu.kanade.tachiyomi.ui.browse.source.index

import android.os.Bundle
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter.Companion.toItems
import exh.savedsearches.EXHSavedSearch
import exh.savedsearches.JsonSavedSearch
import exh.util.asFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.lang.RuntimeException

/**
 * Presenter of [IndexController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param source the source.
 * @param db manages the database calls.
 * @param preferences manages the preference calls.
 */
open class IndexPresenter(
    val source: CatalogueSource,
    val db: DatabaseHelper = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get()
) : BasePresenter<IndexController>() {

    /**
     * Fetches the different sources by user settings.
     */
    private var fetchSourcesSubscription: Subscription? = null

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    /**
     * Query from the view.
     */
    var query = ""

    /**
     * Subject which fetches image of given manga.
     */
    private val fetchImageSubject = PublishSubject.create<List<Pair<Manga, Boolean>>>()

    /**
     * Modifiable list of filters.
     */
    var sourceFilters = FilterList()
        set(value) {
            field = value
            filterItems = value.toItems()
        }

    var filterItems: List<IFlexible<*>> = emptyList()

    /**
     * Subscription for fetching images of manga.
     */
    private var fetchImageSubscription: Subscription? = null

    override fun onDestroy() {
        fetchSourcesSubscription?.unsubscribe()
        fetchImageSubscription?.unsubscribe()
        super.onDestroy()
    }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        sourceFilters = source.getFilterList()
    }

    /**
     * Initiates get latest per watching source.
     */
    fun getLatest() {
        // Create image fetch subscription
        initializeFetchImageSubscription()

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                Observable.just(null).subscribeLatestCache({ view, results ->
                    view.setLatestManga(results)
                })
            }
            if (source.supportsLatest) {
                val results = try {
                    source.fetchLatestUpdates(1)
                        .asFlow()
                        .singleOrNull()
                        ?.mangas
                        ?.take(10)
                        ?.map { networkToLocalManga(it, source.id) }
                        ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                fetchImage(results, true)

                withContext(Dispatchers.Main) {
                    Observable.just(results.map { IndexCardItem(it) }).subscribeLatestCache({ view, results ->
                        view.setLatestManga(results)
                    })
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                Observable.just(null).subscribeLatestCache({ view, results ->
                    view.setBrowseManga(results)
                })
            }

            val results = try {
                source.fetchPopularManga(1)
                    .asFlow()
                    .singleOrNull()
                    ?.mangas
                    ?.take(10)
                    ?.map { networkToLocalManga(it, source.id) }
                    ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            fetchImage(results, false)

            withContext(Dispatchers.Main) {
                Observable.just(results.map { IndexCardItem(it) }).subscribeLatestCache({ view, results ->
                    view.setBrowseManga(results)
                })
            }
        }
    }

    /**
     * Initialize a list of manga.
     *
     * @param manga the list of manga to initialize.
     */
    private fun fetchImage(manga: List<Manga>, isLatest: Boolean) {
        fetchImageSubject.onNext(manga.map { it to isLatest })
    }

    /**
     * Subscribes to the initializer of manga details and updates the view if needed.
     */
    private fun initializeFetchImageSubscription() {
        fetchImageSubscription?.unsubscribe()
        fetchImageSubscription = fetchImageSubject.observeOn(Schedulers.io())
            .flatMap { pair ->
                Observable.from(pair).filter { it.first.thumbnail_url == null && !it.first.initialized }
                    .concatMap { getMangaDetailsObservable(it.first, source, it.second) }
            }
            .onBackpressureBuffer()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { pair ->
                    @Suppress("DEPRECATION")
                    view?.onMangaInitialized(pair.first, pair.second)
                },
                { error ->
                    Timber.e(error)
                }
            )
    }

    /**
     * Returns an observable of manga that initializes the given manga.
     *
     * @param manga the manga to initialize.
     * @return an observable of the manga to initialize
     */
    private fun getMangaDetailsObservable(manga: Manga, source: Source, isLatest: Boolean): Observable<Pair<Manga, Boolean>> {
        return source.fetchMangaDetails(manga)
            .flatMap { networkManga ->
                manga.copyFrom(networkManga)
                manga.initialized = true
                db.insertManga(manga).executeAsBlocking()
                Observable.just(manga to isLatest)
            }
            .onErrorResumeNext { Observable.just(manga to isLatest) }
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

    private val filterSerializer = FilterSerializer()

    fun loadSearches(): List<EXHSavedSearch> {
        val loaded = preferences.eh_savedSearches().get()
        return loaded.map {
            try {
                val id = it.substringBefore(':').toLong()
                if (id != source.id) return@map null
                val content = Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
                val originalFilters = source.getFilterList()
                filterSerializer.deserialize(originalFilters, content.filters)
                EXHSavedSearch(
                    content.name,
                    content.query,
                    originalFilters
                )
            } catch (t: RuntimeException) {
                // Load failed
                Timber.e(t, "Failed to load saved search!")
                t.printStackTrace()
                null
            }
        }.filterNotNull()
    }
}
