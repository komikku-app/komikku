package android.graphics

import android.graphics.TestImages.argb
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * `BitmapFactory` decodes and `Bitmap.compress` encodes, checked against images written and read by `javax.imageio`
 * (an independent codec) or against the generated pixels. Tolerances per channel, on a smooth image: PNG and GIF exact;
 * JPEG at most 8 levels and under 1.5 on average (two different JPEG codecs); WebP at quality 100 at most 4 levels and
 * under 1 on average (Skiko encodes WebP lossy only; measured 4 and 0.63), at 80 under 3 on average (measured 1.48).
 */
class BitmapCodecTest {

    private val width = 61
    private val height = 43

    @Test
    fun `decodes a PNG with and without alpha exactly, immutable unless inMutable`() {
        val opaque = TestImages.pattern(width, height)
        val png = TestImages.png(opaque, width, height)
        val decoded = BitmapFactory.decodeByteArray(png, 0, png.size)!!
        decoded.getConfig() shouldBe Bitmap.Config.ARGB_8888
        decoded.isMutable().shouldBeFalse()
        decoded.argb() shouldBe opaque
        decoded.recycle()

        val translucent = IntArray(width * height) { i -> argb(if (i % 3 == 0) 255 else 0, 255, i % 256, 0) }
        val translucentPng = TestImages.png(translucent, width, height)
        val options = BitmapFactory.Options().apply { inMutable = true }
        val mutable = BitmapFactory.decodeStream(ByteArrayInputStream(translucentPng), null, options)!!
        mutable.isMutable().shouldBeTrue()
        mutable.argb() shouldBe IntArray(translucent.size) { i -> if (translucent[i] ushr 24 == 255) translucent[i] else 0 }
        mutable.recycle()
    }

    @Test
    fun `decodes a JPEG within a few levels of ImageIO's decode`() {
        val jpeg = TestImages.jpeg(TestImages.smooth(width, height), width, height)
        val expected = TestImages.pixelsOf(TestImages.readImageIo(jpeg))
        val decoded = BitmapFactory.decodeStream(ByteArrayInputStream(jpeg))!!
        val actual = decoded.argb()
        withClue("max") { TestImages.maxChannelDifference(actual, expected) shouldBeLessThan 9 }
        withClue("mean") { TestImages.meanChannelDifference(actual, expected) shouldBeLessThan 1.5 }
        decoded.recycle()
    }

    @Test
    fun `decodes the first frame of an animated GIF exactly`() {
        val first = TestImages.toImage(TestImages.pattern(width, height), width, height, BufferedImage.TYPE_INT_RGB)
        val second = TestImages.toImage(IntArray(width * height) { argb(255, 0, 0, 255) }, width, height, BufferedImage.TYPE_INT_RGB)
        val gif = TestImages.gif(first, second)
        val expected = TestImages.pixelsOf(TestImages.readImageIo(gif))
        val decoded = BitmapFactory.decodeByteArray(gif, 0, gif.size)!!
        decoded.argb() shouldBe expected
        decoded.recycle()
    }

    @Test
    fun `decodes a WebP within a level or two of the pixels encoded`() {
        val colors = TestImages.smooth(width, height)
        val webp = TestImages.webp(colors, width, height)
        val decoded = BitmapFactory.decodeByteArray(webp, 0, webp.size)!!
        val actual = decoded.argb()
        withClue("max") { TestImages.maxChannelDifference(actual, colors) shouldBeLessThan 5 }
        withClue("mean") { TestImages.meanChannelDifference(actual, colors) shouldBeLessThan 1.0 }
        decoded.recycle()
    }

    @Test
    fun `a JPEG's EXIF orientation is not applied, like BitmapFactory`() {
        val colors = TestImages.smooth(40, 20)
        val plain = TestImages.jpeg(colors, 40, 20)
        // An APP1 segment right after SOI: Exif, big-endian TIFF, one IFD entry Orientation (0x0112) = 6 (rotate 90)
        val exif = byteArrayOf(0xff.toByte(), 0xe1.toByte(), 0, 34) + "Exif".toByteArray() + byteArrayOf(0, 0) +
            byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8) +
            byteArrayOf(0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0)
        val rotated = plain.copyOfRange(0, 2) + exif + plain.copyOfRange(2, plain.size)
        org.jetbrains.skia.Data.makeFromBytes(rotated).use { data ->
            org.jetbrains.skia.Codec.makeFromData(data).use { it.encodedOrigin } shouldBe org.jetbrains.skia.EncodedOrigin.RIGHT_TOP
        }

