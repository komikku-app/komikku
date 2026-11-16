package app.komikku.shared.download

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * KMK: what the borrowed downloader reports through Android's `DownloadNotifier` (notifications on
 * Android). The desktop `DownloadNotifier` counterpart forwards to the [DownloadNotificationSink]
 * registered in DI; the desktop UI shows [DesktopDownloadEvents.events] as snackbars.
 */
sealed interface DownloadEvent {
    data object Paused : DownloadEvent
    data object Completed : DownloadEvent

    /** A queue-size / network warning; [url] is the help page Android's notification opens. */
    data class Warning(val message: String, val url: String? = null, val mangaId: Long? = null) : DownloadEvent

    data class Error(
        val message: String?,
        val chapter: String? = null,
        val mangaTitle: String? = null,
        val mangaId: Long? = null,
    ) : DownloadEvent
}

fun interface DownloadNotificationSink {
    fun onEvent(event: DownloadEvent)
}

/**
 * Desktop sink: events go to [events], the log and, for warnings and errors, [showMessage] (the desktop
 * app passes a toast, shown as a snackbar by the window).
 */
class DesktopDownloadEvents(
    private val showMessage: (String) -> Unit = {},
) : DownloadNotificationSink {
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    override fun onEvent(event: DownloadEvent) {
        when (event) {
            is DownloadEvent.Error -> logcat(LogPriority.WARN) {
                "Download error: ${event.message} (${event.mangaTitle} / ${event.chapter})"
            }
            is DownloadEvent.Warning -> logcat(LogPriority.WARN) { "Download warning: ${event.message}" }
            else -> logcat { "Downloader: $event" }
        }
        when (event) {
            is DownloadEvent.Error -> showMessage(listOfNotNull(event.mangaTitle, event.chapter, event.message).joinToString(": "))
            is DownloadEvent.Warning -> showMessage(event.message)
            else -> {}
        }
        _events.tryEmit(event)
    }
}
