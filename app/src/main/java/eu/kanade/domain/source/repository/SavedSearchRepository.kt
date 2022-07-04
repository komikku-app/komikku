package eu.kanade.domain.source.repository

import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.flow.Flow

interface SavedSearchRepository {

    suspend fun getById(savedSearchId: Long): SavedSearch?

    suspend fun getBySourceId(sourceId: Long): List<SavedSearch>

    fun getBySourceIdAsFlow(sourceId: Long): Flow<List<SavedSearch>>

    suspend fun delete(savedSearchId: Long)

    suspend fun insert(savedSearch: SavedSearch): Long?

    suspend fun insertAll(savedSearch: List<SavedSearch>)
}
