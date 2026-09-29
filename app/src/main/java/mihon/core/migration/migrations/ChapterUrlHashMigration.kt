package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.download.service.DownloadPreferences

/**
 * Old installs relied on chapter URL hashing being implicitly enabled prior to
 * this preference existing. Since version-gated migrations never run for a
 * fresh install (only for upgrades), this only defaults the preference to
 * true for existing users who haven't explicitly set it, preserving their
 * previous download folder naming behavior.
 */
class ChapterUrlHashMigration : Migration {
    override val version: Float = 81f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val downloadPreferences = migrationContext.get<DownloadPreferences>() ?: return@withIOContext false

        val includeChapterUrlHash = downloadPreferences.includeChapterUrlHash()
        if (!includeChapterUrlHash.isSet()) {
            includeChapterUrlHash.set(true)
        }

        return@withIOContext true
    }
}
