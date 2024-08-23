package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.backupFeedMapper
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FeedBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
) {

    /**
     * Backup:
     * - Global Popular/Latest feeds
     * - Global feeds from saved searches
     * - Source's feeds from saved searches
     */
    suspend operator fun invoke(): List<BackupFeed> {
        return handler.awaitList { feed_saved_searchQueries.selectAllFeedWithSavedSearch(backupFeedMapper) }
    }
}
