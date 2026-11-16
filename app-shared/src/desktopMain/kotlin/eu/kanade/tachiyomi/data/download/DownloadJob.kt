package eu.kanade.tachiyomi.data.download

import android.content.Context
import app.komikku.shared.download.DownloaderRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: desktop counterpart of the Android `DownloadJob` (WorkManager worker) with the same companion
 * API, run as a coroutine: [start] (like `ExistingWorkPolicy.REPLACE`) starts the downloader and stays
 * "running" while it is; [stop] cancels it (the borrowed `Downloader.stop` calls it). No foreground
 * service and no network / Wi-Fi checks (desktop has no metered-network notion, so
 * `downloadOnlyOverWifi` is ignored). The run itself is [DownloaderRunner] (only the current run updates
 * "running", so a replaced run that ends late cannot clear the flag of its replacement).
 */
class DownloadJob private constructor() {

    companion object {
        private val runner = DownloaderRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            startDownloader = {
                val downloadManager = Injekt.get<DownloadManager>()
                // Android fails the work when the downloader does not start; a downloader that is
                // already running (started directly) is monitored instead.
                downloadManager.downloaderStart() || downloadManager.isRunning
            },
            isDownloaderRunning = { Injekt.get<DownloadManager>().isRunning },
        )

        fun start(context: Context) = runner.start()

        fun stop(context: Context) = runner.stop()

        fun isRunning(context: Context): Boolean = runner.running.value

        fun isRunningFlow(context: Context): Flow<Boolean> = runner.running
    }
}
