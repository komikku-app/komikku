package app.komikku.desktop.window

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Rectangle

class WindowPlacementTest {

    private val screen = Rectangle(0, 0, 1920, 1080)

    @Test
    fun `saved bounds are restored`() {
        restoreWindowPlacement(x = 100, y = 50, width = 1000, height = 700, screens = listOf(screen)) shouldBe
            RestoredWindowPlacement(WindowPosition(100.dp, 50.dp), DpSize(1000.dp, 700.dp))
    }

    @Test
    fun `unset or off-screen positions fall back to the platform default`() {
        val unset = DesktopWindowPreferences.UNSET_POSITION
        restoreWindowPlacement(unset, unset, 1280, 800, listOf(screen)).position shouldBe WindowPosition.PlatformDefault
        restoreWindowPlacement(2500, 10, 1280, 800, listOf(screen)).position shouldBe WindowPosition.PlatformDefault
    }

    @Test
    fun `size is clamped to the minimum`() {
        restoreWindowPlacement(0, 0, 10, 10, emptyList()).size shouldBe
            DpSize(DesktopWindowPreferences.MIN_WIDTH.dp, DesktopWindowPreferences.MIN_HEIGHT.dp)
    }
}
