package eu.kanade.tachiyomi.data.track

/**
 * KMK: temporary desktop stand-in (8.2) for the borrowed `BackupFileValidator`; replaced by the borrowed
 * Android `TrackerManager` in 8.3.
 */
class TrackerManager {
    interface Tracker {
        val name: String
        val isLoggedIn: Boolean
    }

    @Suppress("UNUSED_PARAMETER")
    fun get(id: Long): Tracker? = null
}
