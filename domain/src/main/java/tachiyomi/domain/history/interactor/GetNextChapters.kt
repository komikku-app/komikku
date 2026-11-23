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
     * bookmark filter hides. Returns the chapters of [chapters] that sort after it, plus other scanlators'
     * copies of it when it isn't completely read.
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
        if (onlyUnread && await(mangaId, onlyUnread = false).any { it.id == fromChapterId }) return null
        val manga = getManga.await(mangaId) ?: return null

        val chapterSort = getChapterSort(manga, sortDescending = false)
        return chapters.filter {
            var order = chapterSort(it, fromChapter)
            // Ties that aren't copies of the same chapter (e.g. unknown upload dates or chapter numbers) fall back to
            // source order, which lists the first chapter last
            if (order == 0 && !(it.isRecognizedNumber && it.chapterNumber == fromChapter.chapterNumber)) {
                order = fromChapter.sourceOrder.compareTo(it.sourceOrder)
            }
            order > 0 || (order == 0 && !fromChapter.read)
        }
    }
    // KMK <--
}
