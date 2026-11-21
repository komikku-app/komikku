package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.data.shortcut.ContinueReadingShortcut
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import kotlin.math.abs

// KMK -->
class WebtoonAutoScrollTest {

    private val frame60Hz = 1_000_000_000L / 60
    private val frame120Hz = 1_000_000_000L / 120

    /** Pixels scrolled in one second, using frames of [frameNanos], like the scroller does. */
    private fun scrollOneSecond(
        speedDp: Int,
        density: Float,
        frameNanos: Long,
        zoom: Float = 1f,
    ): Int {
        val accumulator = SubPixelAccumulator()
        var scrolled = 0
        var elapsed = 0L
        while (elapsed + frameNanos <= 1_000_000_000L) {
            scrolled += accumulator.add(WebtoonAutoScrollMath.pixelsForFrame(speedDp, density, zoom, frameNanos))
            elapsed += frameNanos
        }
        return scrolled
    }

    // region pixelsForFrame

    @Test
    fun `a 100 ms frame at 100 dp per second scrolls 10 dp worth of pixels`() {
        val pixels = WebtoonAutoScrollMath.pixelsForFrame(100, 2f, 1f, 1_000_000_000L / 10)
        // 100 ms is the longest accepted frame: 100 dp/s * 2 px/dp * 0.1 s = 20 px
        assertEquals(20f, pixels, 0.001f)
    }

    @Test
    fun `distance does not depend on the refresh rate`() {
        val at60 = scrollOneSecond(200, 3f, frame60Hz)
        val at120 = scrollOneSecond(200, 3f, frame120Hz)
        // 200 dp/s * 3 px/dp = 600 px, allowing for the last incomplete frame
        assertTrue(abs(at60 - 600) <= 10, "60 Hz scrolled $at60")
        assertTrue(abs(at120 - 600) <= 10, "120 Hz scrolled $at120")
        assertTrue(abs(at60 - at120) <= 10)
    }

    @Test
    fun `distance scales with the screen density`() {
        val mdpi = scrollOneSecond(100, 1f, frame60Hz)
        val xxhdpi = scrollOneSecond(100, 3f, frame60Hz)
        assertTrue(abs(xxhdpi - mdpi * 3) <= 6, "mdpi=$mdpi xxhdpi=$xxhdpi")
    }

    @Test
    fun `zoom compensates so the content moves at the same speed on screen`() {
        val normal = WebtoonAutoScrollMath.pixelsForFrame(100, 2f, 1f, frame60Hz)
        val zoomedIn = WebtoonAutoScrollMath.pixelsForFrame(100, 2f, 2f, frame60Hz)
        val zoomedOut = WebtoonAutoScrollMath.pixelsForFrame(100, 2f, 0.5f, frame60Hz)
        assertEquals(normal / 2, zoomedIn, 0.0001f)
        assertEquals(normal * 2, zoomedOut, 0.0001f)
    }

    @Test
    fun `a zero scale does not divide by zero`() {
        val pixels = WebtoonAutoScrollMath.pixelsForFrame(100, 2f, 0f, frame60Hz)
        assertTrue(pixels.isFinite())
        assertTrue(pixels > 0f)
    }

    @Test
    fun `a stalled frame is capped`() {
        val capped = WebtoonAutoScrollMath.pixelsForFrame(100, 2f, 1f, WebtoonAutoScrollMath.MAX_FRAME_DELTA_NANOS)
        val stalled = WebtoonAutoScrollMath.pixelsForFrame(100, 2f, 1f, 5_000_000_000L)
        assertEquals(capped, stalled, 0f)
    }

    @Test
    fun `no distance for no time, negative time or no speed`() {
        assertEquals(0f, WebtoonAutoScrollMath.pixelsForFrame(100, 2f, 1f, 0L), 0f)
        assertEquals(0f, WebtoonAutoScrollMath.pixelsForFrame(100, 2f, 1f, -5L), 0f)
        assertEquals(0f, WebtoonAutoScrollMath.pixelsForFrame(0, 2f, 1f, frame60Hz), 0f)
        assertEquals(0f, WebtoonAutoScrollMath.pixelsForFrame(-10, 2f, 1f, frame60Hz), 0f)
    }

    @Test
    fun `changing the speed applies to the next frame`() {
        val slow = WebtoonAutoScrollMath.pixelsForFrame(
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN,
            2f,
            1f,
            frame60Hz,
        )
        val fast = WebtoonAutoScrollMath.pixelsForFrame(
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MAX,
            2f,
            1f,
            frame60Hz,
        )
        assertEquals(
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MAX.toFloat() / ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN,
            fast / slow,
            0.001f,
        )
    }

    // endregion

    // region SubPixelAccumulator

    @Test
    fun `the slowest speed still moves, thanks to the accumulated remainder`() {
        // 10 dp/s on a 1x screen is 10 px/s, that is 0.166 px per 60 Hz frame
        val scrolled = scrollOneSecond(10, 1f, frame60Hz)
        assertTrue(abs(scrolled - 10) <= 1, "scrolled $scrolled")
    }

    @Test
    fun `accumulator never loses pixels`() {
        val accumulator = SubPixelAccumulator()
        var total = 0
        repeat(1000) { total += accumulator.add(0.3f) }
        assertTrue(abs(total - 300) <= 1, "scrolled $total")
    }

