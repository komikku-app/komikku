package mihon.core.migration.migrations

import android.app.Application
import android.widget.Toast
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.util.system.toast
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext

class MoveEncryptionSettingsToAppStateMigration : Migration {
    override val version: Float = 66f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return@withIOContext false
        if (prefs.getBoolean(Preference.privateKey("encrypt_database"), false)) {
            withUIContext {
                context.toast(
                    "Restart the app to load your encrypted library",
                    Toast.LENGTH_LONG,
                )
            }
        }

        val appStatePrefsToReplace = listOf(
            "__PRIVATE_sql_password",
            "__PRIVATE_encrypt_database",
            "__PRIVATE_cbz_password",
        )

        MigrateUtils.replacePreferences(
            preferenceStore = preferenceStore,
            filterPredicate = { it.key in appStatePrefsToReplace },
            newKey = { Preference.appStateKey(it.replace("__PRIVATE_", "").trim()) },
        )

        return@withIOContext true
    }
}
