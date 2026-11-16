package app.komikku.desktop.ui.reader

import android.app.Application
import app.komikku.shared.reader.ReaderChapterList
import app.komikku.shared.reader.ReaderProgressChapter
import app.komikku.shared.reader.ReaderProgressSaver
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
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
 * The viewer shows one chapter at a time as [State.items]: a "previous chapter" transition, the pages,
 * a "next chapter" transition. Moving past a transition loads the neighbouring chapter.
 * Not ported: download-ahead, delete after reading (no downloads yet), merged / E-Hentai specifics,
 * sync triggers, page actions (save / share / set as cover). Trackers: see [ReaderProgressSaver]'s hook.
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
    ) {
        val currentChapter: ReaderChapter? get() = viewerChapters?.currChapter
        val pages: List<ReaderPage> get() = currentChapter?.pages.orEmpty()
        val currentItem: ReaderItem? get() = items.getOrNull(currentIndex)

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
        val pos = chapterList.indexOf(chapter)
        val newChapters = ViewerChapters(chapter, chapterList.getOrNull(pos - 1), chapterList.getOrNull(pos + 1))
        val pages = chapter.pages.orEmpty()
        val items = buildList {
            add(ChapterTransition.Prev(chapter, newChapters.prevChapter))
            addAll(pages)
            add(ChapterTransition.Next(chapter, newChapters.nextChapter))
        }
        val pageIndex = when (start) {
            ChapterStart.Requested -> chapter.requestedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            ChapterStart.First -> 0
            ChapterStart.Last -> pages.lastIndex
        }
        newChapters.ref()
        _state.value.viewerChapters?.unref()
        _state.update {
            it.copy(
                viewerChapters = newChapters,
                items = items,
                currentIndex = pageIndex + 1,
                bookmarked = chapter.chapter.bookmark,
                isLoading = false,
                error = null,
            )
        }
        (items[pageIndex + 1] as? ReaderPage)?.let(::onPageSelected)
    }

    /** Called when [index] of [State.items] becomes the current item (page turn, scroll, slider). */
    fun moveTo(index: Int) {
        val state = _state.value
        val clamped = index.coerceIn(0, (state.items.size - 1).coerceAtLeast(0))
        if (clamped == state.currentIndex && state.items.isNotEmpty()) return
        _state.update { it.copy(currentIndex = clamped) }
        when (val item = state.items.getOrNull(clamped)) {
            is ReaderPage -> onPageSelected(item)
            is ChapterTransition -> item.to?.let { preload(it) }
            null -> {}
        }
    }

    /**
     * Next item ([step] = 2 for double pages); on the "next chapter" transition, opens the next chapter.
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

    /** Previous item; on the "previous chapter" transition, opens the previous chapter at its last page. */
    fun previous(step: Int = 1) {
        val state = _state.value
        if (state.isLoadingAdjacent) return
        if (state.currentIndex <= 0) {
            state.viewerChapters?.prevChapter?.let { loadAdjacent(it, ChapterStart.Last) }
        } else {
            moveTo((state.currentIndex - step).coerceAtLeast(0))
        }
    }

    fun moveToFirstPage() = moveTo(1)

    fun moveToLastPage() = moveTo(_state.value.items.size - 2)

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

    /** Loads a neighbouring chapter's page list in the background (Android: `preload`). */
    private fun preload(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded || chapter.state == ReaderChapter.State.Loading) return
        scope.launch {
            try {
                loader.loadChapter(chapter)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // shown on the transition item through chapter.stateFlow
            }
        }
    }

    private fun onPageSelected(page: ReaderPage) {
        val readerChapter = page.chapter
        val pages = readerChapter.pages ?: return
        readerChapter.requestedPage = page.index
        val progressChapter = progressOf(readerChapter)
        val manga = _state.value.manga ?: return
        scope.launch(NonCancellable) {
            try {
                progressSaver.onPageSelected(
                    manga = manga,
                    chapter = progressChapter,
                    pageIndex = page.index,
                    pageCount = pages.size,
                    incognito = _state.value.incognito,
                    pageFailed = page.status is Page.State.Error,
                )
                readerChapter.chapter.read = progressChapter.read
                readerChapter.chapter.last_page_read = progressChapter.lastPageRead.toInt()
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Could not save reading progress" }
            }
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
        scope.launch(NonCancellable) { progressSaver.setBookmark(progressChapter, bookmarked) }
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
    }

    /** The domain chapter of the current item (for titles). */
    val currentDomainChapter get() = _state.value.currentChapter?.chapter?.toDomainChapter()
}
