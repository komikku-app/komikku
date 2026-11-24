package app.komikku.desktop.ui.reader

import android.app.Application
import app.komikku.shared.reader.ReaderChapterList
import app.komikku.shared.reader.ReaderProgressChapter
import app.komikku.shared.reader.ReaderProgressSaver
import app.komikku.shared.reader.ReaderViewerItems
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderItem
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The desktop reader's state holder (plain class + [StateFlow]), the counterpart of Android's
 * `ReaderViewModel` built from the same parts: the borrowed `ChapterLoader` / page loaders /
 * `ReaderChapter` models, the shared [ReaderChapterList] (which chapters, in which order) and
 * [ReaderProgressSaver] (last page, read flag, duplicates, history, bookmarks).
 *
 * The viewers show [State.items], built like Android's viewer adapters ([ReaderViewerItems], P11): the previous
 * chapter's pages once loaded, the "previous chapter" transition, the current chapter's pages, the "next chapter"
 * transition and the next chapter's pages once loaded. The next chapter is preloaded within the last five pages, a
 * neighbour also when its transition is reached (Android `requestPreloadChapter`); a page of a neighbour becomes the
 * current chapter when it is reached (Android `loadNewChapter`: history of the left chapter saved, read timer
 * restarted). Moving past a transition whose neighbour is not loaded yet loads it.
 * Navigation (P11 review fixes): the current item is the one source of truth for what is shown; every state change goes
 * through [commit] (re-anchored by item identity, normalised to the start of its spread, progress / preload / chapter
 * switch for a newly shown spread), chapter switches are serialised and follow the item the viewer is on when they run,
 * and the paged viewer's spreads ([State.spreads]) serve page turns both ways, progress and preloading alike.
 * Downloads (8.1, Android rules): "download ahead" (`autoDownloadWhileReading`) once a downloaded chapter is
 * past 25 % and the next one is downloaded too, and "delete after reading" (`removeAfterReadSlots`):
 * completed chapters are queued with `DownloadManager.enqueueChaptersToDelete` and deleted on [close].
 * Not ported: merged / E-Hentai specifics, sync triggers, page actions (save / share / set as cover).
 * Trackers: see [ReaderProgressSaver]'s hook.
 */
