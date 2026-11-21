package tachiyomi.domain.readinglog.service

import tachiyomi.domain.readinglog.model.StreakInfo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// KMK -->
/**
 * Counts consecutive reading days, forgiving one skipped day.
 *
 * A single skipped day does not break the streak (and does not count as a read day) as long as no other day
 * was skipped in the previous [GRACE_WINDOW_DAYS] days. Two skipped days in a row always break it.
 */
object StreakCalculator {

    const val GRACE_WINDOW_DAYS = 7L

    fun calculate(readDays: Set<LocalDate>, today: LocalDate): StreakInfo {
        if (readDays.isEmpty()) return StreakInfo.Empty

        val readToday = today in readDays
        // Today is not over: not having read yet is not a miss
        val last = if (readToday) today else today.minusDays(1)
        val first = readDays.min()

        var current = 0
        var longest = 0
        var lastGrace: LocalDate? = null
        var previousRead = false

        var day = first
        while (!day.isAfter(last)) {
            if (day in readDays) {
                current++
                previousRead = true
            } else if (current > 0 && previousRead && isGraceFree(lastGrace, day)) {
                lastGrace = day
                previousRead = false
            } else {
                current = 0
                previousRead = false
            }
            if (current > longest) longest = current
            day = day.plusDays(1)
        }

        return StreakInfo(
            current = current,
            longest = longest,
            readToday = readToday,
            graceAvailable = isGraceFree(lastGrace, today),
            atRisk = current > 0 && !readToday && lastGrace == today.minusDays(1),
        )
    }

    private fun isGraceFree(lastGrace: LocalDate?, day: LocalDate): Boolean {
        return lastGrace == null || ChronoUnit.DAYS.between(lastGrace, day) >= GRACE_WINDOW_DAYS
    }
}
// KMK <--
