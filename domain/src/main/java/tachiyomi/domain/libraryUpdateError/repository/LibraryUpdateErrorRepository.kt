package tachiyomi.domain.libraryUpdateError.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError

interface LibraryUpdateErrorRepository {

    suspend fun getAll(): List<LibraryUpdateError>

    fun getAllAsFlow(): Flow<List<LibraryUpdateError>>

    suspend fun deleteAll()

    suspend fun delete(errorIds: List<Long>)

    suspend fun deleteMangaError(mangaIds: List<Long>)

    suspend fun cleanUnrelevantMangaErrors()

    suspend fun upsert(libraryUpdateError: LibraryUpdateError)

    suspend fun insert(libraryUpdateError: LibraryUpdateError)

    suspend fun insertAll(libraryUpdateErrors: List<LibraryUpdateError>)
}
