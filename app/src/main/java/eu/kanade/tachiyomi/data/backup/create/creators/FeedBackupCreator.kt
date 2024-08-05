package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.backupFeedMapper
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FeedBackupCreator(
    private val handler: DatabaseHandler = Injekt.get()
) {

    /**
     * Backup global Popular/Latest feeds
     */
    suspend fun backupFeeds(): List<BackupFeed> {
        return handler.awaitList { feed_saved_searchQueries.selectAllFeedWithSavedSearch(backupFeedMapper) }
    }
}
