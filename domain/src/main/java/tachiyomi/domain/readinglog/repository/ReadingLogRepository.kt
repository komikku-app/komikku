package tachiyomi.domain.readinglog.repository

import tachiyomi.domain.readinglog.model.DayActivity
import tachiyomi.domain.readinglog.model.TitleActivity
import java.time.LocalDate

// KMK -->
interface ReadingLogRepository {

    suspend fun addReadTime(day: LocalDate, mangaId: Long, readTimeMs: Long)

    /** Activity of each day with something read between [from] and [to], both included. */
    suspend fun getDailyActivity(from: LocalDate, to: LocalDate): List<DayActivity>

    /** Every day with something read, from the log and from the history. */
    suspend fun getReadDays(): Set<LocalDate>

    /** Titles read between [from] and [to], both included. */
    suspend fun getTopTitles(from: LocalDate, to: LocalDate, limit: Int): List<TitleActivity>

    /** Years with something read, the latest first. */
    suspend fun getActiveYears(): List<Int>
}
// KMK <--
