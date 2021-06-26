package eu.kanade.tachiyomi.ui.library

@Deprecated("Deprecated in favor for SortModeSetting")
object LibrarySort {

    const val ALPHA = 0
    const val LAST_READ = 1
    const val LAST_CHECKED = 2
    const val UNREAD = 3
    const val TOTAL = 4
    const val LATEST_CHAPTER = 6
    const val CHAPTER_FETCH_DATE = 10
    const val DATE_ADDED = 8

    // SY -->
    const val DRAG_AND_DROP = 7
    const val TAG_LIST = 9
    // SY <--

    @Deprecated("Removed in favor of searching by source")
    const val SOURCE = 5
}
