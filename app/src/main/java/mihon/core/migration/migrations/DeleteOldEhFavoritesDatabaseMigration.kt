package mihon.core.migration.migrations

import android.app.Application
import exh.log.xLogE
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import java.io.File

class DeleteOldEhFavoritesDatabaseMigration : Migration {
    override val version: Float = 24f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        try {
            sequenceOf(
                "fav-sync",
                "fav-sync.management",
                "fav-sync.lock",
                "fav-sync.note",
            ).map {
                File(context.filesDir, it)
            }.filter(File::exists).forEach {
                if (it.isDirectory) {
                    it.deleteRecursively()
                } else {
                    it.delete()
                }
            }
        } catch (e: Exception) {
            xLogE("Failed to delete old favorites database", e)
        }

        return@withIOContext true
    }
}
