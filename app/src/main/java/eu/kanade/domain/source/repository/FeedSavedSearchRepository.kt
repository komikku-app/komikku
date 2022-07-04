package eu.kanade.domain.source.repository

import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.flow.Flow

interface FeedSavedSearchRepository {

    suspend fun getGlobal(): List<FeedSavedSearch>

    fun getGlobalAsFlow(): Flow<List<FeedSavedSearch>>

    suspend fun getGlobalFeedSavedSearch(): List<SavedSearch>

    suspend fun countGlobal(): Long

    suspend fun getBySourceId(sourceId: Long): List<FeedSavedSearch>

    fun getBySourceIdAsFlow(sourceId: Long): Flow<List<FeedSavedSearch>>

    suspend fun getBySourceIdFeedSavedSearch(sourceId: Long): List<SavedSearch>

    suspend fun countBySourceId(sourceId: Long): Long

    suspend fun delete(feedSavedSearchId: Long)

    suspend fun insert(feedSavedSearch: FeedSavedSearch): Long?

    suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>)
}
