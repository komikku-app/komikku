package app.komikku.desktop.ui.manga

import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.shared.chapter.ChapterListActions
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop manga details: the stored manga and chapter list, kept in sync with the source through the
 * same interactors as Android's `MangaScreenModel` (`UpdateMangaFromRemote` → `SyncChaptersWithSource`,
 * `SetReadStatus`, `UpdateChapter`, `SetMangaChapterFlags`, `SetMangaCategories`, `GetNextChapters`).
 * Platform-neutral list rules come from `:app-shared` ([ChapterListActions]).
 */
class MangaDetailsScreenModel(
    private val mangaId: Long,
    private val getMangaWithChapters: GetMangaWithChapters = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    private val readerLauncher: ReaderLauncher = Injekt.get(),
) : StateScreenModel<MangaDetailsScreenModel.State>(State()) {

    data class State(
        val manga: Manga? = null,
        /** Filtered (manga's chapter filters) and sorted (manga's sort flags) for display. */
        val chapters: List<Chapter> = emptyList(),
        val selection: Set<Long> = emptySet(),
        val isRefreshing: Boolean = false,
        val error: String? = null,
        /** Next chapter to read (resume / start), `null` when everything is read. */
        val nextChapter: Chapter? = null,
        val dialog: Dialog? = null,
        /** All chapters of the manga (unfiltered), for the download actions. */
        val allChapters: List<Chapter> = emptyList(),
        /** Download state + progress per chapter id (absent = not downloaded / local source). */
        val downloads: Map<Long, ChapterDownload> = emptyMap(),
    ) {
        val selectedChapters: List<Chapter> get() = chapters.filter { it.id in selection }

        fun downloadState(chapterId: Long): Download.State = downloads[chapterId]?.state ?: Download.State.NOT_DOWNLOADED
    }

    data class ChapterDownload(val state: Download.State, val progress: Int = 0)

    sealed interface Dialog {
        data class ChangeCategories(val categories: List<Category>, val selected: Set<Long>) : Dialog
        data object Tracking : Dialog
    }

    private var fetchedOnOpen = false

    init {
        screenModelScope.launch {
            getMangaWithChapters.subscribe(mangaId, applyFilter = true).collectLatest { (manga, chapters) ->
                val displayed = ChapterListActions.sortForDisplay(manga, chapters.filterForDisplay(manga))
                val next = withIOContext { getNextChapters.await(manga.id, onlyUnread = true).firstOrNull() }
                mutableState.update { state ->
                    state.copy(
                        manga = manga,
                        chapters = displayed,
                        selection = state.selection.filterTo(mutableSetOf()) { id -> displayed.any { it.id == id } },
                        nextChapter = next,
                        allChapters = chapters,
                    )
                }
                refreshDownloads()
                if (!fetchedOnOpen) {
                    fetchedOnOpen = true
                    // Android fetches when the manga was never initialized or has no chapters yet.
                    if (!manga.initialized || chapters.isEmpty()) refresh()
                }
            }
        }
    }

    init {
        // Android: `MangaScreenModel` observes the status / progress flows and updates only the chapter of
        // each download. Sampled, not debounced: a steady stream of progress events would starve a debounce.
        screenModelScope.launch {
            val pending = ConcurrentHashMap<Long, Download>()
            merge(downloadManager.statusFlow(), downloadManager.progressFlow())
                .filter { it.manga.id == mangaId }
                .onEach { pending[it.chapter.id] = it }
                .sample(DOWNLOAD_STATE_SAMPLE_MILLIS)
                .collect {
                    val updates = pending.keys.toList().mapNotNull { id -> pending.remove(id) }
                    if (updates.isNotEmpty()) mutableState.update { it.copy(downloads = it.downloads.withDownloads(updates)) }
                }
        }
        // Queue changes (added / removed downloads) and the download cache (finished / deleted chapters):
        // recompute every chapter's state.
        screenModelScope.launch {
            merge(downloadManager.queueState.map { }, downloadCache.changes)
                .sample(DOWNLOAD_STATE_SAMPLE_MILLIS)
                .collectLatest { refreshDownloads() }
        }
    }

    /** Android `updateDownloadState`: [updates]' chapters take their download's current state. */
    private fun Map<Long, ChapterDownload>.withDownloads(updates: List<Download>): Map<Long, ChapterDownload> =
        toMutableMap().apply {
            updates.forEach { download ->
                val status = download.status
                if (status == Download.State.NOT_DOWNLOADED) {
                    remove(download.chapter.id)
                } else {
                    put(download.chapter.id, ChapterDownload(status, download.progress))
                }
            }
        }

    private suspend fun refreshDownloads() {
        val manga = state.value.manga ?: return
        if (manga.isLocal()) return
        val chapters = state.value.allChapters
        val downloads = withIOContext {
            chapters.mapNotNull { chapter ->
                val queued = downloadManager.getQueuedDownloadOrNull(chapter.id)
                when {
                    queued != null -> chapter.id to ChapterDownload(queued.status, queued.progress)
                    downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.ogTitle, manga.source) ->
                        chapter.id to ChapterDownload(Download.State.DOWNLOADED, 100)
                    else -> null
                }
            }.toMap()
        }
        mutableState.update { it.copy(downloads = downloads) }
    }

    /** Queues [chapters] (Android `runChapterDownloadActions(START)`); restarts the queue after errors. */
    fun download(chapters: List<Chapter>) {
        selectAll(false)
        val manga = state.value.manga ?: return
        if (manga.isLocal() || chapters.isEmpty()) return
        val hadErrors = chapters.any { state.value.downloadState(it.id) == Download.State.ERROR }
        downloadManager.downloadChapters(manga, chapters)
        if (hadErrors) downloadManager.startDownloads()
    }

    fun startDownloadNow(chapter: Chapter) {
        screenModelScope.launch { withIOContext { downloadManager.startDownloadNow(chapter.id) } }
    }

    fun cancelDownload(chapter: Chapter) {
        val download = downloadManager.getQueuedDownloadOrNull(chapter.id) ?: return
        downloadManager.cancelQueuedDownloads(listOf(download))
    }

    /** Deletes the downloads of [chapters] (Android `deleteChapters`: category exclusions ignored). */
    fun deleteDownloads(chapters: List<Chapter>) {
        selectAll(false)
        val manga = state.value.manga ?: return
        if (chapters.isEmpty()) return
        downloadManager.deleteChapters(chapters, manga, sourceManager.getOrStub(manga.source), ignoreCategoryExclusion = true)
    }

    /**
     * The entry's page on its source, for "Open in WebView" (Android `MangaScreen.getMangaUrl`); `null` for a source that
     * is not an `HttpSource` (local, not installed / loaded yet) or fails to build it. Calls the source: not on the UI
     * thread.
     */
    fun mangaUrl(): String? {
        val manga = state.value.manga ?: return null
        val source = sourceManager.get(manga.source) as? HttpSource ?: return null
        return try {
            source.getMangaUrl(manga.toSManga())
        } catch (e: Exception) {
            xLogE("Failed to build the entry's address", e)
            null
        }
    }

    /** Download menu (Android `runDownloadAction`). */
    fun runDownloadAction(action: DownloadAction) {
        val state = state.value
        val manga = state.manga ?: return
        val source = if (readerPreferences.skipFiltered().get()) state.chapters else state.allChapters
        val chapters = ChapterListActions.chaptersToDownload(manga, source, action) {
            state.downloadState(it.id) == Download.State.NOT_DOWNLOADED
        }
        download(chapters)
    }

    /** Android `ChapterFilter.applyFilters` minus the sort (unread / bookmarked / downloaded filters). */
    private fun List<Chapter>.filterForDisplay(manga: Manga): List<Chapter> {
        val isLocal = manga.isLocal()
        val unreadFilter = manga.unreadFilter
        val bookmarkedFilter = manga.bookmarkedFilter
        val downloadedFilter = manga.downloadedFilter
        return filter { chapter -> applyFilter(unreadFilter) { !chapter.read } }
            .filter { chapter -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
            .filter { chapter ->
                applyFilter(downloadedFilter) {
                    isLocal ||
                        downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.ogTitle, manga.source)
                }
            }
    }

    /** Fetches details and chapters from the source and stores them (Android: `fetchAllFromSource`). */
    fun refresh() {
        val manga = state.value.manga ?: return
        if (state.value.isRefreshing) return
        mutableState.update { it.copy(isRefreshing = true, error = null) }
        screenModelScope.launch {
            val error = try {
                withIOContext {
                    updateMangaFromRemote(
                        source = sourceManager.getOrStub(manga.source),
                        manga = manga,
                        fetchDetails = true,
                        fetchChapters = true,
                        manualFetch = true,
                    ).getOrThrow()
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is NoChaptersException) NO_CHAPTERS else e.message ?: e::class.simpleName
            }
            mutableState.update { it.copy(isRefreshing = false, error = error) }
        }
    }

    fun toggleSelection(chapter: Chapter) {
        mutableState.update {
            it.copy(selection = if (chapter.id in it.selection) it.selection - chapter.id else it.selection + chapter.id)
        }
    }

    fun selectAll(selected: Boolean) {
        mutableState.update { state -> state.copy(selection = if (selected) state.chapters.mapTo(mutableSetOf()) { it.id } else emptySet()) }
    }

    fun markRead(chapters: List<Chapter>, read: Boolean) {
        selectAll(false)
        if (chapters.isEmpty()) return
        screenModelScope.launch { withIOContext { setReadStatus.await(read = read, chapters = chapters.toTypedArray()) } }
    }

    fun markPreviousRead(pointer: Chapter) {
        val manga = state.value.manga ?: return
        markRead(ChapterListActions.previousChapters(manga, state.value.chapters, pointer), true)
    }

    fun bookmark(chapters: List<Chapter>, bookmarked: Boolean) {
        selectAll(false)
        screenModelScope.launch {
            withIOContext {
                updateChapter.awaitAll(
                    chapters.filterNot { it.bookmark == bookmarked }.map { ChapterUpdate(id = it.id, bookmark = bookmarked) },
                )
            }
        }
    }

    /** Picks a sort mode; picking the active one flips the direction (Android: same interactor). */
    fun setSorting(mode: Long) {
        val manga = state.value.manga ?: return
        screenModelScope.launch { withIOContext { setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, mode) } }
    }

    /**
     * Adds to / removes from the library like Android's `toggleFavorite` (default category preference:
     * a chosen category, "default" (0) or ask when categories exist). Duplicate detection, tracker
     * binding and download deletion are not on desktop.
     */
    fun toggleFavorite() {
        val manga = state.value.manga ?: return
        screenModelScope.launch {
            withIOContext {
                if (manga.favorite) {
                    updateManga.awaitUpdateFavorite(manga.id, false)
                    return@withIOContext
                }
                val categories = getCategories.await().filterNot { it.isSystemCategory }
                val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    defaultCategory != null -> {
                        if (updateManga.awaitUpdateFavorite(manga.id, true)) setMangaCategories.await(manga.id, listOf(defaultCategory.id))
                    }
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        if (updateManga.awaitUpdateFavorite(manga.id, true)) setMangaCategories.await(manga.id, emptyList())
                    }
                    else -> showCategoryDialog()
                }
            }
        }
    }

    fun showCategoryDialog() {
        screenModelScope.launch {
            val categories = withIOContext { getCategories.await().filterNot { it.isSystemCategory } }
            val selected = withIOContext { getCategories.await(mangaId).mapTo(mutableSetOf()) { it.id } }
            mutableState.update { it.copy(dialog = Dialog.ChangeCategories(categories, selected)) }
        }
    }

    /** Android `moveMangaToCategoriesAndAddToLibrary`. */
    fun setCategoriesAndAddToLibrary(categoryIds: List<Long>) {
        dismissDialog()
        val manga = state.value.manga ?: return
        screenModelScope.launch {
            withIOContext {
                setMangaCategories.await(manga.id, categoryIds)
                if (!manga.favorite) updateManga.awaitUpdateFavorite(manga.id, true)
            }
        }
    }

    fun showTrackingDialog() {
        mutableState.update { it.copy(dialog = Dialog.Tracking) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun openReader(chapter: Chapter) {
        readerLauncher.open(mangaId, chapter.id)
    }

    fun continueReading() {
        val chapter = state.value.nextChapter ?: return
        openReader(chapter)
    }

    companion object {
        /** Marker for [State.error]: the source returned no chapters (shown with MR `no_chapters_error`). */
        const val NO_CHAPTERS = "\u0000no_chapters"

        /** Download indicators update at most this often (like the download queue screen). */
        private const val DOWNLOAD_STATE_SAMPLE_MILLIS = 100L
    }
}
