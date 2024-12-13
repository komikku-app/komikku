package mihon.core.migration.migrations

import exh.source.EH_OLD_ID
import exh.source.EH_SOURCE_ID
import exh.source.EXH_OLD_ID
import exh.source.EXH_SOURCE_ID
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class EHentaiMigration : Migration {
    override val version: Float = 72f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        MigrateUtils.updateSourceId(migrationContext, EH_SOURCE_ID, EH_OLD_ID)
        MigrateUtils.updateSourceId(migrationContext, EXH_SOURCE_ID, EXH_OLD_ID)

        return@withIOContext true
    }
}
