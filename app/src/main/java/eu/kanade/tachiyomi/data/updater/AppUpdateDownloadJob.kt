package eu.kanade.tachiyomi.data.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class AppUpdateDownloadJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = AppUpdateNotifier(context)
    private val network: NetworkHelper by injectLazy()

    override suspend fun doWork(): Result {
        val url = inputData.getString(EXTRA_DOWNLOAD_URL)
        val title = inputData.getString(EXTRA_DOWNLOAD_TITLE) ?: context.stringResource(MR.strings.app_name)

        if (url.isNullOrEmpty()) {
            return Result.failure()
        }

        setForegroundSafely()

        val notifyOnInstall = inputData.getBoolean(EXTRA_NOTIFY_ON_INSTALL, false)

        withIOContext {
            downloadApk(title, url, notifyOnInstall)
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_APP_UPDATER,
            notifier.onDownloadStarted().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    /**
     * Called to start downloading apk of new update
     *
     * @param url url location of file
     */
    private suspend fun downloadApk(
        title: String,
        url: String,
        // KMK -->
        notifyOnInstall: Boolean,
        // KMK <--
        ) {
        // Show notification download starting.
        notifier.onDownloadStarted(title)

        val progressListener = object : ProgressListener {
            // KMK -->
            // Total size of the downloading file, should be set when starting and kept over retries
            var totalSize = 0L
            // KMK <--

            // Progress of the download
            var savedProgress = 0

            // Keep track of the last notification sent to avoid posting too many.
            var lastTick = 0L

            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                // KMK -->
                val downloadedSize: Long
                if (totalSize == 0L) {
                    totalSize = contentLength
                    downloadedSize = bytesRead
                } else {
                    downloadedSize = totalSize - contentLength + bytesRead
                }
                // KMK <--
                val progress = (100 * (downloadedSize.toFloat() / totalSize)).toInt()
                val currentTime = System.currentTimeMillis()
                if (progress > savedProgress && currentTime - 200 > lastTick) {
                    savedProgress = progress
                    lastTick = currentTime
                    notifier.onProgressChange(progress)
                }
            }
        }

        try {
            // File where the apk will be saved.
            val apkFile = File(context.externalCacheDir, "update.apk")
            // KMK -->
            network.downloadFileWithResume(url, apkFile, progressListener)
            // KMK <--
            notifier.cancel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startInstalling(apkFile, notifyOnInstall)
            } else {
                notifier.promptInstall(apkFile.getUriCompat(context))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.toString() }
            val shouldCancel = e is CancellationException ||
                (e is StreamResetException && e.errorCode == ErrorCode.CANCEL)
            if (shouldCancel) {
                notifier.cancel()
            } else {
                notifier.onDownloadError(
                    url,
                    // KMK -->
                    e.message,
                    // KMK <--
                )
            }
        }
    }

    @RequiresApi(31)
    private suspend fun startInstalling(file: File, notifyOnInstall: Boolean) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val data = file.inputStream()

            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            session.openWrite("package", 0, -1).use { packageInSession ->
                data.copyTo(packageInSession)
            }
            if (notifyOnInstall) {
                PreferenceManager.getDefaultSharedPreferences(context).edit {
                    putBoolean(NOTIFY_ON_INSTALL_KEY, true)
                }
            }

            val newIntent = Intent(context, AppUpdateBroadcast::class.java)
                .setAction(PACKAGE_INSTALLED_ACTION)
                .putExtra(EXTRA_NOTIFY_ON_INSTALL, notifyOnInstall)
                .putExtra(EXTRA_FILE_URI, file.getUriCompat(context).toString())

            val pendingIntent = PendingIntent.getBroadcast(context, -10053, newIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            val statusReceiver = pendingIntent.intentSender
            session.commit(statusReceiver)
            notifier.onInstalling()
            withContext(Dispatchers.IO) {
                data.close()
            }
            launchUI {
                delay(5000)
                val hasNotification = context.notificationManager
                    .activeNotifications.any { it.id == Notifications.ID_APP_UPDATER }
                // If the package manager crashes for whatever reason (china phone)
                // set a timeout and let the user manually install
                if (packageInstaller.getSessionInfo(sessionId) == null && !hasNotification) {
                    notifier.cancelInstallNotification()
                    notifier.promptInstall(file.getUriCompat(context))
                    PreferenceManager.getDefaultSharedPreferences(context).edit {
                        remove(NOTIFY_ON_INSTALL_KEY)
                    }
                }
            }
        } catch (error: Exception) {
            // Either install package can't be found (probably bots) or there's a security exception
            // with the download manager. Nothing we can workaround.
            context.toast(error.message)
            notifier.cancelInstallNotification()
            notifier.promptInstall(file.getUriCompat(context))
            PreferenceManager.getDefaultSharedPreferences(context).edit {
                remove(NOTIFY_ON_INSTALL_KEY)
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateDownload"
        const val PACKAGE_INSTALLED_ACTION =
            "${BuildConfig.APPLICATION_ID}.SESSION_SELF_API_PACKAGE_INSTALLED"
        internal const val EXTRA_FILE_URI = "${BuildConfig.APPLICATION_ID}.AppInstaller.FILE_URI"
        internal const val EXTRA_NOTIFY_ON_INSTALL = "ACTION_ON_INSTALL"
        internal const val NOTIFY_ON_INSTALL_KEY = "notify_on_install_complete"
        private const val IDLE_RUN = "idle_run"

        const val EXTRA_DOWNLOAD_URL = "DOWNLOAD_URL"
        const val EXTRA_DOWNLOAD_TITLE = "DOWNLOAD_TITLE"

        const val ALWAYS = 0
        const val ONLY_ON_UNMETERED = 1
        const val NEVER = 2

        fun start(context: Context, url: String, title: String? = null, notifyOnInstall: Boolean = true, waitUntilIdle: Boolean = false) {
            val constraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
            )

            val request = OneTimeWorkRequestBuilder<AppUpdateDownloadJob>()
                .setConstraints(constraints)
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        EXTRA_DOWNLOAD_URL to url,
                        EXTRA_DOWNLOAD_TITLE to title,
                    ),
                )
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}
