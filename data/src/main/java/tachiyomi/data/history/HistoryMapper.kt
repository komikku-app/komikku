package tachiyomi.data.history

import tachiyomi.domain.anime.model.MangaCover
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryWithRelations
import java.util.Date

object HistoryMapper {
    fun mapHistory(
        id: Long,
        chapterId: Long,
        readAt: Date?,
        readDuration: Long,
    ): History = History(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
        readDuration = readDuration,
    )

    fun mapHistoryWithRelations(
        historyId: Long,
        mangaId: Long,
        chapterId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Long,
        chapterNumber: Double,
        readAt: Date?,
        readDuration: Long,
    ): HistoryWithRelations = HistoryWithRelations(
        id = historyId,
        chapterId = chapterId,
        mangaId = mangaId,
        // SY -->
        ogTitle = title,
        // SY <--
        chapterNumber = chapterNumber,
        readAt = readAt,
        readDuration = readDuration,
        coverData = MangaCover(
            animeId = mangaId,
            sourceId = sourceId,
            isAnimeFavorite = isFavorite,
            ogUrl = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
