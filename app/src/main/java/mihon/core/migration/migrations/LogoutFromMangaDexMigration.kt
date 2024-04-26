package mihon.core.migration.migrations

import eu.kanade.tachiyomi.data.track.TrackerManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class LogoutFromMangaDexMigration : Migration {
    override val version: Float = 45f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        // Force MangaDex log out due to login flow change
        migrationContext.get<TrackerManager>()?.mdList?.logout()

        return@withIOContext true
    }
}
