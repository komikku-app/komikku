package tachiyomi.domain.chapterTag.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapterTag.model.ChapterTag
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository

class RenameChapterTag(
    private val chapterTagRepository: ChapterTagRepository,
) {

    suspend fun await(chapterTag: ChapterTag, name: String) = withNonCancellableContext {
        try {
            chapterTagRepository.rename(chapterTag.id, name)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
