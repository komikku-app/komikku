package mihon.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.util.system.updaterEnabled
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class SetupAppUpdateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        if (!updaterEnabled) return false

        val context = migrationContext.get<Application>() ?: return false
        AppUpdateJob.setupTask(context)
        return true
    }
}
