package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.FeedSavedSearchRepository
import eu.kanade.tachiyomi.util.system.logcat
import exh.savedsearches.models.FeedSavedSearch
import logcat.LogPriority
import logcat.asLog

class InsertFeedSavedSearch(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(feedSavedSearch: FeedSavedSearch): Long? {
        return try {
            feedSavedSearchRepository.insert(feedSavedSearch)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.asLog() }
            null
        }
    }

    suspend fun awaitAll(feedSavedSearch: List<FeedSavedSearch>) {
        try {
            feedSavedSearchRepository.insertAll(feedSavedSearch)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.asLog() }
        }
    }
}
