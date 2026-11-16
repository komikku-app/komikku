package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * KMK: desktop counterpart of the Android pager viewer configuration, constants only (same values),
 * for the borrowed `ReaderPreferences`. The desktop reader's own configuration lives in `:desktopApp`.
 */
abstract class PagerConfig private constructor() {

    object CenterMarginType {
        const val NONE = 0
        const val DOUBLE_PAGE_CENTER_MARGIN = 1
        const val WIDE_PAGE_CENTER_MARGIN = 2
        const val DOUBLE_AND_WIDE_CENTER_MARGIN = 3
    }

    object PageLayout {
        const val SINGLE_PAGE = 0
        const val DOUBLE_PAGES = 1
        const val AUTOMATIC = 2
    }
}
