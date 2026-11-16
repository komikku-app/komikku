package android.util

/**
 * android-compat: `android.util.DisplayMetrics`. Desktop defaults to a density of 1 (160 dpi),
 * i.e. 1 dp = 1 px; Compose Desktop does its own scaling for UI.
 */
class DisplayMetrics {
    @JvmField var widthPixels: Int = 0

    @JvmField var heightPixels: Int = 0

    @JvmField var density: Float = 1f

    @JvmField var densityDpi: Int = DENSITY_DEFAULT

    @JvmField var scaledDensity: Float = 1f

    @JvmField var xdpi: Float = DENSITY_DEFAULT.toFloat()

    @JvmField var ydpi: Float = DENSITY_DEFAULT.toFloat()

    fun setToDefaults() {
        widthPixels = 0
        heightPixels = 0
        density = 1f
        densityDpi = DENSITY_DEFAULT
        scaledDensity = 1f
        xdpi = DENSITY_DEFAULT.toFloat()
        ydpi = DENSITY_DEFAULT.toFloat()
    }

    companion object {
        const val DENSITY_DEFAULT = 160
        const val DENSITY_MEDIUM = 160
        const val DENSITY_HIGH = 240
        const val DENSITY_XHIGH = 320
    }
}
