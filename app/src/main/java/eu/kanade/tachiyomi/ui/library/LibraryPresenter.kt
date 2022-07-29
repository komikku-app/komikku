package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastAny
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.core.util.asFlow
import eu.kanade.core.util.asObservable
import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.GetIdsOfFavoriteMangaWithMetadata
import eu.kanade.domain.manga.interactor.GetLibraryManga
import eu.kanade.domain.manga.interactor.GetMergedMangaById
import eu.kanade.domain.manga.interactor.GetSearchTags
import eu.kanade.domain.manga.interactor.GetSearchTitles
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.domain.track.model.Track
import eu.kanade.presentation.library.LibraryState
import eu.kanade.presentation.library.LibraryStateImpl
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.util.lang.combineLatest
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import exh.favorites.FavoritesSyncHelper
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.search.Namespace
import exh.search.QueryComponent
import exh.search.SearchEngine
import exh.search.Text
import exh.source.EH_SOURCE_ID
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import exh.source.isMetadataSource
import exh.util.cancellable
import exh.util.isLewd
import exh.util.nullIfBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.Collator
import java.util.Collections
import java.util.Locale
import eu.kanade.tachiyomi.data.database.models.Manga as DbManga

/**
 * Class containing library information.
 */
private data class Library(val categories: List<Category>, val mangaMap: LibraryMap)

/**
 * Typealias for the library manga, using the category as keys, and list of manga as values.
 */
typealias LibraryMap = Map<Long, List<LibraryItem>>

/**
 * Presenter of [LibraryController].
 */
