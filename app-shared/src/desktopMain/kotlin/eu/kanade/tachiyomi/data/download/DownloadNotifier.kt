package eu.kanade.tachiyomi.data.download

import android.content.Context
import app.komikku.shared.download.DownloadEvent
import app.komikku.shared.download.DownloadNotificationSink
import eu.kanade.tachiyomi.data.download.model.Download
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: desktop counterpart of the Android `DownloadNotifier` (same FQN, constructor and members) for
 * the borrowed `Downloader`. There are no system notifications: progress is read by the UI from the
 * queue itself; pause / completion / warnings / errors go to the [DownloadNotificationSink] in DI
 * (dropped when none is registered).
 */
@Suppress("UNUSED_PARAMETER")
internal class DownloadNotifier(private val context: Context) {

    private val sink: DownloadNotificationSink by lazy {
        runCatching { Injekt.get<DownloadNotificationSink>() }.getOrNull() ?: DownloadNotificationSink { }
    }

    fun dismissProgress() {}

    fun dismissPaused() {}

    fun onProgressChange(download: Download) {}

    fun onPaused() = sink.onEvent(DownloadEvent.Paused)

    fun onComplete() = sink.onEvent(DownloadEvent.Completed)

    /** [contentIntent] is the URL from the desktop `NotificationHandler.openUrl`. */
    fun onWarning(reason: String, timeout: Long? = null, contentIntent: String? = null, mangaId: Long? = null) {
        sink.onEvent(DownloadEvent.Warning(reason, contentIntent, mangaId))
    }

    fun onError(error: String? = null, chapter: String? = null, mangaTitle: String? = null, mangaId: Long? = null) {
        sink.onEvent(DownloadEvent.Error(error, chapter, mangaTitle, mangaId))
    }
}
