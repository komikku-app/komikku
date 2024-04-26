package mihon.core.migration.migrations

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.App
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class ChangeThemeModeToUppercaseMigration : Migration {
    override val version: Float = 42f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<App>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uiPreferences = migrationContext.get<UiPreferences>() ?: return@withIOContext false
        if (uiPreferences.themeMode().isSet()) {
            prefs.edit {
                val themeMode = prefs.getString(uiPreferences.themeMode().key(), null) ?: return@edit
                putString(uiPreferences.themeMode().key(), themeMode.uppercase())
            }
        }

        return@withIOContext true
    }
}
