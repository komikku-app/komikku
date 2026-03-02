package exh.recs.batch

object SearchFlags {

    const val INCLUDE_SOURCES = 0b00001
    const val INCLUDE_TRACKERS = 0b00010
    const val HIDE_LIBRARY_RESULTS = 0b00100

    fun hasIncludeSources(value: Int): Boolean {
        return value and INCLUDE_SOURCES != 0
    }

    fun hasIncludeTrackers(value: Int): Boolean {
        return value and INCLUDE_TRACKERS != 0
    }

    fun hasHideLibraryResults(value: Int): Boolean {
        return value and HIDE_LIBRARY_RESULTS != 0
    }
}
