package mihon.core.migration.migrations

import eu.kanade.tachiyomi.App
import exh.eh.EHentaiUpdateWorker
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class SetupEHentaiUpdateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<App>() ?: return false
        EHentaiUpdateWorker.scheduleBackground(context)
        return true
    }
}
