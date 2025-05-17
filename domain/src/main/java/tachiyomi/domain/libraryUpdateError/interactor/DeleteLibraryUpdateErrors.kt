package tachiyomi.domain.libraryUpdateError.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository

class DeleteLibraryUpdateErrors(
    private val libraryUpdateErrorRepository: LibraryUpdateErrorRepository,
) {

    suspend fun deleteAll() = withNonCancellableContext {
        try {
            libraryUpdateErrorRepository.deleteAll()
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }

    suspend fun delete(errorIds: List<Long>) = withNonCancellableContext {
        try {
            libraryUpdateErrorRepository.delete(errorIds)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }

    suspend fun deleteMangaError(mangaIds: List<Long>) = withNonCancellableContext {
        try {
            libraryUpdateErrorRepository.deleteMangaError(mangaIds)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }

    suspend fun cleanUnrelevantMangaErrors() = withNonCancellableContext {
        try {
            libraryUpdateErrorRepository.cleanUnrelevantMangaErrors()
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
