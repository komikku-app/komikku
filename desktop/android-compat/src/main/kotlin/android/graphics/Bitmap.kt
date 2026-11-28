package android.graphics

import app.komikku.desktop.compat.NativeBitmaps
import app.komikku.desktop.compat.unsupported
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.ImageInfo
import java.io.OutputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import org.jetbrains.skia.Bitmap as SkBitmap

/**
 * android-compat: `android.graphics.Bitmap` over a Skia bitmap in native memory, the members Keiyoushi's extensions call
 * (page interceptors that unscramble images; `docs/kmp-desktop/EXTENSION_COMPAT.md`). Only [Config.ARGB_8888]; pixels
 * are stored premultiplied and read back unpremultiplied, like Android's. Decoded and derived bitmaps are immutable
 * unless asked otherwise, as on Android. [recycle] frees the native memory at once, a cleaner frees a bitmap an
 * extension forgets; a bitmap may be used from any thread (one at a time per bitmap). Other members throw
 * `UnsupportedOnDesktop`.
 */
class Bitmap private constructor(skia: SkBitmap, private val mutable: Boolean) {

    enum class Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE, RGBA_1010102 }

    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    private val width = skia.width
    private val height = skia.height

    internal val pixels = NativeBitmaps.Pixels(skia)
    private val cleanable = NativeBitmaps.register(this, pixels)

    fun getWidth(): Int = width

    fun getHeight(): Int = height

    fun getConfig(): Config = Config.ARGB_8888

    fun isMutable(): Boolean = mutable

    fun isRecycled(): Boolean = pixels.closed

    fun getRowBytes(): Int = width * 4

    fun getByteCount(): Int = width * height * 4

    /** Frees the native pixels now; later pixel access throws, like Android. */
    fun recycle() = cleanable.clean()

    fun getPixel(x: Int, y: Int): Int {
        checkXY(x, y)
        return use("getPixel") { NativeBitmaps.readArgb(it.skia, x, y, 1, 1)[0] }
    }

    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        if (width == 0 || height == 0) return
        checkPixelsAccess(x, y, width, height, offset, stride, pixels)
        val argb = use("getPixels") { NativeBitmaps.readArgb(it.skia, x, y, width, height) }
        for (row in 0 until height) argb.copyInto(pixels, offset + row * stride, row * width, (row + 1) * width)
    }

    fun setPixel(x: Int, y: Int, color: Int) {
        checkMutable()
        checkXY(x, y)
        change("setPixel") { NativeBitmaps.writeArgb(it.skia, intArrayOf(color), x, y, 1, 1) }
    }

    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        checkMutable()
        if (width == 0 || height == 0) return
        checkPixelsAccess(x, y, width, height, offset, stride, pixels)
        val argb = IntArray(width * height)
        for (row in 0 until height) pixels.copyInto(argb, row * width, offset + row * stride, offset + row * stride + width)
        change("setPixels") { NativeBitmaps.writeArgb(it.skia, argb, x, y, width, height) }
    }

    fun eraseColor(color: Int) {
        check(mutable) { "cannot erase immutable bitmaps" }
        change("eraseColor") { it.skia.erase(color) }
    }

    /**
     * Copies raw pixels like Android's `ARGB_8888` memory: bytes R, G, B, A, premultiplied ([ByteBuffer] as is,
     * [IntBuffer] in native order); advances the buffer's position by the bitmap's size.
     */
    fun copyPixelsFromBuffer(src: Buffer) {
        check(!isRecycled()) { "copyPixelsFromBuffer called on recycled bitmap" }
        val size = getByteCount()
        val bytes = when (src) {
            is ByteBuffer -> {
                if (src.remaining() < size) throw RuntimeException("Buffer not large enough for pixels")
                ByteArray(size).also { src.duplicate().get(it) }.also { src.position(src.position() + size) }
            }
            is IntBuffer -> {
                if (src.remaining().toLong() * 4 < size) throw RuntimeException("Buffer not large enough for pixels")
                val ints = IntArray(size / 4).also { src.duplicate().get(it) }
                src.position(src.position() + size / 4)
                ByteBuffer.allocate(size).order(ByteOrder.nativeOrder()).apply { asIntBuffer().put(ints) }.array()
            }
            else -> unsupported(CLASS, "copyPixelsFromBuffer(${src.javaClass.name})")
        }
        change("copyPixelsFromBuffer") {
            NativeBitmaps.write(it.skia, ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL), bytes, 0, 0)
        }
    }

    fun copy(config: Config, isMutable: Boolean): Bitmap {
        checkConfig(config, "copy")
        return use("copy") { Bitmap(copyOf(it.skia, 0, 0, width, height), isMutable) }
    }

    /**
     * Encodes the pixels to [stream]. WebP is always lossy: Skiko has no lossless WebP encoder, so
     * [CompressFormat.WEBP_LOSSLESS] (and [CompressFormat.WEBP] at 100, lossless on Android 10 and later) give lossy
     * WebP at quality 100, within a few levels of the pixels. JPEG drops alpha (premultiplied colours, i.e. over
     * black), like Android.
     */
    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean {
        check(!isRecycled()) { "Can't compress a recycled bitmap" }
        require(quality in 0..100) { "quality must be 0..100" }
        val (skiaFormat, skiaQuality) = when (format) {
            CompressFormat.JPEG -> EncodedImageFormat.JPEG to quality
            CompressFormat.PNG -> EncodedImageFormat.PNG to 100
            CompressFormat.WEBP -> EncodedImageFormat.WEBP to quality
            CompressFormat.WEBP_LOSSY -> EncodedImageFormat.WEBP to quality
            CompressFormat.WEBP_LOSSLESS -> EncodedImageFormat.WEBP to 100
        }
        val encoded = use("compress") { pixels -> pixels.withImage { it.encodeToData(skiaFormat, skiaQuality)?.use { data -> data.bytes } } }
            ?: return false
        stream.write(encoded)
        return true
    }

    fun copyPixelsToBuffer(dst: Buffer): Unit = unsupported(CLASS, "copyPixelsToBuffer")

    fun setHasAlpha(hasAlpha: Boolean): Unit = unsupported(CLASS, "setHasAlpha")

    fun setPremultiplied(premultiplied: Boolean): Unit = unsupported(CLASS, "setPremultiplied")

    fun extractAlpha(): Bitmap = unsupported(CLASS, "extractAlpha")

    /** Runs [block] on the live pixels (a recycled bitmap throws, like Android). */
    internal inline fun <T> use(member: String, block: (NativeBitmaps.Pixels) -> T): T = synchronized(pixels) {
        check(!pixels.closed) { "Can't call $member() on a recycled bitmap" }
        block(pixels)
    }

    /** [use] for a change of the pixels. */
    internal inline fun change(member: String, block: (NativeBitmaps.Pixels) -> Unit) = use(member, block)

    private fun checkMutable() = check(mutable) { "Bitmap is immutable" }

    private fun checkXY(x: Int, y: Int) {
        require(x >= 0) { "x must be >= 0" }
        require(y >= 0) { "y must be >= 0" }
        require(x < width) { "x must be < bitmap.width()" }
        require(y < height) { "y must be < bitmap.height()" }
    }

    private fun checkPixelsAccess(x: Int, y: Int, width: Int, height: Int, offset: Int, stride: Int, pixels: IntArray) {
        checkXY(x, y)
        require(width >= 0) { "width must be >= 0" }
        require(height >= 0) { "height must be >= 0" }
        require(x + width <= this.width) { "x + width must be <= bitmap.width()" }
        require(y + height <= this.height) { "y + height must be <= bitmap.height()" }
        require(kotlin.math.abs(stride) >= width) { "abs(stride) must be >= width" }
        val lastScanline = offset + (height - 1) * stride
        if (offset < 0 || offset + width > pixels.size || lastScanline < 0 || lastScanline + width > pixels.size) {
            throw ArrayIndexOutOfBoundsException()
        }
    }

    override fun toString(): String = "android.graphics.Bitmap(${width}x$height${if (isRecycled()) ", recycled" else ""})"

    companion object {
        private const val CLASS = "android.graphics.Bitmap"

        /** A mutable, transparent bitmap. */
        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            require(width > 0 && height > 0) { "width and height must be > 0" }
            checkConfig(config, "createBitmap")
            return Bitmap(NativeBitmaps.allocate(width, height), mutable = true)
        }

        /** An immutable copy of a rectangle of [source] ([source] itself when that is all of an immutable one). */
        @JvmStatic
        fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
            require(x >= 0) { "x must be >= 0" }
            require(y >= 0) { "y must be >= 0" }
            require(width > 0 && height > 0) { "width and height must be > 0" }
            require(x + width <= source.width) { "x + width must be <= bitmap.width()" }
            require(y + height <= source.height) { "y + height must be <= bitmap.height()" }
            if (!source.mutable && x == 0 && y == 0 && width == source.width && height == source.height) return source
            return source.use("createBitmap") { Bitmap(copyOf(it.skia, x, y, width, height), mutable = false) }
        }

        /** An immutable bitmap of unpremultiplied `0xAARRGGBB` [colors], row after row. */
        @JvmStatic
        fun createBitmap(colors: IntArray, width: Int, height: Int, config: Config): Bitmap {
            require(width > 0 && height > 0) { "width and height must be > 0" }
            if (colors.size < width * height) throw ArrayIndexOutOfBoundsException()
            checkConfig(config, "createBitmap")
            val skia = NativeBitmaps.allocate(width, height)
            NativeBitmaps.writeArgb(skia, colors.copyOf(width * height), 0, 0, width, height)
            return Bitmap(skia, mutable = false)
        }

        @JvmStatic
        fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap =
            unsupported(CLASS, "createScaledBitmap")

        /** A bitmap over pixels just decoded into [skia] (BitmapFactory). */
        internal fun wrap(skia: SkBitmap, mutable: Boolean) = Bitmap(skia, mutable)

        private fun checkConfig(config: Config, member: String) {
            if (config != Config.ARGB_8888) unsupported(CLASS, "$member(Config.$config)")
        }

        private fun copyOf(skia: SkBitmap, x: Int, y: Int, width: Int, height: Int): SkBitmap {
            val info = ImageInfo.makeN32Premul(width, height)
            val bytes = checkNotNull(skia.readPixels(info, info.minRowBytes, x, y)) { "Could not read the pixels" }
            return NativeBitmaps.allocate(width, height).also { NativeBitmaps.write(it, info, bytes, 0, 0) }
        }
    }
}
