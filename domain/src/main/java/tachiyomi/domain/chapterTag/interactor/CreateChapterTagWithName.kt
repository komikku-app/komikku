package tachiyomi.domain.chapterTag.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapterTag.model.ChapterTag
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository

class CreateChapterTagWithName(
    private val chapterTagRepository: ChapterTagRepository,
) {

    suspend fun await(name: String): Result = withNonCancellableContext {
        try {
            val id = chapterTagRepository.insert(name = name)
            Result.Success(ChapterTag(id = id, name = name))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data class Success(val chapterTag: ChapterTag) : Result
        data class InternalError(val error: Throwable) : Result
    }
}
