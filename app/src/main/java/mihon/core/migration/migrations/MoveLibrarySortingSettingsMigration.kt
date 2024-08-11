package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences

class MoveLibrarySortingSettingsMigration : Migration {
    override val version: Float = 20f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return@withIOContext false
        try {
            val oldSortingMode = prefs.getInt(libraryPreferences.sortingMode().key(), 0 /* ALPHABETICAL */)
            val oldSortingDirection = prefs.getBoolean("library_sorting_ascending", true)

            val newSortingMode = when (oldSortingMode) {
                0 -> "ALPHABETICAL"
                1 -> "LAST_READ"
                2 -> "LAST_MANGA_UPDATE"
                3 -> "UNREAD_COUNT"
                4 -> "TOTAL_CHAPTERS"
                6 -> "LATEST_CHAPTER"
                7 -> "DRAG_AND_DROP"
                8 -> "DATE_ADDED"
                9 -> "TAG_LIST"
                10 -> "CHAPTER_FETCH_DATE"
                else -> "ALPHABETICAL"
            }

            val newSortingDirection = when (oldSortingDirection) {
                true -> "ASCENDING"
                else -> "DESCENDING"
            }

            prefs.edit(commit = true) {
                remove(libraryPreferences.sortingMode().key())
                remove("library_sorting_ascending")
            }

            prefs.edit {
                putString(libraryPreferences.sortingMode().key(), newSortingMode)
                putString("library_sorting_ascending", newSortingDirection)
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "Already done migration" }
        }

        return@withIOContext true
    }
}
