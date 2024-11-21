package eu.kanade.tachiyomi.data.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.kmk.KMR

class AppUpdateBroadcast : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppUpdateDownloadJob.PACKAGE_INSTALLED_ACTION == intent.action) {
            val extras = intent.extras ?: return
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent = intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT)
                    context.startActivity(confirmIntent?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                    prefs.edit {
                        remove(AppUpdateDownloadJob.NOTIFY_ON_INSTALL_KEY)
                    }
                    val notifyOnInstall = extras.getBoolean(AppUpdateDownloadJob.EXTRA_NOTIFY_ON_INSTALL, true)
                    try {
                        if (notifyOnInstall) {
                            AppUpdateNotifier(context).onInstallFinished()
                        }
                    } finally {
                        AppUpdateDownloadJob.stop(context)
                    }
                }
                PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_ABORTED, PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_STORAGE -> {
                    if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                        context.toast(KMR.strings.could_not_install_update)
                        val uri = intent.getStringExtra(AppUpdateDownloadJob.EXTRA_FILE_URI) ?: return
                        val appUpdateNotifier = AppUpdateNotifier(context)
                        appUpdateNotifier.cancelInstallNotification()
                        appUpdateNotifier.onInstallError(uri.toUri())
                    }
                }
            }
        } else if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val notifyOnInstall = prefs.getBoolean(AppUpdateDownloadJob.NOTIFY_ON_INSTALL_KEY, false)
            prefs.edit {
                remove(AppUpdateDownloadJob.NOTIFY_ON_INSTALL_KEY)
            }
            if (notifyOnInstall) {
                AppUpdateNotifier(context).onInstallFinished()
            }
        }
    }
}
