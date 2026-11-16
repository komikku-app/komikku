package eu.kanade.tachiyomi.data.library

/**
 * KMK: desktop counterpart of the Android `LibraryUpdateNotifier`, constants only (same values), for
 * the borrowed `Downloader`'s queue-size warning.
 */
abstract class LibraryUpdateNotifier private constructor() {
    companion object {
        const val HELP_WARNING_URL =
            "https://komikku-app.github.io/docs/faq/library#why-am-i-warned-about-large-bulk-updates-and-downloads"
    }
}
