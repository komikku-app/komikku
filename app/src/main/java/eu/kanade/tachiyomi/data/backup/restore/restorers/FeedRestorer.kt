package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import exh.EXHMigrations
import exh.util.nullIfBlank
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FeedRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
) {
    suspend fun restoreFeeds(backupFeeds: List<BackupFeed>) {
        if (backupFeeds.isEmpty()) return

        handler.await(true) {
            val currentFeeds = handler.awaitList {
                feed_saved_searchQueries.selectAllFeedWithSavedSearch()
            }
            val currentSavedSearches = handler.awaitList {
                saved_searchQueries.selectAll()
            }

            backupFeeds.map {
                // KMK -->
                EXHMigrations.migrateBackupFeed(it)
                // KMK <--
            }.filter { backupFeed ->
                // Filter out source's global Popular/Latest feed already existed
                backupFeed.savedSearch == null &&
                    currentFeeds.none { currentFeed ->
                        currentFeed.source == backupFeed.source && backupFeed.global
                    } ||
                    // Filter out feed with saveSearch already existed (both global/non-global)
                    backupFeed.savedSearch != null &&
                    currentFeeds.none { currentFeed ->
                        currentFeed.source == backupFeed.source &&
                            currentFeed.global == backupFeed.global &&
                            currentFeed.name == backupFeed.savedSearch.name &&
                            currentFeed.query.orEmpty() == backupFeed.savedSearch.query &&
                            (currentFeed.filters_json ?: "[]") == backupFeed.savedSearch.filterList
                    }
            }.forEach { backupFeed ->
                val savedSearchId = backupFeed.savedSearch?.let {
                    val existedSavedSearchId = currentSavedSearches.find { currentSavedSearch ->
                        currentSavedSearch.source == backupFeed.source &&
                            currentSavedSearch.name == backupFeed.savedSearch.name &&
                            currentSavedSearch.query.orEmpty() == backupFeed.savedSearch.query &&
                            (currentSavedSearch.filters_json ?: "[]") == backupFeed.savedSearch.filterList
                    }?._id

                    existedSavedSearchId ?: handler.awaitOneExecutable(true) {
                        // Just in case, trying to create the associated saved_search
                        saved_searchQueries.insert(
                            source = backupFeed.source,
                            name = backupFeed.savedSearch.name,
                            query = backupFeed.savedSearch.query.nullIfBlank(),
                            filtersJson = backupFeed.savedSearch.filterList.nullIfBlank()
                                ?.takeUnless { it == "[]" },
                        )
                        saved_searchQueries.selectLastInsertedRowId()
                    }
                }

                feed_saved_searchQueries.insert(
                    sourceId = backupFeed.source,
                    savedSearch = savedSearchId,
                    global = backupFeed.global,
                )
            }
        }
    }
}
