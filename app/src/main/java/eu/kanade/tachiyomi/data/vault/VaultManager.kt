package eu.kanade.tachiyomi.data.vault

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import logcat.LogPriority
import tachiyomi.core.common.Constants
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.vault.service.VaultPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.system.exitProcess

/**
 * Switches between the standard library and the private vault.
 *
 * The switch itself is always done through an app restart: the database driver, cover cache and
 * download directory are selected once at startup from the one-shot handoff, so no query or
 * transaction can ever run against the wrong database.
 *
 * The vault is a process-scoped session. The persisted flag only covers the restart that enters
 * the vault; any other process start (killed app, crash, reboot) returns to the standard library.
 */
object VaultManager {

    fun isDeviceSecure(context: Context): Boolean {
        if (context.getSystemService<KeyguardManager>()?.isDeviceSecure != true) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true

        return BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Critical writes must never be interrupted by the process exit that follows a profile switch.
     */
    fun canSwitch(context: Context): Boolean {
        return !BackupRestoreJob.isRunning(context) &&
            !BackupCreateJob.isManualJobRunning(context) &&
            !SyncDataJob.isRunning(context)
    }

    /**
     * Reads and clears the one-shot handoff written by [enter].
     *
     * Must only be called in the main app process, before the database driver is created.
     */
    fun consumePendingEnter(context: Context): Boolean {
        val pending = Injekt.get<VaultPreferences>().pendingEnter().get()

        // commit() instead of apply(): the handoff must not survive a process restart.
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit(commit = true) {
                putBoolean(Preference.appStateKey(VaultPreferences.KEY_PENDING_ENTER), false)
                // Remove the persisted active flag used by builds before the one-shot handoff.
                remove(Preference.appStateKey(LEGACY_ACTIVE_KEY))
            }

        return pending
    }

    fun enter(context: Context) {
        setPendingEnter(context, pending = true)
    }

    fun lock(context: Context) {
        logcat(LogPriority.INFO, tag = "VaultManager") { "Switching to the standard library" }
        setPendingEnter(context, pending = false)
    }

    private fun setPendingEnter(context: Context, pending: Boolean) {
        // commit() instead of apply(): the process is killed right after this call.
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit(commit = true) {
                putBoolean(Preference.appStateKey(VaultPreferences.KEY_PENDING_ENTER), pending)
            }

        restart(context)
    }

    private fun restart(context: Context) {
        val intent = Intent()
            .setClassName(context, Constants.MAIN_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }
}

private const val LEGACY_ACTIVE_KEY = "vault_active"
