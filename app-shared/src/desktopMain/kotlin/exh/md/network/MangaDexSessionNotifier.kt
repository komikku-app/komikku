package exh.md.network

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt

/**
 * KMK: desktop counterpart of the Android `MangaDexSessionNotifier` (same FQN and members) for the borrowed `MdList`
 * and `MangaDexLoginHelper`. Like Android it tells the user that MangaDex logged them out because the server rejected
 * the refresh token, through the [MangaDexSessionNotice] the desktop app registers in Injekt (a system notification);
 * without one (tests, tools) the rejection is only logged. Settings > Tracking shows MDList as logged out either way,
 * because the interceptor cleared the credentials.
 *
 * Best effort, as on Android: failures are logged and never thrown, so they cannot break a request, a login or a logout.
 */
class MangaDexSessionNotifier(
    // Resolved on each use, so creating MdList (which owns this notifier) does not touch the DI graph.
    private val notice: () -> MangaDexSessionNotice? = {
        Injekt.getInstanceOrNull(MangaDexSessionNotice::class.java)
    },
) {

    fun notifySessionExpired() {
        logcat(LogPriority.WARN) { "MangaDex rejected the refresh token; log in to MDList again" }
        try {
            notice()?.show()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to show the MangaDex session expired notification" }
        }
    }

    fun dismiss() {
        try {
            notice()?.dismiss()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to dismiss the MangaDex session expired notification" }
        }
    }
}

/** KMK: how the desktop app shows the "MangaDex session expired" notice of [MangaDexSessionNotifier]. */
interface MangaDexSessionNotice {

    fun show()

    /** Withdraws a notice still shown (after a new login); a notifier that cannot withdraw one does nothing. */
    fun dismiss() {}
}
