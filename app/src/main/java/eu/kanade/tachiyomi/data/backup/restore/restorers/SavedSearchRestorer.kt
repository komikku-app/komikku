package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import exh.util.nullIfBlank
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SavedSearchRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    // KMK -->
    private val feedRestorer: FeedRestorer = FeedRestorer(),
    // KMK <--
) {
    suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        if (backupSavedSearches.isEmpty()) return

        // KMK -->
        handler.await(true) {
            // KMK <--
            val currentSavedSearches = handler.awaitList {
                saved_searchQueries.selectNamesAndSources()
            }

            backupSavedSearches.filter { backupSavedSearch ->
                currentSavedSearches.none { it.source == backupSavedSearch.source && it.name == backupSavedSearch.name }
            }.forEach { backupSavedSearch ->
                // KMK -->
                val savedSearchId = handler.awaitOneExecutable(true) {
                    // KMK <--
                    saved_searchQueries.insert(
                        source = backupSavedSearch.source,
                        name = backupSavedSearch.name,
                        query = backupSavedSearch.query.nullIfBlank(),
                        filtersJson = backupSavedSearch.filterList.nullIfBlank()
                            ?.takeUnless { it == "[]" },
                    )
                    // KMK -->
                    saved_searchQueries.selectLastInsertedRowId()
                }

                feedRestorer.restoreFeeds(backupSavedSearch.backupFeeds, savedSearchId)
                // KMK <--
            }
        }
    }
}
