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
        if (onlyUnread) {
            val filteredChapters = await(mangaId, onlyUnread = false)
            // [chapters] is a subset of this list, so an empty one means the query failed
            if (filteredChapters.isEmpty() || filteredChapters.any { it.id == fromChapterId }) return null
        }
        val manga = getManga.await(mangaId) ?: return null

        val isMerged = manga.source == MERGED_SOURCE_ID
        val isSourceSort = manga.sorting == Manga.CHAPTER_SORTING_SOURCE
        // Deduping can rewrite a merged manga's source order, so compare the stored one, which is only comparable
        // within one entry
        val sourceOrders = if (isMerged) {
            getMergedChaptersByMangaId.await(mangaId, dedupe = false, applyFilter = true)
                .associate { it.id to it.sourceOrder }
        } else {
            emptyMap()
        }
        fun Chapter.storedSourceOrder() = if (id == fromChapter.id) sourceOrder else sourceOrders[id] ?: sourceOrder
        fun compareSourceOrder(c1: Chapter, c2: Chapter): Int {
            if (c1.mangaId != c2.mangaId) return 0
            // Source order lists the first chapter last
            return c2.storedSourceOrder().compareTo(c1.storedSourceOrder())
        }

        val chapterSort = getChapterSort(manga, sortDescending = false)
        val nextChapters = chapters.filter {
            if (it.isRecognizedNumber && it.chapterNumber == fromChapter.chapterNumber) {
                return@filter !fromChapter.read
            }
            // Ties (e.g. unknown upload dates) are broken by chapter number, then by source order. Chapters still tied
            // can't be placed after the hidden chapter.
            var order = if (isSourceSort) compareSourceOrder(it, fromChapter) else chapterSort(it, fromChapter)
            if (order == 0) order = it.chapterNumber.compareTo(fromChapter.chapterNumber)
            if (order == 0 && !isSourceSort) order = compareSourceOrder(it, fromChapter)
            order > 0
        }

        // Order tied chapters the same way. A merged manga sorted by source order keeps its merged order.
        if (isMerged && isSourceSort) return nextChapters
        val resultSort = Comparator(chapterSort).thenBy { it.chapterNumber }
        return nextChapters.sortedWith(if (isMerged) resultSort else resultSort.thenByDescending { it.sourceOrder })
    }
    // KMK <--
}
