package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class MoveCoverOnlyGridSettingMigration : Migration {
    override val version: Float = 28f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getString("pref_display_mode_library", null) == "NO_TITLE_GRID") {
            prefs.edit(commit = true) {
                putString("pref_display_mode_library", "COVER_ONLY_GRID")
            }
        }

        return@withIOContext true
    }
}
