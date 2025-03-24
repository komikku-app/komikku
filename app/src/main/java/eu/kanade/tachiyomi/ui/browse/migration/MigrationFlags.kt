package eu.kanade.tachiyomi.ui.browse.migration

object MigrationFlags {

    const val CHAPTERS = 0b000001
    const val CATEGORIES = 0b000010
    const val TRACK = 0b000100
    const val CUSTOM_COVER = 0b001000
    const val EXTRA = 0b010000
    const val DELETE_DOWNLOADED = 0b100000

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
