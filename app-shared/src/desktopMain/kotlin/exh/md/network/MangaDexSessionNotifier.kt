package exh.md.network

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * KMK: desktop counterpart of the Android `MangaDexSessionNotifier` (same FQN and members) for the borrowed `MdList`
 * and `MangaDexLoginHelper`. There are no system notifications: a rejected session is logged, and Settings > Tracking
 * shows MDList as logged out because the interceptor cleared the credentials.
 */
class MangaDexSessionNotifier {

    fun notifySessionExpired() {
        logcat(LogPriority.WARN) { "MangaDex rejected the refresh token; log in to MDList again" }
    }

    fun dismiss() {}
}
