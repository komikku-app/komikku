package tachiyomi.core.common.util.system

import okio.BufferedSource
import java.io.File
import java.io.InputStream

/**
 * KMK: desktop subset of the Android `ImageUtil` (src/androidMain), with the same signatures for the
 * members shared modules call: image type detection from file names and magic bytes. Bitmap
 * transformations (split, merge, rotate, background detection) belong to the desktop reader
 * (Phase 7) and are not provided here.
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
                val brands = (8 until header.size - 3 step 4)
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

    private const val HEADER_SIZE = 32
    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
    private val HEIF_SEQUENCE_BRANDS = setOf("hevc", "hevx", "msf1")
}
