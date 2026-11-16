package android.view

import android.content.Context
import android.content.res.Resources

/**
 * android-compat: `android.view.ViewConfiguration` with the AOSP default timeouts and sizes.
 * Scaled values use the compat display density (1 on desktop, see `DisplayMetrics`).
 * Needed by `presentation-core` (scrollbar fade timing and thickness).
 */
open class ViewConfiguration private constructor(density: Float) {

    open val scaledScrollBarSize: Int = (SCROLL_BAR_SIZE_DP * density + 0.5f).toInt()

    open val scaledTouchSlop: Int = (TOUCH_SLOP_DP * density + 0.5f).toInt()

    open val scaledDoubleTapSlop: Int = (DOUBLE_TAP_SLOP_DP * density + 0.5f).toInt()

    open val scaledMinimumFlingVelocity: Int = (MINIMUM_FLING_VELOCITY_DP * density + 0.5f).toInt()

    open val scaledMaximumFlingVelocity: Int = (MAXIMUM_FLING_VELOCITY_DP * density + 0.5f).toInt()

    companion object {
        private const val SCROLL_BAR_SIZE_DP = 4
        private const val TOUCH_SLOP_DP = 8
        private const val DOUBLE_TAP_SLOP_DP = 100
        private const val MINIMUM_FLING_VELOCITY_DP = 50
        private const val MAXIMUM_FLING_VELOCITY_DP = 8000

        private val instance by lazy { ViewConfiguration(Resources.getSystem().displayMetrics.density) }

        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun get(context: Context?): ViewConfiguration = instance

        @JvmStatic
        fun getScrollBarFadeDuration(): Int = 250

        @JvmStatic
        fun getScrollDefaultDelay(): Int = 300

        @JvmStatic
        fun getTapTimeout(): Int = 100

        @JvmStatic
        fun getDoubleTapTimeout(): Int = 300

        @JvmStatic
        fun getLongPressTimeout(): Int = 400

        @JvmStatic
        fun getPressedStateDuration(): Int = 64
    }
}
