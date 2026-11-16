package app.komikku.shared.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * KMK: the coroutine behind the desktop `DownloadJob`: [start] (replacing a running job, like
 * `ExistingWorkPolicy.REPLACE`) starts the downloader through [startDownloader] and keeps [running] `true`
 * while [isDownloaderRunning] says so; [stop] cancels it.
 *
 * Only the current job writes [running]: a replaced job that finishes late (it was cancelled while polling)
 * must not reset the flag its replacement already set.
 */
class DownloaderRunner(
    private val scope: CoroutineScope,
    /** Starts the downloader; `true` when it runs (also when it already ran). Called on [scope]. */
    private val startDownloader: () -> Boolean,
    private val isDownloaderRunning: () -> Boolean,
    private val pollMillis: Long = 250,
) {
    private val lock = Any()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var job: Job? = null

    fun start() {
        synchronized(lock) {
            job?.cancel()
            job = scope.launch {
                // setRunning takes the lock that start() holds while assigning `job`, so the check sees this job.
                val self = coroutineContext.job
                try {
                    if (!startDownloader()) return@launch
                    setRunning(self, true)
                    while (isActive && isDownloaderRunning()) {
                        delay(pollMillis)
                    }
                } finally {
                    setRunning(self, false)
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            job?.cancel()
            job = null
            _running.value = false
        }
    }

    private fun setRunning(owner: Job, value: Boolean) {
        synchronized(lock) {
            if (job === owner) _running.value = value
        }
    }
}
