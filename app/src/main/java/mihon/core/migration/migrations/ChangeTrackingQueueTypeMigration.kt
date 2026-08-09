package mihon.core.migration.migrations

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class ChangeTrackingQueueTypeMigration : Migration {
    override val version: Float = 44f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val trackingQueuePref = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)
        trackingQueuePref.all.forEach { (key, value) ->
            val stringValue = value.toString()
            if (stringValue.contains(":")) {
                val lastChapterRead = stringValue.split(":").getOrNull(1)?.toFloatOrNull()
                if (lastChapterRead != null) {
                    trackingQueuePref.edit {
                        remove(key)
                        putFloat(key, lastChapterRead)
                    }
                }
            }
        }

        return@withIOContext true
    }
}
