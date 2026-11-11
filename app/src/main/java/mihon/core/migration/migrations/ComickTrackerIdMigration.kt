package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore

// KMK -->
class ComickTrackerIdMigration : Migration {
    override val version: Float = 82f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val preferences = preferenceStore.getAll()
        val legacySuffixes = listOf(
            "pref_mangasync_username_63",
            "pref_mangasync_password_63",
            "pref_tracker_auth_expired_63",
            "track_token_63",
        )

        for (suffix in legacySuffixes) {
            val oldKey = "__PRIVATE_$suffix"
            val value = preferences[oldKey] ?: continue
            val newKey = "__PRIVATE_${suffix.removeSuffix("63")}12"
            if (newKey !in preferences) {
                when (value) {
                    is String -> preferenceStore.getString(newKey).set(value)
                    is Boolean -> preferenceStore.getBoolean(newKey).set(value)
                }
            }
            when (value) {
                is String -> preferenceStore.getString(oldKey).delete()
                is Boolean -> preferenceStore.getBoolean(oldKey).delete()
            }
        }
        return true
    }
}
// KMK <--
