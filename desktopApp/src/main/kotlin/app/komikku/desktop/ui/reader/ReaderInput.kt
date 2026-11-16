package app.komikku.desktop.ui.reader

import androidx.compose.ui.input.key.Key
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode

/** Reader commands triggered by keys, click zones and toolbar buttons. */
enum class ReaderAction {
    Next,
    Previous,
    First,
    Last,
    NextChapter,
    PreviousChapter,
    ScrollDown,
    ScrollUp,
    PageDown,
    PageUp,
    ZoomIn,
    ZoomOut,
    ZoomReset,
    ToggleFullscreen,
    ToggleMenu,
    Back,
}

/**
 * Keyboard map of the desktop reader (pure, unit-tested):
 *
 * | Key | Paged (LTR / vertical) | Paged RTL | Webtoon |
 * |-----|------------------------|-----------|---------|
 * | → | next | previous | — |
 * | ← | previous | next | — |
 * | ↓ / ↑ | next / previous | next / previous | scroll |
 * | PgDn / PgUp, Space / Shift+Space | next / previous | same | screen down / up |
 * | Home / End | first / last page | same | same |
 * | N / P | next / previous chapter | same | same |
 * | + / - / 0 (with or without Ctrl) | zoom in / out / reset | same | same |
 * | F11, F | fullscreen | same | same |
 * | M, Enter | menu | same | same |
 * | Esc | back (closes the menu first) | same | same |
 */
object ReaderKeyMap {

    fun actionFor(key: Key, shift: Boolean, mode: ReadingMode): ReaderAction? {
        val rtl = mode == ReadingMode.RIGHT_TO_LEFT
        val webtoon = mode.type == ReadingMode.ViewerType.Webtoon
        return when (key) {
            Key.DirectionRight -> if (webtoon) {
                null
            } else if (rtl) {
                ReaderAction.Previous
            } else {
                ReaderAction.Next
            }
            Key.DirectionLeft -> if (webtoon) {
                null
            } else if (rtl) {
                ReaderAction.Next
            } else {
                ReaderAction.Previous
            }
            Key.DirectionDown -> if (webtoon) ReaderAction.ScrollDown else ReaderAction.Next
            Key.DirectionUp -> if (webtoon) ReaderAction.ScrollUp else ReaderAction.Previous
            Key.PageDown -> if (webtoon) ReaderAction.PageDown else ReaderAction.Next
            Key.PageUp -> if (webtoon) ReaderAction.PageUp else ReaderAction.Previous
            Key.Spacebar -> when {
                webtoon -> if (shift) ReaderAction.PageUp else ReaderAction.PageDown
                shift -> ReaderAction.Previous
                else -> ReaderAction.Next
            }
            Key.MoveHome -> ReaderAction.First
            Key.MoveEnd -> ReaderAction.Last
            Key.N -> ReaderAction.NextChapter
            Key.P -> ReaderAction.PreviousChapter
            Key.Plus, Key.NumPadAdd, Key.Equals -> ReaderAction.ZoomIn
            Key.Minus, Key.NumPadSubtract -> ReaderAction.ZoomOut
            Key.Zero, Key.NumPad0 -> ReaderAction.ZoomReset
            Key.F11, Key.F -> ReaderAction.ToggleFullscreen
            Key.M, Key.Enter -> ReaderAction.ToggleMenu
            Key.Escape -> ReaderAction.Back
            else -> null
        }
    }

    /**
     * Click zones of the paged viewer, like Android's default "L shaped"/edge navigation simplified to
     * thirds: the outer thirds turn pages (direction-aware), the middle toggles the menu. Vertical
     * paging uses the top / bottom thirds.
     */
    fun clickAction(xFraction: Float, yFraction: Float, mode: ReadingMode): ReaderAction {
        if (mode.direction == ReadingMode.Direction.Vertical) {
            return when {
                yFraction < 1f / 3 -> ReaderAction.Previous
                yFraction > 2f / 3 -> ReaderAction.Next
                else -> ReaderAction.ToggleMenu
            }
        }
        val rtl = mode == ReadingMode.RIGHT_TO_LEFT
        return when {
            xFraction < 1f / 3 -> if (rtl) ReaderAction.Next else ReaderAction.Previous
            xFraction > 2f / 3 -> if (rtl) ReaderAction.Previous else ReaderAction.Next
            else -> ReaderAction.ToggleMenu
        }
    }
}
