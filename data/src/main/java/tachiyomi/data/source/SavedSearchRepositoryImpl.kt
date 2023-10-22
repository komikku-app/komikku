package tachiyomi.data.source

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

class SavedSearchRepositoryImpl(
    private val handler: DatabaseHandler,
) : SavedSearchRepository {

    override suspend fun getById(savedSearchId: Long): SavedSearch? {
        return handler.awaitOneOrNull { saved_searchQueries.selectById(savedSearchId, SavedSearchMapper::map) }
    }

    override suspend fun getBySourceId(sourceId: Long): List<SavedSearch> {
        return handler.awaitList { saved_searchQueries.selectBySource(sourceId, SavedSearchMapper::map) }
    }

    override fun getBySourceIdAsFlow(sourceId: Long): Flow<List<SavedSearch>> {
        return handler.subscribeToList { saved_searchQueries.selectBySource(sourceId, SavedSearchMapper::map) }
    }

    override suspend fun delete(savedSearchId: Long) {
        handler.await { saved_searchQueries.deleteById(savedSearchId) }
    }

    override suspend fun insert(savedSearch: SavedSearch): Long {
        return handler.awaitOneExecutable(true) {
            saved_searchQueries.insert(
                savedSearch.source,
                savedSearch.name,
                savedSearch.query,
                savedSearch.filtersJson,
            )
            saved_searchQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun insertAll(savedSearch: List<SavedSearch>) {
        handler.await(true) {
            savedSearch.forEach {
                saved_searchQueries.insert(
                    it.source,
                    it.name,
                    it.query,
                    it.filtersJson,
                )
            }
        }
    }
}
