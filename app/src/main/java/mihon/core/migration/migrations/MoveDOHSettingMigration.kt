package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class MoveDOHSettingMigration : Migration {
    override val version: Float = 14f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val networkPreferences = migrationContext.get<NetworkPreferences>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // Migrate DNS over HTTPS setting
        val wasDohEnabled = prefs.getBoolean("enable_doh", false)
        if (wasDohEnabled) {
            prefs.edit {
                putInt(networkPreferences.dohProvider().key(), PREF_DOH_CLOUDFLARE)
                remove("enable_doh")
            }
        }

        return@withIOContext true
    }
}
