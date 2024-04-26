package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences

class RemoveShorterLibraryUpdatesMigration : Migration {
    override val version: Float = 18f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return@withIOContext false
        val updateInterval = libraryPreferences.autoUpdateInterval().get()
        if (updateInterval == 1 || updateInterval == 2) {
            libraryPreferences.autoUpdateInterval().set(3)
        }

        return@withIOContext true
    }
}
