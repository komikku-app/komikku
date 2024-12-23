package eu.kanade.tachiyomi.data.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.net.toUri
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.kmk.KMR

class AppUpdateBroadcast : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appUpdateNotifier = AppUpdateNotifier(context)

        if (intent.action == AppUpdateDownloadJob.PACKAGE_INSTALLED_ACTION) {
            /*
             * Callback on PackageInstaller status
             */
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent = intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT)
                    context.startActivity(confirmIntent?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

                    appUpdateNotifier.cancelInstallNotification()
                    val uri = intent.getStringExtra(AppUpdateDownloadJob.EXTRA_FILE_URI) ?: return
                    val title = intent.getStringExtra(AppUpdateDownloadJob.EXTRA_DOWNLOAD_TITLE)
                    appUpdateNotifier.promptInstall(uri.toUri(), title)
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    try {
                        appUpdateNotifier.onInstallFinished()
                    } finally {
                        AppUpdateDownloadJob.stop(context)
                        appUpdateNotifier.cancelInstallNotification()
                    }
                }
                PackageInstaller.STATUS_FAILURE,
                PackageInstaller.STATUS_FAILURE_ABORTED,
                PackageInstaller.STATUS_FAILURE_BLOCKED,
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                PackageInstaller.STATUS_FAILURE_INVALID,
                PackageInstaller.STATUS_FAILURE_STORAGE,
                -> {
                    if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                        context.toast(KMR.strings.could_not_install_update)
                        val uri = intent.getStringExtra(AppUpdateDownloadJob.EXTRA_FILE_URI) ?: return
                        val title = intent.getStringExtra(AppUpdateDownloadJob.EXTRA_DOWNLOAD_TITLE)
                        appUpdateNotifier.cancelInstallNotification()
                        appUpdateNotifier.onInstallError(uri.toUri(), title)
                    }
                }
            }
        } else if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            /*
             * System broadcast that is sent when the current application package has been replaced with a new version,
             * to perform actions when app is updated or reinstalled.
             */
            appUpdateNotifier.onInstallFinished()
        }
    }
}
