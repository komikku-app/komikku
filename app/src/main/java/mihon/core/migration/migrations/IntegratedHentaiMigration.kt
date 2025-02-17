package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.UnsortedPreferences

class IntegratedHentaiMigration : Migration {
    override val version: Float = 71f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val isHentaiEnabled = migrationContext.get<UnsortedPreferences>()?.isHentaiEnabled()
            ?: return@withIOContext false
        if (!isHentaiEnabled.isSet() || isHentaiEnabled.get()) {
            isHentaiEnabled.set(true)
        }
        return@withIOContext true
    }
}
