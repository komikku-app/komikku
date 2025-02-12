package tachiyomi.domain.error.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.error.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.error.repository.LibraryUpdateErrorWithRelationsRepository

class GetLibraryUpdateErrorWithRelations(
    private val libraryUpdateErrorWithRelationsRepository: LibraryUpdateErrorWithRelationsRepository,
) {

    fun subscribeAll(): Flow<List<LibraryUpdateErrorWithRelations>> {
        return libraryUpdateErrorWithRelationsRepository.subscribeAll()
    }
}
