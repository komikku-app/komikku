package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.InsertManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.manga.model.toMangaUpdate
import eu.kanade.domain.source.interactor.CountFeedSavedSearchGlobal
import eu.kanade.domain.source.interactor.DeleteFeedSavedSearchById
import eu.kanade.domain.source.interactor.GetFeedSavedSearchGlobal
import eu.kanade.domain.source.interactor.GetSavedSearchBySourceId
import eu.kanade.domain.source.interactor.GetSavedSearchGlobalFeed
import eu.kanade.domain.source.interactor.InsertFeedSavedSearch
import eu.kanade.presentation.browse.FeedItemUI
import eu.kanade.presentation.browse.FeedState
import eu.kanade.presentation.browse.FeedStateImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNonCancellableIO
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.logcat
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * Presenter of [feedTab]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param sourceManager manages the different sources.
 * @param preferences manages the preference calls.
 */
open class FeedPresenter(
    private val presenterScope: CoroutineScope,
    private val state: FeedStateImpl = FeedState() as FeedStateImpl,
    val sourceManager: SourceManager = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val insertManga: InsertManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal = Injekt.get(),
    private val getSavedSearchGlobalFeed: GetSavedSearchGlobalFeed = Injekt.get(),
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
) : FeedState by state {

    /**
     * Fetches the different sources by user settings.
     */
    private var fetchSourcesSubscription: Subscription? = null

    fun onCreate() {
        getFeedSavedSearchGlobal.subscribe()
            .distinctUntilChanged()
            .onEach {
                val items = getSourcesToGetFeed(it).map { (feed, savedSearch) ->
                    createCatalogueSearchItem(
                        feed = feed,
                        savedSearch = savedSearch,
                        source = sourceManager.get(feed.source) as? CatalogueSource,
                        results = null,
                    )
                }
                state.items = items
                state.isEmpty = items.isEmpty()
                state.isLoading = false
                getFeed(items)
            }
            .launchIn(presenterScope)
    }

    fun onDestroy() {
        fetchSourcesSubscription?.unsubscribe()
    }

    fun openAddDialog() {
        presenterScope.launchIO {
            if (hasTooManyFeeds()) {
                return@launchIO
            }
            dialog = Dialog.AddFeed(getEnabledSources())
        }
    }

    fun openAddSearchDialog(source: CatalogueSource) {
        presenterScope.launchIO {
            dialog = Dialog.AddFeedSearch(source, (if (source.supportsLatest) listOf(null) else emptyList()) + getSourceSavedSearches(source.id))
        }
    }

    suspend fun hasTooManyFeeds(): Boolean {
        return countFeedSavedSearchGlobal.await() > 10
    }

    fun getEnabledSources(): List<CatalogueSource> {
        val languages = preferences.enabledLanguages().get()
        val pinnedSources = preferences.pinnedSources().get()

        val list = sourceManager.getVisibleCatalogueSources()
            .filter { it.lang in languages }
            .sortedBy { "(${it.lang}) ${it.name}" }

        return list.sortedBy { it.id.toString() !in pinnedSources }
    }

    suspend fun getSourceSavedSearches(sourceId: Long): List<SavedSearch> {
        return getSavedSearchBySourceId.await(sourceId)
    }

    fun createFeed(source: CatalogueSource, savedSearch: SavedSearch?) {
        presenterScope.launchNonCancellableIO {
            insertFeedSavedSearch.await(
                FeedSavedSearch(
                    id = -1,
                    source = source.id,
                    savedSearch = savedSearch?.id,
                    global = true,
                ),
            )
        }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        presenterScope.launchNonCancellableIO {
            deleteFeedSavedSearchById.await(feed.id)
        }
    }

    private suspend fun getSourcesToGetFeed(feedSavedSearch: List<FeedSavedSearch>): List<Pair<FeedSavedSearch, SavedSearch?>> {
        val savedSearches = getSavedSearchGlobalFeed.await()
            .associateBy { it.id }
        return feedSavedSearch
            .map { it to savedSearches[it.savedSearch] }
    }

    /**
     * Creates a catalogue search item
     */
    private fun createCatalogueSearchItem(
        feed: FeedSavedSearch,
        savedSearch: SavedSearch?,
        source: CatalogueSource?,
        results: List<DomainManga>?,
    ): FeedItemUI {
        return FeedItemUI(
            feed,
            savedSearch,
            source,
            savedSearch?.name ?: (source?.name ?: feed.source.toString()),
            if (savedSearch != null) {
                source?.name ?: feed.source.toString()
            } else {
                LocaleHelper.getDisplayName(source?.lang)
            },
            results,
        )
    }

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(feedSavedSearch: List<FeedItemUI>) {
        fetchSourcesSubscription?.unsubscribe()
        fetchSourcesSubscription = Observable.from(feedSavedSearch)
            .flatMap(
                { itemUI ->
                    if (itemUI.source != null) {
                        Observable.defer {
                            if (itemUI.savedSearch == null) {
                                itemUI.source.fetchLatestUpdates(1)
                            } else {
                                itemUI.source.fetchSearchManga(1, itemUI.savedSearch.query.orEmpty(), getFilterList(itemUI.savedSearch, itemUI.source))
                            }
                        }
                            .subscribeOn(Schedulers.io())
                            .onErrorReturn { MangasPage(emptyList(), false) } // Ignore timeouts or other exceptions
                            .map { it.mangas } // Get manga from search result.
                            .map { list -> list.map { networkToLocalManga(it, itemUI.source.id) } } // Convert to local manga.
                            .map { list -> itemUI.copy(results = list.mapNotNull { it.toDomainManga() }) }
                    } else {
                        Observable.just(itemUI.copy(results = emptyList()))
                    }
                },
                5,
            )
            .observeOn(AndroidSchedulers.mainThread())
            // Update matching source with the obtained results
            .doOnNext { result ->
                synchronized(state) {
                    state.items = state.items?.map { if (it.feed.id == result.feed.id) result else it }
                }
            }
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
    fun getManga(initialManga: DomainManga, source: CatalogueSource?): State<DomainManga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .collectLatest { manga ->
                    if (manga == null) return@collectLatest
                    withIOContext {
                        initializeManga(source, manga)
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
    private suspend fun initializeManga(source: CatalogueSource?, manga: DomainManga) {
        if (source == null || manga.thumbnailUrl != null || manga.initialized) return
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

    sealed class Dialog {
        data class AddFeed(val options: List<CatalogueSource>) : Dialog()
        data class AddFeedSearch(val source: CatalogueSource, val options: List<SavedSearch?>) : Dialog()
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog()
    }
}
