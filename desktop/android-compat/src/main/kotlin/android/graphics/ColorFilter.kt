package android.graphics

import org.jetbrains.skia.ColorFilter as SkColorFilter

/** android-compat: `android.graphics.ColorFilter`, the base of colour filters a [Paint] applies when drawing. */
open class ColorFilter internal constructor() {

    /** The filter for Skia (owned by this object). */
    internal open fun toSkia(): SkColorFilter? = null
}
