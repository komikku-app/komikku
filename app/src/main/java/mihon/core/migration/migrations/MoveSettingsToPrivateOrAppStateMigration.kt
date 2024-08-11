package mihon.core.migration.migrations

import android.app.Application
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext

class MoveSettingsToPrivateOrAppStateMigration : Migration {
    override val version: Float = 59f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return@withIOContext false
        val prefsToReplace = listOf(
            "pref_download_only",
            "incognito_mode",
            "last_catalogue_source",
            "trusted_signatures",
            "last_app_closed",
            "library_update_last_timestamp",
            "library_unseen_updates_count",
            "last_used_category",
            "last_app_check",
            "last_ext_check",
            "last_version_code",
            "skip_pre_migration",
            "eh_auto_update_stats",
            "storage_dir",
        )
        MigrateUtils.replacePreferences(
            preferenceStore = preferenceStore,
            filterPredicate = { it.key in prefsToReplace },
            newKey = { Preference.appStateKey(it) },
        )

        val privatePrefsToReplace = listOf(
            "sql_password",
            "encrypt_database",
            "cbz_password",
            "password_protect_downloads",
            "eh_ipb_member_id",
            "enable_exhentai",
            "eh_ipb_member_id",
            "eh_ipb_pass_hash",
            "eh_igneous",
            "eh_ehSettingsProfile",
            "eh_exhSettingsProfile",
            "eh_settingsKey",
            "eh_sessionCookie",
            "eh_hathPerksCookie",
        )

        MigrateUtils.replacePreferences(
            preferenceStore = preferenceStore,
            filterPredicate = { it.key in privatePrefsToReplace },
            newKey = { Preference.privateKey(it) },
        )

        // Deleting old download cache index files, but might as well clear it all out
        context.cacheDir.deleteRecursively()

        return@withIOContext true
    }
}
