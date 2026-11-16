package eu.kanade.tachiyomi.ui.browse.source.globalsearch

/**
 * KMK: moved out of `app/…/globalsearch/SearchScreenModel.kt` (same package and name, so the same JVM
 * class) because the borrowed `SourcePreferences` uses it for `globalSearchPinnedState()` and must
 * compile for desktop (docs/kmp-desktop/PROGRESS.md, 5.2).
 */
enum class SourceFilter {
    All,
    PinnedOnly,
}
