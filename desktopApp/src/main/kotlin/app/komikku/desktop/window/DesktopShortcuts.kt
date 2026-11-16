package app.komikku.desktop.window

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Keyboard shortcut scaffold of the main window. Window-level (`Window(onPreviewKeyEvent = …)`), so
 * shortcuts work regardless of focus. Today: Ctrl+1…5 (Cmd+1…5 on macOS) switch the navigation
 * destinations. Add new shortcuts as [Action]s in [actionFor].
 */
class DesktopShortcuts(
    private val isMacOs: Boolean = System.getProperty("os.name").orEmpty().lowercase().startsWith("mac"),
) {

    sealed interface Action {
        /** Switch to the navigation destination at [index] (0-based, rail order). */
        data class SelectTab(val index: Int) : Action
    }

    /** Set by the UI that can perform the actions; `null` while nothing handles them. */
    var handler: ((Action) -> Unit)? = null

    fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val action = actionFor(
            key = event.key,
            isCtrlPressed = event.isCtrlPressed,
            isMetaPressed = event.isMetaPressed,
            isAltPressed = event.isAltPressed,
            isShiftPressed = event.isShiftPressed,
        ) ?: return false
        val handler = handler ?: return false
        handler(action)
        return true
    }

    fun actionFor(
        key: Key,
        isCtrlPressed: Boolean,
        isMetaPressed: Boolean,
        isAltPressed: Boolean,
        isShiftPressed: Boolean,
    ): Action? {
        val primaryModifier = if (isMacOs) isMetaPressed && !isCtrlPressed else isCtrlPressed && !isMetaPressed
        if (!primaryModifier || isAltPressed || isShiftPressed) return null
        val index = TAB_KEYS.indexOfFirst { key in it }
        return if (index >= 0) Action.SelectTab(index) else null
    }

    private companion object {
        val TAB_KEYS = listOf(
            setOf(Key.One, Key.NumPad1),
            setOf(Key.Two, Key.NumPad2),
            setOf(Key.Three, Key.NumPad3),
            setOf(Key.Four, Key.NumPad4),
            setOf(Key.Five, Key.NumPad5),
        )
    }
}
