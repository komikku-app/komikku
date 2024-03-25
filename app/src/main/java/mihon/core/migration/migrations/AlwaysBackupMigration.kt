package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.backup.service.BackupPreferences

class AlwaysBackupMigration : Migration {
    override val version: Float = 40f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val backupPreferences = migrationContext.get<BackupPreferences>() ?: return@withIOContext false
        if (backupPreferences.backupInterval().get() == 0) {
            backupPreferences.backupInterval().set(12)
        }

        return@withIOContext true
    }
}
