package mihon.core.migration.migrations

import android.app.Application
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class MoveCacheToDiskSettingMigration : Migration {
    override val version: Float = 66f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val readerPreferences = migrationContext.get<ReaderPreferences>() ?: return@withIOContext false
        val cacheImagesToDisk = prefs.getBoolean("cache_archive_manga_on_disk", false)
        if (cacheImagesToDisk) {
            readerPreferences.archiveReaderMode().set(ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK)
        }

        return@withIOContext true
    }
}
