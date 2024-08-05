package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import exh.util.nullIfBlank
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SavedSearchRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
) {
    suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        if (backupSavedSearches.isEmpty()) return

        // KMK -->
        handler.await(true) {
            // KMK <--
            val currentSavedSearches = handler.awaitList {
                // KMK -->
                // saved_searchQueries.selectNamesAndSources()
                saved_searchQueries.selectAll()
                // KMK <--
            }

            backupSavedSearches.filter { backupSavedSearch ->
                currentSavedSearches.none { currentSavedSearch ->
                    currentSavedSearch.source == backupSavedSearch.source &&
                        currentSavedSearch.name == backupSavedSearch.name &&
                        // KMK -->
                        currentSavedSearch.query.orEmpty() == backupSavedSearch.query &&
                        (currentSavedSearch.filters_json ?: "[]") == backupSavedSearch.filterList
                    // KMK <--
                }
            }.forEach { backupSavedSearch ->
                saved_searchQueries.insert(
                    source = backupSavedSearch.source,
                    name = backupSavedSearch.name,
                    query = backupSavedSearch.query.nullIfBlank(),
                    filtersJson = backupSavedSearch.filterList.nullIfBlank()
                        ?.takeUnless { it == "[]" },
                )
            }
        }
    }
}