class LibraryPresenter(
    private val state: LibraryStateImpl = LibraryState() as LibraryStateImpl,
    private val handler: DatabaseHandler = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    // SY -->
    private val searchEngine: SearchEngine = SearchEngine(),
    private val customMangaManager: CustomMangaManager = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
    private val getMergedChaptersByMangaId: GetMergedChapterByMangaId = Injekt.get(),
    private val getIdsOfFavoriteMangaWithMetadata: GetIdsOfFavoriteMangaWithMetadata = Injekt.get(),
    private val getSearchTags: GetSearchTags = Injekt.get(),
    private val getSearchTitles: GetSearchTitles = Injekt.get(),
    // SY <--
) : BasePresenter<LibraryController>(), LibraryState by state {

    private val context = preferences.context

    var loadedManga by mutableStateOf(emptyMap<Long, List<LibraryItem>>())
        private set

    val isPerCategory by preferences.categorizedDisplaySettings().asState()

    var currentDisplayMode by preferences.libraryDisplayMode().asState()

    val tabVisibility by preferences.categoryTabs().asState()

    val mangaCountVisibility by preferences.categoryNumberOfItems().asState()

    var activeCategory: Int by preferences.lastUsedCategory().asState()

    val isDownloadOnly: Boolean by preferences.downloadedOnly().asState()

    val isIncognitoMode: Boolean by preferences.incognitoMode().asState()

    /**
     * Relay used to apply the UI filters to the last emission of the library.
     */
    private val filterTriggerRelay = BehaviorRelay.create(Unit)

    /**
     * Relay used to apply the UI update to the last emission of the library.
     */
    private val badgeTriggerRelay = BehaviorRelay.create(Unit)

    /**
     * Relay used to apply the selected sorting method to the last emission of the library.
     */
    private val sortTriggerRelay = BehaviorRelay.create(Unit)

    private var librarySubscription: Job? = null

    // SY -->
    val favoritesSync = FavoritesSyncHelper(context)

    val groupType by preferences.groupLibraryBy().asState()

    private val loggedServices by lazy { trackManager.services.filter { it.isLogged } }

    private val services by lazy {
        trackManager.services.associate { service ->
            service.id to context.getString(service.nameRes())
        }
    }

    /**
     * Relay used to apply the UI update to the last emission of the library.
     */
    private val buttonTriggerRelay = BehaviorRelay.create(Unit)

    /**
     * Relay used to apply the UI update to the last emission of the library.
     */
    private val groupingTriggerRelay = BehaviorRelay.create(Unit)
    // SY <--

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        // SY -->
        combine(
            preferences.isHentaiEnabled().asFlow(),
            preferences.disabledSources().asFlow(),
            preferences.enableExhentai().asFlow(),
        ) { isHentaiEnabled, disabledSources, enableExhentai ->
            state.showSyncExh = isHentaiEnabled && (EH_SOURCE_ID.toString() !in disabledSources || enableExhentai)
        }.flowOn(Dispatchers.IO).launchIn(presenterScope)
        // SY <--

        subscribeLibrary()
    }

    /**
     * Subscribes to library if needed.
     */
    fun subscribeLibrary() {
        /**
         * TODO: Move this to a coroutine world
         * - Move filter and sort to getMangaForCategory and only filter and sort the current display category instead of whole library as some has 5000+ items in the library
         * - Create new db view and new query to just fetch the current category save as needed to instance variable
         * - Fetch badges to maps and retrive as needed instead of fetching all of them at once
         */
        if (librarySubscription == null || librarySubscription!!.isCancelled) {
            librarySubscription = presenterScope.launchIO {
                getLibraryObservable()
                    .combineLatest(badgeTriggerRelay.observeOn(Schedulers.io())) { lib, _ ->
                        lib.apply { setBadges(mangaMap) }
                    }
                    // SY -->
                    .combineLatest(buttonTriggerRelay.observeOn(Schedulers.io())) { lib, _ ->
                        lib.apply { setButtons(mangaMap) }
                    }
                    .combineLatest(groupingTriggerRelay.observeOn(Schedulers.io())) { lib, _ ->
                        val (map, categories) = applyGrouping(lib.mangaMap, lib.categories)
                        lib.copy(mangaMap = map, categories = categories)
                    }
                    // SY <--
                    .combineLatest(getFilterObservable()) { lib, tracks ->
                        lib.copy(mangaMap = applyFilters(lib.mangaMap, tracks))
                    }
                    .combineLatest(sortTriggerRelay.observeOn(Schedulers.io())) { lib, _ ->
                        lib.copy(mangaMap = applySort(lib.categories, lib.mangaMap))
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .asFlow()
                    .collectLatest {
                        // SY -->
                        state.categories = it.categories
                        // SY <--
                        state.isLoading = false
                        loadedManga = it.mangaMap
                    }
            }
        }
    }

    /**
     * Applies library filters to the given map of manga.
     *
     * @param map the map to filter.
     */
    private fun applyFilters(map: LibraryMap, trackMap: Map<Long, Map<Long, Boolean>>): LibraryMap {
        val downloadedOnly = preferences.downloadedOnly().get()
        val filterDownloaded = preferences.filterDownloaded().get()
        val filterUnread = preferences.filterUnread().get()
        val filterStarted = preferences.filterStarted().get()
        val filterCompleted = preferences.filterCompleted().get()
        val loggedInServices = trackManager.services.filter { trackService -> trackService.isLogged }
            .associate { trackService ->
                Pair(trackService.id, preferences.filterTracking(trackService.id).get())
            }
        val isNotAnyLoggedIn = !loggedInServices.values.any()
        // SY -->
        val filterLewd = preferences.filterLewd().get()
        // SY <--

        val filterFnDownloaded: (LibraryItem) -> Boolean = downloaded@{ item ->
            if (!downloadedOnly && filterDownloaded == State.IGNORE.value) return@downloaded true
            val isDownloaded = when {
                item.manga.toDomainManga()!!.isLocal() -> true
                item.downloadCount != -1 -> item.downloadCount > 0
                else -> downloadManager.getDownloadCount(item.manga.toDomainManga()!!) > 0
            }

            return@downloaded if (downloadedOnly || filterDownloaded == State.INCLUDE.value) isDownloaded
            else !isDownloaded
        }

        val filterFnUnread: (LibraryItem) -> Boolean = unread@{ item ->
            if (filterUnread == State.IGNORE.value) return@unread true
            val isUnread = item.manga.unreadCount != 0

            return@unread if (filterUnread == State.INCLUDE.value) isUnread
            else !isUnread
        }

        val filterFnStarted: (LibraryItem) -> Boolean = started@{ item ->
            if (filterStarted == State.IGNORE.value) return@started true
            val hasStarted = item.manga.hasStarted

            return@started if (filterStarted == State.INCLUDE.value) hasStarted
            else !hasStarted
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = completed@{ item ->
            if (filterCompleted == State.IGNORE.value) return@completed true
            val isCompleted = item.manga.status == SManga.COMPLETED

            return@completed if (filterCompleted == State.INCLUDE.value) isCompleted
            else !isCompleted
        }

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotAnyLoggedIn) return@tracking true

            val trackedManga = trackMap[item.manga.id ?: -1]

            val containsExclude = loggedInServices.filterValues { it == State.EXCLUDE.value }
            val containsInclude = loggedInServices.filterValues { it == State.INCLUDE.value }

            if (!containsExclude.any() && !containsInclude.any()) return@tracking true

            val exclude = trackedManga?.filter { containsExclude.containsKey(it.key) && it.value }?.values ?: emptyList()
            val include = trackedManga?.filter { containsInclude.containsKey(it.key) && it.value }?.values ?: emptyList()

            if (containsInclude.any() && containsExclude.any()) {
                return@tracking if (exclude.isNotEmpty()) !exclude.any() else include.any()
            }

            if (containsExclude.any()) return@tracking !exclude.any()

            if (containsInclude.any()) return@tracking include.any()

            return@tracking false
        }

        // SY -->
        val filterFnLewd: (LibraryItem) -> Boolean = lewd@{ item ->
            if (filterLewd == State.IGNORE.value) return@lewd true
            val isLewd = item.manga.isLewd()

            return@lewd if (filterLewd == State.INCLUDE.value) isLewd
            else !isLewd
        }
        // SY <--

        val filterFn: (LibraryItem) -> Boolean = filter@{ item ->
            return@filter !(
                !filterFnDownloaded(item) ||
                    !filterFnUnread(item) ||
                    !filterFnStarted(item) ||
                    !filterFnCompleted(item) ||
                    !filterFnTracking(item) ||
                    // SY -->
                    !filterFnLewd(item)
                // SY <--
                )
        }

        return map.mapValues { entry -> entry.value.filter(filterFn) }
    }

    // SY -->
    /**
     * Sets the button on each manga.
     *
     * @param map the map of manga.
     */
    private fun setButtons(map: LibraryMap) {
        val startReadingButton = preferences.startReadingButton().get()

        for ((_, itemList) in map) {
            for (item in itemList) {
                item.startReadingButton = startReadingButton
            }
        }
    }
    // SY <--

    /**
     * Sets downloaded chapter count to each manga.
     *
     * @param map the map of manga.
     */
    private fun setBadges(map: LibraryMap) {
        val showDownloadBadges = preferences.downloadBadge().get()
        val showUnreadBadges = preferences.unreadBadge().get()
        val showLocalBadges = preferences.localBadge().get()
        val showLanguageBadges = preferences.languageBadge().get()

        for ((_, itemList) in map) {
            for (item in itemList) {
                item.downloadCount = if (showDownloadBadges) {
                    // SY -->
                    if (item.manga.source == MERGED_SOURCE_ID) {
                        item.manga.id?.let { mergeMangaId ->
                            runBlocking {
                                getMergedMangaById.await(mergeMangaId)
                            }.sumOf { downloadManager.getDownloadCount(it) }
                        } ?: 0
                    } else /* SY <-- */ downloadManager.getDownloadCount(item.manga.toDomainManga()!!)
                } else {
                    // Unset download count if not enabled
                    -1
                }

                item.unreadCount = if (showUnreadBadges) {
                    item.manga.unreadCount
                } else {
                    // Unset unread count if not enabled
                    -1
                }

                item.isLocal = if (showLocalBadges) {
                    item.manga.toDomainManga()!!.isLocal()
                } else {
                    // Hide / Unset local badge if not enabled
                    false
                }

                item.sourceLanguage = if (showLanguageBadges) {
                    sourceManager.getOrStub(item.manga.source).lang.uppercase()
                } else {
                    // Unset source language if not enabled
                    ""
                }
            }
        }
    }

    /**
     * Applies library sorting to the given map of manga.
     *
     * @param map the map to sort.
     */
    private fun applySort(categories: List<Category>, map: LibraryMap): LibraryMap {
        val lastReadManga by lazy {
            var counter = 0
            // TODO: Make [applySort] a suspended function
            runBlocking {
                handler.awaitList {
                    mangasQueries.getLastRead()
                }.associate { it._id to counter++ }
            }
        }
        val latestChapterManga by lazy {
            var counter = 0
            // TODO: Make [applySort] a suspended function
            runBlocking {
                handler.awaitList {
                    mangasQueries.getLatestByChapterUploadDate()
                }.associate { it._id to counter++ }
            }
        }
        val chapterFetchDateManga by lazy {
            var counter = 0
            // TODO: Make [applySort] a suspended function
            runBlocking {
                handler.awaitList {
                    mangasQueries.getLatestByChapterFetchDate()
                }.associate { it._id to counter++ }
            }
        }

        // SY -->
        val listOfTags by lazy {
            preferences.sortTagsForLibrary().get()
                .asSequence()
                .mapNotNull {
                    val list = it.split("|")
                    (list.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null) to (list.getOrNull(1) ?: return@mapNotNull null)
                }
                .sortedBy { it.first }
                .map { it.second }
                .toList()
        }
        // SY <--

        val defaultSortingMode = SortModeSetting.get(preferences, null)
        val sortingModes = categories.associate { category ->
            category.id to SortModeSetting.get(preferences, category)
        }

        val defaultSortDirection = SortDirectionSetting.get(preferences, null)
        val sortDirections = categories.associate { category ->
            category.id to SortDirectionSetting.get(preferences, category)
        }

        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        val sortFn: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            val sortingMode = if (groupType == LibraryGroup.BY_DEFAULT) {
                sortingModes[i1.manga.category.toLong()] ?: defaultSortingMode
            } else {
                defaultSortingMode
            }
            val sortAscending = if (groupType == LibraryGroup.BY_DEFAULT) {
                sortDirections[i1.manga.category.toLong()] ?: defaultSortDirection
            } else {
                defaultSortDirection
            } == SortDirectionSetting.ASCENDING

            when (sortingMode) {
                SortModeSetting.ALPHABETICAL -> {
                    collator.compare(i1.manga.title.lowercase(locale), i2.manga.title.lowercase(locale))
                }
                SortModeSetting.LAST_READ -> {
                    val manga1LastRead = lastReadManga[i1.manga.id!!] ?: 0
                    val manga2LastRead = lastReadManga[i2.manga.id!!] ?: 0
                    manga1LastRead.compareTo(manga2LastRead)
                }
                SortModeSetting.LAST_MANGA_UPDATE -> {
                    i1.manga.last_update.compareTo(i2.manga.last_update)
                }
                SortModeSetting.UNREAD_COUNT -> when {
                    // Ensure unread content comes first
                    i1.manga.unreadCount == i2.manga.unreadCount -> 0
                    i1.manga.unreadCount == 0 -> if (sortAscending) 1 else -1
                    i2.manga.unreadCount == 0 -> if (sortAscending) -1 else 1
                    else -> i1.manga.unreadCount.compareTo(i2.manga.unreadCount)
                }
                SortModeSetting.TOTAL_CHAPTERS -> {
                    i1.manga.totalChapters.compareTo(i2.manga.totalChapters)
                }
                SortModeSetting.LATEST_CHAPTER -> {
                    val manga1latestChapter = latestChapterManga[i1.manga.id!!]
                        ?: latestChapterManga.size
                    val manga2latestChapter = latestChapterManga[i2.manga.id!!]
                        ?: latestChapterManga.size
                    manga1latestChapter.compareTo(manga2latestChapter)
                }
                SortModeSetting.CHAPTER_FETCH_DATE -> {
                    val manga1chapterFetchDate = chapterFetchDateManga[i1.manga.id!!] ?: 0
                    val manga2chapterFetchDate = chapterFetchDateManga[i2.manga.id!!] ?: 0
                    manga1chapterFetchDate.compareTo(manga2chapterFetchDate)
                }
                SortModeSetting.DATE_ADDED -> {
                    i1.manga.date_added.compareTo(i2.manga.date_added)
                }
                // SY -->
                SortModeSetting.TAG_LIST -> {
                    val manga1IndexOfTag = listOfTags.indexOfFirst { i1.manga.getGenres()?.contains(it) ?: false }
                    val manga2IndexOfTag = listOfTags.indexOfFirst { i2.manga.getGenres()?.contains(it) ?: false }
                    manga1IndexOfTag.compareTo(manga2IndexOfTag)
                }
                // SY <--
                else -> throw IllegalStateException("Invalid SortModeSetting: $sortingMode")
            }
        }

        return map.mapValues { entry ->
            val sortAscending = if (groupType == LibraryGroup.BY_DEFAULT) {
                sortDirections[entry.key.toLong()] ?: defaultSortDirection
            } else {
                defaultSortDirection
            } == SortDirectionSetting.ASCENDING

            val comparator = if (sortAscending) {
                Comparator(sortFn)
            } else {
                Collections.reverseOrder(sortFn)
            }

            entry.value.sortedWith(comparator)
        }
    }

    /**
     * Get the categories and all its manga from the database.
     *
     * @return an observable of the categories and its manga.
     */
    private fun getLibraryObservable(): Observable<Library> {
        return combine(getCategoriesFlow(), getLibraryMangasFlow()) { dbCategories, libraryManga ->
            val categories = if (libraryManga.containsKey(0) || libraryManga.isEmpty()) {
                arrayListOf(Category.default(context)) + dbCategories
            } else {
                dbCategories
            }

            libraryManga.forEach { (categoryId, libraryManga) ->
                val category = categories.first { category -> category.id == categoryId }
                libraryManga.forEach { libraryItem ->
                    libraryItem.displayMode = category.displayMode
                }
            }

            // SY -->
            state.ogCategories = categories
            // SY <--
            Library(categories, libraryManga)
        }.asObservable()
    }

    // SY -->
    private fun applyGrouping(map: LibraryMap, categories: List<Category>): Pair<LibraryMap, List<Category>> {
        val groupType = preferences.groupLibraryBy().get()
        var editedCategories = categories
        val items = when (groupType) {
            LibraryGroup.BY_DEFAULT -> map
            LibraryGroup.UNGROUPED -> {
                editedCategories = listOf(Category(0, "All", 0, 0))
                mapOf(
                    0L to map.values.flatten().distinctBy { it.manga.id },
                )
            }
            else -> {
                val (items, customCategories) = getGroupedMangaItems(
                    map.values.flatten().distinctBy { it.manga.id },
                )
                editedCategories = customCategories
                items
            }
        }

        return items to editedCategories
    }
    // SY <--

    /**
     * Get the categories from the database.
     *
     * @return an observable of the categories.
     */
    private fun getCategoriesFlow(): Flow<List<Category>> {
        return getCategories.subscribe()
    }

    /**
     * Get the manga grouped by categories.
     *
     * @return an observable containing a map with the category id as key and a list of manga as the
     * value.
     */
    private fun getLibraryMangasFlow(): Flow<LibraryMap> {
        return getLibraryManga.subscribe()
            .map { list ->
                list.map { libraryManga ->
                    // Display mode based on user preference: take it from global library setting or category
                    LibraryItem(libraryManga)
                }.groupBy { it.manga.category.toLong() }
            }
    }

    /**
     * Get the tracked manga from the database and checks if the filter gets changed
     *
     * @return an observable of tracked manga.
     */
    private fun getFilterObservable(): Observable<Map<Long, Map<Long, Boolean>>> {
        return filterTriggerRelay.observeOn(Schedulers.io())
            .combineLatest(getTracksObservable()) { _, tracks -> tracks }
    }

    /**
     * Get the tracked manga from the database
     *
     * @return an observable of tracked manga.
     */
    private fun getTracksObservable(): Observable<Map<Long, Map<Long, Boolean>>> {
        // TODO: Move this to domain/data layer
        return getTracks.subscribe()
            .map { tracks ->
                tracks
                    .groupBy { it.mangaId }
                    .mapValues { tracksForMangaId ->
                        // Check if any of the trackers is logged in for the current manga id
                        tracksForMangaId.value.associate {
                            Pair(it.syncId, trackManager.getService(it.syncId)?.isLogged.takeUnless { isLogged -> isLogged == true && it.syncId == TrackManager.MDLIST && it.status == FollowStatus.UNFOLLOWED.int.toLong() } ?: false)
                        }
                    }
            }
            .asObservable()
            .observeOn(Schedulers.io())
    }

    /**
     * Requests the library to be filtered.
     */
    fun requestFilterUpdate() {
        filterTriggerRelay.call(Unit)
    }

    /**
     * Requests the library to have download badges added.
     */
    fun requestBadgesUpdate() {
        badgeTriggerRelay.call(Unit)
    }

    // SY -->
    /**
     * Requests the library to have buttons toggled.
     */
    fun requestButtonsUpdate() {
        buttonTriggerRelay.call(Unit)
    }

    /**
     * Requests the library to have groups refreshed.
     */
    fun requestGroupsUpdate() {
        groupingTriggerRelay.call(Unit)
    }

    // SY <--

    /**
     * Requests the library to be sorted.
     */
    fun requestSortUpdate() {
        sortTriggerRelay.call(Unit)
    }

    /**
     * Called when a manga is opened.
     */
    fun onOpenManga() {
        // Avoid further db updates for the library when it's not needed
        librarySubscription?.cancel()
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas.toSet()
            .map { getCategories.await(it.id) }
            .reduce { set1, set2 -> set1.intersect(set2).toMutableList() }
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.toSet().map { getCategories.await(it.id) }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2).toMutableList() }
        return mangaCategories.flatten().distinct().subtract(common).toMutableList()
    }

    /**
     * Queues all unread chapters from the given list of manga.
     *
     * @param mangas the list of manga.
     */
    fun downloadUnreadChapters(mangas: List<Manga>) {
        mangas.forEach { manga ->
            launchIO {
                if (manga.source == MERGED_SOURCE_ID) {
                    val mergedSource = sourceManager.get(MERGED_SOURCE_ID) as MergedSource
                    val mergedMangas = getMergedMangaById.await(manga.id)
                    mergedSource
                        .getChapters(manga.id)
                        .filter { !it.read }
                        .groupBy { it.mangaId }
                        .forEach ab@{ (mangaId, chapters) ->
                            val mergedManga = mergedMangas.firstOrNull { it.id == mangaId } ?: return@ab
                            downloadManager.downloadChapters(mergedManga, chapters.map(Chapter::toDbChapter))
                        }
                } else {
                    /* SY --> */
                    val chapters = if (manga.isEhBasedManga()) {
                        getChapterByMangaId.await(manga.id).minByOrNull { it.sourceOrder }
                            ?.takeUnless { it.read }
                            .let(::listOfNotNull)
                    } else /* SY <-- */ getChapterByMangaId.await(manga.id)
                        .filter { !it.read }

                    downloadManager.downloadChapters(manga, chapters.map { it.toDbChapter() })
                }
            }
        }
    }

    // SY -->
    fun cleanTitles(mangas: List<DbManga>) {
        mangas.forEach { manga ->
            val editedTitle = manga.title.replace("\\[.*?]".toRegex(), "").trim().replace("\\(.*?\\)".toRegex(), "").trim().replace("\\{.*?\\}".toRegex(), "").trim().let {
                if (it.contains("|")) {
                    it.replace(".*\\|".toRegex(), "").trim()
                } else {
                    it
                }
            }
            if (manga.title == editedTitle) return@forEach
            val mangaJson = CustomMangaManager.MangaJson(
                id = manga.id,
                title = editedTitle.nullIfBlank(),
                author = manga.author.takeUnless { it == manga.originalAuthor },
                artist = manga.artist.takeUnless { it == manga.originalArtist },
                description = manga.description.takeUnless { it == manga.originalDescription },
                genre = manga.getGenres().takeUnless { it == manga.getOriginalGenres() },
                status = manga.status.takeUnless { it == manga.originalStatus }?.toLong(),
            )

            customMangaManager.saveMangaInfo(mangaJson)
        }
    }

    fun syncMangaToDex(mangaList: List<DbManga>) {
        launchIO {
            MdUtil.getEnabledMangaDex(preferences, sourceManager)?.let { mdex ->
                mangaList.forEach {
                    mdex.updateFollowStatus(MdUtil.getMangaId(it.url), FollowStatus.READING)
                }
            }
        }
    }
    // SY <--

    /**
     * Marks mangas' chapters read status.
     *
     * @param mangas the list of manga.
     */
    fun markReadStatus(mangas: List<Manga>, read: Boolean) {
        mangas.forEach { manga ->
            launchIO {
                setReadStatus.await(
                    manga = manga,
                    read = read,
                )
            }
        }
    }

    /**
     * Remove the selected manga.
     *
     * @param mangaList the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangaList: List<DbManga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        launchIO {
            val mangaToDelete = mangaList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = mangaToDelete.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id!!,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        if (source is MergedSource) {
                            val mergedMangas = getMergedMangaById.await(manga.id!!)
                            val sources = mergedMangas.distinctBy { it.source }.map { sourceManager.getOrStub(it.source) }
                            mergedMangas.forEach merge@{ mergedManga ->
                                val mergedSource = sources.firstOrNull { mergedManga.source == it.id } as? HttpSource ?: return@merge
                                downloadManager.deleteManga(mergedManga, mergedSource)
                            }
                        } else downloadManager.deleteManga(manga.toDomainManga()!!, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Category>, removeCategories: List<Category>) {
        presenterScope.launchIO {
            mangaList.map { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .subtract(removeCategories)
                    .plus(addCategories)
                    .map { it.id }
                setMangaCategories.await(manga.id, categoryIds)
            }
        }
    }

    @Composable
    fun getMangaCountForCategory(categoryId: Long): androidx.compose.runtime.State<Int?> {
        return produceState<Int?>(initialValue = null, loadedManga) {
            value = loadedManga[categoryId]?.size
        }
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) preferences.landscapeColumns() else preferences.portraitColumns()).asState()
    }

    // TODO: This is good but should we separate title from count or get categories with count from db
    @Composable
    fun getToolbarTitle(): androidx.compose.runtime.State<LibraryToolbarTitle> {
        val category = categories.getOrNull(activeCategory)

        val defaultTitle = stringResource(id = R.string.label_library)
        val default = remember { LibraryToolbarTitle(defaultTitle) }

        return produceState(initialValue = default, category, loadedManga, mangaCountVisibility, tabVisibility) {
            val title = if (tabVisibility.not()) category?.name ?: defaultTitle else defaultTitle

            value = when {
                category == null -> default
                (tabVisibility.not() && mangaCountVisibility.not()) -> LibraryToolbarTitle(title)
                tabVisibility.not() && mangaCountVisibility -> LibraryToolbarTitle(title, loadedManga[category.id]?.size)
                (tabVisibility && categories.size > 1) && mangaCountVisibility -> LibraryToolbarTitle(title)
                tabVisibility && mangaCountVisibility -> LibraryToolbarTitle(title, loadedManga[category.id]?.size)
                else -> default
            }
        }
    }

    // SY -->
    @Composable
    fun getMangaForCategory(page: Int): androidx.compose.runtime.State<List<LibraryItem>> {
        val categoryId = remember(categories) {
            categories.getOrNull(page)?.id ?: -1
        }
        val unfiltered = loadedManga[categoryId] ?: emptyList()

        return produceState(initialValue = unfiltered, unfiltered, searchQuery) {
            val query = searchQuery
            value = withIOContext {
                if (unfiltered.isNotEmpty() && !query.isNullOrBlank()) {
                    // Prepare filter object
                    val parsedQuery = searchEngine.parseQuery(query)
                    val mangaWithMetaIds = getIdsOfFavoriteMangaWithMetadata.await()
                    val tracks = if (loggedServices.isNotEmpty()) {
                        getTracks.await(unfiltered.mapNotNull { it.manga.id }.distinct())
                    } else emptyMap()
                    val sources = unfiltered
                        .distinctBy { it.manga.source }
                        .mapNotNull { sourceManager.get(it.manga.source) }
                        .associateBy { it.id }
                    unfiltered.asFlow().cancellable().filter { item ->
                        val mangaId = item.manga.id ?: -1
                        val sourceId = item.manga.source
                        if (isMetadataSource(sourceId)) {
                            if (mangaWithMetaIds.binarySearch(mangaId) < 0) {
                                // No meta? Filter using title
                                filterManga(
                                    queries = parsedQuery,
                                    manga = item.manga,
                                    tracks = tracks[mangaId],
                                    source = sources[sourceId],
                                )
                            } else {
                                val tags = getSearchTags.await(mangaId)
                                val titles = getSearchTitles.await(mangaId)
                                filterManga(
                                    queries = parsedQuery,
                                    manga = item.manga,
                                    tracks = tracks[mangaId],
                                    source = sources[sourceId],
                                    checkGenre = false,
                                    searchTags = tags,
                                    searchTitles = titles,
                                )
                            }
                        } else {
                            filterManga(
                                queries = parsedQuery,
                                manga = item.manga,
                                tracks = tracks[mangaId],
                                source = sources[sourceId],
                            )
                        }
                    }.toList()
                } else {
                    unfiltered
                }
            }
        }
    }

    private fun filterManga(
        queries: List<QueryComponent>,
        manga: LibraryManga,
        tracks: List<Track>?,
        source: Source?,
        checkGenre: Boolean = true,
        searchTags: List<SearchTag>? = null,
        searchTitles: List<SearchTitle>? = null,
    ): Boolean {
        val sourceIdString = manga.source.takeUnless { it == LocalSource.ID }?.toString()
        val genre = if (checkGenre) manga.getGenres().orEmpty() else emptyList()
        return queries.all { queryComponent ->
            when (queryComponent.excluded) {
                false -> when (queryComponent) {
                    is Text -> {
                        val query = queryComponent.asQuery()
                        manga.title.contains(query, true) ||
                            (manga.author?.contains(query, true) == true) ||
                            (manga.artist?.contains(query, true) == true) ||
                            (manga.description?.contains(query, true) == true) ||
                            (source?.name?.contains(query, true) == true) ||
                            (sourceIdString != null && sourceIdString == query) ||
                            (loggedServices.isNotEmpty() && tracks != null && filterTracks(query, tracks)) ||
                            (genre.any { it.contains(query, true) }) ||
                            (searchTags?.any { it.name.contains(query, true) } == true) ||
                            (searchTitles?.any { it.title.contains(query, true) } == true)
                    }
                    is Namespace -> {
                        searchTags != null && searchTags.any {
                            val tag = queryComponent.tag
                            (it.namespace.equals(queryComponent.namespace, true) && tag?.run { it.name.contains(tag.asQuery(), true) } == true) ||
                                (tag == null && it.namespace.equals(queryComponent.namespace, true))
                        }
                    }
                    else -> true
                }
                true -> when (queryComponent) {
                    is Text -> {
                        val query = queryComponent.asQuery()
                        query.isBlank() || (
                            (!manga.title.contains(query, true)) &&
                                (manga.author?.contains(query, true) != true) &&
                                (manga.artist?.contains(query, true) != true) &&
                                (manga.description?.contains(query, true) != true) &&
                                (source?.name?.contains(query, true) != true) &&
                                (sourceIdString != null && sourceIdString != query) &&
                                (loggedServices.isEmpty() || loggedServices.isNotEmpty() && tracks == null || tracks != null && !filterTracks(query, tracks)) &&
                                (genre.none { it.contains(query, true) }) &&
                                (searchTags?.any { it.name.contains(query, true) } != true) &&
                                (searchTitles?.any { it.title.contains(query, true) } != true)
                            )
                    }
                    is Namespace -> {
                        val searchedTag = queryComponent.tag?.asQuery()
                        searchTags == null || (queryComponent.namespace.isBlank() && searchedTag.isNullOrBlank()) || searchTags.all { mangaTag ->
                            if (queryComponent.namespace.isBlank() && !searchedTag.isNullOrBlank()) {
                                !mangaTag.name.contains(searchedTag, true)
                            } else if (searchedTag.isNullOrBlank()) {
                                mangaTag.namespace == null || !mangaTag.namespace.equals(queryComponent.namespace, true)
                            } else if (mangaTag.namespace.isNullOrBlank()) {
                                true
                            } else {
                                !mangaTag.name.contains(searchedTag, true) || !mangaTag.namespace.equals(queryComponent.namespace, true)
                            }
                        }
                    }
                    else -> true
                }
            }
        }
    }

    private fun filterTracks(constraint: String, tracks: List<eu.kanade.domain.track.model.Track>): Boolean {
        return tracks.any {
            val trackService = trackManager.getService(it.syncId)
            if (trackService != null) {
                val status = trackService.getStatus(it.status.toInt())
                val name = services[it.syncId]
                status.contains(constraint, true) || name?.contains(constraint, true) == true
            } else false
        }
    }
    // SY <--

    @Composable
    fun getDisplayMode(index: Int): androidx.compose.runtime.State<DisplayModeSetting> {
        val category = categories[index]
        return derivedStateOf {
            // SY -->
            if (groupType != LibraryGroup.BY_DEFAULT || isPerCategory.not() || (category.id == 0L && groupType == LibraryGroup.BY_DEFAULT)) {
                // SY <--
                currentDisplayMode
            } else {
                DisplayModeSetting.fromFlag(category.displayMode)
            }
        }
    }

    fun hasSelection(): Boolean {
        return selection.isNotEmpty()
    }

    fun clearSelection() {
        state.selection = emptyList()
    }

    fun toggleSelection(manga: LibraryManga) {
        val mutableList = state.selection.toMutableList()
        if (selection.fastAny { it.id == manga.id }) {
            mutableList.remove(manga)
        } else {
            mutableList.add(manga)
        }
        state.selection = mutableList
    }

    fun selectAll(index: Int) {
        val category = categories[index]
        val items = loadedManga[category.id] ?: emptyList()
        state.selection = state.selection.toMutableList().apply {
            addAll(items.filterNot { it.manga in selection }.map { it.manga })
        }
    }

    fun invertSelection(index: Int) {
        val category = categories[index]
        val items = (loadedManga[category.id] ?: emptyList()).map { it.manga }
        state.selection = items.filterNot { it in selection }
    }

    // SY -->
    /** Returns first unread chapter of a manga */
    fun getFirstUnread(manga: Manga): Chapter? {
        val chapters = if (manga.source == MERGED_SOURCE_ID) {
            (sourceManager.get(MERGED_SOURCE_ID) as MergedSource).getChaptersAsBlocking(manga.id)
        } else runBlocking { getChapterByMangaId.await(manga.id) }
        return if (manga.isEhBasedManga()) {
            val chapter = chapters.sortedBy { it.sourceOrder }.getOrNull(0)
            if (chapter?.read == false) chapter else null
        } else {
            chapters.sortedByDescending { it.sourceOrder }.find { !it.read }
        }
    }

    private fun getGroupedMangaItems(libraryManga: List<LibraryItem>): Pair<LibraryMap, List<Category>> {
        val groupType = preferences.groupLibraryBy().get()
        val grouping: MutableMap<Long, Pair<Long, String>> = mutableMapOf()
        when (groupType) {
            LibraryGroup.BY_STATUS -> {
                grouping.putAll(
                    listOf(
                        SManga.ONGOING.toLong() to context.getString(R.string.ongoing),
                        SManga.LICENSED.toLong() to context.getString(R.string.licensed),
                        SManga.CANCELLED.toLong() to context.getString(R.string.cancelled),
                        SManga.ON_HIATUS.toLong() to context.getString(R.string.on_hiatus),
                        SManga.PUBLISHING_FINISHED.toLong() to context.getString(R.string.publishing_finished),
                        SManga.COMPLETED.toLong() to context.getString(R.string.completed),
                        SManga.UNKNOWN.toLong() to context.getString(R.string.unknown),
                    ).associateBy(Pair<Long, *>::first),
                )
            }
            LibraryGroup.BY_SOURCE ->
                libraryManga
                    .map { it.manga.source }
                    .distinct()
                    .sorted()
                    .map { sourceId ->
                        sourceId to (sourceId to sourceManager.getOrStub(sourceId).name)
                    }
                    .let(grouping::putAll)
            LibraryGroup.BY_TRACK_STATUS -> {
                grouping.putAll(
                    TrackStatus.values()
                        .map { it.int.toLong() to context.getString(it.res) }
                        .associateBy(Pair<Long, *>::first),
                )
            }
        }
        val map: MutableMap<Long, MutableList<LibraryItem>> = mutableMapOf()

        when (groupType) {
            LibraryGroup.BY_TRACK_STATUS -> {
                val tracks = runBlocking { getTracks.await() }.groupBy { it.mangaId }
                libraryManga.forEach { libraryItem ->
                    val status = tracks[libraryItem.manga.id]?.firstNotNullOfOrNull { track ->
                        TrackStatus.parseTrackerStatus(track.syncId, track.status)
                    } ?: TrackStatus.OTHER

                    map.getOrPut(status.int.toLong()) { mutableListOf() } += libraryItem
                }
            }
            LibraryGroup.BY_SOURCE -> {
                libraryManga.forEach { libraryItem ->
                    val group = grouping[libraryItem.manga.source]
                    if (group != null) {
                        map.getOrPut(group.first) { mutableListOf() } += libraryItem
                    } else {
                        grouping.getOrPut(Long.MAX_VALUE) {
                            Long.MAX_VALUE to context.getString(R.string.unknown)
                        }
                        map.getOrPut(Long.MAX_VALUE) { mutableListOf() } += libraryItem
                    }
                }
            }
            else -> {
                libraryManga.forEach { libraryItem ->
                    val group = grouping[libraryItem.manga.status.toLong()]
                    if (group != null) {
                        map.getOrPut(group.first) { mutableListOf() } += libraryItem
                    } else {
                        grouping.getOrPut(Long.MAX_VALUE) {
                            Long.MAX_VALUE to context.getString(R.string.unknown)
                        }
                        map.getOrPut(Long.MAX_VALUE) { mutableListOf() } += libraryItem
                    }
                }
            }
        }

        val categories = when (groupType) {
            LibraryGroup.BY_SOURCE -> grouping.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Pair<*, String>::second))
            LibraryGroup.BY_TRACK_STATUS, LibraryGroup.BY_STATUS -> grouping.values.filter { it.first in map.keys }
            else -> grouping.values
        }.map { (id, name) ->
            Category(id, name, 0, 0)
        }

        return map to categories
    }

    fun runSync() {
        favoritesSync.runSync(presenterScope)
    }
    // SY <--
}
