package tachiyomi.data.readinglog

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.readinglog.model.DayActivity
import tachiyomi.domain.readinglog.model.TitleActivity
import tachiyomi.domain.readinglog.repository.ReadingLogRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

// KMK -->
class ReadingLogRepositoryImpl(
    private val handler: DatabaseHandler,
) : ReadingLogRepository {

    override suspend fun addReadTime(day: LocalDate, mangaId: Long, readTimeMs: Long) {
        handler.await { readingLogQueries.addReadTime(day.toEpochDay(), mangaId, readTimeMs) }
    }

    override suspend fun getDailyActivity(from: LocalDate, to: LocalDate): List<DayActivity> {
        val time = handler.awaitList {
            readingLogQueries.getDailyReadTime(from.toEpochDay(), to.toEpochDay()) { day, readTime ->
                LocalDate.ofEpochDay(day) to (readTime?.toLong() ?: 0L)
            }
        }.toMap()
        val chapters = getHistoryDays().filter { it >= from && it <= to }.groupingBy { it }.eachCount()

        return (time.keys + chapters.keys)
            .sorted()
            .map { DayActivity(it, time[it] ?: 0L, chapters[it] ?: 0) }
    }

    override suspend fun getReadDays(): Set<LocalDate> {
        val logged = handler.awaitList { readingLogQueries.getReadDays() }.map { LocalDate.ofEpochDay(it) }
        return logged.toSet() + getHistoryDays()
    }

    override suspend fun getTopTitles(from: LocalDate, to: LocalDate, limit: Int): List<TitleActivity> {
        val zone = ZoneId.systemDefault()
        val fromDate = Date.from(from.atStartOfDay(zone).toInstant())
        val toDate = Date.from(to.plusDays(1).atStartOfDay(zone).toInstant())

        val byTime = handler.awaitList {
            readingLogQueries.getTopMangaByReadTime(from.toEpochDay(), to.toEpochDay(), limit.toLong()) { mangaId, title, genre, readTime ->
                TitleActivity(mangaId, title, genre.orEmpty(), readTime?.toLong() ?: 0L, 0)
            }
        }
        val byChapters = handler.awaitList {
            readingLogQueries.getTopMangaByChapters(fromDate, toDate, limit.toLong()) { mangaId, title, genre, chapterCount ->
                TitleActivity(mangaId, title, genre.orEmpty(), 0L, chapterCount.toInt())
            }
        }

        val merged = LinkedHashMap<Long, TitleActivity>()
        (byTime + byChapters).forEach { item ->
            merged.merge(item.mangaId, item) { a, b ->
                a.copy(readTimeMs = maxOf(a.readTimeMs, b.readTimeMs), chapters = maxOf(a.chapters, b.chapters))
            }
        }
        return merged.values.toList()
    }

    override suspend fun getActiveYears(): List<Int> {
        val loggedYears = handler.awaitList { readingLogQueries.getReadDays() }.map { LocalDate.ofEpochDay(it).year }
        return (loggedYears + getHistoryDays().map { it.year }).distinct().sortedDescending()
    }

    private suspend fun getHistoryDays(): List<LocalDate> {
        val zone = ZoneId.systemDefault()
        return handler.awaitList { readingLogQueries.getHistoryDates() }
            .mapNotNull { row -> row.last_read?.let { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() } }
    }
}
// KMK <--
