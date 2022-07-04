package eu.kanade.data.source

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.exh.feedSavedSearchMapper
import eu.kanade.data.exh.savedSearchMapper
import eu.kanade.domain.source.repository.FeedSavedSearchRepository
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.flow.Flow

class FeedSavedSearchRepositoryImpl(
    private val handler: DatabaseHandler,
) : FeedSavedSearchRepository {

    override suspend fun getGlobal(): List<FeedSavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectAllGlobal(feedSavedSearchMapper) }
    }

    override fun getGlobalAsFlow(): Flow<List<FeedSavedSearch>> {
        return handler.subscribeToList { feed_saved_searchQueries.selectAllGlobal(feedSavedSearchMapper) }
    }

    override suspend fun getGlobalFeedSavedSearch(): List<SavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectGlobalFeedSavedSearch(savedSearchMapper) }
    }

    override suspend fun countGlobal(): Long {
        return handler.awaitOne { feed_saved_searchQueries.countGlobal() }
    }

    override suspend fun getBySourceId(sourceId: Long): List<FeedSavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectBySource(sourceId, feedSavedSearchMapper) }
    }

    override fun getBySourceIdAsFlow(sourceId: Long): Flow<List<FeedSavedSearch>> {
        return handler.subscribeToList { feed_saved_searchQueries.selectBySource(sourceId, feedSavedSearchMapper) }
    }

    override suspend fun getBySourceIdFeedSavedSearch(sourceId: Long): List<SavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectSourceFeedSavedSearch(sourceId, savedSearchMapper) }
    }

    override suspend fun countBySourceId(sourceId: Long): Long {
        return handler.awaitOne { feed_saved_searchQueries.countSourceFeedSavedSearch(sourceId) }
    }

    override suspend fun delete(feedSavedSearchId: Long) {
        handler.await { feed_saved_searchQueries.deleteById(feedSavedSearchId) }
    }

    override suspend fun insert(feedSavedSearch: FeedSavedSearch): Long {
        return handler.awaitOne(true) {
            feed_saved_searchQueries.insert(
                feedSavedSearch.source,
                feedSavedSearch.savedSearch,
                feedSavedSearch.global,
            )
            feed_saved_searchQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>) {
        return handler.await(true) {
            feedSavedSearch.forEach {
                feed_saved_searchQueries.insert(
                    it.source,
                    it.savedSearch,
                    it.global,
                )
            }
        }
    }
}
