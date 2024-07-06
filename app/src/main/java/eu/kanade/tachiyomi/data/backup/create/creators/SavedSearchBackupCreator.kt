package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.backupFeedMapper
import eu.kanade.tachiyomi.data.backup.models.backupSavedSearchMapper
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SavedSearchBackupCreator(
    private val handler: DatabaseHandler = Injekt.get()
) {

    suspend fun backupSavedSearches(): List<BackupSavedSearch> {
        // KMK -->
        // return handler.awaitList { saved_searchQueries.selectAll(backupSavedSearchMapper) }
        val savedSearches = handler.awaitList { saved_searchQueries.selectAll() }
        val feedSavedSearches =
            handler.awaitList { feed_saved_searchQueries.selectAllFeedHasSavedSearch() }

        return savedSearches.map { savedSearch ->
            val feeds = feedSavedSearches.filter { it.saved_search == savedSearch._id }
            backupSavedSearchMapper(
                savedSearch._id,
                savedSearch.source,
                savedSearch.name,
                savedSearch.query,
                savedSearch.filters_json,
                feeds.map { it.backupFeedMapper() },
            )
        }
        // KMK <--
    }
}
