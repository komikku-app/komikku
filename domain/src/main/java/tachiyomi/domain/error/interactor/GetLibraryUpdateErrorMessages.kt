package tachiyomi.domain.error.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.error.model.LibraryUpdateErrorMessage
import tachiyomi.domain.error.repository.LibraryUpdateErrorMessageRepository

class GetLibraryUpdateErrorMessages(
    private val libraryUpdateErrorMessageRepository: LibraryUpdateErrorMessageRepository,
) {

    fun subscribe(): Flow<List<LibraryUpdateErrorMessage>> {
        return libraryUpdateErrorMessageRepository.getAllAsFlow()
    }

    suspend fun await(): List<LibraryUpdateErrorMessage> {
        return libraryUpdateErrorMessageRepository.getAll()
    }
}
