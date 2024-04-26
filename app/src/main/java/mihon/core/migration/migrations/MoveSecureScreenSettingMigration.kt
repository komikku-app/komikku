package mihon.core.migration.migrations

import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class MoveSecureScreenSettingMigration : Migration {
    override val version: Float = 27f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<App>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val securityPreferences = migrationContext.get<SecurityPreferences>() ?: return@withIOContext false
        val oldSecureScreen = prefs.getBoolean("secure_screen", false)
        if (oldSecureScreen) {
            securityPreferences.secureScreen().set(SecurityPreferences.SecureScreenMode.ALWAYS)
        }

        return@withIOContext true
    }
}
