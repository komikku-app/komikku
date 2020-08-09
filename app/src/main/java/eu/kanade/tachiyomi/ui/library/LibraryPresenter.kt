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
import eu.kanade.tachiyomi.source.model.Filter.TriState.Companion.STATE_EXCLUDE
import eu.kanade.tachiyomi.source.model.Filter.TriState.Companion.STATE_IGNORE
import eu.kanade.tachiyomi.source.model.Filter.TriState.Companion.STATE_INCLUDE
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.combineLatest
import eu.kanade.tachiyomi.util.lang.isNullOrUnsubscribed
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.removeCovers
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.favorites.FavoritesSyncHelper
import exh.util.isLewd
import exh.util.nullIfBlank
import java.util.Collections
import java.util.Comparator
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    // SY -->
    private val customMangaManager: CustomMangaManager = Injekt.get()
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

    private var groupType = preferences.groupLibraryBy().get()

    private val libraryIsGrouped
        get() = groupType != LibraryGroup.UNGROUPED

    private val loggedServices by lazy { Injekt.get<TrackManager>().services.filter { it.isLogged } }

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
                .combineLatest(filterTriggerRelay.observeOn(Schedulers.io())) { lib, _ ->
                    lib.copy(mangaMap = applyFilters(lib.mangaMap))
                }
                .combineLatest(sortTriggerRelay.observeOn(Schedulers.io())) { lib, _ ->
                    lib.copy(mangaMap = applySort(lib.mangaMap))
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeLatestCache({ view, (categories, mangaMap) ->
                    view.onNextLibraryUpdate(categories, mangaMap)
                })
        }
    }

    // SY -->
    /**
     * Applies library filters to the given map of manga.
     *
     * @param map the map to filter.
     */
    private fun applyFilters(map: LibraryMap): LibraryMap {
        val filterDownloaded = preferences.filterDownloaded().get()
        val filterDownloadedOnly = preferences.downloadedOnly().get()
        val filterUnread = preferences.filterUnread().get()
        val filterCompleted = preferences.filterCompleted().get()
        val filterTracked = preferences.filterTracked().get()
        val filterLewd = preferences.filterLewd().get()

        val filterFn: (LibraryItem) -> Boolean = f@{ item ->
            // Filter when there isn't unread chapters.
            if (filterUnread == STATE_INCLUDE && item.manga.unread == 0) {
                return@f false
            }
            if (filterUnread == STATE_EXCLUDE && item.manga.unread > 0) {
                return@f false
            }
            if (filterCompleted == STATE_INCLUDE && item.manga.status != SManga.COMPLETED) {
                return@f false
            }
            if (filterCompleted == STATE_EXCLUDE && item.manga.status == SManga.COMPLETED) {
                return@f false
            }
            if (filterTracked != STATE_IGNORE) {
                val tracks = db.getTracks(item.manga).executeAsBlocking()
                if (filterTracked == STATE_INCLUDE && tracks.isEmpty()) return@f false
                else if (filterTracked == STATE_EXCLUDE && tracks.isNotEmpty()) return@f false
            }
            if (filterLewd != STATE_IGNORE) {
                val isLewd = item.manga.isLewd()
                if (filterLewd == STATE_INCLUDE && !isLewd) return@f false
                else if (filterLewd == STATE_EXCLUDE && isLewd) return@f false
            }
            // Filter when there are no downloads.
            if (filterDownloaded != STATE_IGNORE || filterDownloadedOnly) {
                val isDownloaded = when {
                    item.manga.isLocal() -> true
                    item.downloadCount != -1 -> item.downloadCount > 0
                    else -> downloadManager.getDownloadCount(item.manga) > 0
                }
                return@f if (filterDownloaded == STATE_INCLUDE || filterDownloadedOnly) isDownloaded else !isDownloaded
            }
            true
        }

        return map.mapValues { entry -> entry.value.filter(filterFn) }
    }

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

        for ((_, itemList) in map) {
            for (item in itemList) {
                item.downloadCount = if (showDownloadBadges) {
                    downloadManager.getDownloadCount(item.manga)
                } else {
                    // Unset download count if not enabled
                    -1
                }

                item.unreadCount = if (showUnreadBadges) {
                    item.manga.unread
                } else {
                    // Unset unread count if not enabled
                    -1
                }
            }
        }
    }

    /**
     * Applies library sorting to the given map of manga.
     *
     * @param map the map to sort.
     */
    private fun applySort(map: LibraryMap): LibraryMap {
        val sortingMode = preferences.librarySortingMode().get()

        val lastReadManga by lazy {
            var counter = 0
            db.getLastReadManga().executeAsBlocking().associate { it.id!! to counter++ }
        }
        val totalChapterManga by lazy {
            var counter = 0
            db.getTotalChapterManga().executeAsBlocking().associate { it.id!! to counter++ }
        }
        val latestChapterManga by lazy {
            var counter = 0
            db.getLatestChapterManga().executeAsBlocking().associate { it.id!! to counter++ }
        }

        val sortFn: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            when (sortingMode) {
                LibrarySort.ALPHA -> i1.manga.title.compareTo(i2.manga.title, true)
                LibrarySort.LAST_READ -> {
                    // Get index of manga, set equal to list if size unknown.
                    val manga1LastRead = lastReadManga[i1.manga.id!!] ?: lastReadManga.size
                    val manga2LastRead = lastReadManga[i2.manga.id!!] ?: lastReadManga.size
                    manga1LastRead.compareTo(manga2LastRead)
                }
                LibrarySort.LAST_CHECKED -> i2.manga.last_update.compareTo(i1.manga.last_update)
                LibrarySort.UNREAD -> i1.manga.unread.compareTo(i2.manga.unread)
                LibrarySort.TOTAL -> {
                    val manga1TotalChapter = totalChapterManga[i1.manga.id!!] ?: 0
                    val mange2TotalChapter = totalChapterManga[i2.manga.id!!] ?: 0
                    manga1TotalChapter.compareTo(mange2TotalChapter)
                }
                LibrarySort.LATEST_CHAPTER -> {
                    val manga1latestChapter = latestChapterManga[i1.manga.id!!]
                        ?: latestChapterManga.size
                    val manga2latestChapter = latestChapterManga[i2.manga.id!!]
                        ?: latestChapterManga.size
                    manga1latestChapter.compareTo(manga2latestChapter)
                }
                LibrarySort.DATE_ADDED -> i2.manga.date_added.compareTo(i1.manga.date_added)
                // SY -->
                LibrarySort.DRAG_AND_DROP -> {
                    0
                }
                // SY <--
                else -> throw Exception("Unknown sorting mode")
            }
        }

        val comparator = if (preferences.librarySortingAscending().get()) {
            Comparator(sortFn)
        } else {
            Collections.reverseOrder(sortFn)
        }

        return map.mapValues { entry -> entry.value.sortedWith(comparator) }
    }

    /*private fun sortAlphabetical(i1: LibraryItem, i2: LibraryItem): Int {
        // return if (preferences.removeArticles().getOrDefault())
        return i1.manga.title.removeArticles().compareTo(i2.manga.title.removeArticles(), true)
        // else i1.manga.title.compareTo(i2.manga.title, true)
    }*/

    /**
     * Get the categories and all its manga from the database.
     *
     * @return an observable of the categories and its manga.
     */
    private fun getLibraryObservable(): Observable<Library> {
        return Observable.combineLatest(getCategoriesObservable(), getLibraryMangasObservable()) { dbCategories, libraryManga ->
            val categories = if (libraryManga.containsKey(0)) {
                arrayListOf(Category.createDefault()) + dbCategories
            } else {
                dbCategories
            }

            this.categories = categories
            Library(categories, libraryManga)
        }
    }

    // SY -->
    private fun applyGrouping(map: LibraryMap, categories: List<Category>): Pair<LibraryMap, List<Category>> {
        groupType = preferences.groupLibraryBy().get()
        var editedCategories: List<Category> = categories
        val libraryMangaAsList = map.flatMap { it.value }.distinctBy { it.manga.id }
        val items = if (groupType == LibraryGroup.BY_DEFAULT) {
            map
        } else if (!libraryIsGrouped) {
            editedCategories = listOf(Category.create("All").apply { this.id = 0 })
            libraryMangaAsList
                .groupBy { 0 }
        } else {
            val (items, customCategories) = getGroupedMangaItems(libraryMangaAsList)
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
        val libraryDisplayMode = preferences.libraryDisplayMode()
        return db.getLibraryMangas().asRxObservable()
            .map { list ->
                list.map { LibraryItem(it, libraryDisplayMode) }.groupBy { it.manga.category }
            }
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
     * Queues all unread chapters from the given list of manga.
     *
     * @param mangas the list of manga.
     */
    fun downloadUnreadChapters(mangas: List<Manga>) {
        mangas.forEach { manga ->
            launchIO {
                /* SY --> */ val chapters = if (manga.source == EH_SOURCE_ID || manga.source == EXH_SOURCE_ID) {
                    val chapter = db.getChapters(manga).executeAsBlocking().minBy { it.source_order }
                    if (chapter != null && !chapter.read) listOf(chapter) else emptyList()
                } else /* SY <-- */ db.getChapters(manga).executeAsBlocking()
                    .filter { !it.read }

                downloadManager.downloadChapters(manga, chapters)
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
            val mangaJson = manga.id?.let {
                CustomMangaManager.MangaJson(
                    it,
                    editedTitle.nullIfBlank(),
                    (if (manga.author != manga.originalAuthor) manga.author else null),
                    (if (manga.artist != manga.originalArtist) manga.artist else null),
                    (if (manga.description != manga.originalDescription) manga.description else null),
                    (if (manga.genre != manga.originalGenre) manga.getGenres()?.toTypedArray() else null)
                )
            }
            mangaJson?.let {
                customMangaManager.saveMangaInfo(it)
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
                val chapters = db.getChapters(manga).executeAsBlocking()
                chapters.forEach {
                    it.read = read
                    if (!read) {
                        it.last_page_read = 0
                    }
                }
                db.updateChaptersProgress(chapters).executeAsBlocking()

                if (preferences.removeAfterMarkedAsRead()) {
                    deleteChapters(manga, chapters)
                }
            }
        }
    }

    private fun deleteChapters(manga: Manga, chapters: List<Chapter>) {
        sourceManager.get(manga.source)?.let { source ->
            downloadManager.deleteChapters(chapters, manga, source)
        }
    }

    /**
     * Remove the selected manga from the library.
     *
     * @param mangas the list of manga to delete.
     * @param deleteChapters whether to also delete downloaded chapters.
     */
    fun removeMangaFromLibrary(mangas: List<Manga>, deleteChapters: Boolean) {
        launchIO {
            val mangaToDelete = mangas.distinctBy { it.id }

            mangaToDelete.forEach {
                it.favorite = false
                it.removeCovers(coverCache)
            }
            db.insertMangas(mangaToDelete).executeAsBlocking()

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        downloadManager.deleteManga(manga, source)
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
            for (cat in categories) {
                mc.add(MangaCategory.create(manga, cat))
            }
        }

        db.setMangaCategories(mc, mangas)
    }

    // SY -->
    /** Returns first unread chapter of a manga */
    fun getFirstUnread(manga: Manga): Chapter? {
        val chapters = db.getChapters(manga).executeAsBlocking()
        return if (manga.source == EH_SOURCE_ID || manga.source == EXH_SOURCE_ID) {
            val chapter = chapters.sortedBy { it.source_order }.getOrNull(0)
            if (chapter?.read == false) chapter else null
        } else {
            chapters.sortedByDescending { it.source_order }.find { !it.read }
        }
    }

    private fun getGroupedMangaItems(libraryManga: List<LibraryItem>): Pair<LibraryMap, List<Category>> {
        val grouping: MutableList<Triple<String, Int, String>> = mutableListOf()
        when (groupType) {
            LibraryGroup.BY_STATUS -> libraryManga.distinctBy { it.manga.status }.map { it.manga.status }.forEachIndexed { index, status ->
                grouping += Triple(status.toString(), index, mapStatus(status))
            }
            LibraryGroup.BY_SOURCE -> libraryManga.distinctBy { it.manga.source }.map { it.manga.source }.forEachIndexed { index, sourceLong ->
                grouping += Triple(sourceLong.toString(), index, sourceManager.getOrStub(sourceLong).name)
            }
            LibraryGroup.BY_TRACK_STATUS -> {
                grouping += Triple("1", 1, context.getString(R.string.reading))
                grouping += Triple("2", 2, context.getString(R.string.repeating))
                grouping += Triple("3", 3, context.getString(R.string.plan_to_read))
                grouping += Triple("4", 4, context.getString(R.string.on_hold))
                grouping += Triple("5", 5, context.getString(R.string.completed))
                grouping += Triple("6", 6, context.getString(R.string.dropped))
                grouping += Triple("7", 7, context.getString(R.string.not_tracked))
            }
        }
        val map: MutableMap<Int, MutableList<LibraryItem>> = mutableMapOf()

        libraryManga.forEach { libraryItem ->
            when (groupType) {
                LibraryGroup.BY_TRACK_STATUS -> {
                    val status: String = {
                        val tracks = db.getTracks(libraryItem.manga).executeAsBlocking()
                        val track = tracks.find { track ->
                            loggedServices.any { it.id == track?.sync_id }
                        }
                        val service = loggedServices.find { it.id == track?.sync_id }
                        if (track != null && service != null) {
                            service.getStatus(track.status)
                        } else {
                            "not tracked"
                        }
                    }()
                    val group = grouping.find { it.first == mapTrackingOrder(status) }
                    if (group != null) {
                        map[group.second]?.plusAssign(libraryItem) ?: map.put(group.second, mutableListOf(libraryItem))
                    } else {
                        map[7]?.plusAssign(libraryItem) ?: map.put(7, mutableListOf(libraryItem))
                    }
                }
                LibraryGroup.BY_SOURCE -> {
                    val group = grouping.find { it.first.toLongOrNull() == libraryItem.manga.source }
                    if (group != null) {
                        map[group.second]?.plusAssign(libraryItem) ?: map.put(group.second, mutableListOf(libraryItem))
                    } else {
                        if (grouping.all { it.second != Int.MAX_VALUE }) grouping += Triple(Int.MAX_VALUE.toString(), Int.MAX_VALUE, context.getString(R.string.unknown))
                        map[Int.MAX_VALUE]?.plusAssign(libraryItem) ?: map.put(Int.MAX_VALUE, mutableListOf(libraryItem))
                    }
                }
                else -> {
                    val group = grouping.find { it.first == libraryItem.manga.status.toString() }
                    if (group != null) {
                        map[group.second]?.plusAssign(libraryItem) ?: map.put(group.second, mutableListOf(libraryItem))
                    } else {
                        if (grouping.all { it.second != Int.MAX_VALUE }) grouping += Triple(Int.MAX_VALUE.toString(), Int.MAX_VALUE, context.getString(R.string.unknown))
                        map[Int.MAX_VALUE]?.plusAssign(libraryItem) ?: map.put(Int.MAX_VALUE, mutableListOf(libraryItem))
                    }
                }
            }
        }

        val categories = (
            when (groupType) {
                LibraryGroup.BY_SOURCE -> grouping.sortedBy { it.third.toLowerCase() }
                LibraryGroup.BY_TRACK_STATUS -> grouping.filter { it.second in map.keys }
                else -> grouping
            }
            ).map {
            val category = Category.create(it.third)
            category.id = it.second
            category
        }

        return map to categories
    }

    private fun mapTrackingOrder(status: String): String {
        with(context) {
            return when (status) {
                getString(R.string.reading), getString(R.string.currently_reading) -> "1"
                getString(R.string.repeating) -> "2"
                getString(R.string.plan_to_read), getString(R.string.want_to_read) -> "3"
                getString(R.string.on_hold), getString(R.string.paused) -> "4"
                getString(R.string.completed) -> "5"
                getString(R.string.dropped) -> "6"
                else -> "7"
            }
        }
    }

    private fun mapStatus(status: Int): String {
        return context.getString(
            when (status) {
                SManga.LICENSED -> R.string.licensed
                SManga.ONGOING -> R.string.ongoing
                SManga.COMPLETED -> R.string.completed
                else -> R.string.unknown
            }
        )
    }
    // SY <--
}
