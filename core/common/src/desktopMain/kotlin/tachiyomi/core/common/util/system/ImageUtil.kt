package tachiyomi.core.common.util.system

import com.hippo.unifile.UniFile
import logcat.LogPriority
import okio.BufferedSource
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import java.io.InputStream
import java.util.Locale
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.min

/**
 * KMK: desktop subset of the Android `ImageUtil` (src/androidMain), with the same signatures for the
 * members shared modules call: image type detection from file names and magic bytes, and (8.1)
 * [splitTallImage] for the borrowed downloader through `javax.imageio` region decoding (JPEG / PNG /
 * GIF / BMP; other formats are kept unsplit). Other bitmap transformations (merge, rotate, background
 * detection) are not provided.
 */
object ImageUtil {

    fun isImage(name: String?, openStream: (() -> InputStream)? = null): Boolean {
        if (name == null) return false
        if (File(name).extension.equals("cbi", ignoreCase = true)) return true

        val extension = name.substringAfterLast('.')
        return ImageType.entries.any { it.extension == extension } || openStream?.let { findImageType(it) } != null
    }

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        return try {
            detect(readHeader(stream) ?: return null)?.type
        } catch (_: Exception) {
            null
        }
    }

    fun getExtensionFromMimeType(mime: String?, openStream: () -> InputStream): String {
        val type = mime?.let { ImageType.entries.find { it.mime == mime } } ?: findImageType(openStream)
        return type?.extension ?: "jpg"
    }

    /** GIF and animated WebP are animated formats the desktop image stack (Skia) decodes. */
    fun isAnimatedAndSupported(source: BufferedSource): Boolean {
        return try {
            val detected = detect(readHeader(source.peek().inputStream()) ?: return false) ?: return false
            when (detected.type) {
                ImageType.GIF -> true
                ImageType.WEBP -> detected.isAnimated
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Same contract as Android's `splitTallImage`: a page more than 3 times taller than wide is cut into
     * JPEG parts named `<prefix>__001.jpg`, … of at most [optimalImageHeight] rows, and the original is
     * deleted; `true` when nothing had to be done or the split succeeded. Images `javax.imageio` cannot
     * read (WebP, AVIF, JPEG XL, HEIF) and animated images are left as they are (`true`).
     */
    fun splitTallImage(
        tmpDir: UniFile,
        imageFile: UniFile,
        filenamePrefix: String,
    ): Boolean {
        val bytes = imageFile.openInputStream().use { it.readBytes() }
        val detected = detect(bytes.copyOf(min(bytes.size, HEADER_SIZE)))
        if (detected == null || detected.isAnimated || detected.type == ImageType.GIF) return true

        val input = ImageIO.createImageInputStream(bytes.inputStream()) ?: return true
        val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return true.also { input.close() }
        val parts = mutableListOf<String>()
        return try {
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            if (width <= 0 || height / width <= 3) return true

            val partCount = (height - 1) / optimalImageHeight + 1
            val partHeight = height / partCount
            var top = 0
            var index = 0
            while (top < height) {
                val bottom = if (index == partCount - 1) height else min(top + partHeight, height)
                val param = reader.defaultReadParam.apply { sourceRegion = Rectangle(0, top, width, bottom - top) }
                val part = reader.read(0, param)
                val rgb = java.awt.image.BufferedImage(part.width, part.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
                rgb.createGraphics().apply {
                    color = java.awt.Color.WHITE
                    fillRect(0, 0, part.width, part.height)
                    drawImage(part, 0, 0, null)
                    dispose()
                }
                val name = "${filenamePrefix}__${"%03d".format(Locale.ENGLISH, index + 1)}.jpg"
                tmpDir.findFile(name)?.delete()
                val file = tmpDir.createFile(name) ?: error("Could not create $name")
                parts += name
                file.openOutputStream().use { out ->
                    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
                    ImageIO.createImageOutputStream(out).use { ios ->
                        writer.output = ios
                        val writeParam = writer.defaultWriteParam.apply {
                            compressionMode = ImageWriteParam.MODE_EXPLICIT
                            compressionQuality = 1f
                        }
                        writer.write(null, IIOImage(rgb, null, null), writeParam)
                        writer.dispose()
                    }
                }
                top = bottom
                index++
            }
            imageFile.delete()
            true
        } catch (e: Exception) {
            parts.forEach { tmpDir.findFile(it)?.delete() }
            logcat(LogPriority.ERROR, e) { "Failed to split tall image" }
            false
        } finally {
            reader.dispose()
            input.close()
        }
    }

    /** Android: twice the display's maximum height; desktop: twice the tallest screen (headless: 2 × 2160). */
    private val optimalImageHeight: Int by lazy {
        val screenHeight = runCatching {
            if (GraphicsEnvironment.isHeadless()) {
                null
            } else {
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.maxOfOrNull { it.displayMode.height }
            }
        }.getOrNull() ?: 2160
        screenHeight * 2
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),
        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
    }

    private class Detected(val type: ImageType, val isAnimated: Boolean)

    private fun readHeader(stream: InputStream): ByteArray? {
        val bytes = ByteArray(HEADER_SIZE)
        val length = if (stream.markSupported()) {
            stream.mark(bytes.size)
            stream.readNBytes(bytes, 0, bytes.size).also { stream.reset() }
        } else {
            stream.readNBytes(bytes, 0, bytes.size)
        }
        return if (length <= 0) null else bytes.copyOf(length)
    }

    // Signatures: JPEG FF D8 FF, PNG 89 'PNG', GIF 'GIF8', WebP 'RIFF'....'WEBP' (animated: VP8X chunk
    // with the animation flag), JPEG XL codestream FF 0A or container 'JXL ', and ISO-BMFF 'ftyp'
    // brands for AVIF / HEIF.
    private fun detect(header: ByteArray): Detected? {
        fun at(offset: Int, vararg expected: Int) =
            header.size >= offset + expected.size && expected.indices.all { header[offset + it].toInt() and 0xff == expected[it] }
        fun ascii(offset: Int, text: String) = at(offset, *text.map { it.code }.toIntArray())

        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> Detected(ImageType.JPEG, false)
            at(0, 0x89, 0x50, 0x4E, 0x47) -> Detected(ImageType.PNG, false)
            ascii(0, "GIF8") -> Detected(ImageType.GIF, true)
            ascii(0, "RIFF") && ascii(8, "WEBP") -> {
                val animated = ascii(12, "VP8X") && header.size > 20 && header[20].toInt() and 0x02 != 0
                Detected(ImageType.WEBP, animated)
            }
            at(0, 0xFF, 0x0A) || (at(0, 0x00, 0x00, 0x00, 0x0C) && ascii(4, "JXL ")) -> Detected(ImageType.JXL, false)
            ascii(4, "ftyp") -> {
                // The whole `ftyp` box (size at offset 0; 1 = 64-bit size after the type, 0 = to the end), up to the
                // [HEADER_SIZE] bytes read: an AVIF's `avif` brand can come late among the compatible brands
                // (major `mif1`, compatible `mif1 heic miaf MA1B avif`), and AVIF brands win over HEIF ones.
                val boxSize = ((header[0].toLong() and 0xff) shl 24) or ((header[1].toLong() and 0xff) shl 16) or
                    ((header[2].toLong() and 0xff) shl 8) or (header[3].toLong() and 0xff)
                val start = if (boxSize == 1L) 16 else 8
                val end = if (boxSize < start + 4L) header.size else min(boxSize, header.size.toLong()).toInt()
                val brands = (start until end - 3 step 4)
                    .map { String(header, it, 4, Charsets.US_ASCII) }
                    .filterIndexed { index, _ -> index != 1 } // skip minor_version
                when {
                    brands.any { it == "avif" || it == "avis" } -> Detected(ImageType.AVIF, "avis" in brands)
                    brands.any { it in HEIF_BRANDS } -> Detected(ImageType.HEIF, brands.any { it in HEIF_SEQUENCE_BRANDS })
                    else -> null
                }
            }
            else -> null
        }
    }

    // The bytes read for detection, enough for an `ftyp` box with many compatible brands (was 32)
    private const val HEADER_SIZE = 256
    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
    private val HEIF_SEQUENCE_BRANDS = setOf("hevc", "hevx", "msf1")
}
