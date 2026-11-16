package eu.kanade.tachiyomi.data.download

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: desktop counterpart of the Android `DownloadJob` (WorkManager worker) with the same companion
 * API, run as a coroutine: [start] (like `ExistingWorkPolicy.REPLACE`) starts the downloader and stays
 * "running" while it is; [stop] cancels it (the borrowed `Downloader.stop` calls it). No foreground
 * service and no network / Wi-Fi checks (desktop has no metered-network notion, so
 * `downloadOnlyOverWifi` is ignored).
 */
class DownloadJob private constructor() {

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val running = MutableStateFlow(false)
        private var job: Job? = null

        @Synchronized
        fun start(context: Context) {
            job?.cancel()
            job = scope.launch {
                val downloadManager = Injekt.get<DownloadManager>()
                // Android fails the work when the downloader does not start; a downloader that is
                // already running (started directly) is monitored instead.
                val active = downloadManager.downloaderStart() || downloadManager.isRunning
                if (!active) return@launch
                running.value = true
                try {
                    while (isActive && downloadManager.isRunning) {
                        delay(250)
                    }
                } finally {
                    running.value = false
                }
            }
        }

        @Synchronized
        fun stop(context: Context) {
            job?.cancel()
            job = null
            running.value = false
        }

        fun isRunning(context: Context): Boolean = running.value

        fun isRunningFlow(context: Context): Flow<Boolean> = running.asStateFlow()
    }
}
