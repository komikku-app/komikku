package tachiyomi.domain.error.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.error.interactor.DeleteLibraryUpdateErrors.Result
import tachiyomi.domain.error.model.LibraryUpdateError
import tachiyomi.domain.error.repository.LibraryUpdateErrorRepository

class InsertLibraryUpdateErrors(
    private val libraryUpdateErrorRepository: LibraryUpdateErrorRepository,
) {
    suspend fun upsert(libraryUpdateError: LibraryUpdateError) = withNonCancellableContext {
        try {
            libraryUpdateErrorRepository.upsert(libraryUpdateError)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }

    suspend fun insert(libraryUpdateError: LibraryUpdateError) = withNonCancellableContext {
        try {
            libraryUpdateErrorRepository.insert(libraryUpdateError)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }

    suspend fun insertAll(libraryUpdateErrors: List<LibraryUpdateError>) = withNonCancellableContext {
        try {
            libraryUpdateErrorRepository.insertAll(libraryUpdateErrors)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }
}
