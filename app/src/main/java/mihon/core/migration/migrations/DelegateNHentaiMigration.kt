package mihon.core.migration.migrations

import exh.source.NHENTAI_OLD_ID
import exh.source.NHENTAI_SOURCE_ID
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class DelegateNHentaiMigration : Migration {
    override val version: Float = 6f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        MigrateUtils.updateSourceId(migrationContext, NHENTAI_SOURCE_ID, NHENTAI_OLD_ID)

        return@withIOContext true
    }
}
