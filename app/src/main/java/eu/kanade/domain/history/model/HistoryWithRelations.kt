package eu.kanade.domain.history.model

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
    val thumbnailUrl: String,
    val chapterNumber: Float,
    val readAt: Date?,
    val readDuration: Long,
) {
    // SY -->
    val title: String = customMangaManager.getManga(mangaId)?.title ?: ogTitle

    companion object {
        private val customMangaManager: CustomMangaManager by injectLazy()
    }
    // SY <--
}
