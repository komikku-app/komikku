package eu.kanade.tachiyomi.ui.category.biometric

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TimeRangeTest {

    @Test
    fun `a range within a day contains the times between its start and end`() {
        val range = TimeRange(9.hours, 17.hours)

        (9.hours in range) shouldBe true
        (12.hours + 30.minutes in range) shouldBe true
        (17.hours in range) shouldBe true
        (8.hours + 59.minutes in range) shouldBe false
        (20.hours in range) shouldBe false
    }

    @Test
    fun `a range across midnight contains the times before and after midnight`() {
        // The time picker stores 22:00 - 06:00 as it was entered: start after end
        val range = TimeRange(22.hours, 6.hours)

        (22.hours in range) shouldBe true
        (23.hours + 30.minutes in range) shouldBe true
        (0.hours in range) shouldBe true
        (6.hours in range) shouldBe true
        (12.hours in range) shouldBe false
        (21.hours + 59.minutes in range) shouldBe false
    }

    @Test
    fun `a range read from its preference string keeps its order`() {
        val range = TimeRange.fromPreferenceString("1320,360")!!

        (1.hours in range) shouldBe true
        (12.hours in range) shouldBe false
    }
}
