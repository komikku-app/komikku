package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.backupSavedSearchMapper
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SavedSearchBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupSavedSearch> {
        return handler.awaitList { saved_searchQueries.selectAll(backupSavedSearchMapper) }
    }
}
