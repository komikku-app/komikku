package tachiyomi.domain.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.repository.ChapterRepository

class SetChapterCustomName(
    private val chapterRepository: ChapterRepository,
) {

    /**
     * Sets the name shown for a chapter instead of the one provided by the source.
     * A blank [customName] restores the source name.
     */
    suspend fun await(chapterId: Long, customName: String?) {
        try {
            chapterRepository.updateCustomName(chapterId, customName?.trim()?.ifBlank { null })
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
