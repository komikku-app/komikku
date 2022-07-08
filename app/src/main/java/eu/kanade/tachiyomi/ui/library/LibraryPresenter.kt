package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.core.util.asObservable
import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.GetLibraryManga
import eu.kanade.domain.manga.interactor.GetMergedMangaById
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.util.lang.combineLatest
import eu.kanade.tachiyomi.util.lang.isNullOrUnsubscribed
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import exh.favorites.FavoritesSyncHelper
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import exh.util.isLewd
import exh.util.nullIfBlank
import kotlinx.coroutines.runBlocking
import rx.Observable
import rx.Subscription
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
    private val handler: DatabaseHandler = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    // SY -->
    private val customMangaManager: CustomMangaManager = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
    private val getMergedChaptersByMangaId: GetMergedChapterByMangaId = Injekt.get(),
    // SY <--
) : BasePresenter<LibraryController>() {

    private val context = preferences.context

    /**
     * Categories of the library.
     */
    var categories: List<Category> = emptyList()
        private set

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

    /**
     * Library subscription.
     */
    private var librarySubscription: Subscription? = null

    // SY -->
    val favoritesSync = FavoritesSyncHelper(context)

    var groupType = preferences.groupLibraryBy().get()

    private val libraryIsGrouped
        get() = groupType != LibraryGroup.UNGROUPED

    private val loggedServices by lazy { trackManager.services.filter { it.isLogged } }

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
        subscribeLibrary()
    }

    /**
     * Subscribes to library if needed.
     */
    fun subscribeLibrary() {
        // TODO: Move this to a coroutine world
        if (librarySubscription.isNullOrUnsubscribed()) {
            librarySubscription = getLibraryObservable()
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
                .subscribeLatestCache({ view, (categories, mangaMap) ->
                    view.onNextLibraryUpdate(categories, mangaMap)
                },)
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

            val exclude = trackedManga?.filter { containsExclude.containsKey(it.key.toLong()) && it.value }?.values ?: emptyList()
            val include = trackedManga?.filter { containsInclude.containsKey(it.key.toLong()) && it.value }?.values ?: emptyList()

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
                SortModeSetting.DRAG_AND_DROP -> {
                    0
                }
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
        return Observable.combineLatest(getCategoriesObservable(), getLibraryMangasObservable()) { dbCategories, libraryManga ->
            val categories = if (libraryManga.containsKey(0)) {
                arrayListOf(Category.default(context)) + dbCategories
            } else {
                dbCategories
            }

            libraryManga.forEach { (categoryId, libraryManga) ->
                val category = categories.first { category -> category.id == categoryId.toLong() }
                libraryManga.forEach { libraryItem ->
                    libraryItem.displayMode = category.displayMode
                }
            }

            this.categories = categories
            Library(categories, libraryManga)
        }
    }

    // SY -->
    private fun applyGrouping(map: LibraryMap, categories: List<Category>): Pair<LibraryMap, List<Category>> {
        groupType = preferences.groupLibraryBy().get()
        var editedCategories = categories
        val items = if (groupType == LibraryGroup.BY_DEFAULT) {
            map
        } else if (!libraryIsGrouped) {
            editedCategories = listOf(Category(0, "All", 0, 0, emptyList()))
            mapOf(
                0L to map.values.flatten().distinctBy { it.manga.id },
            )
        } else {
            val (items, customCategories) = getGroupedMangaItems(
                map.values.flatten().distinctBy { it.manga.id },
            )
            editedCategories = customCategories
            items
        }

        return items to editedCategories
    }
    // SY <--

    /**
     * Get the categories from the database.
     *
     * @return an observable of the categories.
     */
    private fun getCategoriesObservable(): Observable<List<Category>> {
        return getCategories.subscribe().asObservable()
    }

    /**
     * Get the manga grouped by categories.
     *
     * @return an observable containing a map with the category id as key and a list of manga as the
     * value.
     */
    private fun getLibraryMangasObservable(): Observable<LibraryMap> {
        val defaultLibraryDisplayMode = preferences.libraryDisplayMode()
        val shouldSetFromCategory = preferences.categorizedDisplaySettings()

        return getLibraryManga.subscribe().asObservable()
            .map { list ->
                list.map { libraryManga ->
                    // Display mode based on user preference: take it from global library setting or category
                    LibraryItem(
                        libraryManga,
                        shouldSetFromCategory,
                        defaultLibraryDisplayMode,
                    )
                }.groupBy { it.manga.category.toLong() }
            }
    }

    /**
     * Get the tracked manga from the database and checks if the filter gets changed
     *
     * @return an observable of tracked manga.
     */
    private fun getFilterObservable(): Observable<Map<Long, Map<Long, Boolean>>> {
        return getTracksObservable().combineLatest(filterTriggerRelay.observeOn(Schedulers.io())) { tracks, _ -> tracks }
    }

    /**
     * Get the tracked manga from the database
     *
     * @return an observable of tracked manga.
     */
    private fun getTracksObservable(): Observable<Map<Long, Map<Long, Boolean>>> {
        // TODO: Move this to domain/data layer
        return getTracks.subscribe()
            .asObservable().map { tracks ->
                tracks
                    .groupBy { it.mangaId }
                    .mapValues { tracksForMangaId ->
                        // Check if any of the trackers is logged in for the current manga id
                        tracksForMangaId.value.associate {
                            Pair(it.syncId, trackManager.getService(it.syncId)?.isLogged.takeUnless { isLogged -> isLogged == true && it.syncId == TrackManager.MDLIST && it.status == FollowStatus.UNFOLLOWED.int.toLong() } ?: false)
                        }
                    }
            }
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
        librarySubscription?.let { remove(it) }
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
    fun cleanTitles(mangas: List<Manga>) {
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
                author = manga.author.takeUnless { it == manga.ogAuthor },
                artist = manga.artist.takeUnless { it == manga.ogArtist },
                description = manga.description.takeUnless { it == manga.ogDescription },
                genre = manga.genre.takeUnless { it == manga.ogGenre },
                status = manga.status.takeUnless { it == manga.ogStatus }?.toLong(),
            )

            customMangaManager.saveMangaInfo(mangaJson)
        }
    }

    fun syncMangaToDex(mangaList: List<Manga>) {
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
        val grouping: MutableMap<Number, Pair<Long, String>> = mutableMapOf()
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
                        grouping.getOrPut(Int.MAX_VALUE) {
                            Long.MAX_VALUE to context.getString(R.string.unknown)
                        }
                        map.getOrPut(Long.MAX_VALUE) { mutableListOf() } += libraryItem
                    }
                }
            }
            else -> {
                libraryManga.forEach { libraryItem ->
                    val group = grouping[libraryItem.manga.status]
                    if (group != null) {
                        map.getOrPut(group.first) { mutableListOf() } += libraryItem
                    } else {
                        grouping.getOrPut(Int.MAX_VALUE) {
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
            Category(id, name, 0, 0, emptyList())
        }

        return map to categories
    }

    override fun onDestroy() {
        super.onDestroy()
        favoritesSync.onDestroy()
    }
    // SY <--
}
