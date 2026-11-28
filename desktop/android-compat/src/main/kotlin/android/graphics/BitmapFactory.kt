package android.graphics

import app.komikku.desktop.compat.NativeBitmaps
import app.komikku.desktop.compat.unsupported
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.io.InputStream

/**
 * android-compat: `android.graphics.BitmapFactory` with Skia's codecs (JPEG, PNG, WebP, GIF's first frame, BMP) to
 * [Bitmap.Config.ARGB_8888], premultiplied, without applying EXIF orientation (as on Android). A bitmap is immutable
 * unless [Options.inMutable]; data that does not decode gives `null` and a cut-off image the part that decoded, like
 * Android.
 */
object BitmapFactory {

    class Options {
        @JvmField
        var inMutable = false

        @JvmField
        var inPreferredConfig: Bitmap.Config? = Bitmap.Config.ARGB_8888
    }

    @JvmStatic
    fun decodeStream(input: InputStream?): Bitmap? = decodeStream(input, null, null)

    @JvmStatic
    fun decodeStream(input: InputStream?, outPadding: Rect?, opts: Options?): Bitmap? {
        val bytes = input?.readBytes() ?: return null
        return decodeByteArray(bytes, 0, bytes.size, opts)
    }

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = decodeByteArray(data, offset, length, null)

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? {
        if (offset < 0 || length < 0 || offset + length > data.size) throw ArrayIndexOutOfBoundsException()
        val config = opts?.inPreferredConfig
        if (config != null && config != Bitmap.Config.ARGB_8888) {
            unsupported("android.graphics.BitmapFactory", "decode(inPreferredConfig = $config)")
        }
        val codec = try {
            Data.makeFromBytes(data, offset, length).use { Codec.makeFromData(it) }
        } catch (_: IllegalArgumentException) {
            return null
        }
        return codec.use {
            val skia = NativeBitmaps.allocate(it.width, it.height)
            try {
                it.readPixels(skia, 0)
            } catch (e: IllegalArgumentException) {
                // A cut-off image keeps the rows that decoded (the rest transparent), as Android's kIncompleteInput
                if (e.message?.startsWith(INCOMPLETE_INPUT) != true) {
                    skia.close()
                    return null
                }
            } catch (_: Exception) {
                skia.close()
                return null
            }
            Bitmap.wrap(skia, mutable = opts?.inMutable == true)
        }
    }

    /** How Skia's codec reports a cut-off image whose decoded part it kept. */
    private const val INCOMPLETE_INPUT = "Incomplete input"
}
