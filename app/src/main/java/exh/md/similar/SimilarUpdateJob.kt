package exh.md.similar

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SimilarUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        SimilarUpdateService.start(context)
        return Result.success()
    }

    companion object {
        const val TAG = "RelatedUpdate"

        fun setupTask(context: Context, skipInitial: Boolean = false) {
            val preferences = Injekt.get<PreferencesHelper>()
            val enabled = preferences.mangadexSimilarEnabled().get()
            val interval = preferences.mangadexSimilarUpdateInterval().get()
            if (enabled) {
                // We are enabled, so construct the constraints
                val wifiRestriction = if (preferences.mangadexSimilarOnlyOverWifi().get()) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(wifiRestriction)
                    .build()

                // If we are not skipping the initial then run it right now
                // Note that we won't run it if the constraints are not satisfied
                if (!skipInitial) {
                    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SimilarUpdateJob>().setConstraints(constraints).build())
                }

                // Finally build the periodic request
                val request = PeriodicWorkRequestBuilder<SimilarUpdateJob>(
                    interval.toLong(),
                    TimeUnit.DAYS,
                    1,
                    TimeUnit.HOURS
                )
                    .addTag(TAG)
                    .setConstraints(constraints)
                    .build()

                if (interval > 0) {
                    WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
                } else {
                    WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
                }
            } else {
                WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
            }
        }

        fun doWorkNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SimilarUpdateJob>().build())
        }
    }
}
