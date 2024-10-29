package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.UnsortedPreferences

class IntegratedHentaiMigration : Migration {
    override val version: Float = 71f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val unsortedPreferences = migrationContext.get<UnsortedPreferences>() ?: return@withIOContext false
        unsortedPreferences.isHentaiEnabled().set(true)
        return@withIOContext true
    }
}
