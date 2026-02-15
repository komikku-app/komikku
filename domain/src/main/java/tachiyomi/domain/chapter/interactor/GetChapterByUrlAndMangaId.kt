package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetChapterByUrlAndMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(url: String, sourceId: Long, includeDeleted: Boolean = false): Chapter? {
        return try {
            chapterRepository.getChapterByUrlAndMangaId(url, sourceId, includeDeleted)
        } catch (e: Exception) {
            null
        }
    }
}
