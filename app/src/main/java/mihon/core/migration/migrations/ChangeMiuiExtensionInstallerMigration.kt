package mihon.core.migration.migrations

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.util.system.DeviceUtil
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class ChangeMiuiExtensionInstallerMigration : Migration {
    override val version: Float = 27f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val basePreferences = migrationContext.get<BasePreferences>() ?: return@withIOContext false
        if (
            DeviceUtil.isMiui &&
            basePreferences.extensionInstaller().get() == BasePreferences.ExtensionInstaller
                .PACKAGEINSTALLER
        ) {
            basePreferences.extensionInstaller().set(BasePreferences.ExtensionInstaller.LEGACY)
        }

        return@withIOContext true
    }
}
