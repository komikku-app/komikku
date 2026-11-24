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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        /** [ChapterTransition.Prev], the current chapter's pages, [ChapterTransition.Next]. */
        val items: List<ReaderItem> = emptyList(),
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
    ) {
        val currentChapter: ReaderChapter? get() = viewerChapters?.currChapter
        val pages: List<ReaderPage> get() = currentChapter?.pages.orEmpty()
        val currentItem: ReaderItem? get() = items.getOrNull(currentIndex)

        /** The index in [items] of the current chapter's page [pageIndex] (0-based), `-1` when not there. */
        fun indexOfPage(pageIndex: Int): Int {
            val chapter = currentChapter ?: return -1
            return items.indexOfFirst { it is ReaderPage && it.chapter == chapter && it.index == pageIndex }
        }

        /** 1-based page number shown in the UI (the nearest page on a transition item). */
        val currentPageNumber: Int
            get() = when (val item = currentItem) {
                is ReaderPage -> item.number
                is ChapterTransition.Prev -> 1
                is ChapterTransition.Next -> pages.size
                null -> 0
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

    private suspend fun loadChapter(chapter: ReaderChapter, page: Int?, start: ChapterStart) = mutex.withLock {
        loader.loadChapter(chapter, page)
        val newChapters = viewerChaptersOf(chapter)
        val pages = chapter.pages.orEmpty()
        val pageIndex = when (start) {
            ChapterStart.Requested -> chapter.requestedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            ChapterStart.First -> 0
            ChapterStart.Last -> pages.lastIndex
        }
        val target = pages.getOrNull(pageIndex)
        val items = ReaderViewerItems.build(newChapters, forceTransition = alwaysShowTransition())
        // Android: add the new references first to avoid recycling a chapter that stays
        newChapters.ref()
        _state.value.viewerChapters?.unref()
        _state.update {
            it.copy(
                viewerChapters = newChapters,
                items = items,
                currentIndex = ReaderViewerItems.indexOf(items, target).coerceAtLeast(0),
                bookmarked = chapter.chapter.bookmark,
                isLoading = false,
                error = null,
                jump = it.jump + 1,
            )
        }
        target?.let(::onPageSelected)
    }

    private fun viewerChaptersOf(chapter: ReaderChapter): ViewerChapters {
        val pos = chapterList.indexOf(chapter)
        return ViewerChapters(chapter, chapterList.getOrNull(pos - 1), chapterList.getOrNull(pos + 1))
    }

    /** Android's viewers: `alwaysShowChapterTransition`, or the viewer is on a transition (it stays while it is shown). */
    private fun forceTransition(current: ReaderItem?): Boolean =
        current is ChapterTransition || alwaysShowTransition()

    private fun alwaysShowTransition(): Boolean = readerPreferences.alwaysShowChapterTransition().get()

    /** Rebuilds [State.items] for the current chapters (a neighbour was loaded), staying on the current item. */
    private suspend fun refreshItems() = mutex.withLock {
        val chapters = _state.value.viewerChapters ?: return@withLock
        _state.update { state ->
            val current = state.currentItem
            val items = ReaderViewerItems.build(chapters, forceTransition(current))
            val index = ReaderViewerItems.indexOf(items, current).takeIf { it >= 0 } ?: state.currentIndex.coerceIn(0, items.lastIndex)
            state.copy(items = items, currentIndex = index)
        }
    }

    /** The chapter being made current by [switchChapter], so that several pages reached meanwhile switch once. */
    @Volatile
    private var switchingTo: ReaderChapter? = null

    /**
     * Android `loadNewChapter`: a page of a neighbouring chapter was reached, so that chapter becomes the current one
     * (history of the left chapter saved, read timer restarted, the items rebuilt around it; the viewer stays on the
     * item it is on).
     */
    private fun switchChapter(chapter: ReaderChapter) {
        if (switchingTo == chapter) return
        switchingTo = chapter
        scope.launch {
            try {
                val left = _state.value.currentChapter
                if (left != null && left != chapter) progressSaver.saveHistory(left.chapter.id!!, _state.value.incognito)
                progressSaver.restartReadTimer()
                mutex.withLock {
                    val state = _state.value
                    if (state.currentChapter == chapter || chapter !in chapterList) return@withLock
                    loader.loadChapter(chapter)
                    val newChapters = viewerChaptersOf(chapter)
                    newChapters.ref()
                    state.viewerChapters?.unref()
                    _state.update {
                        val current = it.currentItem
                        val items = ReaderViewerItems.build(newChapters, forceTransition(current))
                        val index = ReaderViewerItems.indexOf(items, current).takeIf { i -> i >= 0 }
                            ?: ReaderViewerItems.indexOf(items, chapter.pages?.getOrNull(chapter.requestedPage)).coerceAtLeast(0)
                        it.copy(viewerChapters = newChapters, items = items, currentIndex = index, bookmarked = chapter.chapter.bookmark)
                    }
                }
                (_state.value.currentItem as? ReaderPage)?.let(::preloadIfInRange)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                _state.update { it.copy(error = e) }
            } finally {
                if (switchingTo == chapter) switchingTo = null
            }
        }
    }

    /** Called when [index] of [State.items] becomes the current item (page turn, scroll, slider). */
    fun moveTo(index: Int) {
        val state = _state.value
        val clamped = index.coerceIn(0, (state.items.size - 1).coerceAtLeast(0))
        if (clamped == state.currentIndex && state.items.isNotEmpty()) return
        _state.update { it.copy(currentIndex = clamped) }
        when (val item = state.items.getOrNull(clamped)) {
            is ReaderPage -> {
                onPageSelected(item)
                if (item.chapter != state.currentChapter) switchChapter(item.chapter)
            }
            is ChapterTransition -> item.to?.let { preload(it) }
            null -> {}
        }
    }

    /** Moves to [item] if it is in [State.items] (the webtoon list reports the item it shows on top). */
    fun moveToItem(item: ReaderItem) {
        val index = ReaderViewerItems.indexOf(_state.value.items, item)
        if (index >= 0) moveTo(index)
    }

    /** Moves to the current chapter's page [pageIndex] (0-based; slider, first / last page) and lets the viewers follow. */
    fun moveToPage(pageIndex: Int) {
        val index = _state.value.indexOfPage(pageIndex)
        if (index < 0) return
        moveTo(index)
        _state.update { it.copy(jump = it.jump + 1) }
    }

    /**
     * Next item ([step] = 2 for double pages); at the end of the items (the "next chapter" transition while the next
     * chapter is not loaded yet), opens the next chapter.
     */
    fun next(step: Int = 1) {
        val state = _state.value
        if (state.isLoadingAdjacent) return
        if (state.currentIndex >= state.items.lastIndex) {
            state.viewerChapters?.nextChapter?.let { loadAdjacent(it, ChapterStart.First) }
        } else {
            moveTo((state.currentIndex + step).coerceAtMost(state.items.lastIndex))
        }
    }

    /** Previous item; at the start of the items, opens the previous chapter at its last page. */
    fun previous(step: Int = 1) {
        val state = _state.value
        if (state.isLoadingAdjacent) return
        if (state.currentIndex <= 0) {
            state.viewerChapters?.prevChapter?.let { loadAdjacent(it, ChapterStart.Last) }
        } else {
            moveTo((state.currentIndex - step).coerceAtLeast(0))
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
        if (chapter.state is ReaderChapter.State.Loaded || chapter.state == ReaderChapter.State.Loading) return
        scope.launch {
            try {
                loader.loadChapter(chapter)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // shown on the transition item through chapter.stateFlow
                return@launch
            }
            refreshItems()
        }
    }

    private fun onPageSelected(page: ReaderPage) {
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

/** Android's viewers preload the next chapter within this many pages of the current chapter's end. */
private const val PRELOAD_PAGES = 5
