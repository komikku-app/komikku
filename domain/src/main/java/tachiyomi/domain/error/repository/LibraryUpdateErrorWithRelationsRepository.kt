package tachiyomi.domain.error.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.error.model.LibraryUpdateErrorWithRelations

interface LibraryUpdateErrorWithRelationsRepository {

    fun subscribeAll(): Flow<List<LibraryUpdateErrorWithRelations>>
}
