package exh.util

import android.content.Context
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.guava.await

object WorkerUtil {
    /**
     * Returns true if a periodic job is currently scheduled.
     * @param context The application context.
     * @param uniqueWorkName The unique work tag to check for scheduled jobs.
     * @return True if a periodic job is scheduled, false otherwise.
     * @throws Exception If there is an error retrieving the work info.
     */
    suspend fun isPeriodicJobScheduled(context: Context, uniqueWorkName: String): Boolean {
        val workInfos = context.workManager
            .getWorkInfosForUniqueWork(uniqueWorkName)
            .await()

        return workInfos.any { workInfo ->
            !workInfo.state.isFinished
        }
    }
}