    @Test
    fun `accumulator returns whole pixels only and keeps the rest`() {
        val accumulator = SubPixelAccumulator()
        assertEquals(0, accumulator.add(0.6f))
        assertEquals(1, accumulator.add(0.6f))
        assertEquals(1, accumulator.add(1.2f))
    }

    @Test
    fun `accumulator reset forgets the remainder`() {
        val accumulator = SubPixelAccumulator()
        accumulator.add(0.9f)
        accumulator.reset()
        assertEquals(0, accumulator.add(0.2f))
    }

    // endregion

    // region AutoScrollStateMachine

    @Test
    fun `starts stopped`() {
        val machine = AutoScrollStateMachine()
        assertEquals(AutoScrollStateMachine.State.STOPPED, machine.state)
        assertFalse(machine.isActive)
        assertFalse(machine.isScrolling)
    }

    @Test
    fun `start runs, stop stops`() {
        val machine = AutoScrollStateMachine()
        machine.start()
        assertTrue(machine.isScrolling)
        assertTrue(machine.isActive)
        machine.stop()
        assertEquals(AutoScrollStateMachine.State.STOPPED, machine.state)
    }

    @Test
    fun `hold waits and release resumes`() {
        val machine = AutoScrollStateMachine()
        machine.start()
        machine.hold()
        assertEquals(AutoScrollStateMachine.State.HOLDING, machine.state)
        assertTrue(machine.isActive)
        assertFalse(machine.isScrolling)
        machine.release()
        assertTrue(machine.isScrolling)
    }

    @Test
    fun `hold and release do nothing while stopped`() {
        val machine = AutoScrollStateMachine()
        machine.hold()
        assertEquals(AutoScrollStateMachine.State.STOPPED, machine.state)
        machine.release()
        assertEquals(AutoScrollStateMachine.State.STOPPED, machine.state)
    }

    @Test
    fun `stop works while holding and cannot be undone by release`() {
        val machine = AutoScrollStateMachine()
        machine.start()
        machine.hold()
        machine.stop()
        machine.release()
        assertEquals(AutoScrollStateMachine.State.STOPPED, machine.state)
    }

    @Test
    fun `holding twice and releasing twice is stable`() {
        val machine = AutoScrollStateMachine()
        machine.start()
        machine.hold()
        machine.hold()
        assertEquals(AutoScrollStateMachine.State.HOLDING, machine.state)
        machine.release()
        machine.release()
        assertEquals(AutoScrollStateMachine.State.RUNNING, machine.state)
    }

    // endregion

    // region Preferences

    @Test
    fun `speed bounds, step and default are consistent`() {
        assertTrue(ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN > 0)
        assertTrue(ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN < ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MAX)
        assertTrue(
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_DEFAULT in
                ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN..ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MAX,
        )
        val span = ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MAX - ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN
        // The slider can reach both ends and the default
        assertEquals(0, span % ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_STEP)
        assertEquals(
            0,
            (ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_DEFAULT - ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN) %
                ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_STEP,
        )
    }

    @Test
    fun `preferences have the expected defaults`() {
        val preferences = ReaderPreferences(InMemoryPreferenceStore())
        assertEquals(
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_DEFAULT,
            preferences.webtoonAutoScrollSpeed().get(),
        )
        assertFalse(preferences.webtoonAutoScrollStopAtChapterEnd().get())
    }

    @Test
    fun `speed preference keeps the value that was set`() {
        // InMemoryPreferenceStore hands out a new preference on every call, so the same one is reused
        val speed = ReaderPreferences(InMemoryPreferenceStore()).webtoonAutoScrollSpeed()
        speed.set(250)
        assertEquals(250, speed.get())
        assertEquals(ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_DEFAULT, speed.defaultValue())
    }

    // endregion

    // region Sleep timer and shortcut

    @Test
    fun `sleep timer is off by default and reaches both ends of the slider`() {
        assertEquals(0, ReaderPreferences.AUTO_SCROLL_SLEEP_TIMER_DEFAULT)
        val values = ReaderPreferences.AUTO_SCROLL_SLEEP_TIMER_RANGE.toList()
        assertEquals(0, values.first())
        assertEquals(ReaderPreferences.AUTO_SCROLL_SLEEP_TIMER_MAX, values.last())
        // the slider has the two ends plus these stops in between
        assertEquals(values.size - 2, ReaderPreferences.AUTO_SCROLL_SLEEP_TIMER_SLIDER_STEPS)
    }

    @Test
    fun `sleep timer and shortcut preferences have the expected defaults`() {
        val preferences = ReaderPreferences(InMemoryPreferenceStore())
        assertEquals(0, preferences.autoScrollSleepTimerMinutes().get())
        assertFalse(preferences.continueReadingShortcut().get())
    }

    @Test
    fun `shortcut label keeps short titles and shortens long ones`() {
        assertEquals("Berserk", ContinueReadingShortcut.labelFor("Berserk"))
        val long = ContinueReadingShortcut.labelFor("A Very Long Title That Does Not Fit On A Launcher")
        assertEquals(25, long.length)
        assertTrue(long.endsWith("…"))
    }

    // endregion
}
// KMK <--