        val decoded = BitmapFactory.decodeByteArray(rotated, 0, rotated.size)!!

        decoded.getWidth() shouldBe 40
        decoded.getHeight() shouldBe 20
        val stored = BitmapFactory.decodeByteArray(plain, 0, plain.size)!!
        decoded.argb() shouldBe stored.argb()
        decoded.recycle()
        stored.recycle()
    }

    @Test
    fun `a cut-off JPEG decodes the part that arrived, like Android`() {
        val size = 256
        val jpeg = TestImages.jpeg(TestImages.smooth(size, size), size, size)
        val full = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)!!.let { it.argb().also { _ -> it.recycle() } }

        val partial = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size * 6 / 10)!!

        partial.getWidth() shouldBe size
        partial.getHeight() shouldBe size
        val topRows = size * 32
        val bottomRow = size * (size - 1)
        partial.argb().copyOfRange(0, topRows) shouldBe full.copyOfRange(0, topRows)
        partial.getPixel(0, size - 1) shouldBe 0
        full[bottomRow] shouldNotBe 0
        partial.recycle()
    }

    @Test
    fun `data that is no image gives null`() {
        BitmapFactory.decodeByteArray(ByteArray(64) { it.toByte() }, 0, 64).shouldBeNull()
        BitmapFactory.decodeStream(ByteArrayInputStream(ByteArray(0))).shouldBeNull()
    }

    @Test
    fun `compress to PNG is lossless, also for alpha`() {
        val pattern = TestImages.pattern(width, height)
        val colors = IntArray(width * height) { i -> if (i % 5 == 0) 0 else pattern[i] }
        val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        val png = bitmap.compressed(Bitmap.CompressFormat.PNG, 0)
        TestImages.pixelsOf(TestImages.readImageIo(png)) shouldBe colors
        bitmap.recycle()
    }

    @Test
    fun `compress to JPEG follows the quality`() {
        val colors = TestImages.smooth(width, height)
        val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        val high = bitmap.compressed(Bitmap.CompressFormat.JPEG, 90)
        val low = bitmap.compressed(Bitmap.CompressFormat.JPEG, 10)
        (low.size < high.size).shouldBeTrue()
        val decoded = TestImages.pixelsOf(TestImages.readImageIo(high))
        withClue("max") { TestImages.maxChannelDifference(decoded, colors) shouldBeLessThan 9 }
        withClue("mean") { TestImages.meanChannelDifference(decoded, colors) shouldBeLessThan 1.5 }
        bitmap.recycle()
    }

    @Test
    fun `compress to WebP is lossy, at quality 100 when lossless is asked`() {
        val colors = TestImages.smooth(width, height)
        val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        for ((format, quality) in listOf(Bitmap.CompressFormat.WEBP to 100, Bitmap.CompressFormat.WEBP_LOSSLESS to 0)) {
            val webp = bitmap.compressed(format, quality)
            BitmapFactory.decodeByteArray(webp, 0, webp.size)!!.run {
                val actual = argb()
                withClue("$format max") { TestImages.maxChannelDifference(actual, colors) shouldBeLessThan 5 }
                withClue("$format mean") { TestImages.meanChannelDifference(actual, colors) shouldBeLessThan 1.0 }
                recycle()
            }
        }
        val lossy = bitmap.compressed(Bitmap.CompressFormat.WEBP_LOSSY, 80)
        BitmapFactory.decodeByteArray(lossy, 0, lossy.size)!!.run {
            TestImages.meanChannelDifference(argb(), colors) shouldBeLessThan 3.0
            recycle()
        }
        (lossy.size < bitmap.compressed(Bitmap.CompressFormat.WEBP, 100).size).shouldBeTrue()
        bitmap.recycle()
    }

    private fun Bitmap.compressed(format: Bitmap.CompressFormat, quality: Int): ByteArray =
        ByteArrayOutputStream().also { compress(format, quality, it).shouldBeTrue() }.toByteArray()
}
