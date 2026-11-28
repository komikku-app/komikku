package exh.md.network

import android.app.Application
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notify
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Tells the user that MangaDex logged them out because the server rejected the refresh token. Tapping the
 * notification opens Settings > MangaDex to log in again.
 *
 * Best effort: failures are logged and never thrown, so they cannot break a request, a login or a logout.
 */
class MangaDexSessionNotifier {

    // Resolved on first use, so creating MdList (which owns this notifier) does not touch the DI graph.
    private val context by lazy { Injekt.get<Application>() }

    fun notifySessionExpired() {
        try {
            val text = context.stringResource(KMR.strings.mangadex_session_expired_notification_text)
            context.notify(Notifications.ID_MANGADEX_SESSION_EXPIRED, Notifications.CHANNEL_COMMON) {
                setContentTitle(context.stringResource(KMR.strings.mangadex_session_expired_notification_title))
                setContentText(text)
                setStyle(NotificationCompat.BigTextStyle().bigText(text))
                setSmallIcon(R.drawable.ic_komikku)
                setColor(ContextCompat.getColor(context, R.color.ic_launcher))
                setContentIntent(NotificationReceiver.openMangadexSettingsPendingActivity(context))
                setAutoCancel(true)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to show the MangaDex session expired notification" }
        }
    }

    fun dismiss() {
        try {
            context.cancelNotification(Notifications.ID_MANGADEX_SESSION_EXPIRED)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to dismiss the MangaDex session expired notification" }
        }
    }
}
