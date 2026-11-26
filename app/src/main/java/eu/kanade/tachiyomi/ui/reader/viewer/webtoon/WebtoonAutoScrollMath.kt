package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

// KMK -->
/**
 * Pure helpers used by [WebtoonAutoScroller]. Kept free of Android classes so they can be unit tested.
 */
object WebtoonAutoScrollMath {

    /**
     * Longest frame time that is taken into account. A longer gap (the app was stalled, a debugger was
     * attached, the display was asleep...) would otherwise make the list jump by a large distance.
     */
    const val MAX_FRAME_DELTA_NANOS = 100_000_000L

    private const val NANOS_PER_SECOND = 1_000_000_000f

    /**
     * Zoom scales below this are treated as this value, to avoid dividing by (almost) zero.
     */
    private const val MIN_ZOOM_SCALE = 0.1f

    /**
     * Distance, in pixels, that the list has to be scrolled during a frame that lasted [frameDeltaNanos]
     * so that the content moves on screen at [speedDpPerSecond].
     *
     * The result does not depend on the refresh rate, since it is based on the real frame time, and not on
     * the screen density, since the speed is expressed in dp. [zoomScale] is the scale applied to the
     * RecyclerView: a scrolled pixel is displayed as [zoomScale] screen pixels, so it is compensated for.
     */
    fun pixelsForFrame(
        speedDpPerSecond: Int,
        density: Float,
        zoomScale: Float,
        frameDeltaNanos: Long,
    ): Float {
        if (speedDpPerSecond <= 0 || frameDeltaNanos <= 0L) return 0f
        val seconds = frameDeltaNanos.coerceAtMost(MAX_FRAME_DELTA_NANOS) / NANOS_PER_SECOND
        return speedDpPerSecond * density * seconds / zoomScale.coerceAtLeast(MIN_ZOOM_SCALE)
    }
}

/**
 * RecyclerView can only be scrolled by whole pixels, but a frame usually asks for a fraction of one.
 * This keeps the fractional part so that it is not lost, otherwise slow speeds would never move.
 */
class SubPixelAccumulator {

    private var remainder = 0f

    /**
     * Adds [pixels] and returns the number of whole pixels that can be scrolled now.
     */
    fun add(pixels: Float): Int {
        val total = remainder + pixels
        val whole = total.toInt()
        remainder = total - whole
        return whole
    }

    fun reset() {
        remainder = 0f
    }
}

/**
 * Decision taken for every frame of the auto-scroll.
 */
enum class AutoScrollGate {
    /** Keep scrolling. */
    PROCEED,

    /** Wait without scrolling, for example while the page that is about to be shown is loading. */
    HOLD,

    /** Stop because there is nothing else to show, or the content can't be shown. */
    STOP,
}

/**
 * State of the auto-scroll.
 *
 * - [State.STOPPED]: not scrolling.
 * - [State.RUNNING]: scrolling.
 * - [State.HOLDING]: started, but waiting for something (like a page to load) before scrolling again.
 */
class AutoScrollStateMachine {

    enum class State { STOPPED, RUNNING, HOLDING }

    var state = State.STOPPED
        private set

    val isActive get() = state != State.STOPPED

    val isScrolling get() = state == State.RUNNING

    fun start() {
        state = State.RUNNING
    }

    fun stop() {
        state = State.STOPPED
    }

    fun hold() {
        if (state == State.RUNNING) state = State.HOLDING
    }

    fun release() {
        if (state == State.HOLDING) state = State.RUNNING
    }
}
// KMK <--
