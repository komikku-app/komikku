package android.graphics

import app.komikku.desktop.compat.NativeBitmaps
import app.komikku.desktop.compat.unsupported
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Rect as SkRect

/**
 * android-compat: `android.graphics.Canvas` drawing into a mutable [Bitmap] with Skia: bitmaps (with source and
 * destination rectangles), colours, lines and circles, and the matrix and clip stack. A bitmap is sampled like
 * Android: nearest pixel without a paint, filtered when the paint filters bitmaps (a new `Paint` does), so a
 * same-size copy of a rectangle is exact either way. Text drawing throws `UnsupportedOnDesktop`.
 */
open class Canvas(bitmap: Bitmap) {

    private val target = bitmap
    private val skia: SkCanvas

    init {
        check(bitmap.isMutable()) { "Immutable bitmap passed to Canvas constructor" }
        skia = bitmap.use("Canvas") { it.openCanvas() }
    }

    open fun getWidth(): Int = target.getWidth()

    open fun getHeight(): Int = target.getHeight()

    open fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
        val from = src ?: Rect(0, 0, bitmap.getWidth(), bitmap.getHeight())
        drawImage(
            bitmap,
            SkRect.makeLTRB(from.left.toFloat(), from.top.toFloat(), from.right.toFloat(), from.bottom.toFloat()),
            SkRect.makeLTRB(dst.left.toFloat(), dst.top.toFloat(), dst.right.toFloat(), dst.bottom.toFloat()),
            paint,
        )
    }

    open fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        val width = bitmap.getWidth().toFloat()
        val height = bitmap.getHeight().toFloat()
        drawImage(bitmap, SkRect.makeWH(width, height), SkRect.makeXYWH(left, top, width, height), paint)
    }

    open fun drawColor(color: Int): Unit = draw("drawColor", null) { canvas, _ ->
        SkPaint().use {
            it.color = color
            canvas.drawPaint(it)
        }
    }

    open fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint): Unit =
        draw("drawLine", paint) { canvas, skPaint -> canvas.drawLine(startX, startY, stopX, stopY, skPaint!!) }

    open fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint): Unit =
        draw("drawCircle", paint) { canvas, skPaint -> canvas.drawCircle(cx, cy, radius, skPaint!!) }

    open fun drawText(text: String, x: Float, y: Float, paint: Paint): Unit = unsupported(CLASS, "drawText")

    open fun save(): Int = state { it.save() }

    open fun restore() {
        state {
            if (it.saveCount <= 1) throw IllegalStateException("Underflow in restore - more restores than saves")
            it.restore()
        }
    }

    open fun getSaveCount(): Int = state { it.saveCount }

    open fun translate(dx: Float, dy: Float) {
        state { it.translate(dx, dy) }
    }

    open fun scale(sx: Float, sy: Float) {
        state { it.scale(sx, sy) }
    }

    open fun rotate(degrees: Float) {
        state { it.rotate(degrees) }
    }

    open fun rotate(degrees: Float, px: Float, py: Float) {
        state { it.rotate(degrees, px, py) }
    }

    /** Intersects the clip with the rectangle; true when the rectangle is not empty. */
    open fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean = state {
        it.clipRect(SkRect.makeLTRB(left, top, right, bottom), ClipMode.INTERSECT, false)
        right > left && bottom > top
    }

    private fun drawImage(bitmap: Bitmap, src: SkRect, dst: SkRect, paint: Paint?) {
        if (bitmap.isRecycled()) throw RuntimeException("Canvas: trying to use a recycled bitmap $bitmap")
        val sampling = if (paint?.isFilterBitmap() == true) LINEAR else NEAREST
        NativeBitmaps.locked(bitmap.pixels, target.pixels) {
            if (bitmap.pixels.closed) throw RuntimeException("Canvas: trying to use a recycled bitmap $bitmap")
            target.change("drawBitmap") {
                val image = bitmap.pixels.snapshot()
                val skPaint = paint?.toSkia()
                try {
                    skia.drawImageRect(image, src, dst, sampling, skPaint, false)
                } finally {
                    skPaint?.close()
                }
            }
        }
    }

    private inline fun draw(member: String, paint: Paint?, crossinline block: (SkCanvas, SkPaint?) -> Unit) {
        val skPaint = paint?.toSkia()
        try {
            target.change(member) { block(skia, skPaint) }
        } finally {
            skPaint?.close()
        }
    }

    private inline fun <T> state(block: (SkCanvas) -> T): T = target.use("Canvas") { block(skia) }

    private companion object {
        const val CLASS = "android.graphics.Canvas"
        val NEAREST: SamplingMode = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
        val LINEAR: SamplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
    }
}
