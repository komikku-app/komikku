package eu.kanade.domain.track.service

import android.content.Context
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.store.DelayedTrackingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * KMK: desktop counterpart of the Android `DelayedTrackingUpdateJob` (WorkManager, network constraint,
 * exponential backoff from 5 minutes, at most 4 runs) for the borrowed `TrackChapter`: a coroutine that
 * replays the persisted [DelayedTrackingStore] with the same body, 5 / 10 / 20 / 40 minutes after the
 * failure, until the store is empty. `setupTask` replaces a pending run, like `ExistingWorkPolicy.REPLACE`.
 * Pending items survive restarts in the store, but a retry is only scheduled again by the next failure.
 */
object DelayedTrackingUpdateJob {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** First retry delay (tests shorten it). */
    @Volatile
    var initialDelay: Duration = 5.minutes

    @Synchronized
    fun setupTask(context: Context) {
        job?.cancel()
        job = scope.launch {
            var wait = initialDelay
            repeat(4) {
                delay(wait)
                if (runOnce(context)) return@launch
                wait *= 2
            }
        }
    }

    /** Android `doWork` body; `true` when nothing is left. */
    suspend fun runOnce(context: Context): Boolean {
        val getTracks = Injekt.get<GetTracks>()
        val trackChapter = Injekt.get<TrackChapter>()
        val delayedTrackingStore = Injekt.get<DelayedTrackingStore>()
        delayedTrackingStore.getItems()
            .mapNotNull {
                val track = getTracks.awaitOne(it.trackId)
                if (track == null) {
                    delayedTrackingStore.remove(it.trackId)
                }
                track?.copy(lastChapterRead = it.lastChapterRead.toDouble())
            }
            .forEach { track ->
                logcat(LogPriority.DEBUG) {
                    "Updating delayed track item: ${track.mangaId}, last chapter read: ${track.lastChapterRead}"
                }
                trackChapter.await(context, track.mangaId, track.lastChapterRead, setupJobOnFailure = false)
            }
        return delayedTrackingStore.getItems().isEmpty()
    }
}
