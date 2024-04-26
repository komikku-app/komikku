package mihon.core.migration.migrations

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.App
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class ResetRotationSettingMigration : Migration {
    override val version: Float = 16f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<App>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // Reset rotation to Free after replacing Lock
        if (prefs.contains("pref_rotation_type_key")) {
            prefs.edit {
                putInt("pref_rotation_type_key", 1)
            }
        }

        return@withIOContext true
    }
}
