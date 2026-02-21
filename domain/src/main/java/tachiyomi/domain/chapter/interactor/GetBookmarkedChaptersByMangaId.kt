package tachiyomi.domain.chapter.interactor

import exh.source.MERGED_SOURCE_ID
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetManga

class GetBookmarkedChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
    // SY -->
    private val getManga: GetManga,
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    // SY <--
) {

    suspend fun await(mangaId: Long): List<Chapter> {
        return try {
            // SY -->
            val manga = getManga.await(mangaId) ?: return emptyList()
            if (manga.source == MERGED_SOURCE_ID) {
                return getMergedChaptersByMangaId.await(mangaId, applyFilter = true)
                    .filter { it.bookmark }
            }
            // SY <--
            chapterRepository.getBookmarkedChaptersByMangaId(mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
