package tachiyomi.domain.updates.model

import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.injectLazy

data class UpdatesWithRelations(
    val mangaId: Long,
    // SY -->
    val ogMangaTitle: String,
    // SY <--
    val chapterId: Long,
    val chapterName: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val fillermark: Boolean,
    val lastPageRead: Long,
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: MangaCover,
) {
    // SY -->
    val mangaTitle: String = getCustomMangaInfo.get(mangaId)?.title ?: ogMangaTitle

    companion object {
        private val getCustomMangaInfo: GetCustomMangaInfo by injectLazy()
    }
    // SY <--
}
