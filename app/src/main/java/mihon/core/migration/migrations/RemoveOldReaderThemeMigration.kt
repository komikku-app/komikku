package mihon.core.migration.migrations

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class RemoveOldReaderThemeMigration : Migration {
    override val version: Float = 18f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val readerPreferences = migrationContext.get<ReaderPreferences>() ?: return@withIOContext false
        val readerTheme = readerPreferences.readerTheme().get()
        if (readerTheme == 4) {
            readerPreferences.readerTheme().set(3)
        }

        return@withIOContext true
    }
}
