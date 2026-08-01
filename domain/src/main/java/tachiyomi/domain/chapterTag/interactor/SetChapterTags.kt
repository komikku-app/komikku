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

    /**
     * Adds tags without dropping the ones the chapter already carries. Used when restoring a
     * backup, where the tags on disk should be merged into the device's state rather than replace it.
     */
    suspend fun awaitAdd(chapterId: Long, tagIds: List<Long>) {
        try {
            chapterTagRepository.addChapterTags(chapterId, tagIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
