package eu.kanade.domain.history.model

import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import uy.kohesive.injekt.injectLazy
import java.util.Date

data class HistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val mangaId: Long,
    // SY -->
    val ogTitle: String,
    // SY <--
    val chapterNumber: Float,
    val readAt: Date?,
    val readDuration: Long,
    val coverData: MangaCover,
) {
    // SY -->
    val title: String = customMangaManager.getManga(mangaId)?.title ?: ogTitle

    companion object {
        private val customMangaManager: CustomMangaManager by injectLazy()
    }
    // SY <--
}
