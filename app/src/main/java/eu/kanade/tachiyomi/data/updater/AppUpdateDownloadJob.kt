package eu.kanade.tachiyomi.data.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.BackoffPolicy
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
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import exh.log.xLogE
import exh.source.ExhPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.service.AppUpdatePolicy
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class AppUpdateDownloadJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = AppUpdateNotifier(context)
    private val network: NetworkHelper by injectLazy()

    // KMK -->
    private val exhPreferences = Injekt.get<ExhPreferences>()
    // KMK <--

    override suspend fun doWork(): Result {
        // KMK -->
        val idleRun = inputData.getBoolean(SCHEDULED_RUN, false)
        if (idleRun) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                return Result.failure()
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val restrictions = exhPreferences.appShouldAutoUpdate().get()
                if ((
                        AppUpdatePolicy.DEVICE_ONLY_ON_WIFI in restrictions &&
                            !context.isConnectedToWifi()
                        ) ||
                    (
                        AppUpdatePolicy.DEVICE_NETWORK_NOT_METERED in restrictions &&
                            context.connectivityManager.isActiveNetworkMetered
                        )
                ) {
                    return Result.retry()
                }
            }
        }
        // KMK <--

        val url = inputData.getString(EXTRA_DOWNLOAD_URL)
        val title = inputData.getString(EXTRA_DOWNLOAD_TITLE) ?: context.stringResource(MR.strings.app_name)

        if (url.isNullOrEmpty()) {
            return Result.failure()
        }

        setForegroundSafely()
        // KMK -->
        instance = WeakReference(this)
        // KMK <--

        withIOContext {
            downloadApk(title, url)
        }

        // KMK -->
        instance = null
        // KMK <--

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
    private suspend fun downloadApk(title: String, url: String) = coroutineScope {
        // Show notification download starting.
        with(notifier) {
            onDownloadStarted(title)
                // KMK -->
                .show()
            // KMK <--
        }

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
            if (isStopped) {
                cancel()
                return@coroutineScope
            }
            // KMK <--

            notifier.cancel()
            // KMK -->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startInstalling(apkFile, title)
            } else {
                // KMK <--
                notifier.promptInstall(apkFile.getUriCompat(context))
            }
        } catch (e: Exception) {
            xLogE("App update stopped:", e)
            val shouldCancel = e is CancellationException ||
                isStopped ||
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

    // KMK -->
    @RequiresApi(31)
    private suspend fun startInstalling(file: File, title: String) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val data = file.inputStream()

            val installParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            installParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val sessionId = packageInstaller.createSession(installParams)
            val session = packageInstaller.openSession(sessionId)
            session.openWrite("package", 0, -1).use { packageInSession ->
                data.copyTo(packageInSession)
            }

            val newIntent = Intent(context, AppUpdateBroadcast::class.java)
                .setAction(PACKAGE_INSTALLED_ACTION)
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
            notifier.onInstalling(file.getUriCompat(context))
            withContext(Dispatchers.IO) {
                data.close()
            }
            launchUI {
                delay(1000)
                val hasNotification = context.notificationManager
                    .activeNotifications.any { it.id == Notifications.ID_APP_INSTALL }
                // If the package manager crashes for whatever reason (china phone)
                // set a timeout and let the user manually install
                if (packageInstaller.getSessionInfo(sessionId) == null && !hasNotification) {
                    notifier.cancelInstallNotification()
                    notifier.promptInstall(file.getUriCompat(context))
                }
            }
        } catch (error: Exception) {
            // Either install package can't be found (probably bots) or there's a security exception
            // with the download manager. Nothing we can workaround.
            context.toast(error.message)
            notifier.cancelInstallNotification()
            notifier.promptInstall(file.getUriCompat(context))
        }
    }
    // KMK <--

    companion object {
        private const val TAG = "AppUpdateDownload"

        // KMK -->
        const val PACKAGE_INSTALLED_ACTION =
            "${BuildConfig.APPLICATION_ID}.SESSION_SELF_API_PACKAGE_INSTALLED"
        internal const val EXTRA_FILE_URI = "${BuildConfig.APPLICATION_ID}.AppInstaller.FILE_URI"
        private const val SCHEDULED_RUN = "scheduled_run"
        // KMK <--

        const val EXTRA_DOWNLOAD_URL = "DOWNLOAD_URL"
        const val EXTRA_DOWNLOAD_TITLE = "DOWNLOAD_TITLE"

        // KMK -->
        private var instance: WeakReference<AppUpdateDownloadJob>? = null
        // KMK <--

        fun start(
            context: Context,
            url: String,
            title: String? = null,
            // KMK -->
            scheduled: Boolean = false,
            // KMK <--
        ) {
            val data = Data.Builder()
            data.putString(EXTRA_DOWNLOAD_URL, url)
            data.putString(EXTRA_DOWNLOAD_TITLE, title)
            val request = OneTimeWorkRequestBuilder<AppUpdateDownloadJob>()
                .addTag(TAG)
                .apply {
                    // KMK -->
                    if (scheduled) {
                        data.putBoolean(SCHEDULED_RUN, true)
                        val restrictions = Injekt.get<ExhPreferences>().appShouldAutoUpdate().get()
                        val networkType = if (AppUpdatePolicy.DEVICE_NETWORK_NOT_METERED in restrictions) {
                            NetworkType.UNMETERED
                        } else {
                            NetworkType.CONNECTED
                        }
                        val networkRequestBuilder = NetworkRequest.Builder()
                        if (AppUpdatePolicy.DEVICE_ONLY_ON_WIFI in restrictions) {
                            networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        }
                        if (AppUpdatePolicy.DEVICE_NETWORK_NOT_METERED in restrictions) {
                            networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        }
                        val constraints = Constraints.Builder()
                            // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                            .setRequiredNetworkRequest(networkRequestBuilder.build(), networkType)
                            .setRequiresCharging(AppUpdatePolicy.DEVICE_CHARGING in restrictions)
                            .setRequiresBatteryNotLow(true)
                            .build()

                        setConstraints(constraints)
                        setInitialDelay(10, TimeUnit.MINUTES)
                        setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    } else {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        // KMK <--
                        setConstraints(
                            Constraints(
                                requiredNetworkType = NetworkType.CONNECTED,
                            ),
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
