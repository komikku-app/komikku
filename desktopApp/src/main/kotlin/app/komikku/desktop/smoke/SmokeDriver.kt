package app.komikku.desktop.smoke

import java.io.File

/**
 * What the smoke script needs from the window: the app's navigators, keyboard input, the text on screen, waiting for
 * the UI to settle and screenshots. [WindowSmokeDriver] drives the real window of the packaged app; the tests drive
 * the same script headless through the Compose test framework.
 */
internal interface SmokeDriver {

    val navigators: SmokeNavigators

    /** Runs [block] on the UI thread. */
    suspend fun <T> onUi(block: () -> T): T

    /** One key press (down and up) as the keyboard sends it. */
    suspend fun press(key: SmokeKey)

    /** Types [text] as the keyboard would. */
    suspend fun type(text: String)

    /** The texts on screen (the accessibility names and descriptions of the last frame). */
    suspend fun texts(): List<String>

    /** Waits until no composition has pending work, at most [timeoutMillis]; `false` when it never settled. */
    suspend fun awaitIdle(timeoutMillis: Long): Boolean

    /** Saves a PNG of the window to [file]. */
    suspend fun screenshot(file: File): Screenshot

    /** The accessibility tree of the last frame, one node per line. */
    suspend fun accessibilityTree(): String
}

internal enum class SmokeKey { PageDown, Escape, Enter }

/**
 * A saved screenshot: its size in pixels, the size the window should give ([expectedWidth] × [expectedHeight]) and
 * the number of distinct colours (counted up to a small limit).
 */
internal data class Screenshot(
    val width: Int,
    val height: Int,
    val expectedWidth: Int,
    val expectedHeight: Int,
    val colours: Int,
) {
    /** Why the picture is not usable (blank or not the window's size), or `null`. */
    fun problem(): String? = when {
        width != expectedWidth || height != expectedHeight ->
            "screenshot is ${width}x$height, expected ${expectedWidth}x$expectedHeight"
        colours < 2 -> "screenshot is blank (one colour)"
        else -> null
    }

    override fun toString(): String = "${width}x$height, ${if (colours >= COLOUR_LIMIT) "$COLOUR_LIMIT+" else colours} colours"

    companion object {
        const val COLOUR_LIMIT = 64

        /** Distinct values in [pixels] (ARGB), counted up to [COLOUR_LIMIT]. */
        fun countColours(pixels: IntArray): Int {
            val seen = HashSet<Int>()
            for (pixel in pixels) {
                if (seen.add(pixel) && seen.size >= COLOUR_LIMIT) break
            }
            return seen.size
        }
    }
}
