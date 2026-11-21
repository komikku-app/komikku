package tachiyomi.domain.readinglog.model

import java.time.LocalDate

// KMK -->
/** What was read on one [day]: the time spent in the reader and the chapters last read that day. */
data class DayActivity(
    val day: LocalDate,
    val readTimeMs: Long,
    val chapters: Int,
) {
    val level: ActivityLevel get() = ActivityLevel.of(readTimeMs, chapters)
}

/** How strongly a day is colored in the heatmap. */
enum class ActivityLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH,
    ;

    companion object {
        private const val MINUTE_MS = 60_000L

        fun of(readTimeMs: Long, chapters: Int): ActivityLevel = when {
            readTimeMs >= 30 * MINUTE_MS -> VERY_HIGH
            readTimeMs >= 15 * MINUTE_MS -> HIGH
            readTimeMs >= 5 * MINUTE_MS -> MEDIUM
            // Days before the log existed only know that something was read
            readTimeMs > 0 || chapters > 0 -> LOW
            else -> NONE
        }
    }
}

data class TitleActivity(
    val mangaId: Long,
    val title: String,
    val genres: List<String>,
    val readTimeMs: Long,
    val chapters: Int,
)

data class StreakInfo(
    val current: Int,
    val longest: Int,
    val readToday: Boolean,
    /** A grace day is free again: none was used in the last few days. */
    val graceAvailable: Boolean,
    /** Yesterday was skipped: the streak is alive thanks to the grace day and needs a read today. */
    val atRisk: Boolean,
) {
    companion object {
        val Empty = StreakInfo(0, 0, readToday = false, graceAvailable = true, atRisk = false)
    }
}

data class YearInReview(
    val year: Int,
    val totalReadTimeMs: Long,
    val chaptersRead: Int,
    val daysRead: Int,
    val longestStreak: Int,
    /** Month number (1 to 12) with the most days read, null when nothing was read. */
    val busiestMonth: Int?,
    /** Days read in each month, index 0 is January. */
    val daysReadPerMonth: List<Int>,
    val topTitles: List<TitleActivity>,
    val topGenres: List<String>,
    val activity: List<DayActivity>,
) {
    val hasData: Boolean get() = daysRead > 0
}
// KMK <--
