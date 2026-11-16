package app.komikku.shared.reader

import exh.source.isEhBasedManga
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import java.util.Date

/**
 * KMK: a chapter's reading position as the reader tracks it (Android keeps it in the mutable
 * `ReaderChapter.chapter`). [read] and [lastPageRead] are updated by [ReaderProgressSaver].
 */
class ReaderProgressChapter(
    val id: Long,
    val mangaId: Long,
    val sourceOrder: Long,
    val chapterNumber: Double,
    var read: Boolean,
    var lastPageRead: Long,
    var bookmark: Boolean,
)

/**
 * KMK: tracker side effect of finishing a chapter in the reader (Android: `ReaderViewModel.updateTrackChapterRead`
 * → `TrackChapter`). **Phase 8 hook:** desktop has no trackers yet and registers [None].
 */
fun interface ReaderTrackerHook {
    suspend fun onChapterRead(manga: Manga, chapterNumber: Double)

    companion object {
        val None = ReaderTrackerHook { _, _ -> }
    }
}

/**
 * KMK: the database side effects of reading, extracted from Android's `ReaderViewModel`
 * (`updateChapterProgress`, `updateChapterProgressOnComplete`, `updateHistory`, `toggleChapterBookmark`)
 * with the same interactors and rules, so platform readers share them:
 *
 * - nothing is written in incognito mode or for a page that failed to load;
 * - the last page (or the second-to-last when a double page shows the last two) marks the chapter read;
 * - E-Hentai based manga: completing a chapter also marks the chapters after it (by source order) read;
 * - "mark duplicate read chapters as read" ([LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING]):
 *   unread chapters with the same recognized number are marked read;
 * - history = read date + session duration of the chapter.
 *
 * Not ported here (platform-specific in Android's view model): sync triggers, download-ahead, delete
 * after reading.
 */
class ReaderProgressSaver(
    private val updateChapter: UpdateChapter,
    private val upsertHistory: UpsertHistory,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val libraryPreferences: LibraryPreferences,
    private val trackerHook: ReaderTrackerHook = ReaderTrackerHook.None,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Saves the position after [pageIndex] (0-based) of [pageCount] pages became the current page.
     * @return `true` if this call marked the chapter read.
     */
    suspend fun onPageSelected(
        manga: Manga,
        chapter: ReaderProgressChapter,
        pageIndex: Int,
        pageCount: Int,
        incognito: Boolean,
        pageFailed: Boolean = false,
        hasExtraPage: Boolean = false,
    ): Boolean {
        if (incognito || pageFailed) return false
        chapter.lastPageRead = pageIndex.toLong()

        val lastIndex = pageCount - 1
        var completed = false
        if (lastIndex == pageIndex || (hasExtraPage && lastIndex - 1 == pageIndex)) {
            completed = !chapter.read
            onComplete(manga, chapter)
        }

        updateChapter.await(
            ChapterUpdate(
                id = chapter.id,
                read = chapter.read,
                lastPageRead = chapter.lastPageRead,
            ),
        )
        return completed
    }

    private suspend fun onComplete(manga: Manga, chapter: ReaderProgressChapter) {
        chapter.read = true
        // Android: `unfilteredChapterList` (all chapters of the manga, filters ignored)
        var unfilteredCache: List<Chapter>? = null
        suspend fun unfiltered() = unfilteredCache ?: getChaptersByMangaId.await(chapter.mangaId).also { unfilteredCache = it }

        if (manga.isEhBasedManga()) {
            val updates = unfiltered()
                .filter { it.sourceOrder > chapter.sourceOrder }
                .map { ChapterUpdate(id = it.id, read = true) }
            updateChapter.awaitAll(updates)
        }

        trackerHook.onChapterRead(manga, chapter.chapterNumber)

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!markDuplicateAsRead) return

        val duplicates = unfiltered().mapNotNull {
            if (!it.read && it.isRecognizedNumber && it.chapterNumber.toFloat() == chapter.chapterNumber.toFloat()) {
                ChapterUpdate(id = it.id, read = true)
            } else {
                null
            }
        }
        updateChapter.awaitAll(duplicates)
    }

    /** Toggles and stores the chapter's bookmark (allowed in incognito mode, like on Android). */
    suspend fun setBookmark(chapter: ReaderProgressChapter, bookmarked: Boolean) {
        chapter.bookmark = bookmarked
        updateChapter.await(ChapterUpdate(id = chapter.id, bookmark = bookmarked))
    }

    /** Read-time bookkeeping for history (Android: `chapterReadStartTime` + `updateHistory`). */
    private var readStartTime: Long? = null

    fun restartReadTimer() {
        readStartTime = clock()
    }

    /**
     * Writes the history entry of [chapterId] (read date now, duration since [restartReadTimer]) and
     * resets the timer. Skipped in incognito mode.
     */
    suspend fun saveHistory(chapterId: Long, incognito: Boolean) {
        if (incognito) return
        val end = clock()
        val duration = readStartTime?.let { end - it } ?: 0
        upsertHistory.await(HistoryUpdate(chapterId, Date(end), duration))
        readStartTime = null
    }
}
