package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.Choreographer
import androidx.recyclerview.widget.RecyclerView

// KMK -->
/**
 * Scrolls a [RecyclerView] continuously at a constant speed, one step per display frame.
 *
 * Must be used from the main thread only.
 *
 * @param speedDpPerSecond current speed, read on every frame so that changes apply immediately.
 * @param gate decides, on every frame, if the list can scroll, has to wait or has to stop.
 * @param onStopRequested called when the scroller stopped by itself (not through [stop]).
 */
class WebtoonAutoScroller(
    private val recycler: RecyclerView,
    private val speedDpPerSecond: () -> Int,
    private val gate: () -> AutoScrollGate,
    private val onStopRequested: () -> Unit,
) {

    private val stateMachine = AutoScrollStateMachine()
    private val accumulator = SubPixelAccumulator()
    private val choreographer = Choreographer.getInstance()

    private var lastFrameTimeNanos = NO_FRAME

    val isActive get() = stateMachine.isActive

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!stateMachine.isActive) return

            val deltaNanos = if (lastFrameTimeNanos == NO_FRAME) 0L else frameTimeNanos - lastFrameTimeNanos
            lastFrameTimeNanos = frameTimeNanos

            when (gate()) {
                AutoScrollGate.STOP -> {
                    stop()
                    onStopRequested()
                    return
                }
                AutoScrollGate.HOLD -> stateMachine.hold()
                AutoScrollGate.PROCEED -> stateMachine.release()
            }

            if (stateMachine.isScrolling) {
                val pixels = WebtoonAutoScrollMath.pixelsForFrame(
                    speedDpPerSecond = speedDpPerSecond(),
                    density = recycler.resources.displayMetrics.density,
                    zoomScale = recycler.scaleY,
                    frameDeltaNanos = deltaNanos,
                )
                val whole = accumulator.add(pixels)
                if (whole != 0) {
                    recycler.scrollBy(0, whole)
                }
            }

            choreographer.postFrameCallback(this)
        }
    }

    fun start() {
        if (stateMachine.isActive) return
        stateMachine.start()
        lastFrameTimeNanos = NO_FRAME
        accumulator.reset()
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        stateMachine.stop()
        choreographer.removeFrameCallback(frameCallback)
    }

    private companion object {
        const val NO_FRAME = -1L
    }
}
// KMK <--
