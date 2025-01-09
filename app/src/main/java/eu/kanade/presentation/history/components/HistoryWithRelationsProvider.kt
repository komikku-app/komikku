package eu.kanade.presentation.history.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import tachiyomi.domain.history.model.HistoryWithRelations
import java.util.Date

internal class HistoryWithRelationsProvider : PreviewParameterProvider<HistoryWithRelations> {

    private val simple = HistoryWithRelations(
        id = 1L,
        chapterId = 2L,
        mangaId = 3L,
        // SY -->
        ogTitle = "Test Title",
        // SY <--
        chapterNumber = 10.2,
        readAt = Date(1697247357L),
        readDuration = 123L,
        coverData = tachiyomi.domain.anime.model.MangaCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            ogUrl = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithoutReadAt = HistoryWithRelations(
        id = 1L,
        chapterId = 2L,
        mangaId = 3L,
        // SY -->
        ogTitle = "Test Title",
        // SY <--
        chapterNumber = 10.2,
        readAt = null,
        readDuration = 123L,
        coverData = tachiyomi.domain.anime.model.MangaCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            ogUrl = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithNegativeChapterNumber = HistoryWithRelations(
        id = 1L,
        chapterId = 2L,
        mangaId = 3L,
        // SY -->
        ogTitle = "Test Title",
        // SY <--
        chapterNumber = -2.0,
        readAt = Date(1697247357L),
        readDuration = 123L,
        coverData = tachiyomi.domain.anime.model.MangaCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            ogUrl = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    override val values: Sequence<HistoryWithRelations>
        get() = sequenceOf(simple, historyWithoutReadAt, historyWithNegativeChapterNumber)
}
