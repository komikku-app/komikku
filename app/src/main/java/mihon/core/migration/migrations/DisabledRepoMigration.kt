package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.source.service.SourcePreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class DisabledRepoMigration : Migration {
    override val version: Float = 80f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            val disabledRepos = prefs.getStringSet(sourcePreferences.disabledRepos().key(), emptySet()) ?: return@edit
            disabledRepos
                .map {
                    it.removeSuffix("/index.min.json").removeSuffix("/index.json")
                        .removeSuffix("/repo.json") + "/repo.json"
                }.toSet()
                .let {
                    putStringSet(sourcePreferences.disabledRepos().key(), it)
                }
        }
        return@withIOContext true
    }
}