class ReaderModel(
    private val mangaId: Long,
    private val initialChapterId: Long,
    private val initialPage: Int? = null,
    private val app: Application = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val getIncognitoState: GetIncognitoState = Injekt.get(),
    private val setMangaViewerFlags: SetMangaViewerFlags = Injekt.get(),
    private val progressSaver: ReaderProgressSaver = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
) {

    data class State(
        val manga: Manga? = null,
        val viewerChapters: ViewerChapters? = null,
        /** The neighbouring chapters' pages and transitions around the current chapter's pages ([ReaderViewerItems]). */
        val items: List<ReaderItem> = emptyList(),
        /**
         * The current item, the single source of truth for what is shown: always the first item of its spread
         * ([spreads]); the current chapter follows it ([currentChapter] is switched to the chapter of its page).
         */
        val currentIndex: Int = 0,
        val readingMode: ReadingMode = ReadingMode.RIGHT_TO_LEFT,
        val orientation: ReaderOrientation = ReaderOrientation.FREE,
        val bookmarked: Boolean = false,
        val isLoading: Boolean = true,
        val isLoadingAdjacent: Boolean = false,
        val error: Throwable? = null,
        val incognito: Boolean = false,
        /**
         * Changed when the position is set by the reader (a chapter opened, a chapter or page jump) rather than reached
         * by the viewer, so that the webtoon list scrolls there; the viewers keep their place on other item changes.
         */
        val jump: Int = 0,
        /** Pages known to be wide (width > height, Android `ImageUtil.isWideImage`), by [pageKey]; P11. */
        val widePages: Set<String> = emptySet(),
        /** Wide pages shown as two halves (Android "Split wide pages"), by [pageKey]; the second half is an [InsertPage]. */
        val splitPages: Set<String> = emptySet(),
        /** The paged viewer shows double pages (the layout as resolved by the screen for its window; P11). */
        val doublePages: Boolean = false,
    ) {
        fun isWide(page: ReaderPage): Boolean = pageKey(page) in widePages

        /**
         * What the viewer shows, item by item: with [doublePages] two neighbouring pages of the same chapter, paired
         * from the chapter's first page and again after every page shown alone (a wide page, a split half; SY's double
         * pages); else one item each. The one rule for page turns both ways, progress and preloading.
         */
        val spreads: List<IntRange> by lazy { ReaderViewerItems.spreads(items, doublePages, ::pairable) }

        private fun pairable(page: ReaderPage) = page !is InsertPage && !isWide(page) && pageKey(page) !in splitPages

        /** The position in [spreads] of the spread holding [index]. */
        fun spreadIndexOf(index: Int): Int = ReaderViewerItems.spreadIndexOf(spreads, index)

        /** The pages shown now (empty on a transition), in reading order. */
        val shownPages: List<ReaderPage>
            get() = spreads.getOrNull(spreadIndexOf(currentIndex))?.mapNotNull { items[it] as? ReaderPage }.orEmpty()

        val currentChapter: ReaderChapter? get() = viewerChapters?.currChapter
        val pages: List<ReaderPage> get() = currentChapter?.pages.orEmpty()
        val currentItem: ReaderItem? get() = items.getOrNull(currentIndex)

        /** The index in [items] of the current chapter's page [pageIndex] (0-based), `-1` when not there. */
        fun indexOfPage(pageIndex: Int): Int {
            val chapter = currentChapter ?: return -1
            return items.indexOfFirst { it is ReaderPage && it !is InsertPage && it.chapter == chapter && it.index == pageIndex }
        }

        /** 1-based page number shown in the UI (the nearest page on a transition item). */
        val currentPageNumber: Int
            get() = when (val item = currentItem) {
                is ReaderPage -> item.number
                is ChapterTransition.Prev -> 1
                is ChapterTransition.Next -> pages.size
                null -> 0
            }

        /**
         * The pages the paged viewer loads and decodes ahead (P11): those of the spread before and of the two after
         * the shown one, each with the width its slot will have in a viewer [viewportWidth] wide (the shown slot's
         * rule), the image page of a second half, never a page that is shown now.
         */
        fun preloadPages(viewportWidth: Int): List<Pair<ReaderPage, Int>> {
            val at = spreadIndexOf(currentIndex)
            if (at < 0) return emptyList()
            val shown = shownPages.map(::imagePage).toSet()
            return listOf(at - 1, at + 1, at + 2)
                .mapNotNull { spreads.getOrNull(it) }
                .flatMap { spread ->
                    val pages = spread.mapNotNull { items[it] as? ReaderPage }
                    pages.map { imagePage(it) to slotDecodeWidth(viewportWidth, pages.size) }
                }
                .filter { (page, _) -> page !in shown }
                .distinctBy { it.first }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Database writes (progress, bookmarks): one at a time and in order, and not cancelled by [close]
     * (Android: `launchNonCancellable` on the view model scope).
     */
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        progressScope.launch {
            for (write in writes) {
                try {
                    write()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Could not save reading progress" }
                }
            }
        }
    }

    private fun enqueueWrite(block: suspend () -> Unit) {
        writes.trySend(block)
    }
    private val mutex = Mutex()

    /**
     * The page-list loads in flight, one per chapter (single flight): the initial load, a preload, an adjacent chapter
     * opened and a chapter switch all go through [loadPages] and join the same load, so a chapter never gets a second
     * page list while the items hold the pages of the first one (P12 review).
     */
    private val chapterLoads = mutableMapOf<ReaderChapter, Deferred<Unit>>()

    /** How many callers joined a load already in flight (tests). */
    internal val joinedChapterLoads = MutableStateFlow(0)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var loader: ChapterLoader
    private var chapterList: List<ReaderChapter> = emptyList()
    private val progress = mutableMapOf<Long, ReaderProgressChapter>()

    /** Loads the manga, its reader chapter list and the initial chapter. Call once. */
    suspend fun init(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val manga = getManga.await(mangaId) ?: error("Manga $mangaId not found")
            sourceManager.isInitialized.first { it }
            val source = sourceManager.getOrStub(manga.source)
            val incognito = getIncognitoState.await(manga.source)
            val options = ReaderChapterList.Options(
                skipRead = readerPreferences.skipRead().get(),
                skipFiltered = readerPreferences.skipFiltered().get(),
                skipDupe = readerPreferences.skipDupe().get(),
                downloadedOnly = basePreferences.downloadedOnly().get(),
            )
            val chapters = ReaderChapterList.build(
                manga = manga,
                chapters = getChaptersByMangaId.await(manga.id, applyFilter = true),
                selectedChapterId = initialChapterId,
                options = options,
                isDownloaded = { chapter ->
                    manga.isLocal() ||
                        downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.ogTitle, manga.source)
                },
            )
            chapterList = chapters.map(::ReaderChapter)
            loader = ChapterLoader(
                context = app,
                downloadManager = downloadManager,
                downloadProvider = downloadProvider,
                manga = manga,
                source = source,
                sourceManager = sourceManager,
                readerPrefs = readerPreferences,
                mergedReferences = emptyList(),
                mergedManga = null,
            )
            _state.update {
                it.copy(
                    manga = manga,
                    incognito = incognito,
                    readingMode = resolveReadingMode(manga),
                    orientation = ReaderOrientation.fromPreference(manga.readerOrientation.toInt()),
                )
            }
            progressSaver.restartReadTimer()
            scope.launch {
                combine(readerPreferences.dualPageSplitPaged().changes(), readerPreferences.dualPageRotateToFit().changes()) { _, _ -> }
                    .drop(1)
                    .collect { resplitPages() }
            }
            val initial = chapterList.first { it.chapter.id == initialChapterId }
            loadChapter(initial, initialPage, ChapterStart.Requested)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            _state.update { it.copy(isLoading = false, error = e) }
            Result.failure(e)
        }
    }

    private fun resolveReadingMode(manga: Manga): ReadingMode {
        val mode = ReadingMode.fromPreference(manga.readingMode.toInt())
        return if (mode == ReadingMode.DEFAULT) {
            ReadingMode.fromPreference(readerPreferences.defaultReadingMode().get()).takeIf { it != ReadingMode.DEFAULT }
                ?: ReadingMode.RIGHT_TO_LEFT
        } else {
            mode
        }
    }

    private enum class ChapterStart { Requested, First, Last }

    /**
     * Every change of [State] goes through here: [transform] is applied atomically, the result normalised (the
     * current index on the first item of its spread) and, when what is shown changed, [onShown] runs for it. This keeps
     * the current item the single source of truth for progress, preloading and chapter switches.
     */
    private fun commit(transform: (State) -> State) {
        var before = _state.value
        var after = before
        _state.update { state ->
            before = state
            normalised(transform(state)).also { after = it }
        }
        if (shownKey(before) != shownKey(after)) onShown(after)
    }

    private fun normalised(state: State): State {
        if (state.items.isEmpty()) return if (state.currentIndex == 0) state else state.copy(currentIndex = 0)
        val clamped = state.currentIndex.coerceIn(0, state.items.lastIndex)
        val start = state.spreads.getOrNull(state.spreadIndexOf(clamped))?.first ?: clamped
        return if (start == state.currentIndex) state else state.copy(currentIndex = start)
    }

    /**
     * What is shown: the first item (pages by identity, transitions by their pair of chapters) and how many pages; a
     * change means another spread is shown.
     */
    private fun shownKey(state: State): Pair<ReaderItem?, Int> = state.currentItem to state.shownPages.size

    /** The new shown spread (Android `PagerViewer.onPageChange` / `WebtoonViewer.onPageSelected`). */
    private fun onShown(state: State) {
        when (val item = state.currentItem) {
            is ReaderPage -> {
                // Android: the second half of a split page does not change the progress; a two-page spread reports its
                // first page with the extra page (SY), so that the chapter's last page counts as read
                if (item !is InsertPage) onPageSelected(item, hasExtraPage = state.shownPages.size == 2)
                if (item.chapter != state.currentChapter) requestChapterSync()
            }
            is ChapterTransition -> item.to?.let { preload(it) }
            null -> {}
        }
    }

    /**
     * The load of [chapter]'s page list ([ChapterLoader.loadChapter], which returns at once for a loaded chapter): the
     * one in flight when there is one, else a new one, registered before it starts. [page] counts only for a new load
     * (only the first load of the opened chapter passes one).
     */
    private fun loadPages(chapter: ReaderChapter, page: Int? = null): Deferred<Unit> = synchronized(chapterLoads) {
        chapterLoads[chapter]?.let { running ->
            joinedChapterLoads.update { it + 1 }
            return running
        }
        val load = scope.async(start = CoroutineStart.LAZY) { loader.loadChapter(chapter, page) }
        chapterLoads[chapter] = load
        load.invokeOnCompletion {
            synchronized(chapterLoads) { if (chapterLoads[chapter] === load) chapterLoads.remove(chapter) }
        }
        load.start()
        load
    }

    private fun isLoadingPages(chapter: ReaderChapter): Boolean = synchronized(chapterLoads) { chapter in chapterLoads }

    private suspend fun loadChapter(chapter: ReaderChapter, page: Int?, start: ChapterStart) = mutex.withLock {
        loadPages(chapter, page).await()
        val newChapters = viewerChaptersOf(chapter)
        var replaced: ViewerChapters? = null
        commit { state ->
            replaced = state.viewerChapters
            val items = itemsFor(newChapters, state.splitPages, forceTransition = alwaysShowTransition())
            val index = when (start) {
                ChapterStart.Requested -> {
                    val pages = chapter.pages.orEmpty()
                    ReaderViewerItems.indexOf(items, pages.getOrNull(chapter.requestedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))))
                }
                ChapterStart.First -> items.indexOfFirst { it is ReaderPage && it.chapter == chapter }
                // The chapter's last item: the second half of a split last page, the last spread of double pages
                ChapterStart.Last -> items.indexOfLast { it is ReaderPage && it.chapter == chapter }
            }
            state.copy(
                viewerChapters = newChapters,
                items = items,
                currentIndex = index.coerceAtLeast(0),
                bookmarked = chapter.chapter.bookmark,
                isLoading = false,
                error = null,
                jump = state.jump + 1,
            )
        }
        // Android: add the new references first to avoid recycling a chapter that stays
        newChapters.ref()
        replaced?.unref()
    }

    private fun viewerChaptersOf(chapter: ReaderChapter): ViewerChapters {
        val pos = chapterList.indexOf(chapter)
        return ViewerChapters(chapter, chapterList.getOrNull(pos - 1), chapterList.getOrNull(pos + 1))
    }

    /** Android's viewers: `alwaysShowChapterTransition`, or the viewer is on a transition (it stays while it is shown). */
    private fun forceTransition(current: ReaderItem?): Boolean =
        current is ChapterTransition || alwaysShowTransition()

    private fun alwaysShowTransition(): Boolean = readerPreferences.alwaysShowChapterTransition().get()

    /** The items for [chapters] ([ReaderViewerItems.build]) with the second half of each of the [split] pages after it. */
    private fun itemsFor(chapters: ViewerChapters, split: Set<String>, forceTransition: Boolean): List<ReaderItem> =
        withSplitPages(ReaderViewerItems.build(chapters, forceTransition), split)

    /** [state] with its items rebuilt for its chapters and split pages, on the same current item. */
    private fun rebuilt(state: State): State {
        val chapters = state.viewerChapters ?: return state
        val current = state.currentItem
        val items = itemsFor(chapters, state.splitPages, forceTransition(current))
        return state.copy(items = items, currentIndex = anchorIndex(items, current) ?: state.currentIndex)
    }

    /** Rebuilds [State.items] for the current chapters (a neighbour was loaded), staying on the current item. */
    private fun refreshItems() = commit(::rebuilt)

    /** Switches requested and not finished yet ([requestChapterSync]). */
    private val pendingSyncs = MutableStateFlow(0)

    /**
     * The current page belongs to a neighbouring chapter: make that chapter the current one ([syncChapter]). Switches
     * run one at a time, in order, under the same lock as chapter loads.
     */
    private fun requestChapterSync() {
        pendingSyncs.update { it + 1 }
        scope.launch {
            try {
                mutex.withLock { syncChapter() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                _state.update { it.copy(error = e) }
            } finally {
                pendingSyncs.update { it - 1 }
            }
        }
    }

    /**
     * Android `loadNewChapter`: the chapter of the page the viewer is on **now** becomes the current one (history of
     * the left chapter saved, read timer restarted, the items rebuilt around that page; the viewer stays on it). Read
     * again after each step, so that pages reached meanwhile (next, then back) end on the right chapter; nothing when
     * the viewer is on a page of the current chapter or on a transition.
     */
    private suspend fun syncChapter() {
        while (true) {
            val target = (_state.value.currentItem as? ReaderPage)?.chapter ?: return
            val left = _state.value.currentChapter ?: return
            if (target == left || target !in chapterList) return
            loadPages(target).await()
            val newChapters = viewerChaptersOf(target)
            var replaced: ViewerChapters? = null
            commit { state ->
                replaced = null
                val current = state.currentItem as? ReaderPage
                // The viewer moved on to another chapter (or a transition) meanwhile: check again
                if (current?.chapter != target || state.currentChapter != left) return@commit state
                replaced = state.viewerChapters
                val items = itemsFor(newChapters, state.splitPages, forceTransition(current))
                state.copy(
                    viewerChapters = newChapters,
                    items = items,
                    currentIndex = anchorIndex(items, current) ?: 0,
                    bookmarked = target.chapter.bookmark,
                )
            }
            // Applied, unless the viewer moved on meanwhile (then check again)
            val old = replaced ?: continue
            newChapters.ref()
            old.unref()
            progressSaver.saveHistory(left.chapter.id!!, _state.value.incognito)
            progressSaver.restartReadTimer()
            _state.value.shownPages.firstOrNull()?.let(::preloadIfInRange)
        }
    }

    /** Called when [index] of [State.items] becomes the current item (page turn, scroll, slider). */
    fun moveTo(index: Int) = commit { it.copy(currentIndex = index) }

    /** Moves to [item] if it is in [State.items] (the webtoon list reports the item it shows on top). */
    fun moveToItem(item: ReaderItem) = commit { state ->
        val index = ReaderViewerItems.indexOf(state.items, item)
        if (index >= 0) state.copy(currentIndex = index) else state
    }

    /** Moves to the current chapter's page [pageIndex] (0-based; slider, first / last page) and lets the viewers follow. */
    fun moveToPage(pageIndex: Int) = commit { state ->
        val index = state.indexOfPage(pageIndex)
        if (index >= 0) state.copy(currentIndex = index, jump = state.jump + 1) else state
    }

    /** Sets the page layout the screen resolved (double pages in a paged mode; P11). */
    fun setDoublePages(doublePages: Boolean) = commit { it.copy(doublePages = doublePages) }

    /**
     * The next spread ([State.spreads]). On the last item: past a "next chapter" transition whose chapter is not
     * loaded, opens it; on a page (of a neighbour whose switch is pending), waits for the switch and turns again.
     */
    fun next() = turn(forward = true)

    /** The previous spread; at the start like [next], opening the previous chapter on its last item. */
    fun previous() = turn(forward = false)

    private fun turn(forward: Boolean) {
        if (_state.value.isLoadingAdjacent) return
        var atEnd = false
        commit { state ->
            val at = state.spreadIndexOf(state.currentIndex)
            val target = state.spreads.getOrNull(if (forward) at + 1 else at - 1)
            atEnd = target == null
            if (target == null) state else state.copy(currentIndex = target.first)
        }
        if (atEnd) onEndReached(forward)
    }

    private fun onEndReached(forward: Boolean) {
        val state = _state.value
        when (val item = state.currentItem) {
            is ChapterTransition -> {
                val to = item.to ?: return
                // Only a transition to a chapter whose pages are not in the items yet loads it
                if (state.items.none { it is ReaderPage && it.chapter == to }) {
                    loadAdjacent(to, if (forward) ChapterStart.First else ChapterStart.Last)
                }
            }
            is ReaderPage -> if (pendingSyncs.value > 0) {
                scope.launch {
                    pendingSyncs.first { it == 0 }
                    if (_state.value.currentItem === item) turn(forward)
                }
            }
            null -> {}
        }
    }

    fun moveToFirstPage() = moveToPage(0)

    fun moveToLastPage() = moveToPage(_state.value.pages.lastIndex)

    /** Toolbar / N key: open the next chapter where its reading position is (Android: `loadNextChapter`). */
    fun loadNextChapter() {
        _state.value.viewerChapters?.nextChapter?.let { loadAdjacent(it, ChapterStart.Requested) }
    }

    fun loadPreviousChapter() {
        _state.value.viewerChapters?.prevChapter?.let { loadAdjacent(it, ChapterStart.Requested) }
    }

    private fun loadAdjacent(chapter: ReaderChapter, start: ChapterStart) {
        _state.update { it.copy(isLoadingAdjacent = true) }
        scope.launch {
            try {
                val current = _state.value.currentChapter
                if (current != null) progressSaver.saveHistory(current.chapter.id!!, _state.value.incognito)
                progressSaver.restartReadTimer()
                // A preload of this chapter still running is joined (loadPages), never loaded a second time
                loadChapter(chapter, null, start)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                _state.update { it.copy(error = e) }
            } finally {
                _state.update { it.copy(isLoadingAdjacent = false) }
            }
        }
    }

    /** Android's viewers: within the last five pages of the current chapter, preload the next one. */
    private fun preloadIfInRange(page: ReaderPage) {
        val pages = page.chapter.pages ?: return
        val viewerChapters = _state.value.viewerChapters ?: return
        if (page.chapter != viewerChapters.currChapter || pages.size - page.number >= PRELOAD_PAGES) return
        viewerChapters.nextChapter?.let(::preload)
    }

    /**
     * Loads a neighbouring chapter's page list in the background (Android: `preload`), then adds its pages to the
     * items (Android: `ReloadViewerChapters`).
     */
    private fun preload(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded || isLoadingPages(chapter)) return
        // Registered now, before anything runs, so that an adjacent load arriving next joins it
        val load = loadPages(chapter)
        scope.launch {
            try {
                load.await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // shown on the transition item through chapter.stateFlow
                return@launch
            }
            refreshItems()
        }
    }

    /**
     * [page] (the first of the shown pages) became the current page; [hasExtraPage]: a second page is shown next to it
     * (Android's pager: the chapter is read when that one is its last page).
     */
    private fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean = false) {
        val readerChapter = page.chapter
        val pages = readerChapter.pages ?: return
        readerChapter.requestedPage = page.index
        val progressChapter = progressOf(readerChapter)
        val manga = _state.value.manga ?: return
        if (page.number.toDouble() / pages.size > 0.25) downloadNextChapters(manga)
        preloadIfInRange(page)
        enqueueWrite {
            run {
                val completed = progressSaver.onPageSelected(
                    manga = manga,
                    chapter = progressChapter,
                    pageIndex = page.index,
                    pageCount = pages.size,
                    incognito = _state.value.incognito,
                    pageFailed = page.status is Page.State.Error,
                    hasExtraPage = hasExtraPage,
                )
                readerChapter.chapter.read = progressChapter.read
                readerChapter.chapter.last_page_read = progressChapter.lastPageRead.toInt()
                // Android `updateChapterProgressOnComplete`: every time the last page is reached
                if (completed) deleteChapterIfNeeded(manga, readerChapter)
            }
        }
    }

    /** Android `ReaderViewModel.deleteChapterIfNeeded` + `enqueueDeleteReadChapters`. */
    private suspend fun deleteChapterIfNeeded(manga: Manga, currentChapter: ReaderChapter) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots().get()
        if (removeAfterReadSlots == -1) return
        val position = chapterList.indexOf(currentChapter)
        val chapterToDelete = chapterList.getOrNull(position - removeAfterReadSlots) ?: return
        if (!chapterToDelete.chapter.read) return
        downloadManager.enqueueChaptersToDelete(listOf(chapterToDelete.chapter.toDomainChapter()!!), manga)
    }

    /** Next chapter whose download-ahead was requested (Android re-requests on every page; this skips repeats). */
    @Volatile
    private var downloadAheadRequestedFor: Long? = null

    @Volatile
    private var downloadAheadJob: Job? = null

    /**
     * Android `ReaderViewModel.downloadNextChapters`. Re-evaluated on each page past 25 % until the request
     * was made: a check that fails (current / next chapter not downloaded yet) is retried on the next page.
     */
    private fun downloadNextChapters(manga: Manga) {
        val amount = downloadPreferences.autoDownloadWhileReading().get()
        if (amount == 0 || manga.isLocal()) return
        val current = _state.value.currentChapter ?: return
        val nextChapter = _state.value.viewerChapters?.nextChapter?.chapter ?: return
        if (downloadAheadRequestedFor == nextChapter.id || downloadAheadJob?.isActive == true) return
        downloadAheadJob = scope.launch(Dispatchers.IO) {
            // Only download ahead if current + next chapter is already downloaded too to avoid jank
            // (Android: the current chapter's loader is a `DownloadPageLoader`, internal to its file).
            val currentChapter = current.chapter
            val isCurrentDownloaded = downloadManager.isChapterDownloaded(
                currentChapter.name,
                currentChapter.scanlator,
                currentChapter.url,
                manga.ogTitle,
                manga.source,
            )
            if (!isCurrentDownloaded) return@launch
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                nextChapter.url,
                manga.ogTitle,
                manga.source,
            )
            if (!isNextChapterDownloaded) return@launch
            val chapters = getNextChapters.await(manga.id, nextChapter.id!!).run {
                if (readerPreferences.skipDupe().get()) removeDuplicates(nextChapter.toDomainChapter()!!) else this
            }.take(amount)
            downloadManager.downloadChapters(manga, chapters)
            downloadAheadRequestedFor = nextChapter.id
        }
    }

    private fun progressOf(chapter: ReaderChapter): ReaderProgressChapter {
        val db = chapter.chapter
        return progress.getOrPut(db.id!!) {
            ReaderProgressChapter(
                id = db.id!!,
                mangaId = db.manga_id!!,
                sourceOrder = db.source_order.toLong(),
                chapterNumber = db.chapter_number.toDouble(),
                read = db.read,
                lastPageRead = db.last_page_read.toLong(),
                bookmark = db.bookmark,
            )
        }
    }

    fun toggleBookmark() {
        val chapter = _state.value.currentChapter ?: return
        val progressChapter = progressOf(chapter)
        val bookmarked = !progressChapter.bookmark
        chapter.chapter.bookmark = bookmarked
        _state.update { it.copy(bookmarked = bookmarked) }
        enqueueWrite { progressSaver.setBookmark(progressChapter, bookmarked) }
    }

    /** Stores the manga's reading mode in its viewer flags (Android: `setMangaReadingMode`). */
    fun setReadingMode(mode: ReadingMode) {
        val manga = _state.value.manga ?: return
        scope.launch {
            setMangaViewerFlags.awaitSetReadingMode(manga.id, mode.flagValue.toLong())
            val updated = getManga.await(manga.id) ?: return@launch
            _state.update { it.copy(manga = updated, readingMode = resolveReadingMode(updated)) }
            // Split halves are a paged-viewer item (Android reloads the viewer)
            resplitPages()
        }
    }

    // Wide pages (P11): what the paged viewer learns from decoded pages, Android `PagerPageHolder.process`

    /** Second halves of split pages by their page's key (kept so a rebuilt item list keeps its position). */
    private val insertPages = java.util.concurrent.ConcurrentHashMap<String, InsertPage>()

    /**
     * The viewer decoded [page] at [width] × [height] (its source size): a wide page is remembered (double pages show
     * it alone) and, with "Split wide pages" in a paged mode (and not "Rotate wide pages to fit", which wins like in
     * Android's `process`), split: its second half follows it as an [InsertPage] (Android `onPageSplit`).
     */
    fun onPageSize(page: ReaderPage, width: Int, height: Int) {
        if (page is InsertPage || width <= height) return
        val key = pageKey(page)
        val split = splitWidePages()
        val state = _state.value
        if (key in state.widePages && (!split || key in state.splitPages)) return
        // One change: the spreads and (when split) the items are rebuilt on the same current item
        commit {
            val updated = it.copy(widePages = it.widePages + key, splitPages = if (split) it.splitPages + key else it.splitPages)
            if (split) rebuilt(updated) else updated
        }
    }

    private fun splitWidePages(): Boolean =
        readerPreferences.dualPageSplitPaged().get() &&
            !readerPreferences.dualPageRotateToFit().get() &&
            _state.value.readingMode.type != ReadingMode.ViewerType.Webtoon

    /**
     * Android reloads the pager when "Split wide pages" (or the viewer) changes: the known wide pages are split or
     * joined again according to the current settings, on the same page (a second half that goes away: its page).
     */
    private fun resplitPages() {
        val split = if (splitWidePages()) _state.value.widePages else emptySet()
        if (split == _state.value.splitPages) return
        commit { rebuilt(it.copy(splitPages = split)) }
    }

    /** [items] with the second half of each of the [split] pages after it. */
    private fun withSplitPages(items: List<ReaderItem>, split: Set<String>): List<ReaderItem> {
        if (split.isEmpty()) return items
        return buildList {
            items.forEach { item ->
                add(item)
                if (item is ReaderPage && item !is InsertPage && pageKey(item) in split) {
                    val key = pageKey(item)
                    add(insertPages[key]?.takeIf { it.parent === item } ?: InsertPage(item).also { insertPages[key] = it })
                }
            }
        }
    }

    /** Stores the manga's orientation flag like Android (no effect on a desktop window). */
    fun setOrientation(orientation: ReaderOrientation) {
        val manga = _state.value.manga ?: return
        scope.launch {
            setMangaViewerFlags.awaitSetOrientation(manga.id, orientation.flagValue.toLong())
            val updated = getManga.await(manga.id) ?: return@launch
            _state.update { it.copy(manga = updated, orientation = orientation) }
        }
    }

    fun retry(page: ReaderPage) {
        page.chapter.pageLoader?.retryPage(page)
    }

    /** Saves history, releases loaders and stops background work. Blocks briefly for the history write. */
    fun close() {
        val state = _state.value
        val chapterId = state.currentChapter?.chapter?.id
        runBlocking(Dispatchers.IO) {
            // Let pending progress writes finish first (they run in order).
            val done = CompletableDeferred<Unit>()
            writes.trySend { done.complete(Unit) }
            withTimeoutOrNull(10_000) { done.await() }
            if (chapterId != null) {
                try {
                    progressSaver.saveHistory(chapterId, state.incognito)
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Could not save history" }
                }
            }
        }
        state.viewerChapters?.unref()
        scope.cancel()
        writes.close()
        // Android `deletePendingChapters` (delete after reading)
        progressScope.launch {
            try {
                downloadManager.deletePendingChapters()
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Could not delete read chapters" }
            }
        }
    }

    /** The domain chapter of the current item (for titles). */
    val currentDomainChapter get() = _state.value.currentChapter?.chapter?.toDomainChapter()
}

