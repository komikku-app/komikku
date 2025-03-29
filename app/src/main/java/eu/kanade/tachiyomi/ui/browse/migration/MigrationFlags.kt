package eu.kanade.tachiyomi.ui.browse.migration

object MigrationFlags {

    const val CHAPTERS = 0b00001
    const val CATEGORIES = 0b00010
    const val TRACK = 0b00100
    const val CUSTOM_COVER = 0b01000
    const val DELETE_DOWNLOADED = 0b10000
    const val EXTRA = 0b1000000

    fun hasChapters(value: Int): Boolean {
        return value and CHAPTERS != 0
    }

    fun hasCategories(value: Int): Boolean {
        return value and CATEGORIES != 0
    }

    fun hasTracks(value: Int): Boolean {
        return value and TRACK != 0
    }

    fun hasCustomCover(value: Int): Boolean {
        return value and CUSTOM_COVER != 0
    }

    fun hasExtra(value: Int): Boolean {
        return value and EXTRA != 0
    }

    fun hasDeleteDownloaded(value: Int): Boolean {
        return value and DELETE_DOWNLOADED != 0
    }
}
