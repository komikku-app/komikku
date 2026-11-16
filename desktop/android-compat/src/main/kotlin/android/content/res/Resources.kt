package android.content.res

import android.util.DisplayMetrics

/**
 * android-compat: `android.content.res.Resources`. Only display metrics are available on desktop;
 * there are no Android resources (`R.*` ids).
 */
open class Resources internal constructor(
    val displayMetrics: DisplayMetrics,
) {

    companion object {
        private val systemResources by lazy { Resources(DisplayMetrics().apply { setToDefaults() }) }

        @JvmStatic
        fun getSystem(): Resources = systemResources
    }
}
