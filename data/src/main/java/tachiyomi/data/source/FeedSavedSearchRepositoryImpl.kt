package tachiyomi.data.source

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class FeedSavedSearchRepositoryImpl(
    private val handler: DatabaseHandler,
) : FeedSavedSearchRepository {

    override suspend fun getGlobal(): List<FeedSavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectAllGlobal(FeedSavedSearchMapper::map) }
    }

    override fun getGlobalAsFlow(): Flow<List<FeedSavedSearch>> {
        return handler.subscribeToList { feed_saved_searchQueries.selectAllGlobal(FeedSavedSearchMapper::map) }
    }

    override suspend fun getGlobalFeedSavedSearch(): List<SavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectGlobalFeedSavedSearch(SavedSearchMapper::map) }
    }

    override suspend fun countGlobal(): Long {
        return handler.awaitOne { feed_saved_searchQueries.countGlobal() }
    }

    override suspend fun getBySourceId(sourceId: Long): List<FeedSavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectBySource(sourceId, FeedSavedSearchMapper::map) }
    }

    override fun getBySourceIdAsFlow(sourceId: Long): Flow<List<FeedSavedSearch>> {
        return handler.subscribeToList { feed_saved_searchQueries.selectBySource(sourceId, FeedSavedSearchMapper::map) }
    }

    override suspend fun getBySourceIdFeedSavedSearch(sourceId: Long): List<SavedSearch> {
        return handler.awaitList {
            feed_saved_searchQueries.selectSourceFeedSavedSearch(sourceId, SavedSearchMapper::map)
        }
    }

    override suspend fun countBySourceId(sourceId: Long): Long {
        return handler.awaitOne { feed_saved_searchQueries.countSourceFeedSavedSearch(sourceId) }
    }

    override suspend fun delete(feedSavedSearchId: Long) {
        handler.await { feed_saved_searchQueries.deleteById(feedSavedSearchId) }
    }

    override suspend fun insert(feedSavedSearch: FeedSavedSearch): Long {
        // KMK -->
        return handler.await(true) {
            val currentFeeds = handler.awaitList {
                feed_saved_searchQueries.selectAll(FeedSavedSearchMapper::map)
            }
            val existedFeedId = currentFeeds.find { currentFeed ->
                currentFeed.source == feedSavedSearch.source &&
                    currentFeed.savedSearch == feedSavedSearch.savedSearch &&
                    currentFeed.global == feedSavedSearch.global
            }?.id

            existedFeedId
                // KMK <--
                ?: handler.awaitOneExecutable(true) {
                    feed_saved_searchQueries.insert(
                        feedSavedSearch.source,
                        feedSavedSearch.savedSearch,
                        feedSavedSearch.global,
                    )
                    feed_saved_searchQueries.selectLastInsertedRowId()
                }
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

    // KMK -->
    override suspend fun swapOrder(feed1: FeedSavedSearch, feed2: FeedSavedSearch) {
        return handler.await(true) {
            feed_saved_searchQueries.setOrder(
                id = feed2.id,
                order = feed1.feedOrder,
            )
            feed_saved_searchQueries.setOrder(
                id = feed1.id,
                order = feed2.feedOrder,
            )
        }
    }

    override suspend fun moveToBottom(feed: FeedSavedSearch) {
        return handler.await(true) {
            feed_saved_searchQueries.moveToBottom(
                id = feed.id,
            )
        }
    }
    // KMK <--
}
