package mihon.core.migration.migrations

import eu.kanade.tachiyomi.source.online.all.NHentai
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class DelegateNHentaiMigration : Migration {
    override val version: Float = 6f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        MigrateUtils.updateSourceId(migrationContext, NHentai.otherId, 6907)

        return@withIOContext true
    }
}
