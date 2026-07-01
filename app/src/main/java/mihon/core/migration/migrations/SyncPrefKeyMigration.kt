package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference

/**
 * Migrates sync preference storage keys to namespaced forms so that
 * connection keys ("connection_*") and sync-toggle keys ("sync_*") no longer
 * collide with unrelated preferences.
 */
class SyncPrefKeyMigration : Migration {
    override val version: Float = 80f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val stringRenames = listOf(
            // Sync service connection
            "sync_client_host" to "connection_sync_client_host",
            "sync_client_api_key" to "connection_sync_client_api_key",
            // WebDAV connection (KMK)
            "webdav_url" to "connection_webdav_url",
            "webdav_username" to "connection_webdav_username",
            "webdav_password" to "connection_webdav_password",
            "webdav_folder" to "connection_webdav_folder",
            // Google Drive tokens — appStateKey() prepends "__APP_STATE_"
            Preference.appStateKey("google_drive_access_token") to Preference.appStateKey("connection_google_drive_access_token"),
            Preference.appStateKey("google_drive_refresh_token") to Preference.appStateKey("connection_google_drive_refresh_token"),
        )

        val booleanRenames = listOf(
            // Core sync toggles
            "library_entries" to "sync_library_entries",
            "categories" to "sync_categories",
            "chapters" to "sync_chapters",
            "tracking" to "sync_tracking",
            "history" to "sync_history",
            "appSettings" to "sync_appSettings",
            "extensionRepoSettings" to "sync_extensionStores",
            "sourceSettings" to "sync_sourceSettings",
            "privateSettings" to "sync_privateSettings",
            // SY sync toggles
            "customInfo" to "sync_customInfo",
            "readEntries" to "sync_readEntries",
            "savedSearchesFeeds" to "sync_savedSearchesFeeds",
        )

        prefs.edit {
            stringRenames.forEach { (oldKey, newKey) ->
                if (prefs.contains(oldKey)) {
                    putString(newKey, prefs.getString(oldKey, null))
                    remove(oldKey)
                }
            }
            booleanRenames.forEach { (oldKey, newKey) ->
                if (prefs.contains(oldKey)) {
                    putBoolean(newKey, prefs.getBoolean(oldKey, true))
                    remove(oldKey)
                }
            }
        }

        return true
    }
}
