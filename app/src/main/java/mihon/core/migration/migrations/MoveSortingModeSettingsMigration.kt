package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.category.CategoryMapper
import tachiyomi.domain.library.service.LibraryPreferences

class MoveSortingModeSettingsMigration : Migration {
    override val version: Float = 38f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return@withIOContext false
        val handler = migrationContext.get<DatabaseHandler>() ?: return@withIOContext false
        // Handle renamed enum values
        val newSortingMode = when (
            val oldSortingMode = prefs.getString(libraryPreferences.sortingMode().key(), "ALPHABETICAL")
        ) {
            "LAST_CHECKED" -> "LAST_MANGA_UPDATE"
            "UNREAD" -> "UNREAD_COUNT"
            "DATE_FETCHED" -> "CHAPTER_FETCH_DATE"
            "DRAG_AND_DROP" -> "ALPHABETICAL"
            else -> oldSortingMode
        }
        prefs.edit {
            putString(libraryPreferences.sortingMode().key(), newSortingMode)
        }
        handler.await(true) {
            categoriesQueries.getCategories(CategoryMapper::mapCategory).executeAsList()
                .filter { (it.flags and 0b00111100L) == 0b00100000L }
                .forEach {
                    categoriesQueries.update(
                        categoryId = it.id,
                        flags = it.flags and 0b00111100L.inv(),
                        name = null,
                        order = null,
                    )
                }
        }

        return@withIOContext true
    }
}
