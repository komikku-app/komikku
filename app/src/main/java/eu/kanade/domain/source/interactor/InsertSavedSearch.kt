package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.repository.SavedSearchRepository
import eu.kanade.tachiyomi.util.system.logcat
import exh.savedsearches.models.SavedSearch
import logcat.LogPriority
import logcat.asLog

class InsertSavedSearch(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun await(savedSearch: SavedSearch): Long? {
        return try {
            savedSearchRepository.insert(savedSearch)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.asLog() }
            null
        }
    }

    suspend fun awaitAll(savedSearch: List<SavedSearch>) {
        try {
            savedSearchRepository.insertAll(savedSearch)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.asLog() }
        }
    }
}
