package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FeedRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
) {

    /**
     * Restore global Popular/Latest feeds
     */
    suspend fun restoreFeeds(backupFeeds: List<BackupFeed>) {
        if (backupFeeds.isEmpty()) return

        val currentFeeds = handler.awaitList {
            feed_saved_searchQueries.selectAllGlobalNonSavedSearch()
        }

        handler.await {
            backupFeeds.filter { backupFeed ->
                // Filter if source's global Popular/Latest feed already existed
                currentFeeds.none {
                    it.source == backupFeed.source && backupFeed.global
                }
            }.forEach {
                feed_saved_searchQueries.insert(
                    sourceId = it.source,
                    savedSearch = null,
                    global = it.global,
                )
            }
        }
    }
}
