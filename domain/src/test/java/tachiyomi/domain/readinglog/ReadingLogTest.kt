package tachiyomi.domain.readinglog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.readinglog.model.ActivityLevel
import tachiyomi.domain.readinglog.model.DayActivity
import tachiyomi.domain.readinglog.model.StreakInfo
import tachiyomi.domain.readinglog.model.TitleActivity
import tachiyomi.domain.readinglog.service.StreakCalculator
import tachiyomi.domain.readinglog.service.YearInReviewBuilder
import java.time.LocalDate

// KMK -->
class ReadingLogTest {

    private val today = LocalDate.of(2026, 6, 15)

    private fun days(vararg offsets: Int): Set<LocalDate> = offsets.map { today.plusDays(it.toLong()) }.toSet()

    // region streak

    @Test
    fun `no reading means no streak and a free grace day`() {
        assertEquals(StreakInfo.Empty, StreakCalculator.calculate(emptySet(), today))
    }

    @Test
    fun `consecutive days up to today count`() {
        val info = StreakCalculator.calculate(days(-2, -1, 0), today)
        assertEquals(3, info.current)
        assertEquals(3, info.longest)
        assertTrue(info.readToday)
    }

    @Test
    fun `not having read yet today does not break the streak`() {
        val info = StreakCalculator.calculate(days(-3, -2, -1), today)
        assertEquals(3, info.current)
        assertFalse(info.readToday)
        assertFalse(info.atRisk)
    }

    @Test
    fun `one skipped day is forgiven and does not count`() {
        val info = StreakCalculator.calculate(days(-4, -3, -1, 0), today)
        assertEquals(4, info.current)
    }

    @Test
    fun `two skipped days in a row break the streak`() {
        val info = StreakCalculator.calculate(days(-5, -4, -1, 0), today)
        assertEquals(2, info.current)
        assertEquals(2, info.longest)
    }

    @Test
    fun `a second skipped day inside the grace window breaks the streak`() {
        // read, skip, read, read, skip, read: the second skip is only 3 days after the first
        val info = StreakCalculator.calculate(days(-6, -4, -3, -1, 0), today)
        assertEquals(2, info.current)
    }

    @Test
    fun `the grace day is back after a week`() {
        // skips on -9 and -1, 8 days apart
        val info = StreakCalculator.calculate(days(-10, -8, -7, -6, -5, -4, -3, -2, 0), today)
        assertEquals(9, info.current)
    }

    @Test
    fun `yesterday skipped with the grace day available keeps the streak alive but at risk`() {
        val info = StreakCalculator.calculate(days(-3, -2), today)
        // -1 was skipped (grace used), today is pending
        assertEquals(2, info.current)
        assertTrue(info.atRisk)
        assertFalse(info.graceAvailable)
    }

    @Test
    fun `a streak that ended long ago is zero but the best one is kept`() {
        val info = StreakCalculator.calculate(days(-30, -29, -28, -27), today)
        assertEquals(0, info.current)
        assertEquals(4, info.longest)
        assertTrue(info.graceAvailable)
    }

    @Test
    fun `the grace day is used only once per window even if the skip was long ago`() {
        val info = StreakCalculator.calculate(days(-20, -18), today)
        assertEquals(0, info.current)
        assertEquals(2, info.longest)
    }

    // endregion

    // region levels

    @Test
    fun `activity levels follow the time read`() {
        assertEquals(ActivityLevel.NONE, ActivityLevel.of(0, 0))
        assertEquals(ActivityLevel.LOW, ActivityLevel.of(0, 2))
        assertEquals(ActivityLevel.LOW, ActivityLevel.of(60_000, 0))
        assertEquals(ActivityLevel.MEDIUM, ActivityLevel.of(5 * 60_000L, 0))
        assertEquals(ActivityLevel.HIGH, ActivityLevel.of(15 * 60_000L, 0))
        assertEquals(ActivityLevel.VERY_HIGH, ActivityLevel.of(30 * 60_000L, 0))
    }

    // endregion

    // region year in review

    private fun activity(month: Int, day: Int, minutes: Long, chapters: Int = 1) =
        DayActivity(LocalDate.of(2025, month, day), minutes * 60_000, chapters)

    @Test
    fun `a year without reading has no data`() {
        val review = YearInReviewBuilder.build(2025, emptyList(), emptyList())
        assertFalse(review.hasData)
        assertNull(review.busiestMonth)
        assertEquals(12, review.daysReadPerMonth.size)
    }

    @Test
    fun `totals, busiest month and longest streak`() {
        val review = YearInReviewBuilder.build(
            2025,
            listOf(
                activity(3, 1, 10),
                activity(3, 2, 20, chapters = 3),
                activity(3, 3, 30),
                activity(5, 10, 60),
                // another year is ignored
                DayActivity(LocalDate.of(2024, 12, 31), 99 * 60_000L, 9),
            ),
            emptyList(),
        )
        assertEquals(4, review.daysRead)
        assertEquals(120 * 60_000L, review.totalReadTimeMs)
        assertEquals(6, review.chaptersRead)
        assertEquals(3, review.busiestMonth)
        assertEquals(3, review.longestStreak)
        assertEquals(3, review.daysReadPerMonth[2])
    }

    @Test
    fun `top titles are ranked by time then chapters and genres by rank`() {
        val titles = listOf(
            TitleActivity(1, "A", listOf("Action", "Drama"), 10, 5),
            TitleActivity(2, "B", listOf("Drama"), 100, 1),
            TitleActivity(3, "C", listOf("Comedy"), 0, 9),
            TitleActivity(4, "D", listOf("Comedy"), 0, 2),
            TitleActivity(5, "E", emptyList(), 0, 1),
            TitleActivity(6, "F", emptyList(), 0, 0),
        )
        val review = YearInReviewBuilder.build(2025, listOf(activity(1, 1, 5)), titles)
        assertEquals(listOf("B", "A", "C", "D", "E"), review.topTitles.map { it.title })
        assertEquals("Drama", review.topGenres.first())
        assertEquals(3, review.topGenres.size)
    }

    // endregion
}
// KMK <--
