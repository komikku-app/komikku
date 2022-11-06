package eu.kanade.domain.history.interactor

import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.tachiyomi.util.chapter.getChapterSort
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import kotlin.math.max

class GetNextUnreadChapters(
    private val getChapterByMangaId: GetChapterByMangaId,
    // SY -->
    private val getMergedChapterByMangaId: GetMergedChapterByMangaId,
    // SY <--
    private val getManga: GetManga,
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(): Chapter? {
        val history = historyRepository.getLastHistory() ?: return null
        return await(history.mangaId, history.chapterId).firstOrNull()
    }

    suspend fun await(mangaId: Long): List<Chapter> {
        val manga = getManga.await(mangaId) ?: return emptyList()
        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            return getMergedChapterByMangaId.await(mangaId)
                .sortedWith(getChapterSort(manga, sortDescending = false))
                .filterNot { it.read }
        }
        if (manga.isEhBasedManga()) {
            return getChapterByMangaId.await(mangaId)
                .sortedWith(getChapterSort(manga, sortDescending = false))
                .takeLast(1)
        }
        // SY <--
        return getChapterByMangaId.await(mangaId)
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .filterNot { it.read }
    }

    suspend fun await(mangaId: Long, fromChapterId: Long): List<Chapter> {
        val unreadChapters = await(mangaId)
        val currChapterIndex = unreadChapters.indexOfFirst { it.id == fromChapterId }
        return unreadChapters.subList(max(0, currChapterIndex), unreadChapters.size)
    }
}
