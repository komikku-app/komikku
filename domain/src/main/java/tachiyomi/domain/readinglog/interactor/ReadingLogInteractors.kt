package tachiyomi.domain.readinglog.interactor

import tachiyomi.domain.readinglog.model.DayActivity
import tachiyomi.domain.readinglog.model.StreakInfo
import tachiyomi.domain.readinglog.model.YearInReview
import tachiyomi.domain.readinglog.repository.ReadingLogRepository
import tachiyomi.domain.readinglog.service.StreakCalculator
import tachiyomi.domain.readinglog.service.YearInReviewBuilder
import java.time.LocalDate

// KMK -->
/** Adds the time spent reading a title to the reading log. */
class LogReadTime(
    private val repository: ReadingLogRepository,
) {
    suspend fun await(mangaId: Long, readTimeMs: Long, day: LocalDate = LocalDate.now()) {
        if (readTimeMs <= 0) return
        repository.addReadTime(day, mangaId, readTimeMs)
    }
}

class GetReadingStreak(
    private val repository: ReadingLogRepository,
) {
    suspend fun await(today: LocalDate = LocalDate.now()): StreakInfo {
        return StreakCalculator.calculate(repository.getReadDays(), today)
    }
}

class GetReadingHeatmap(
    private val repository: ReadingLogRepository,
) {
    /** The days read in the last [WEEKS] weeks, up to [today]. */
    suspend fun await(today: LocalDate = LocalDate.now()): List<DayActivity> {
        return repository.getDailyActivity(today.minusWeeks(WEEKS.toLong()), today)
    }

    companion object {
        const val WEEKS = 53
    }
}

class GetYearInReview(
    private val repository: ReadingLogRepository,
) {
    suspend fun years(): List<Int> = repository.getActiveYears()

    suspend fun await(year: Int): YearInReview {
        val from = LocalDate.of(year, 1, 1)
        val to = LocalDate.of(year, 12, 31)
        return YearInReviewBuilder.build(
            year = year,
            activity = repository.getDailyActivity(from, to),
            titles = repository.getTopTitles(from, to, TITLES_LIMIT),
        )
    }

    private companion object {
        const val TITLES_LIMIT = 50
    }
}
// KMK <--
