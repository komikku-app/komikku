package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import kotlin.math.abs

/**
 * Implementation of a [RecyclerView] used by the webtoon reader.
 */
class WebtoonRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    private var isZooming = false
    private var atLastPosition = false
    private var atFirstPosition = false
    private var halfWidth = 0
    private var halfHeight = 0
    var originalHeight = 0
        private set
    private var heightSet = false
    private var firstVisibleItemPosition = 0
    private var lastVisibleItemPosition = 0
    private var currentScale = DEFAULT_RATE
    var zoomOutDisabled = false
        set(value) {
            field = value
            if (value && currentScale < DEFAULT_RATE) {
                zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
            }
        }
    private val minRate
        get() = if (zoomOutDisabled) DEFAULT_RATE else MIN_RATE

    private val listener = GestureListener()
    private val detector = Detector()

    var doubleTapZoom = true

    // KMK -->
    var pinchToZoom = true
    // KMK <--

    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Boolean)? = null
    var autoScrollTogglePauseListener: (() -> Unit)? = null
    var autoScrollActivateListener: (() -> Unit)? = null
    var autoScrollStopListener: (() -> Unit)? = null
    var autoScrollSpeedChangeListener: ((Int) -> Unit)? = null
    var autoScrollInteractionListener: (() -> Unit)? = null
    var autoScrollGestureModeEnabled = false
        set(value) {
            field = value
            if (!value) {
                detector.resetAutoScrollGestureState()
            }
        }
    var autoScrollActivationGestureEnabled = false
        set(value) {
            field = value
            if (!value) {
                detector.resetAutoScrollActivationGestureState()
            }
        }

    private var isManuallyScrolling = false
    private var tapDuringManualScroll = false

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        halfWidth = MeasureSpec.getSize(widthSpec) / 2
        halfHeight = MeasureSpec.getSize(heightSpec) / 2
        if (!heightSet) {
            originalHeight = MeasureSpec.getSize(heightSpec)
            heightSet = true
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (autoScrollGestureModeEnabled && detector.handleAutoScrollTouchEvent(e)) {
            return true
        }

        if (autoScrollActivationGestureEnabled) {
            detector.trackAutoScrollActivationTouchEvent(e)
        }

        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            tapDuringManualScroll = isManuallyScrolling
        }

        detector.onTouchEvent(e)
        return super.onTouchEvent(e)
    }

    fun isAutoScrollGestureModeEnabled(): Boolean {
        return autoScrollGestureModeEnabled
    }

    fun isAutoScrollGestureInProgress(): Boolean {
        return autoScrollGestureModeEnabled && detector.isAutoScrollGestureInProgress()
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        val layoutManager = layoutManager
        lastVisibleItemPosition =
            (layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
        firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        val layoutManager = layoutManager
        val visibleItemCount = layoutManager?.childCount ?: 0
        val totalItemCount = layoutManager?.itemCount ?: 0
        atLastPosition = visibleItemCount > 0 && lastVisibleItemPosition == totalItemCount - 1
        atFirstPosition = firstVisibleItemPosition == 0

        if (state == SCROLL_STATE_IDLE) {
            isManuallyScrolling = false
        }
    }

    private fun getPositionX(positionX: Float): Float {
        if (currentScale < 1) {
            return 0f
        }
        val maxPositionX = halfWidth * (currentScale - 1)
        return positionX.coerceIn(-maxPositionX, maxPositionX)
    }

    private fun getPositionY(positionY: Float): Float {
        if (currentScale < 1) {
            return (originalHeight / 2 - halfHeight).toFloat()
        }
        val maxPositionY = halfHeight * (currentScale - 1)
        return positionY.coerceIn(-maxPositionY, maxPositionY)
    }

    private fun zoom(
        fromRate: Float,
        toRate: Float,
        fromX: Float,
        toX: Float,
        fromY: Float,
        toY: Float,
    ) {
        isZooming = true
        val animatorSet = AnimatorSet()
        val translationXAnimator = ValueAnimator.ofFloat(fromX, toX)
        translationXAnimator.addUpdateListener { animation -> x = animation.animatedValue as Float }

        val translationYAnimator = ValueAnimator.ofFloat(fromY, toY)
        translationYAnimator.addUpdateListener { animation -> y = animation.animatedValue as Float }

        val scaleAnimator = ValueAnimator.ofFloat(fromRate, toRate)
        scaleAnimator.addUpdateListener { animation ->
            currentScale = animation.animatedValue as Float
            setScaleRate(currentScale)
        }
        animatorSet.playTogether(translationXAnimator, translationYAnimator, scaleAnimator)
        animatorSet.duration = ANIMATOR_DURATION_TIME.toLong()
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
        animatorSet.doOnEnd {
            isZooming = false
            currentScale = toRate
        }
    }

    fun zoomFling(velocityX: Int, velocityY: Int): Boolean {
        if (currentScale <= 1f) return false

        val distanceTimeFactor = 0.4f
        val animatorSet = AnimatorSet()

        if (velocityX != 0) {
            val dx = (distanceTimeFactor * velocityX / 2)
            val newX = getPositionX(x + dx)
            val translationXAnimator = ValueAnimator.ofFloat(x, newX)
            translationXAnimator.addUpdateListener { animation -> x = getPositionX(animation.animatedValue as Float) }
            animatorSet.play(translationXAnimator)
        }
        if (velocityY != 0 && (atFirstPosition || atLastPosition)) {
            val dy = (distanceTimeFactor * velocityY / 2)
            val newY = getPositionY(y + dy)
            val translationYAnimator = ValueAnimator.ofFloat(y, newY)
            translationYAnimator.addUpdateListener { animation -> y = getPositionY(animation.animatedValue as Float) }
            animatorSet.play(translationYAnimator)
        }

        animatorSet.duration = 400
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()

        return true
    }

    private fun zoomScrollBy(dx: Int, dy: Int) {
        if (dx != 0) {
            x = getPositionX(x + dx)
        }
        if (dy != 0) {
            y = getPositionY(y + dy)
        }
    }

    private fun setScaleRate(rate: Float) {
        scaleX = rate
        scaleY = rate
    }

    fun onScale(scaleFactor: Float) {
        // KMK ->
        if (!detector.isQuickScaling && !pinchToZoom) return

        scaleTo(currentScale * scaleFactor)
    }

    fun scaleTo(scale: Float) {
        // KMK <--
        currentScale = scale
        currentScale = currentScale.coerceIn(
            minRate,
            MAX_SCALE_RATE,
        )

        setScaleRate(currentScale)

        layoutParams.height = if (currentScale < 1) {
            (originalHeight / currentScale).toInt()
        } else {
            originalHeight
        }
        halfHeight = layoutParams.height / 2

        if (currentScale != DEFAULT_RATE) {
            x = getPositionX(x)
            y = getPositionY(y)
        } else {
            x = 0f
            y = 0f
        }

        requestLayout()
    }

    fun onScaleBegin() {
        if (detector.isDoubleTapping) {
            detector.isQuickScaling = true
        }
    }

    fun onScaleEnd() {
        if (scaleX < minRate) {
            zoom(currentScale, minRate, x, 0f, y, 0f)
        }
    }

    fun onManualScroll() {
        isManuallyScrolling = true
    }

    inner class GestureListener : GestureDetectorWithLongTap.Listener() {

        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            if (!tapDuringManualScroll) {
                tapListener?.invoke(ev)
            }
            return false
        }

        override fun onDoubleTap(ev: MotionEvent): Boolean {
            detector.isDoubleTapping = true
            return false
        }

        fun onDoubleTapConfirmed(ev: MotionEvent) {
            if (!isZooming && doubleTapZoom) {
                if (scaleX != DEFAULT_RATE) {
                    zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
                    layoutParams.height = originalHeight
                    halfHeight = layoutParams.height / 2
                    requestLayout()
                } else {
                    val toScale = 2f
                    val toX = (halfWidth - ev.x) * (toScale - 1)
                    val toY = (halfHeight - ev.y) * (toScale - 1)
                    zoom(DEFAULT_RATE, toScale, 0f, toX, 0f, toY)
                }
            }
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            if (longTapListener?.invoke(ev) == true) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    inner class Detector : GestureDetectorWithLongTap(context, listener) {

        private var scrollPointerId = 0
        private var downX = 0
        private var downY = 0
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val tapTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        private val autoScrollSpeedStep = resources.displayMetrics.density * 24f
        private val autoScrollStopThreshold = touchSlop * 4
        private var isZoomDragging = false
        var isDoubleTapping = false
        var isQuickScaling = false
        private var autoScrollPointerActive = false
        private var autoScrollStartX = 0f
        private var autoScrollStartY = 0f
        private var autoScrollLastY = 0f
        private var autoScrollSpeedAccumulator = 0f
        private var autoScrollTwoFingerLastY = 0f
        private var autoScrollTwoFingerRemainder = 0f
        private var autoScrollDownTime = 0L
        private var autoScrollMoved = false
        private var autoScrollStopTriggered = false
        private var autoScrollMode = AutoScrollGestureMode.NONE
        private var autoScrollActivationTracking = false
        private var autoScrollActivationStartX = 0f
        private var autoScrollActivationStartY = 0f
        private var autoScrollActivationMultiTouch = false

        fun isAutoScrollGestureInProgress(): Boolean {
            return autoScrollPointerActive
        }

        fun resetAutoScrollGestureState() {
            autoScrollPointerActive = false
            autoScrollSpeedAccumulator = 0f
            autoScrollTwoFingerRemainder = 0f
            autoScrollMoved = false
            autoScrollStopTriggered = false
            autoScrollMode = AutoScrollGestureMode.NONE
        }

        fun resetAutoScrollActivationGestureState() {
            autoScrollActivationTracking = false
            autoScrollActivationMultiTouch = false
        }

        fun trackAutoScrollActivationTouchEvent(ev: MotionEvent) {
            if (!autoScrollActivationGestureEnabled || autoScrollGestureModeEnabled) return

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resetAutoScrollActivationGestureState()
                    autoScrollActivationTracking = true
                    autoScrollActivationStartX = ev.x
                    autoScrollActivationStartY = ev.y
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    autoScrollActivationMultiTouch = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ev.pointerCount > 1) {
                        autoScrollActivationMultiTouch = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.x - autoScrollActivationStartX
                    val dy = ev.y - autoScrollActivationStartY
                    val shouldActivate =
                        autoScrollActivationTracking &&
                            !autoScrollActivationMultiTouch &&
                            currentScale == DEFAULT_RATE &&
                            abs(dx) >= autoScrollStopThreshold &&
                            abs(dx) > abs(dy)

                    resetAutoScrollActivationGestureState()
                    if (shouldActivate) {
                        autoScrollActivateListener?.invoke()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    resetAutoScrollActivationGestureState()
                }
            }
        }

        fun handleAutoScrollTouchEvent(ev: MotionEvent): Boolean {
            if (!autoScrollGestureModeEnabled) return false

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resetAutoScrollGestureState()
                    autoScrollPointerActive = true
                    autoScrollStartX = ev.x
                    autoScrollStartY = ev.y
                    autoScrollLastY = ev.y
                    autoScrollDownTime = ev.eventTime
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    autoScrollPointerActive = true
                    autoScrollMode = AutoScrollGestureMode.MOVE
                    autoScrollMoved = true
                    autoScrollTwoFingerLastY = averageY(ev)
                    autoScrollTwoFingerRemainder = 0f
                    stopScroll()
                    onManualScroll()
                    autoScrollInteractionListener?.invoke()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ev.pointerCount >= 2 || autoScrollMode == AutoScrollGestureMode.MOVE) {
                        val currentY = averageY(ev)
                        val deltaY = currentY - autoScrollTwoFingerLastY
                        autoScrollTwoFingerLastY = currentY

                        autoScrollMode = AutoScrollGestureMode.MOVE
                        autoScrollMoved = true
                        autoScrollTwoFingerRemainder += -deltaY

                        val scrollDelta = autoScrollTwoFingerRemainder.toInt()
                        if (scrollDelta != 0) {
                            autoScrollTwoFingerRemainder -= scrollDelta
                            stopScroll()
                            onManualScroll()
                            scrollBy(0, scrollDelta)
                        }
                        autoScrollInteractionListener?.invoke()
                        return true
                    }

                    val dx = ev.x - autoScrollStartX
                    val dy = ev.y - autoScrollStartY
                    if (autoScrollMode == AutoScrollGestureMode.NONE) {
                        if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) {
                            autoScrollMode = AutoScrollGestureMode.SPEED
                            autoScrollMoved = true
                            autoScrollLastY = ev.y
                            autoScrollSpeedAccumulator = 0f
                            stopScroll()
                            autoScrollInteractionListener?.invoke()
                            return true
                        }
                        if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                            autoScrollMode = AutoScrollGestureMode.STOP
                            autoScrollMoved = true
                            stopScroll()
                            autoScrollInteractionListener?.invoke()
                        }
                    }

                    when (autoScrollMode) {
                        AutoScrollGestureMode.SPEED -> {
                            autoScrollSpeedAccumulator += autoScrollLastY - ev.y
                            val steps = (autoScrollSpeedAccumulator / autoScrollSpeedStep).toInt()
                            if (steps != 0) {
                                autoScrollSpeedAccumulator -= steps * autoScrollSpeedStep
                                autoScrollSpeedChangeListener?.invoke(steps)
                                autoScrollInteractionListener?.invoke()
                            }
                            autoScrollLastY = ev.y
                        }
                        AutoScrollGestureMode.STOP -> {
                            if (!autoScrollStopTriggered && abs(dx) >= autoScrollStopThreshold) {
                                autoScrollStopTriggered = true
                                autoScrollStopListener?.invoke()
                                autoScrollInteractionListener?.invoke()
                            }
                        }
                        else -> Unit
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (autoScrollMode == AutoScrollGestureMode.MOVE && ev.pointerCount - 1 < 2) {
                        autoScrollMode = AutoScrollGestureMode.NONE
                        autoScrollTwoFingerRemainder = 0f
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val shouldTogglePause =
                        !autoScrollMoved &&
                            !autoScrollStopTriggered &&
                            autoScrollMode != AutoScrollGestureMode.MOVE &&
                            ev.eventTime - autoScrollDownTime <= tapTimeout
                    resetAutoScrollGestureState()
                    if (shouldTogglePause) {
                        autoScrollTogglePauseListener?.invoke()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    resetAutoScrollGestureState()
                    return true
                }
            }

            return true
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val action = ev.actionMasked
            val actionIndex = ev.actionIndex

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    scrollPointerId = ev.getPointerId(0)
                    downX = (ev.x + 0.5f).toInt()
                    downY = (ev.y + 0.5f).toInt()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrollPointerId = ev.getPointerId(actionIndex)
                    downX = (ev.getX(actionIndex) + 0.5f).toInt()
                    downY = (ev.getY(actionIndex) + 0.5f).toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDoubleTapping && isQuickScaling) {
                        return true
                    }

                    val index = ev.findPointerIndex(scrollPointerId)
                    if (index < 0) {
                        return false
                    }

                    val x = (ev.getX(index) + 0.5f).toInt()
                    val y = (ev.getY(index) + 0.5f).toInt()
                    var dx = x - downX
                    var dy = if (atFirstPosition || atLastPosition) y - downY else 0

                    if (!isZoomDragging && currentScale > 1f) {
                        var startScroll = false

                        if (abs(dx) > touchSlop) {
                            if (dx < 0) {
                                dx += touchSlop
                            } else {
                                dx -= touchSlop
                            }
                            startScroll = true
                        }
                        if (abs(dy) > touchSlop) {
                            if (dy < 0) {
                                dy += touchSlop
                            } else {
                                dy -= touchSlop
                            }
                            startScroll = true
                        }

                        if (startScroll) {
                            isZoomDragging = true
                        }
                    }

                    if (isZoomDragging) {
                        zoomScrollBy(dx, dy)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDoubleTapping && !isQuickScaling) {
                        listener.onDoubleTapConfirmed(ev)
                    }
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
            }
            return super.onTouchEvent(ev)
        }

        private fun averageY(event: MotionEvent): Float {
            var total = 0f
            for (index in 0 until event.pointerCount) {
                total += event.getY(index)
            }
            return total / event.pointerCount
        }
    }
}

private enum class AutoScrollGestureMode {
    NONE,
    SPEED,
    MOVE,
    STOP,
}

private const val ANIMATOR_DURATION_TIME = 200
private const val MIN_RATE = 0.5f
private const val DEFAULT_RATE = 1f
private const val MAX_SCALE_RATE = 3f
