package tachiyomi.domain.error.interactor

import tachiyomi.domain.error.model.LibraryUpdateErrorMessage
import tachiyomi.domain.error.repository.LibraryUpdateErrorMessageRepository

class InsertLibraryUpdateErrorMessages(
    private val libraryUpdateErrorMessageRepository: LibraryUpdateErrorMessageRepository,
) {
    suspend fun get(message: String): Long? {
        return libraryUpdateErrorMessageRepository.get(message)
    }

    suspend fun insert(libraryUpdateErrorMessage: LibraryUpdateErrorMessage): Long {
        return libraryUpdateErrorMessageRepository.insert(libraryUpdateErrorMessage)
    }

    suspend fun insertAll(libraryUpdateErrorMessages: List<LibraryUpdateErrorMessage>): List<Pair<Long, String>> {
        return libraryUpdateErrorMessageRepository.insertAll(libraryUpdateErrorMessages)
    }
}
