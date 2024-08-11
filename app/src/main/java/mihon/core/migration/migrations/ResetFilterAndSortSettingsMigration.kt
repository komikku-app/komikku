package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences

class ResetFilterAndSortSettingsMigration : Migration {
    override val version: Float = 41f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return@withIOContext false
        val preferences = listOf(
            libraryPreferences.filterChapterByRead(),
            libraryPreferences.filterChapterByDownloaded(),
            libraryPreferences.filterChapterByBookmarked(),
            libraryPreferences.sortChapterBySourceOrNumber(),
            libraryPreferences.displayChapterByNameOrNumber(),
            libraryPreferences.sortChapterByAscendingOrDescending(),
        )

        prefs.edit {
            preferences.forEach { preference ->
                val key = preference.key()
                val value = prefs.getInt(key, Int.MIN_VALUE)
                if (value == Int.MIN_VALUE) return@forEach
                remove(key)
                putLong(key, value.toLong())
            }
        }

        return@withIOContext true
    }
}
