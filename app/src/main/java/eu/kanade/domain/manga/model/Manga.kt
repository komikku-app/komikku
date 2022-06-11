package eu.kanade.domain.manga.model

import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.source.model.SManga
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.database.models.Manga as DbManga

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
    val filteredScanlators: List<String>?,
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

    fun toSManga(): SManga {
        return SManga.create().also {
            it.url = url
            it.title = title
            it.artist = artist
            it.author = author
            it.description = description
            it.genre = genre.orEmpty().joinToString()
            it.status = status.toInt()
            it.thumbnail_url = thumbnailUrl
            it.initialized = initialized
        }
    }

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

// TODO: Remove when all deps are migrated
fun Manga.toDbManga(): DbManga = DbManga.create(url, title, source).also {
    it.id = id
    it.favorite = favorite
    it.last_update = lastUpdate
    it.date_added = dateAdded
    it.viewer_flags = viewerFlags.toInt()
    it.chapter_flags = chapterFlags.toInt()
    it.cover_last_modified = coverLastModified
}
