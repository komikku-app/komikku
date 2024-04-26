package mihon.core.migration.migrations

import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class SetupSyncDataMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<App>() ?: return false
        SyncDataJob.setupTask(context)
        return true
    }
}
