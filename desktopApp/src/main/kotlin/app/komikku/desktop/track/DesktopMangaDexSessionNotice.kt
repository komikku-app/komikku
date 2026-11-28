package app.komikku.desktop.track

import android.content.Context
import app.komikku.desktop.notification.DesktopNotification
import app.komikku.desktop.notification.DesktopNotifier
import exh.md.network.MangaDexSessionNotice
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR

/**
 * The desktop's "MangaDex session expired" notification (REVIEW_A4.md 3.1): Android's `MangaDexSessionNotifier` texts
 * through the [DesktopNotifier]. Android's tap opens Settings > MangaDex; desktop notifications have no actions, so the
 * text (log in again) is the pointer. A shown notification cannot be withdrawn, so [dismiss] does nothing.
 */
class DesktopMangaDexSessionNotice(
    private val context: Context,
    private val notifier: DesktopNotifier,
) : MangaDexSessionNotice {

    override fun show() {
        notifier.show(
            DesktopNotification(
                channel = DesktopNotification.Channel.Common,
                title = context.stringResource(KMR.strings.mangadex_session_expired_notification_title),
                text = context.stringResource(KMR.strings.mangadex_session_expired_notification_text),
                level = DesktopNotification.Level.Warning,
            ),
        )
    }
}
