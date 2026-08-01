package tachiyomi.domain.chapterTag.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapterTag.model.ChapterTagFilter
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository

class SetChapterTagFilter(
    private val chapterTagRepository: ChapterTagRepository,
) {

    suspend fun await(mangaId: Long, filter: ChapterTagFilter) {
        try {
            chapterTagRepository.setFilter(mangaId, filter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
