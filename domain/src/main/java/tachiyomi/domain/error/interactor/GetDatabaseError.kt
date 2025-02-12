package tachiyomi.domain.error.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.error.model.DatabaseErrorCount
import tachiyomi.domain.error.repository.DatabaseErrorRepository

class GetDatabaseError(
    private val databaseErrorRepository: DatabaseErrorRepository,
) {

    fun count(): Flow<List<DatabaseErrorCount>> {
        return databaseErrorRepository.count()
    }
}
