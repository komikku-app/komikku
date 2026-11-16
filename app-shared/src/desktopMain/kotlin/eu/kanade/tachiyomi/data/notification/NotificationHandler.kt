package eu.kanade.tachiyomi.data.notification

import android.content.Context

/**
 * KMK: desktop counterpart of the Android `NotificationHandler` for the borrowed downloader: an
 * "open URL" notification action is the URL itself (the desktop `DownloadNotifier` passes it on).
 */
object NotificationHandler {
    @Suppress("UNUSED_PARAMETER")
    fun openUrl(context: Context, url: String): String = url
}
