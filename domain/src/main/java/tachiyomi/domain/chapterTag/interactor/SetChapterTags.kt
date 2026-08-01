package tachiyomi.domain.chapterTag.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository

class SetChapterTags(
    private val chapterTagRepository: ChapterTagRepository,
) {

    suspend fun await(chapterId: Long, tagIds: List<Long>) {
        try {
            chapterTagRepository.setChapterTags(chapterId, tagIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
