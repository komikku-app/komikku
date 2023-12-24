package eu.kanade.tachiyomi.data.backup.create.creators

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.backup.model.BackupSavedSearch
import tachiyomi.domain.backup.model.backupSavedSearchMapper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SavedSearchBackupCreator(
    private val handler: DatabaseHandler = Injekt.get()
) {

    suspend fun backupSavedSearches(): List<BackupSavedSearch> {
        return handler.awaitList { saved_searchQueries.selectAll(backupSavedSearchMapper) }
    }
}
