package app.komikku.desktop.track

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.notification.DesktopNotification
import app.komikku.desktop.notification.DesktopNotifier
import exh.md.network.MangaDexSessionNotice
import exh.md.network.MangaDexSessionNotifier
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * REVIEW_A4.md 3.1: a rejected MangaDex session posts Android's "MangaDex session expired" notification through the
 * desktop notifier (a recording fake here), and the borrowed `MdList`'s notifier stays best effort.
 */
class MangaDexSessionNoticeTest {

    @TempDir
    lateinit var dir: File

    private class RecordingNotifier : DesktopNotifier {
        val shown = mutableListOf<DesktopNotification>()
        override fun show(notification: DesktopNotification): Boolean = shown.add(notification)
    }

    @Test
    fun `an expired session posts Android's notification texts through the desktop notifier`() {
        val context = Application(dir)
        val notifier = RecordingNotifier()
        val sessionNotifier = MangaDexSessionNotifier { DesktopMangaDexSessionNotice(context, notifier) }

        sessionNotifier.notifySessionExpired()

        notifier.shown shouldBe listOf(
            DesktopNotification(
                channel = DesktopNotification.Channel.Common,
                title = context.stringResource(KMR.strings.mangadex_session_expired_notification_title),
                text = context.stringResource(KMR.strings.mangadex_session_expired_notification_text),
                level = DesktopNotification.Level.Warning,
            ),
        )
        notifier.shown.single().title shouldBe "MangaDex session expired"

        // Nothing to withdraw on the desktop: dismiss posts nothing
        sessionNotifier.dismiss()
        notifier.shown.size shouldBe 1
    }

    @Test
    fun `without a notice, or with a failing one, the session notifier never throws`() {
        MangaDexSessionNotifier { null }.apply {
            notifySessionExpired()
            dismiss()
        }
        val failing = object : MangaDexSessionNotice {
            override fun show() = error("tray gone")
            override fun dismiss() = error("tray gone")
        }
        MangaDexSessionNotifier { failing }.apply {
            notifySessionExpired()
            dismiss()
        }
        MangaDexSessionNotifier { throw IllegalStateException("no DI") }.notifySessionExpired()
    }

    @Test
    fun `the desktop app registers the notice the borrowed MdList's notifier looks up`() {
        DesktopInjekt.init(dir)

        Injekt.get<MangaDexSessionNotice>().shouldBeInstanceOf<DesktopMangaDexSessionNotice>()
    }
}
