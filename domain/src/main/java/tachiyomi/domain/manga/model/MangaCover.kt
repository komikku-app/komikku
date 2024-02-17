package tachiyomi.domain.manga.model

import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import uy.kohesive.injekt.injectLazy

/**
 * Contains the required data for MangaCoverFetcher
 */
data class MangaCover(
    val mangaId: Long,
    val sourceId: Long,
    val isMangaFavorite: Boolean,
    // SY -->
    val ogUrl: String?,
    // SY <--
    val lastModified: Long,
) {
    // SY -->
    val url: String = getCustomMangaInfo.get(mangaId)?.thumbnailUrl ?: ogUrl!!

    companion object {
        private val getCustomMangaInfo: GetCustomMangaInfo by injectLazy()
    }
    // SY <--
}

fun Manga.asMangaCover(): MangaCover {
    return MangaCover(
        mangaId = id,
        sourceId = source,
        isMangaFavorite = favorite,
        ogUrl = thumbnailUrl,
        lastModified = coverLastModified,
    )
}
