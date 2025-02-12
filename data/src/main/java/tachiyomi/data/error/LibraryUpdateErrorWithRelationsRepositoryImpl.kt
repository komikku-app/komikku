package tachiyomi.data.error

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.error.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.error.repository.LibraryUpdateErrorWithRelationsRepository

class LibraryUpdateErrorWithRelationsRepositoryImpl(
    private val handler: DatabaseHandler,
) : LibraryUpdateErrorWithRelationsRepository {

    override fun subscribeAll(): Flow<List<LibraryUpdateErrorWithRelations>> {
        return handler.subscribeToList {
            libraryUpdateErrorViewQueries.errors(
                libraryUpdateErrorWithRelationsMapper,
            )
        }
    }
}
