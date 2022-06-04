package eu.kanade.domain.manga.model

import eu.kanade.tachiyomi.data.library.CustomMangaManager
import uy.kohesive.injekt.injectLazy

data class Manga(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val lastUpdate: Long,
    val dateAdded: Long,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val coverLastModified: Long,
    val url: String,
    // SY -->
    val ogTitle: String,
    val ogArtist: String?,
    val ogAuthor: String?,
    val ogDescription: String?,
    val ogGenre: List<String>?,
    val ogStatus: Long,
    // SY <--
    val thumbnailUrl: String?,
    val initialized: Boolean,
    // SY -->
    val filteredScanlators: String?,
// SY <--
) {

    // SY -->
    private val customMangaInfo = if (favorite) {
        customMangaManager.getManga(this)
    } else null

    val title: String
        get() = customMangaInfo?.title ?: ogTitle

    val author: String?
        get() = customMangaInfo?.author ?: ogAuthor

    val artist: String?
        get() = customMangaInfo?.artist ?: ogArtist

    val description: String?
        get() = customMangaInfo?.description ?: ogDescription

    val genre: List<String>?
        get() = customMangaInfo?.genre ?: ogGenre

    val status: Long
        get() = customMangaInfo?.statusLong ?: ogStatus
    // SY <--

    val sorting: Long
        get() = chapterFlags and CHAPTER_SORTING_MASK

    companion object {

        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000L

        const val CHAPTER_SORTING_SOURCE = 0x00000000L
        const val CHAPTER_SORTING_NUMBER = 0x00000100L
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
        const val CHAPTER_SORTING_MASK = 0x00000300L

        // SY -->
        private val customMangaManager: CustomMangaManager by injectLazy()
        // SY <--
    }
}
