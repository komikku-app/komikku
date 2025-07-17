package eu.kanade.presentation.history

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.random.Random

class HistoryScreenModelStateProvider : PreviewParameterProvider<HistoryScreenModel.State> {

    private val multiPage = HistoryScreenModel.State(
        searchQuery = null,
        list =
        persistentListOf(HistoryUiModelExamples.headerToday)
            .asSequence()
            .plus(HistoryUiModelExamples.items().take(3))
            .plus(HistoryUiModelExamples.header { it.minus(1, ChronoUnit.DAYS) })
            .plus(HistoryUiModelExamples.items().take(1))
            .plus(HistoryUiModelExamples.header { it.minus(2, ChronoUnit.DAYS) })
            .plus(HistoryUiModelExamples.items().take(7))
            .toImmutableList(),
        dialog = null,
    )

    private val shortRecent = HistoryScreenModel.State(
        searchQuery = null,
        list = persistentListOf(
            HistoryUiModelExamples.headerToday,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val shortFuture = HistoryScreenModel.State(
        searchQuery = null,
        list = persistentListOf(
            HistoryUiModelExamples.headerTomorrow,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val empty = HistoryScreenModel.State(
        searchQuery = null,
        list = persistentListOf(),
        dialog = null,
    )

    private val loadingWithSearchQuery = HistoryScreenModel.State(
        searchQuery = "Example Search Query",
    )

    private val loading = HistoryScreenModel.State(
        searchQuery = null,
        list = null,
        dialog = null,
    )

    override val values: Sequence<HistoryScreenModel.State> = sequenceOf(
        multiPage,
        shortRecent,
        shortFuture,
        empty,
        loadingWithSearchQuery,
        loading,
    )

    private object HistoryUiModelExamples {
        val headerToday = header()
        val headerTomorrow =
            HistoryUiModel.Header(LocalDate.now().plusDays(1))

        fun header(instantBuilder: (Instant) -> Instant = { it }) =
            HistoryUiModel.Header(LocalDate.from(instantBuilder(Instant.now())))

        fun items() = sequence {
            var count = 1
            while (true) {
                yield(randItem { it.copy(/* SY --> */ogTitle = /* SY <-- */ "Example Title $count") })
                count += 1
            }
        }

        fun randItem(historyBuilder: (HistoryWithRelations) -> HistoryWithRelations = { it }) =
            HistoryUiModel.Item(
                historyBuilder(
                    HistoryWithRelations(
                        id = Random.nextLong(),
                        chapterId = Random.nextLong(),
                        mangaId = Random.nextLong(),
                        // SY -->
                        ogTitle = "Test Title",
                        // SY <--
                        chapterNumber = Random.nextDouble(),
                        // KMK -->
                        read = Random.nextBoolean(),
                        totalChapters = Random.nextLong(1, 100),
                        readCount = 1,
                        // KMK <--
                        readAt = Date.from(Instant.now()),
                        readDuration = Random.nextLong(),
                        coverData = MangaCover(
                            mangaId = Random.nextLong(),
                            sourceId = Random.nextLong(),
                            isMangaFavorite = Random.nextBoolean(),
                            ogUrl = "https://example.com/cover.png",
                            lastModified = Random.nextLong(),
                        ),
                    ),
                ),
            )
    }
}
