package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FeedRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
) {
    suspend fun restoreFeeds(backupFeeds: List<BackupFeed>, savedSearchId: Long? = null) {
        if (backupFeeds.isEmpty()) return

        handler.await(true) {
            val currentFeeds = handler.awaitList {
                feed_saved_searchQueries.selectAllGlobalNonSavedSearch()
            }

            backupFeeds.filter { backupFeed ->
                // Filter if source's global Popular/Latest feed already existed
                currentFeeds.none {
                    it.source == backupFeed.source && backupFeed.global
                }
            }.forEach {
                feed_saved_searchQueries.insert(
                    sourceId = it.source,
                    savedSearch = savedSearchId,
                    global = it.global,
                )
            }
        }
    }
}
