package tachiyomi.data.error

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.error.model.DatabaseErrorCount
import tachiyomi.domain.error.repository.DatabaseErrorRepository

class DatabaseErrorRepositoryImpl(
    private val handler: DatabaseHandler,
) : DatabaseErrorRepository {

    override fun count(): Flow<List<DatabaseErrorCount>> {
        return handler.subscribeToList {
            mangasQueries.countDuplicateLibraryMangaBySourceAndUrl { url, source, count ->
                DatabaseErrorCount(url, source, count.toInt())
            }
        }
    }
}
