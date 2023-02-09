package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import logcat.asLog
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

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