/**
 * Where [item] is in a rebuilt [items] list (by identity, transitions by their chapters, [ReaderViewerItems.indexOf]);
 * a second half that is gone falls back to its page, a page whose chapter was reloaded to the page with its key;
 * `null` when it is not there at all.
 */
internal fun anchorIndex(items: List<ReaderItem>, item: ReaderItem?): Int? {
    ReaderViewerItems.indexOf(items, item).takeIf { it >= 0 }?.let { return it }
    if (item !is ReaderPage) return null
    val page = imagePage(item)
    val key = pageKey(page)
    // A second half of a reloaded page: the second half of the page with its key, if that is split too
    if (item is InsertPage) {
        items.indexOfFirst { it is InsertPage && pageKey(it.parent) == key }.takeIf { it >= 0 }?.let { return it }
    }
    ReaderViewerItems.indexOf(items, page).takeIf { it >= 0 }?.let { return it }
    return items.indexOfFirst { it is ReaderPage && it !is InsertPage && pageKey(it) == key }.takeIf { it >= 0 }
}

/** The page whose image an item shows: a second half's page, else the page itself. */
fun imagePage(page: ReaderPage): ReaderPage = (page as? InsertPage)?.parent ?: page

/** The decode width of a page in a paged-viewer slot: twice the slot's width, as the shown slots decode. */
fun slotDecodeWidth(viewportWidth: Int, pagesShown: Int): Int = (viewportWidth / pagesShown.coerceAtLeast(1) * 2).coerceAtLeast(1)

/** A page's key across item list rebuilds and page reloads: its chapter and index (as `ReaderImages.key`). */
fun pageKey(page: ReaderPage): String = "${page.chapter.chapter.id}-${page.index}"

/** Android's viewers preload the next chapter within this many pages of the current chapter's end. */
private const val PRELOAD_PAGES = 5
