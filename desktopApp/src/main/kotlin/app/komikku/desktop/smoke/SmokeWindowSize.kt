package app.komikku.desktop.smoke

import app.komikku.desktop.window.DesktopScreens

/**
 * The smoke window's size: [PREFERRED_WIDTH] × [PREFERRED_HEIGHT] where the screen has room, else the largest window
 * the usable screen area (without menu bar, Dock or task bar) holds, its aspect kept between [MIN_ASPECT] and
 * [MAX_ASPECT]; refused below [MIN_WIDTH] × [MIN_HEIGHT] (macOS runner of CI run 46139150350: about 1024x684 usable).
 *
 * The minimum is what the script's screens need with the whole of what they check on screen: the details screen's
 * header and both fixture chapters (the lower one ends about 530 px below the content's top), its action row on one
 * line (about 820 px wide next to the rail), plus the window's title bar. `SmokeScriptTest` runs the script at the
 * content size of a minimum window.
 */
internal object SmokeWindowSize {

    const val PREFERRED_WIDTH = 1280
    const val PREFERRED_HEIGHT = 800
    const val MIN_WIDTH = 960
    const val MIN_HEIGHT = 600
    private const val MIN_ASPECT = 1.25
    private const val MAX_ASPECT = 1.6

    sealed interface Choice {
        /** [reason] says why the window is smaller than the preferred one; `null` when it is not. */
        data class Fits(val width: Int, val height: Int, val reason: String?) : Choice {
            override fun toString() = "${width}x$height" + (reason?.let { ", $it" } ?: " (preferred)")
        }

        data class TooSmall(val reason: String) : Choice
    }

    /** The window for a usable screen area of [usableWidth] × [usableHeight]. */
    fun choose(usableWidth: Int, usableHeight: Int): Choice {
        var width = minOf(PREFERRED_WIDTH, usableWidth)
        var height = minOf(PREFERRED_HEIGHT, usableHeight)
        if (width > height * MAX_ASPECT) width = (height * MAX_ASPECT).toInt()
        if (width < height * MIN_ASPECT) height = (width / MIN_ASPECT).toInt()
        val usable = "the usable screen area is ${usableWidth}x$usableHeight"
        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            return Choice.TooSmall("$usable; the smoke window needs at least ${MIN_WIDTH}x$MIN_HEIGHT")
        }
        val reduced = width != PREFERRED_WIDTH || height != PREFERRED_HEIGHT
        return Choice.Fits(width, height, if (reduced) "not ${PREFERRED_WIDTH}x$PREFERRED_HEIGHT: $usable" else null)
    }

    /** [choose] for this machine's main screen; the preferred size where there is no screen (headless). */
    fun forScreen(): Choice {
        val usable = DesktopScreens.usableAreas().firstOrNull() ?: return choose(PREFERRED_WIDTH, PREFERRED_HEIGHT)
        return choose(usable.width, usable.height)
    }
}
