package tachiyomi.domain.error.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.error.model.DatabaseErrorCount

interface DatabaseErrorRepository {

    fun count(): Flow<List<DatabaseErrorCount>>
}
