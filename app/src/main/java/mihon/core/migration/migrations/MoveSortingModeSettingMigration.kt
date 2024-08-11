package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences

class MoveSortingModeSettingMigration : Migration {
    override val version: Float = 39f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            val sort = prefs.getString(libraryPreferences.sortingMode().key(), null) ?: return@edit
            val direction = prefs.getString("library_sorting_ascending", "ASCENDING")!!
            putString(libraryPreferences.sortingMode().key(), "$sort,$direction")
            remove("library_sorting_ascending")
        }

        return@withIOContext true
    }
}
