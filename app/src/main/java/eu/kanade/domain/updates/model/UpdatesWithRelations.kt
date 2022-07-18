package eu.kanade.domain.updates.model

import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.tachiyomi.data.library.CustomMangaManager
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
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: MangaCover,
) {
    // SY -->
    val mangaTitle: String = customMangaManager.getManga(mangaId)?.title ?: ogMangaTitle

    companion object {
        private val customMangaManager: CustomMangaManager by injectLazy()
    }
    // SY <--
}
