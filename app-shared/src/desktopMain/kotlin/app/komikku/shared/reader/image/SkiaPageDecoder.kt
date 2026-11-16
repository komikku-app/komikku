package app.komikku.shared.reader.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import tachiyomi.core.common.util.system.ImageUtil
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * KMK: desktop [PageImageDecoder] on Skia (Skiko). Decodes JPEG, PNG, WebP, GIF and BMP / ICO; animated
 * GIF / WebP show their **first frame only** ([DecodedPage.animated] is set). AVIF, JPEG XL and HEIF
 * are not decoded by Skia on desktop and fail with [UnsupportedPageFormatException] (detected from
 * the magic bytes with the desktop `ImageUtil`).
 *
 * The encoded image is decoded once at full size, then drawn into strips of at most
 * [DecodeRequest.maxTileHeight] rows at the requested width (mipmapped linear filtering), so the
 * retained memory is the downsampled strips; the full-size decode is released immediately. Work runs
 * on [dispatcher] (a limited IO view by default, so only a few full-size decodes exist at a time) and
 * checks for cancellation between steps.
 */
class SkiaPageDecoder(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2),
) : PageImageDecoder<ImageBitmap> {

    override suspend fun readSize(open: () -> InputStream): PixelSize = withContext(dispatcher) {
        val bytes = open().use { it.readBytes() }
        checkSupported(bytes)
        Codec.makeFromData(Data.makeFromBytes(bytes)).use { PixelSize(it.width, it.height) }
    }

    override suspend fun decode(open: () -> InputStream, request: DecodeRequest): DecodedPage<ImageBitmap> =
        withContext(dispatcher) {
            val bytes = open().use { it.readBytes() }
            coroutineContext.ensureActive()
            checkSupported(bytes)
            val frames = try {
                Codec.makeFromData(Data.makeFromBytes(bytes)).use { it.frameCount }
            } catch (_: IllegalArgumentException) {
                1
            }
            val image = try {
                Image.makeFromEncoded(bytes)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("Could not decode the image", e)
            }
            image.use { src ->
                coroutineContext.ensureActive()
                val source = PixelSize(src.width, src.height)
                val target = PageScaling.decodeSize(
                    image = source,
                    displayWidth = request.maxWidth.toFloat(),
                    maxPixels = request.maxPixels,
                )
                val tiles = PageScaling.tiles(target.height, request.maxTileHeight).map { rows ->
                    coroutineContext.ensureActive()
                    val (srcTop, srcBottom) = PageScaling.sourceRows(rows, target.height, source.height)
                    Surface.makeRasterN32Premul(target.width, rows.last + 1 - rows.first).use { surface ->
                        surface.canvas.drawImageRect(
                            src,
                            Rect.makeLTRB(0f, srcTop, source.width.toFloat(), srcBottom),
                            Rect.makeWH(target.width.toFloat(), (rows.last + 1 - rows.first).toFloat()),
                            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                            null,
                            true,
                        )
                        surface.makeImageSnapshot().toComposeImageBitmap()
                    }
                }
                DecodedPage(source = source, decoded = target, tiles = tiles, animated = frames > 1)
            }
        }

    private fun checkSupported(bytes: ByteArray) {
        val type = ImageUtil.findImageType(bytes.inputStream())
        if (type in UNSUPPORTED) throw UnsupportedPageFormatException(type!!.name)
    }

    companion object {
        /** Formats Skia on desktop cannot decode. */
        val UNSUPPORTED = setOf(ImageUtil.ImageType.AVIF, ImageUtil.ImageType.JXL, ImageUtil.ImageType.HEIF)
    }
}

/** KMK: in-memory LRU of decoded pages, bounded by decoded bytes (default 256 MB). */
class DecodedPageCache(maxBytes: Long = 256L * 1024 * 1024) {
    data class Key(val pageKey: String, val maxWidth: Int)

    private val cache = ByteSizedLruCache<Key, DecodedPage<ImageBitmap>>(maxBytes, sizeOf = { it.byteCount })

    operator fun get(key: Key): DecodedPage<ImageBitmap>? = cache[key]

    fun put(key: Key, page: DecodedPage<ImageBitmap>) = cache.put(key, page)

    fun clear() = cache.clear()

    val currentBytes: Long get() = cache.currentBytes
}
