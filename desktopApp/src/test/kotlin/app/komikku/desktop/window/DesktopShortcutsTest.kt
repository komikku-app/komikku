package app.komikku.desktop.window

import androidx.compose.ui.input.key.Key
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DesktopShortcutsTest {

    @Test
    fun `ctrl + digit selects a tab on windows and linux`() {
        val shortcuts = DesktopShortcuts(isMacOs = false)
        shortcuts.actionFor(Key.One, isCtrlPressed = true, isMetaPressed = false, isAltPressed = false, isShiftPressed = false) shouldBe
            DesktopShortcuts.Action.SelectTab(0)
        shortcuts.actionFor(Key.NumPad5, isCtrlPressed = true, isMetaPressed = false, isAltPressed = false, isShiftPressed = false) shouldBe
            DesktopShortcuts.Action.SelectTab(4)
        shortcuts.actionFor(Key.One, isCtrlPressed = false, isMetaPressed = true, isAltPressed = false, isShiftPressed = false) shouldBe null
        shortcuts.actionFor(Key.Six, isCtrlPressed = true, isMetaPressed = false, isAltPressed = false, isShiftPressed = false) shouldBe null
        shortcuts.actionFor(Key.Two, isCtrlPressed = true, isMetaPressed = false, isAltPressed = false, isShiftPressed = true) shouldBe null
    }

    @Test
    fun `cmd + digit selects a tab on macos`() {
        val shortcuts = DesktopShortcuts(isMacOs = true)
        shortcuts.actionFor(Key.Three, isCtrlPressed = false, isMetaPressed = true, isAltPressed = false, isShiftPressed = false) shouldBe
            DesktopShortcuts.Action.SelectTab(2)
        shortcuts.actionFor(Key.Three, isCtrlPressed = true, isMetaPressed = false, isAltPressed = false, isShiftPressed = false) shouldBe null
    }
}
