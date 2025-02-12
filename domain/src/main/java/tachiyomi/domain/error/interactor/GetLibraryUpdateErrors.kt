package tachiyomi.domain.error.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.error.model.LibraryUpdateError
import tachiyomi.domain.error.repository.LibraryUpdateErrorRepository

class GetLibraryUpdateErrors(
    private val libraryUpdateErrorRepository: LibraryUpdateErrorRepository,
) {

    fun subscribe(): Flow<List<LibraryUpdateError>> {
        return libraryUpdateErrorRepository.getAllAsFlow()
    }

    suspend fun await(): List<LibraryUpdateError> {
        return libraryUpdateErrorRepository.getAll()
    }
}
