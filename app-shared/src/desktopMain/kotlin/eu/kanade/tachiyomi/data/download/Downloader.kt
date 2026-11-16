package eu.kanade.tachiyomi.data.download

/**
 * KMK: desktop counterpart of the Android `Downloader`, constants only (same values), for the borrowed
 * `DownloadProvider`. Downloading on desktop is Phase 8.
 */
abstract class Downloader private constructor() {
    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 15
    }
}
