package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.util.isLocal
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
import exh.util.executeOnIO
import exh.util.isLewd
import exh.util.nullIfBlank
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.Collator
import java.util.Collections
import java.util.Comparator
import java.util.Locale

/**
 * Class containing library information.
 */
private data class Library(val categories: List<Category>, val mangaMap: LibraryMap)

/**
 * Typealias for the library manga, using the category as keys, and list of manga as values.
 */
private typealias LibraryMap = Map<Int, List<LibraryItem>>

/**
 * Presenter of [LibraryController].
 */
class LibraryPresenter(
    private val db: DatabaseHelper = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    // SY -->
    private val customMangaManager: CustomMangaManager = Injekt.get(),
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
    private fun applyFilters(map: LibraryMap, trackMap: Map<Long, Map<Int, Boolean>>): LibraryMap {
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
                item.manga.isLocal() -> true
                item.downloadCount != -1 -> item.downloadCount > 0
                else -> downloadManager.getDownloadCount(item.manga) > 0
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
                        item.manga.id?.let { mergeMangaId -> db.getMergedMangas(mergeMangaId).executeAsBlocking().map { downloadManager.getDownloadCount(it) }.sum() } ?: 0
                    } else /* SY <-- */ downloadManager.getDownloadCount(item.manga)
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
                    item.manga.isLocal()
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
            db.getLastReadManga().executeAsBlocking().associate { it.id!! to counter++ }
        }
        val latestChapterManga by lazy {
            var counter = 0
            db.getLatestChapterManga().executeAsBlocking().associate { it.id!! to counter++ }
        }
        val chapterFetchDateManga by lazy {
            var counter = 0
            db.getChapterFetchDateManga().executeAsBlocking().associate { it.id!! to counter++ }
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
            (category.id ?: 0) to SortModeSetting.get(preferences, category)
        }

        val defaultSortDirection = SortDirectionSetting.get(preferences, null)
        val sortDirections = categories.associate { category ->
            (category.id ?: 0) to SortDirectionSetting.get(preferences, category)
        }

        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        val sortFn: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            val sortingMode = if (groupType == LibraryGroup.BY_DEFAULT) {
                sortingModes[i1.manga.category] ?: defaultSortingMode
            } else {
                defaultSortingMode
            }
            val sortAscending = if (groupType == LibraryGroup.BY_DEFAULT) {
                sortDirections[i1.manga.category] ?: defaultSortDirection
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
                SortModeSetting.LAST_CHECKED -> {
                    i1.manga.last_update.compareTo(i2.manga.last_update)
                }
                SortModeSetting.UNREAD -> when {
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
                SortModeSetting.DATE_FETCHED -> {
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
            }
        }

        return map.mapValues { entry ->
            val sortAscending = if (groupType == LibraryGroup.BY_DEFAULT) {
                sortDirections[entry.key] ?: defaultSortDirection
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
                arrayListOf(Category.createDefault(context)) + dbCategories
            } else {
                dbCategories
            }

            libraryManga.forEach { (categoryId, libraryManga) ->
                val category = categories.first { category -> category.id == categoryId }
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
            editedCategories = listOf(Category.create("All").apply { this.id = 0 })
            mapOf(
                0 to map.values.flatten().distinctBy { it.manga.id },
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
        return db.getCategories().asRxObservable()
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
        return db.getLibraryMangas().asRxObservable()
            .map { list ->
                list.map { libraryManga ->
                    // Display mode based on user preference: take it from global library setting or category
                    LibraryItem(
                        libraryManga,
                        shouldSetFromCategory,
                        defaultLibraryDisplayMode,
                    )
                }.groupBy { it.manga.category }
            }
    }

    /**
     * Get the tracked manga from the database and checks if the filter gets changed
     *
     * @return an observable of tracked manga.
     */
    private fun getFilterObservable(): Observable<Map<Long, Map<Int, Boolean>>> {
        return getTracksObservable().combineLatest(filterTriggerRelay.observeOn(Schedulers.io())) { tracks, _ -> tracks }
    }

    /**
     * Get the tracked manga from the database
     *
     * @return an observable of tracked manga.
     */
    private fun getTracksObservable(): Observable<Map<Long, Map<Int, Boolean>>> {
        return db.getTracks().asRxObservable().map { tracks ->
            tracks.groupBy { it.manga_id }
                .mapValues { tracksForMangaId ->
                    // Check if any of the trackers is logged in for the current manga id
                    tracksForMangaId.value.associate {
                        Pair(it.sync_id, trackManager.getService(it.sync_id)?.isLogged.takeUnless { isLogged -> isLogged == true && it.sync_id == TrackManager.MDLIST && it.status == FollowStatus.UNFOLLOWED.int } ?: false)
                    }
                }
        }.observeOn(Schedulers.io())
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
    fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas.toSet()
            .map { db.getCategoriesForManga(it).executeAsBlocking() }
            .reduce { set1: Iterable<Category>, set2 -> set1.intersect(set2).toMutableList() }
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.toSet().map { db.getCategoriesForManga(it).executeAsBlocking() }
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
                    val mergedMangas = db.getMergedMangas(manga.id!!).executeAsBlocking()
                    mergedSource
                        .getChaptersAsBlocking(manga)
                        .filter { !it.read }
                        .groupBy { it.manga_id!! }
                        .forEach ab@{ (mangaId, chapters) ->
                            val mergedManga = mergedMangas.firstOrNull { it.id == mangaId } ?: return@ab
                            downloadManager.downloadChapters(mergedManga, chapters)
                        }
                } else {
                    /* SY --> */
                    val chapters = if (manga.isEhBasedManga()) {
                        db.getChapters(manga).executeOnIO().minByOrNull { it.source_order }?.let { chapter ->
                            if (!chapter.read) listOf(chapter) else emptyList()
                        } ?: emptyList()
                    } else /* SY <-- */ db.getChapters(manga).executeAsBlocking()
                        .filter { !it.read }

                    downloadManager.downloadChapters(manga, chapters)
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
            val mangaJson = manga.id?.let { mangaId ->
                CustomMangaManager.MangaJson(
                    mangaId,
                    editedTitle.nullIfBlank(),
                    manga.author.takeUnless { it == manga.originalAuthor },
                    manga.artist.takeUnless { it == manga.originalArtist },
                    manga.description.takeUnless { it == manga.originalDescription },
                    manga.genre.takeUnless { it == manga.originalGenre }?.let { manga.getGenres() },
                    manga.status.takeUnless { it == manga.originalStatus },
                )
            }
            if (mangaJson != null) {
                customMangaManager.saveMangaInfo(mangaJson)
            }
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
                val chapters = if (manga.source == MERGED_SOURCE_ID) (sourceManager.get(MERGED_SOURCE_ID) as MergedSource).getChaptersAsBlocking(manga) else db.getChapters(manga).executeAsBlocking()
                chapters.forEach {
                    it.read = read
                    if (!read) {
                        it.last_page_read = 0
                    }
                }
                db.updateChaptersProgress(chapters).executeAsBlocking()

                if (read && preferences.removeAfterMarkedAsRead()) {
                    deleteChapters(manga, chapters)
                }
            }
        }
    }

    private fun deleteChapters(manga: Manga, chapters: List<Chapter>) {
        sourceManager.get(manga.source)?.let { source ->
            // SY -->
            if (source is MergedSource) {
                val mergedMangas = db.getMergedMangas(manga.id!!).executeAsBlocking()
                val sources = mergedMangas.distinctBy { it.source }.map { sourceManager.getOrStub(it.source) }
                chapters.groupBy { it.manga_id }.forEach { (mangaId, chapters) ->
                    val mergedManga = mergedMangas.firstOrNull { it.id == mangaId } ?: return@forEach
                    val mergedMangaSource = sources.firstOrNull { it.id == mergedManga.source } ?: return@forEach
                    downloadManager.deleteChapters(chapters, mergedManga, mergedMangaSource)
                }
            } else /* SY <-- */ downloadManager.deleteChapters(chapters, manga, source)
        }
    }

    /**
     * Remove the selected manga.
     *
     * @param mangas the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        launchIO {
            val mangaToDelete = mangas.distinctBy { it.id }

            if (deleteFromLibrary) {
                mangaToDelete.forEach {
                    it.favorite = false
                    it.removeCovers(coverCache)
                }
                db.insertMangas(mangaToDelete).executeAsBlocking()
            }

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        if (source is MergedSource) {
                            val mergedMangas = db.getMergedMangas(manga.id!!).executeAsBlocking()
                            val sources = mergedMangas.distinctBy { it.source }.map { sourceManager.getOrStub(it.source) }
                            mergedMangas.forEach merge@{ mergedManga ->
                                val mergedSource = sources.firstOrNull { mergedManga.source == it.id } as? HttpSource ?: return@merge
                                downloadManager.deleteManga(mergedManga, mergedSource)
                            }
                        } else downloadManager.deleteManga(manga, source)
                    }
                }
            }
        }
    }

    /**
     * Move the given list of manga to categories.
     *
     * @param categories the selected categories.
     * @param mangas the list of manga to move.
     */
    fun moveMangasToCategories(categories: List<Category>, mangas: List<Manga>) {
        val mc = mutableListOf<MangaCategory>()

        for (manga in mangas) {
            categories.mapTo(mc) { MangaCategory.create(manga, it) }
        }

        db.setMangaCategories(mc, mangas)
    }

    /**
     * Bulk update categories of mangas using old and new common categories.
     *
     * @param mangas the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun updateMangasToCategories(mangas: List<Manga>, addCategories: List<Category>, removeCategories: List<Category>) {
        val mangaCategories = mangas.map { manga ->
            val categories = db.getCategoriesForManga(manga).executeAsBlocking()
                .subtract(removeCategories).plus(addCategories).distinct()
            categories.map { MangaCategory.create(manga, it) }
        }.flatten()

        db.setMangaCategories(mangaCategories, mangas)
    }

    // SY -->
    /** Returns first unread chapter of a manga */
    fun getFirstUnread(manga: Manga): Chapter? {
        val chapters = if (manga.source == MERGED_SOURCE_ID) {
            (sourceManager.get(MERGED_SOURCE_ID) as MergedSource).getChaptersAsBlocking(manga)
        } else db.getChapters(manga).executeAsBlocking()
        return if (manga.isEhBasedManga()) {
            val chapter = chapters.sortedBy { it.source_order }.getOrNull(0)
            if (chapter?.read == false) chapter else null
        } else {
            chapters.sortedByDescending { it.source_order }.find { !it.read }
        }
    }

    private fun getGroupedMangaItems(libraryManga: List<LibraryItem>): Pair<LibraryMap, List<Category>> {
        val grouping: MutableMap<Number, Pair<Int, String>> = mutableMapOf()
        when (groupType) {
            LibraryGroup.BY_STATUS -> {
                grouping.putAll(
                    listOf(
                        SManga.ONGOING to context.getString(R.string.ongoing),
                        SManga.LICENSED to context.getString(R.string.licensed),
                        SManga.CANCELLED to context.getString(R.string.cancelled),
                        SManga.ON_HIATUS to context.getString(R.string.ongoing),
                        SManga.PUBLISHING_FINISHED to context.getString(R.string.publishing_finished),
                        SManga.COMPLETED to context.getString(R.string.completed),
                        SManga.UNKNOWN to context.getString(R.string.unknown),
                    ).associateBy(Pair<Int, *>::first),
                )
            }
            LibraryGroup.BY_SOURCE ->
                libraryManga
                    .map { it.manga.source }
                    .distinct()
                    .sorted()
                    .mapIndexed { index, sourceLong ->
                        sourceLong to (index to sourceManager.getOrStub(sourceLong).name)
                    }
                    .let(grouping::putAll)
            LibraryGroup.BY_TRACK_STATUS -> {
                grouping.putAll(
                    listOf(
                        TrackManager.READING to context.getString(R.string.reading),
                        TrackManager.REPEATING to context.getString(R.string.repeating),
                        TrackManager.PLAN_TO_READ to context.getString(R.string.plan_to_read),
                        TrackManager.PAUSED to context.getString(R.string.on_hold),
                        TrackManager.COMPLETED to context.getString(R.string.completed),
                        TrackManager.DROPPED to context.getString(R.string.dropped),
                        TrackManager.OTHER to context.getString(R.string.not_tracked),
                    ).associateBy(Pair<Int, *>::first),
                )
            }
        }
        val map: MutableMap<Int, MutableList<LibraryItem>> = mutableMapOf()

        when (groupType) {
            LibraryGroup.BY_TRACK_STATUS -> {
                val tracks = db.getTracks().executeAsBlocking().groupBy { it.manga_id }
                val statuses = loggedServices.associate {
                    it.id to it.getStatusList().associateWith(it::getStatus)
                }
                libraryManga.forEach { libraryItem ->
                    val status = tracks[libraryItem.manga.id]?.firstNotNullOfOrNull { track ->
                        statuses[track.sync_id]?.get(track.status)
                    } ?: "not tracked"
                    val group = grouping.values.find { (statusInt) ->
                        statusInt == (trackManager.trackMap[status] ?: TrackManager.OTHER)
                    }
                    if (group != null) {
                        map.getOrPut(group.first) { mutableListOf() } += libraryItem
                    } else {
                        map.getOrPut(7) { mutableListOf() } += libraryItem
                    }
                }
            }
            LibraryGroup.BY_SOURCE -> {
                libraryManga.forEach { libraryItem ->
                    val group = grouping[libraryItem.manga.source]
                    if (group != null) {
                        map.getOrPut(group.first) { mutableListOf() } += libraryItem
                    } else {
                        grouping.getOrPut(Int.MAX_VALUE) {
                            Int.MAX_VALUE to context.getString(R.string.unknown)
                        }
                        map.getOrPut(Int.MAX_VALUE) { mutableListOf() } += libraryItem
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
                            Int.MAX_VALUE to context.getString(R.string.unknown)
                        }
                        map.getOrPut(Int.MAX_VALUE) { mutableListOf() } += libraryItem
                    }
                }
            }
        }

        val categories = when (groupType) {
            LibraryGroup.BY_SOURCE -> grouping.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Pair<*, String>::second))
            LibraryGroup.BY_TRACK_STATUS, LibraryGroup.BY_STATUS -> grouping.values.filter { it.first in map.keys }
            else -> grouping.values
        }.map { (id, name) ->
            Category.create(name).also { it.id = id }
        }

        return map to categories
    }

    override fun onDestroy() {
        super.onDestroy()
        favoritesSync.onDestroy()
    }
    // SY <--
}
