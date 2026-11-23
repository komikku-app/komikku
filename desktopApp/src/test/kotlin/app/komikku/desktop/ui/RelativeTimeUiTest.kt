package app.komikku.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/** P3 review: the time behind "Just now" / the date of `relativeTimeSpanString` is read again every minute. */
@OptIn(ExperimentalTestApi::class)
class RelativeTimeUiTest {

    @Test
    fun `the current time ticks every minute`() = runDesktopComposeUiTest {
        var clock = 1_000L
        mainClock.autoAdvance = false
        setContent { Text("now=" + rememberCurrentTimeMillis(clock = { clock })) }
        mainClock.advanceTimeByFrame()
        onNodeWithText("now=1000").assertExists()
        clock = 5_000L
        mainClock.advanceTimeBy(30_000)
        onNodeWithText("now=1000").assertExists()
        mainClock.advanceTimeBy(1.minutes.inWholeMilliseconds)
        onNodeWithText("now=5000").assertExists()
    }
}
