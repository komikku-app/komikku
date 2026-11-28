package app.komikku.desktop.compat

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.lang.ref.Cleaner
import java.util.concurrent.atomic.AtomicLong
import org.jetbrains.skia.Bitmap as SkBitmap
import org.jetbrains.skia.Canvas as SkCanvas

/**
 * The native memory behind `android.graphics.Bitmap`: one Skia bitmap (N32, premultiplied, like Android's
 * `ARGB_8888`) per [Pixels], freed by `Bitmap.recycle()` or, for a bitmap an extension forgets, by a [Cleaner] once
 * the `Bitmap` is unreachable. Nothing here uses or assumes a UI thread: page interceptors run on OkHttp's threads,
 * several at once. Each [Pixels] is its own lock; an operation on two bitmaps takes both in [Pixels.id] order.
 */
internal object NativeBitmaps {

    private val cleaner = Cleaner.create()
    private val live = AtomicLong()
    private val ids = AtomicLong()

    /** Bytes of pixel memory held by bitmaps neither recycled nor cleaned yet. */
    fun liveBytes(): Long = live.get()

    /** Allocates a transparent N32 premultiplied Skia bitmap. */
    fun allocate(width: Int, height: Int): SkBitmap = SkBitmap().apply {
        check(allocPixels(ImageInfo.makeN32Premul(width, height))) { "Could not allocate a $width x $height bitmap" }
        erase(0)
    }

    /** Registers [pixels] to be freed when [owner] becomes unreachable; the returned cleanable frees them now. */
    fun register(owner: Any, pixels: Pixels): Cleaner.Cleanable {
        live.addAndGet(pixels.bytes)
        return cleaner.register(owner, pixels)
    }

    /** Runs [block] holding the locks of [a] and [b] (one lock when they are the same). */
    inline fun <T> locked(a: Pixels, b: Pixels, block: () -> T): T {
        val first = if (a.id <= b.id) a else b
        val second = if (first === a) b else a
        return synchronized(first) { synchronized(second) { block() } }
    }

    /** Writes [bytes] laid out as [info] into [target] at ([x], [y]), converting to N32 premultiplied. */
    fun write(target: SkBitmap, info: ImageInfo, bytes: ByteArray, x: Int, y: Int) {
        val source = SkBitmap()
        try {
            check(source.installPixels(info, bytes, info.minRowBytes)) { "Could not wrap the pixels" }
            SkCanvas(target).use { it.writePixels(source, x, y) }
        } finally {
            source.close()
        }
    }

    /** `width` x `height` pixels of [skia] from ([x], [y]) as unpremultiplied `0xAARRGGBB` ints, like Android's. */
    fun readArgb(skia: SkBitmap, x: Int, y: Int, width: Int, height: Int): IntArray {
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val bytes = checkNotNull(skia.readPixels(info, width * 4, x, y)) { "Could not read the pixels" }
        return IntArray(width * height) { i ->
            val o = i * 4
            (bytes[o].toInt() and 0xff) or
                ((bytes[o + 1].toInt() and 0xff) shl 8) or
                ((bytes[o + 2].toInt() and 0xff) shl 16) or
                ((bytes[o + 3].toInt() and 0xff) shl 24)
        }
    }

    /** Writes unpremultiplied `0xAARRGGBB` [colors] (`width` x `height`) into [skia] at ([x], [y]). */
    fun writeArgb(skia: SkBitmap, colors: IntArray, x: Int, y: Int, width: Int, height: Int) {
        val bytes = ByteArray(width * height * 4)
        colors.forEachIndexed { i, c ->
            val o = i * 4
            bytes[o] = c.toByte()
            bytes[o + 1] = (c ushr 8).toByte()
            bytes[o + 2] = (c ushr 16).toByte()
            bytes[o + 3] = (c ushr 24).toByte()
        }
        write(skia, ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), bytes, x, y)
    }

    /**
     * A bitmap's native objects: the pixels and the canvases drawing into them. Every access holds this object's lock;
     * [run] (recycle or cleaner) closes them all once.
     */
    class Pixels(val skia: SkBitmap) : Runnable {
        val id = ids.incrementAndGet()
        val bytes = skia.rowBytes.toLong() * skia.height

        @Volatile
        var closed = false
            private set

        private val canvases = mutableListOf<SkCanvas>()

        /**
         * Runs [block] with the pixels as an image to draw or encode from, closed afterwards; only while this lock is
         * held. The image shares the pixels (no second buffer), except with [copy], for a bitmap drawn onto itself
         * (the draw would read pixels it is writing): that copy is counted in [liveBytes] while it exists.
         */
        fun <T> withImage(copy: Boolean = false, block: (Image) -> T): T {
            val image = if (copy) {
                live.addAndGet(bytes)
                Image.makeFromBitmap(skia)
            } else {
                checkNotNull(skia.peekPixels()) { "Could not read the pixels" }.use { Image.makeFromPixmap(it) }
            }
            try {
                return block(image)
            } finally {
                image.close()
                if (copy) live.addAndGet(-bytes)
            }
        }

        fun openCanvas(): SkCanvas = SkCanvas(skia).also { canvases += it }

        override fun run() {
            synchronized(this) {
                if (closed) return
                closed = true
                canvases.forEach { it.close() }
                canvases.clear()
                skia.close()
            }
            live.addAndGet(-bytes)
        }
    }
}
