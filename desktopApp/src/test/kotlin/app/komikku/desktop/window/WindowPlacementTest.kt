package app.komikku.desktop.window

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Rectangle

class WindowPlacementTest {

    /** A 1920x1080 screen without a 40 px task bar at the bottom. */
    private val screen = Rectangle(0, 0, 1920, 1040)
    private val unset = DesktopWindowPreferences.UNSET_POSITION

    private fun placement(x: Int, y: Int, width: Int, height: Int) =
        RestoredWindowPlacement(WindowPosition(x.dp, y.dp), DpSize(width.dp, height.dp))

    @Test
    fun `saved bounds inside the screen are restored`() {
        restoreWindowPlacement(x = 100, y = 50, width = 1000, height = 700, screens = listOf(screen)) shouldBe
            placement(100, 50, 1000, 700)
    }

    @Test
    fun `without a saved position the window is centred on the main screen`() {
        restoreWindowPlacement(unset, unset, 1280, 800, listOf(screen)) shouldBe placement(320, 120, 1280, 800)
    }

    @Test
    fun `a small screen gets the whole usable area, not the default 1280x800`() {
        // macOS at 1024x768: menu bar (25) at the top, Dock (59) at the bottom
        val small = Rectangle(0, 25, 1024, 684)
        restoreWindowPlacement(unset, unset, 1280, 800, listOf(small)) shouldBe placement(0, 25, 1024, 684)
    }

    @Test
    fun `a scaled screen holds fewer dp`() {
        // Windows at 150 %: a 1920x1080 screen is 1280x720 in AWT units, the task bar 32 of them
        val scaled = Rectangle(0, 0, 1280, 688)
        restoreWindowPlacement(200, 100, 1280, 800, listOf(scaled)) shouldBe placement(0, 0, 1280, 688)
    }

    @Test
    fun `a saved size bigger than the screen shrinks to it and the window moves inside`() {
        restoreWindowPlacement(600, 500, 2560, 1400, listOf(screen)) shouldBe placement(0, 0, 1920, 1040)
        restoreWindowPlacement(1500, 900, 1000, 700, listOf(screen)) shouldBe placement(920, 340, 1000, 700)
    }

    @Test
    fun `a title bar above or left of the screen comes back on it`() {
        restoreWindowPlacement(-300, -20, 1000, 700, listOf(screen)) shouldBe placement(0, 0, 1000, 700)
    }

    @Test
    fun `a position on a monitor that is gone moves to the main screen`() {
        restoreWindowPlacement(2500, 10, 1280, 800, listOf(screen)) shouldBe placement(320, 120, 1280, 800)
        restoreWindowPlacement(100, -2000, 1280, 800, listOf(screen)) shouldBe placement(320, 120, 1280, 800)
    }

    @Test
    fun `the window stays on the second screen that holds its title bar, fitted to that screen`() {
        // A smaller second screen on the right, its task bar at the bottom
        val second = Rectangle(1920, 0, 1366, 728)
        restoreWindowPlacement(2000, 100, 1280, 800, listOf(screen, second)) shouldBe placement(2000, 0, 1280, 728)
    }

    @Test
    fun `the screen with most of the title bar wins`() {
        val second = Rectangle(1920, 0, 1920, 1040)
        restoreWindowPlacement(1800, 100, 1000, 700, listOf(screen, second)) shouldBe placement(1920, 100, 1000, 700)
    }

    @Test
    fun `size is clamped to the minimum`() {
        restoreWindowPlacement(0, 0, 10, 10, emptyList()).size shouldBe
            DpSize(DesktopWindowPreferences.MIN_WIDTH.dp, DesktopWindowPreferences.MIN_HEIGHT.dp)
        restoreWindowPlacement(0, 0, 10, 10, listOf(screen)).size shouldBe
            DpSize(DesktopWindowPreferences.MIN_WIDTH.dp, DesktopWindowPreferences.MIN_HEIGHT.dp)
    }

    @Test
    fun `headless keeps the saved position and the platform default`() {
        restoreWindowPlacement(unset, unset, 1280, 800, emptyList()).position shouldBe WindowPosition.PlatformDefault
        restoreWindowPlacement(2500, 10, 1280, 800, emptyList()).position shouldBe WindowPosition(2500.dp, 10.dp)
    }
}
