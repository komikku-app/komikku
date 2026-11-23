package tachiyomi.domain.history.interactor

import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import kotlin.math.max

class GetNextChapters(
    private val getChaptersByMangaId: GetChaptersByMangaId,
    // SY -->
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    // SY <--
    private val getManga: GetManga,
    private val historyRepository: HistoryRepository,
    // KMK -->
    private val getChapter: GetChapter,
    // KMK <--
) {

    suspend fun await(onlyUnread: Boolean = true): List<Chapter> {
        val history = historyRepository.getLastHistory() ?: return emptyList()
        return await(history.mangaId, history.chapterId, onlyUnread)
    }

    suspend fun await(mangaId: Long, onlyUnread: Boolean = true): List<Chapter> {
        val manga = getManga.await(mangaId) ?: return emptyList()

        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            val chapters = getMergedChaptersByMangaId.await(mangaId, applyFilter = true)
                .sortedWith(getChapterSort(manga, sortDescending = false))

            return if (onlyUnread) {
                chapters.filterNot { it.read }
            } else {
                chapters
            }
        }
        if (manga.isEhBasedManga()) {
            val chapters = getChaptersByMangaId.await(mangaId, applyFilter = true)
                .sortedWith(getChapterSort(manga, sortDescending = false))

            return if (onlyUnread) {
                chapters.takeLast(1).takeUnless { it.firstOrNull()?.read == true }.orEmpty()
            } else {
                chapters
            }
        }
        // SY <--

        val chapters = getChaptersByMangaId.await(mangaId, applyFilter = true)
            .sortedWith(getChapterSort(manga, sortDescending = false))

        return if (onlyUnread) {
            chapters.filterNot { it.read }
        } else {
            chapters
        }
    }

    suspend fun await(
        mangaId: Long,
        fromChapterId: Long,
        onlyUnread: Boolean = true,
    ): List<Chapter> {
        val chapters = await(mangaId, onlyUnread)
        val currChapterIndex = chapters.indexOfFirst { it.id == fromChapterId }
        // KMK -->
        if (currChapterIndex == -1) {
            getChaptersAfterHiddenChapter(mangaId, fromChapterId, onlyUnread, chapters)?.let { return it }
        }
        // KMK <--
        val nextChapters = chapters.subList(max(0, currChapterIndex), chapters.size)

        if (onlyUnread) {
            return nextChapters
        }

        // The "next chapter" is either:
        // - The current chapter if it isn't completely read
        // - The chapters after the current chapter if the current one is completely read
        val fromChapter = chapters.getOrNull(currChapterIndex)
        // KMK -->
        // Resume from the first unread chapter when the current one can't be found, e.g. it was deleted
        if (currChapterIndex == -1) return nextChapters.dropWhile { it.read }
        // KMK <--
        return if (fromChapter != null && !fromChapter.read) {
            nextChapters
        } else {
            nextChapters.drop(1)
        }
    }

    // KMK -->
    /**
     * Handles a [fromChapterId] that the chapter filters hide, e.g. a chapter whose scanlator was excluded or that the
     * bookmark filter hides. Returns the chapters of [chapters] that sort after it, plus other scanlators' copies of it
     * (same chapter number) when it isn't completely read.
     *
     * Returns null to keep the default behavior when the chapter can't be found or isn't hidden by the filters, i.e. it
     * is only missing from [chapters] because [onlyUnread] dropped it.
     */
    private suspend fun getChaptersAfterHiddenChapter(
        mangaId: Long,
        fromChapterId: Long,
        onlyUnread: Boolean,
        chapters: List<Chapter>,
    ): List<Chapter>? {
        val fromChapter = getChapter.await(fromChapterId) ?: return null
        if (onlyUnread && !isHiddenByFilters(mangaId, fromChapterId)) return null
        val manga = getManga.await(mangaId) ?: return null

        val storedSourceOrders = if (manga.source == MERGED_SOURCE_ID) {
            getMergedChaptersByMangaId.await(mangaId, dedupe = false, applyFilter = true)
                .associate { it.id to it.sourceOrder }
        } else {
            emptyMap()
        }
        return HiddenChapterOrder(manga, fromChapter, storedSourceOrders).chaptersAfter(chapters)
    }

    /**
     * Whether the chapter filters hide [chapterId] from [mangaId]'s chapters. False when the chapters can't be read: an
     * empty list means the query failed, since the unread chapters come from the same list.
     */
    private suspend fun isHiddenByFilters(mangaId: Long, chapterId: Long): Boolean {
        val filteredChapters = await(mangaId, onlyUnread = false)
        return filteredChapters.isNotEmpty() && filteredChapters.none { it.id == chapterId }
    }
    // KMK <--
}

// KMK -->
/**
 * Places chapters relative to [fromChapter], a chapter that the filters hide from [manga]'s chapter list.
 * [storedSourceOrders] holds a merged manga's stored source order, which deduping can rewrite.
 */
private class HiddenChapterOrder(
    manga: Manga,
    private val fromChapter: Chapter,
    private val storedSourceOrders: Map<Long, Long>,
) {
    private val isMerged = manga.source == MERGED_SOURCE_ID
    private val isSourceSort = manga.sorting == Manga.CHAPTER_SORTING_SOURCE
    private val chapterSort = getChapterSort(manga, sortDescending = false)

    fun chaptersAfter(chapters: List<Chapter>): List<Chapter> {
        val nextChapters = chapters.filter(::isAfter)

        // Order tied chapters the same way. A merged manga sorted by source order keeps its merged order.
        if (isMerged && isSourceSort) return nextChapters
        val resultSort = Comparator(chapterSort).thenBy { it.chapterNumber }
        return nextChapters.sortedWith(if (isMerged) resultSort else resultSort.thenByDescending { it.sourceOrder })
    }

    /**
     * Other scanlators' copies of [fromChapter] (same chapter number) count as after it when it isn't completely read.
     * Ties (e.g. unknown upload dates) are broken by chapter number, then by source order. Chapters still tied can't be
     * placed after it.
     */
    private fun isAfter(chapter: Chapter): Boolean {
        if (chapter.isRecognizedNumber && chapter.chapterNumber == fromChapter.chapterNumber) return !fromChapter.read
        var order = if (isSourceSort) compareSourceOrder(chapter) else chapterSort(chapter, fromChapter)
        if (order == 0) order = chapter.chapterNumber.compareTo(fromChapter.chapterNumber)
        if (order == 0 && !isSourceSort) order = compareSourceOrder(chapter)
        return order > 0
    }

    /** Compares the stored source order, which lists the first chapter last and only compares within one entry. */
    private fun compareSourceOrder(chapter: Chapter): Int {
        if (chapter.mangaId != fromChapter.mangaId) return 0
        return fromChapter.sourceOrder.compareTo(storedSourceOrders[chapter.id] ?: chapter.sourceOrder)
    }
}
// KMK <--
