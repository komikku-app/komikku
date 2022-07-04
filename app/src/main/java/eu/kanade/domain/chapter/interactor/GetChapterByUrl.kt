package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority

class GetChapterByUrl(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(url: String): List<Chapter> {
        return try {
            chapterRepository.getChapterByUrl(url)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
