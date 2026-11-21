package tachiyomi.domain.readinglog.service

import tachiyomi.domain.readinglog.model.DayActivity
import tachiyomi.domain.readinglog.model.TitleActivity
import tachiyomi.domain.readinglog.model.YearInReview
import java.time.LocalDate

// KMK -->
object YearInReviewBuilder {

    const val TOP_TITLES = 5
    const val TOP_GENRES = 3
    private const val GENRE_TITLES = 20

    fun build(year: Int, activity: List<DayActivity>, titles: List<TitleActivity>): YearInReview {
        val inYear = activity.filter { it.day.year == year }
        val days = inYear.filter { it.readTimeMs > 0 || it.chapters > 0 }
        val daysPerMonth = List(12) { month -> days.count { it.day.monthValue == month + 1 } }
        val timePerMonth = List(12) { month -> days.filter { it.day.monthValue == month + 1 }.sumOf { it.readTimeMs } }
        val busiest = daysPerMonth.indices
            .filter { daysPerMonth[it] > 0 }
            .maxWithOrNull(compareBy<Int>({ daysPerMonth[it] }, { timePerMonth[it] }))
            ?.plus(1)

        val ranked = titles.sortedWith(
            compareByDescending<TitleActivity> { it.readTimeMs }.thenByDescending { it.chapters },
        )

        return YearInReview(
            year = year,
            totalReadTimeMs = days.sumOf { it.readTimeMs },
            chaptersRead = days.sumOf { it.chapters },
            daysRead = days.size,
            longestStreak = StreakCalculator.calculate(days.mapTo(HashSet()) { it.day }, LocalDate.of(year, 12, 31))
                .longest,
            busiestMonth = busiest,
            daysReadPerMonth = daysPerMonth,
            topTitles = ranked.take(TOP_TITLES),
            topGenres = topGenres(ranked),
            activity = inYear,
        )
    }

    /** Genres of the titles read the most, the higher the title the more its genres weigh. */
    private fun topGenres(ranked: List<TitleActivity>): List<String> {
        val scores = HashMap<String, Int>()
        ranked.take(GENRE_TITLES).forEachIndexed { index, title ->
            title.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { genre ->
                scores.merge(genre, GENRE_TITLES - index, Int::plus)
            }
        }
        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_GENRES)
            .map { it.key }
    }
}
// KMK <--
