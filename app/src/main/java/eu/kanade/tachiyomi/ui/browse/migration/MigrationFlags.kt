package eu.kanade.tachiyomi.ui.browse.migration

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.injectLazy

data class MigrationFlag(
    val flag: Int,
    val isDefaultSelected: Boolean,
    val titleId: StringResource,
) {
    companion object {
        fun create(flag: Int, defaultSelectionMap: Int, titleId: StringResource): MigrationFlag {
            return MigrationFlag(
                flag = flag,
                isDefaultSelected = defaultSelectionMap and flag != 0,
                titleId = titleId,
            )
        }
    }
}

object MigrationFlags {

    const val CHAPTERS = 0b00001
    const val CATEGORIES = 0b00010

    // KMK -->
    const val TRACK = 0b00100
    // KMK <--

    const val CUSTOM_COVER = 0b01000
    const val DELETE_DOWNLOADED = 0b10000

    // KMK -->
    const val EXTRA = 0b1000000
    // KMK <--

    private val coverCache: CoverCache by injectLazy()
    private val downloadCache: DownloadCache by injectLazy()

    fun hasChapters(value: Int): Boolean {
        return value and CHAPTERS != 0
    }

    fun hasCategories(value: Int): Boolean {
        return value and CATEGORIES != 0
    }

    // KMK -->
    fun hasTracks(value: Int): Boolean {
        return value and TRACK != 0
    }
    // KMK <--

    fun hasCustomCover(value: Int): Boolean {
        return value and CUSTOM_COVER != 0
    }

    fun hasDeleteDownloaded(value: Int): Boolean {
        return value and DELETE_DOWNLOADED != 0
    }

    // KMK -->
    /** Returns true if the manga has extra info, include notes. */
    fun hasExtra(value: Int): Boolean {
        return value and EXTRA != 0
    }
    // KMK <--

    /** Returns information about applicable flags with default selections. */
    fun getFlags(manga: Manga?, defaultSelectedBitMap: Int): List<MigrationFlag> {
        val flags = mutableListOf<MigrationFlag>()
        flags += MigrationFlag.create(CHAPTERS, defaultSelectedBitMap, MR.strings.chapters)
        flags += MigrationFlag.create(CATEGORIES, defaultSelectedBitMap, MR.strings.categories)
        // KMK -->
        flags += MigrationFlag.create(TRACK, defaultSelectedBitMap, MR.strings.track)
        // KMK <--

        if (manga != null) {
            if (manga.hasCustomCover(coverCache)) {
                flags += MigrationFlag.create(CUSTOM_COVER, defaultSelectedBitMap, MR.strings.custom_cover)
            }
            if (downloadCache.getDownloadCount(manga) > 0) {
                flags += MigrationFlag.create(DELETE_DOWNLOADED, defaultSelectedBitMap, MR.strings.delete_downloaded)
            }
        }

        // KMK -->
        flags += MigrationFlag.create(EXTRA, defaultSelectedBitMap, SYMR.strings.log_extra)
        // KMK <--

        return flags
    }

    /** Returns a bit map of selected flags. */
    fun getSelectedFlagsBitMap(
        selectedFlags: List<Boolean>,
        flags: List<MigrationFlag>,
    ): Int {
        return selectedFlags
            .zip(flags)
            .filter { (isSelected, _) -> isSelected }
            .map { (_, flag) -> flag.flag }
            .reduceOrNull { acc, mask -> acc or mask } ?: 0
    }
}
