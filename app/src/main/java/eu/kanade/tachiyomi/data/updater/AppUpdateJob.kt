package eu.kanade.tachiyomi.data.updater

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationManager
import exh.log.xLogE
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

class AppUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        try {
            if (!BuildConfig.INCLUDE_UPDATER) {
                cancelTask(context)
                return@coroutineScope Result.success()
            }
            AppUpdateChecker().checkForUpdate(context)
            Result.success()
        } catch (e: Exception) {
            xLogE("Unable to check for update", e)
            Result.failure()
        }
    }

    fun NotificationCompat.Builder.update(block: NotificationCompat.Builder.() -> Unit) {
        block()
        context.notificationManager.notify(Notifications.ID_APP_UPDATER, build())
    }

    companion object {
        private const val TAG = "AppUpdateChecker"

        fun setupTask(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AppUpdateJob>(
                3,
                TimeUnit.DAYS,
                3,
                TimeUnit.HOURS,
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancelTask(context: Context) {
            // cancel and remove job
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
            WorkManager.getInstance(context).pruneWork()
        }
    }
}
