package tachiyomi.domain.chapterTag.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository
import tachiyomi.domain.library.service.LibraryPreferences

class DeleteChapterTag(
    private val chapterTagRepository: ChapterTagRepository,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend fun await(tagId: Long) = withNonCancellableContext {
        try {
            chapterTagRepository.delete(tagId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        // DB rows referencing the tag (assignments + per-manga filters) are removed by the repository
        // in the same transaction; only the library-filter preferences need scrubbing.
        val tagPreferences = listOf(
            libraryPreferences.filterChapterTagsInclude(),
            libraryPreferences.filterChapterTagsExclude(),
        )
        val tagIdString = tagId.toString()
        tagPreferences.forEach { preference ->
            val ids = preference.get()
            if (tagIdString !in ids) return@forEach
            preference.set(ids.minus(tagIdString))
        }

        Result.Success
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
