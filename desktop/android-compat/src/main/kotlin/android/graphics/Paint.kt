package android.graphics

import app.komikku.desktop.compat.unsupported
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Paint as SkPaint

/**
 * android-compat: `android.graphics.Paint`, what bitmap and shape drawing use: colour and alpha, flags (anti-alias,
 * bitmap filtering; a new paint filters bitmaps, as on Android 9 and later), style and stroke. Text members throw
 * `UnsupportedOnDesktop` (text drawing is not part of the desktop layer yet).
 */
open class Paint(flags: Int) {

    enum class Style { FILL, STROKE, FILL_AND_STROKE }

    enum class Cap { BUTT, ROUND, SQUARE }

    enum class Join { MITER, ROUND, BEVEL }

    enum class Align { LEFT, CENTER, RIGHT }

    constructor() : this(0)

    constructor(paint: Paint) : this(paint.flags) {
        set(paint)
    }

    private var flags = flags or HIDDEN_DEFAULT_PAINT_FLAGS
    private var color = 0xff000000.toInt()
    private var style = Style.FILL
    private var strokeWidth = 0f
    private var strokeMiter = 4f
    private var strokeCap = Cap.BUTT
    private var strokeJoin = Join.MITER

    fun set(src: Paint) {
        flags = src.flags
        color = src.color
        style = src.style
        strokeWidth = src.strokeWidth
        strokeMiter = src.strokeMiter
        strokeCap = src.strokeCap
        strokeJoin = src.strokeJoin
    }

    open fun reset() = set(Paint())

    open fun getFlags(): Int = flags

    open fun setFlags(flags: Int) {
        this.flags = flags
    }

    open fun isAntiAlias(): Boolean = flags and ANTI_ALIAS_FLAG != 0

    open fun setAntiAlias(aa: Boolean) = setFlag(ANTI_ALIAS_FLAG, aa)

    open fun isFilterBitmap(): Boolean = flags and FILTER_BITMAP_FLAG != 0

    open fun setFilterBitmap(filter: Boolean) = setFlag(FILTER_BITMAP_FLAG, filter)

    open fun isDither(): Boolean = flags and DITHER_FLAG != 0

    open fun setDither(dither: Boolean) = setFlag(DITHER_FLAG, dither)

    open fun getColor(): Int = color

    open fun setColor(color: Int) {
        this.color = color
    }

    open fun getAlpha(): Int = color ushr 24

    open fun setAlpha(a: Int) {
        color = (color and 0x00ffffff) or (a.coerceIn(0, 255) shl 24)
    }

    open fun setARGB(a: Int, r: Int, g: Int, b: Int) = setColor((a shl 24) or (r shl 16) or (g shl 8) or b)

    open fun getStyle(): Style = style

    open fun setStyle(style: Style) {
        this.style = style
    }

    open fun getStrokeWidth(): Float = strokeWidth

    open fun setStrokeWidth(width: Float) {
        if (width >= 0) strokeWidth = width
    }

    open fun getStrokeMiter(): Float = strokeMiter

    open fun setStrokeMiter(miter: Float) {
        if (miter >= 0) strokeMiter = miter
    }

    open fun getStrokeCap(): Cap = strokeCap

    open fun setStrokeCap(cap: Cap) {
        strokeCap = cap
    }

    open fun getStrokeJoin(): Join = strokeJoin

    open fun setStrokeJoin(join: Join) {
        strokeJoin = join
    }

    open fun getTextSize(): Float = unsupported(CLASS, "getTextSize")

    open fun setTextSize(textSize: Float): Unit = unsupported(CLASS, "setTextSize")

    open fun setTextAlign(align: Align): Unit = unsupported(CLASS, "setTextAlign")

    open fun setTextScaleX(scaleX: Float): Unit = unsupported(CLASS, "setTextScaleX")

    open fun setLetterSpacing(letterSpacing: Float): Unit = unsupported(CLASS, "setLetterSpacing")

    open fun getFontSpacing(): Float = unsupported(CLASS, "getFontSpacing")

    open fun measureText(text: String): Float = unsupported(CLASS, "measureText")

    open fun hasGlyph(string: String): Boolean = unsupported(CLASS, "hasGlyph")

    private fun setFlag(flag: Int, on: Boolean) {
        flags = if (on) flags or flag else flags and flag.inv()
    }

    /** This paint for Skia (the caller closes it). */
    internal fun toSkia(): SkPaint = SkPaint().also {
        it.color = color
        it.isAntiAlias = isAntiAlias()
        it.isDither = isDither()
        it.mode = when (style) {
            Style.FILL -> PaintMode.FILL
            Style.STROKE -> PaintMode.STROKE
            Style.FILL_AND_STROKE -> PaintMode.STROKE_AND_FILL
        }
        it.strokeWidth = strokeWidth
        it.strokeMiter = strokeMiter
        it.strokeCap = when (strokeCap) {
            Cap.BUTT -> PaintStrokeCap.BUTT
            Cap.ROUND -> PaintStrokeCap.ROUND
            Cap.SQUARE -> PaintStrokeCap.SQUARE
        }
        it.strokeJoin = when (strokeJoin) {
            Join.MITER -> PaintStrokeJoin.MITER
            Join.ROUND -> PaintStrokeJoin.ROUND
            Join.BEVEL -> PaintStrokeJoin.BEVEL
        }
    }

    companion object {
        private const val CLASS = "android.graphics.Paint"

        const val ANTI_ALIAS_FLAG = 0x01
        const val FILTER_BITMAP_FLAG = 0x02
        const val DITHER_FLAG = 0x04
        const val UNDERLINE_TEXT_FLAG = 0x08
        const val STRIKE_THRU_TEXT_FLAG = 0x10
        const val FAKE_BOLD_TEXT_FLAG = 0x20
        const val LINEAR_TEXT_FLAG = 0x40
        const val SUBPIXEL_TEXT_FLAG = 0x80
        const val DEV_KERN_TEXT_FLAG = 0x100
        const val EMBEDDED_BITMAP_TEXT_FLAG = 0x400

        /** Set by every constructor since Android 9. */
        private const val HIDDEN_DEFAULT_PAINT_FLAGS = DEV_KERN_TEXT_FLAG or EMBEDDED_BITMAP_TEXT_FLAG or FILTER_BITMAP_FLAG
    }
}
