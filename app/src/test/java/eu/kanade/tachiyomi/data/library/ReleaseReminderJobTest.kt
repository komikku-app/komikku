package eu.kanade.tachiyomi.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// KMK -->
class ReleaseReminderJobTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun manga(title: String, nextUpdate: LocalDateTime?): Manga {
        // Not a favorite on purpose: the title of a favorite is read from the custom info, which needs injection
        return Manga.create().copy(
            ogTitle = title,
            nextUpdate = nextUpdate?.toInstant(ZoneOffset.UTC)?.toEpochMilli() ?: 0L,
        )
    }

    @Test
    fun `waits until the hour later today`() {
        val delay = ReleaseReminderJob.delayUntilNext(18, LocalDateTime.of(2026, 3, 10, 9, 30), utc)
        assertEquals(Duration.ofHours(8).plusMinutes(30), delay)
    }

    @Test
    fun `waits until tomorrow when the hour has passed`() {
        val delay = ReleaseReminderJob.delayUntilNext(9, LocalDateTime.of(2026, 3, 10, 9, 30), utc)
        assertEquals(Duration.ofHours(23).plusMinutes(30), delay)
    }

    @Test
    fun `at the exact hour the next reminder is tomorrow`() {
        val delay = ReleaseReminderJob.delayUntilNext(9, LocalDateTime.of(2026, 3, 10, 9, 0), utc)
        assertEquals(Duration.ofDays(1), delay)
    }

    @Test
    fun `the delay is never zero nor more than a day plus the daylight saving change`() {
        val paris = ZoneId.of("Europe/Paris")
        // The clocks go forward on 2026-03-29 at 02:00
        val delay = ReleaseReminderJob.delayUntilNext(9, LocalDateTime.of(2026, 3, 28, 12, 0), paris)
        assertEquals(Duration.ofHours(20), delay)
        assertTrue(delay > Duration.ZERO)
    }

    @Test
    fun `an out of range hour is clamped`() {
        val delay = ReleaseReminderJob.delayUntilNext(99, LocalDateTime.of(2026, 3, 10, 9, 0), utc)
        assertEquals(Duration.ofHours(14), delay)
    }

    @Test
    fun `only titles expected today are listed, in the order of the forecast`() {
        val today = LocalDate.of(2026, 3, 10)
        val upcoming = listOf(
            manga("Late", LocalDateTime.of(2026, 3, 10, 23, 59)),
            manga("Tomorrow", LocalDateTime.of(2026, 3, 11, 0, 1)),
            manga("Yesterday", LocalDateTime.of(2026, 3, 9, 12, 0)),
            manga("Morning", LocalDateTime.of(2026, 3, 10, 0, 0)),
            manga("Unknown", null),
        )
        assertEquals(listOf("Late", "Morning"), ReleaseReminderJob.titlesDueOn(today, upcoming, utc))
    }
}
// KMK <--
