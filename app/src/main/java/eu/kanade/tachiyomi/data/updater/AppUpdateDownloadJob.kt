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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import exh.log.xLogE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.release.service.AppUpdatePolicy
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.lang.ref.WeakReference
import kotlin.coroutines.cancellation.CancellationException

class AppUpdateDownloadJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = AppUpdateNotifier(context)
    private val network: NetworkHelper by injectLazy()
    private val preferences = Injekt.get<UnsortedPreferences>()

    override suspend fun doWork(): Result {
        val idleRun = inputData.getBoolean(IDLE_RUN, false)
        if (idleRun) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                return Result.failure()
            }
            if (preferences.appShouldAutoUpdate().get() == AppUpdatePolicy.ONLY_ON_WIFI &&
                context.connectivityManager.isActiveNetworkMetered
            ) {
                return Result.retry()
            }
        }

        val url = inputData.getString(EXTRA_DOWNLOAD_URL)
        val title = inputData.getString(EXTRA_DOWNLOAD_TITLE) ?: context.stringResource(MR.strings.app_name)

        if (url.isNullOrEmpty()) {
            return Result.failure()
        }

        setForegroundSafely()
        instance = WeakReference(this)

        val notifyOnInstall = inputData.getBoolean(EXTRA_NOTIFY_ON_INSTALL, true)

        withIOContext {
            downloadApk(title, url, notifyOnInstall)
        }

        instance = null

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
        notifyOnInstall: Boolean = true,
        // KMK <--
        ) = coroutineScope {
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
            if (isStopped) {
                cancel()
                return@coroutineScope
            }
            notifier.cancel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startInstalling(apkFile, title, notifyOnInstall)
            } else {
                notifier.promptInstall(apkFile.getUriCompat(context))
            }
        } catch (e: Exception) {
            xLogE("App update stopped:", e)
            val shouldCancel = e is CancellationException || isStopped ||
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
    private suspend fun startInstalling(file: File, title: String, notifyOnInstall: Boolean = true) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val data = file.inputStream()

            val installParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                installParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = packageInstaller.createSession(installParams)
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
                .putExtra(EXTRA_DOWNLOAD_TITLE, title)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val statusReceiver = pendingIntent.intentSender
            session.commit(statusReceiver)
            notifier.onInstalling()
            withContext(Dispatchers.IO) {
                data.close()
            }
//            launchUI {
//                delay(5000)
//                val hasNotification = context.notificationManager
//                    .activeNotifications.any { it.id == Notifications.ID_APP_UPDATER }
//                // If the package manager crashes for whatever reason (china phone)
//                // set a timeout and let the user manually install
//                if (packageInstaller.getSessionInfo(sessionId) == null && !hasNotification) {
//                    notifier.cancelInstallNotification()
//                    notifier.promptInstall(file.getUriCompat(context))
//                    PreferenceManager.getDefaultSharedPreferences(context).edit {
//                        remove(NOTIFY_ON_INSTALL_KEY)
//                    }
//                }
//            }
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

        private var instance: WeakReference<AppUpdateDownloadJob>? = null

        fun start(
            context: Context,
            url: String,
            title: String? = null,
            notifyOnInstall: Boolean = true,
            waitUntilIdle: Boolean = false,
        ) {
            val data = Data.Builder()
            data.putString(EXTRA_DOWNLOAD_URL, url)
            data.putString(EXTRA_DOWNLOAD_TITLE, title)
            data.putBoolean(EXTRA_NOTIFY_ON_INSTALL, notifyOnInstall)
            val request = OneTimeWorkRequestBuilder<AppUpdateDownloadJob>()
                .addTag(TAG)
                .apply {
                    if (waitUntilIdle) {
                        data.putBoolean(IDLE_RUN, true)
                        val shouldAutoUpdate = Injekt.get<UnsortedPreferences>().appShouldAutoUpdate().get()
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(
                                if (shouldAutoUpdate == AppUpdatePolicy.ALWAYS) {
                                    NetworkType.CONNECTED
                                } else {
                                    NetworkType.UNMETERED
                                },
                            )
                            .setRequiresDeviceIdle(true)
                            .build()
                        setConstraints(constraints)
                    } else {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        setConstraints(
                            Constraints(
                                requiredNetworkType = NetworkType.CONNECTED,
                            )
                        )
                    }
                    setInputData(data.build())
                }
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}
