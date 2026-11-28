package android.graphics

import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.abs

/** Generated test images (no image files in the repository) and pixel comparisons for the graphics tests. */
object TestImages {

    /**
     * An opaque pattern where every pixel tells its place: red follows x, green follows y, blue a checkerboard of
     * 7 x 5 cells, so a pixel or block in the wrong place shows as a large difference.
     */
    fun pattern(width: Int, height: Int): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val r = x * 255 / (width - 1).coerceAtLeast(1)
        val g = y * 255 / (height - 1).coerceAtLeast(1)
        val b = if ((x / 7 + y / 5) % 2 == 0) 30 else 220
        argb(255, r, g, b)
    }

    /**
     * An opaque gradient where red follows x and green follows y: still tells every place apart, and smooth enough for
     * JPEG to keep it within a level or two (the checkerboard of [pattern] costs JPEG's chroma subsampling much more).
     */
    fun gradient(width: Int, height: Int): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        argb(255, x * 255 / (width - 1), y * 255 / (height - 1), (x + y) * 255 / (width + height - 2))
    }

    /** A smooth opaque gradient (JPEG keeps it within a few levels). */
    fun smooth(width: Int, height: Int): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        argb(255, 40 + x * 160 / width, 60 + y * 140 / height, 200 - (x + y) * 100 / (width + height))
    }

    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    fun toImage(argb: IntArray, width: Int, height: Int, type: Int = BufferedImage.TYPE_INT_ARGB): BufferedImage =
        BufferedImage(width, height, type).apply { setRGB(0, 0, width, height, argb, 0, width) }

    fun pixelsOf(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

    fun png(argb: IntArray, width: Int, height: Int): ByteArray = encode(toImage(argb, width, height), "png")

    fun jpeg(argb: IntArray, width: Int, height: Int, quality: Float = 0.95f): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            writer.write(null, IIOImage(toImage(argb, width, height, BufferedImage.TYPE_INT_RGB), null, null), param)
        }
        writer.dispose()
        return out.toByteArray()
    }

    /** A GIF with one frame per image (ImageIO's palette); first frame first. */
    fun gif(vararg frames: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            frames.forEach { writer.writeToSequence(IIOImage(it, null, null), null) }
            writer.endWriteSequence()
        }
        writer.dispose()
        return out.toByteArray()
    }

    /** A WebP of opaque [argb] at quality 100, encoded by Skia directly (not through the compat layer; always lossy). */
    fun webp(argb: IntArray, width: Int, height: Int): ByteArray {
        val bytes = ByteArray(argb.size * 4)
        argb.forEachIndexed { i, c ->
            bytes[i * 4] = c.toByte()
            bytes[i * 4 + 1] = (c ushr 8).toByte()
            bytes[i * 4 + 2] = (c ushr 16).toByte()
            bytes[i * 4 + 3] = (c ushr 24).toByte()
        }
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        return Image.makeRaster(info, bytes, width * 4).use { image ->
            image.encodeToData(EncodedImageFormat.WEBP, 100)!!.use { it.bytes }
        }
    }

    fun readImageIo(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

    private fun encode(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().also { check(ImageIO.write(image, format, it)) }.toByteArray()

    fun Bitmap.argb(): IntArray = IntArray(getWidth() * getHeight()).also {
        getPixels(it, 0, getWidth(), 0, 0, getWidth(), getHeight())
    }

    /** The largest difference of one channel between [actual] and [expected]. */
    fun maxChannelDifference(actual: IntArray, expected: IntArray): Int =
        actual.indices.maxOf { i -> channels(actual[i]).zip(channels(expected[i])).maxOf { (a, b) -> abs(a - b) } }

    /** The mean difference per channel between [actual] and [expected]. */
    fun meanChannelDifference(actual: IntArray, expected: IntArray): Double =
        actual.indices.sumOf { i -> channels(actual[i]).zip(channels(expected[i])).sumOf { (a, b) -> abs(a - b) } } /
            (actual.size * 4.0)

    fun shouldBeWithin(actual: IntArray, expected: IntArray, tolerance: Int, what: String) {
        withClue("$what: largest channel difference") { maxChannelDifference(actual, expected) shouldBeLessThanOrEqual tolerance }
    }

    private fun channels(c: Int) = listOf(c ushr 24, (c shr 16) and 0xff, (c shr 8) and 0xff, c and 0xff)
}
