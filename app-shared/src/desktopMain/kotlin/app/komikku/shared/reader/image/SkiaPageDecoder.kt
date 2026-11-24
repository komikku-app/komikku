package app.komikku.shared.reader.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.AnimationDisposalMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import tachiyomi.core.common.util.system.ImageUtil
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * KMK: desktop [PageImageDecoder] on Skia (Skiko). Decodes JPEG, PNG, WebP, GIF and BMP / ICO; an animated
 * GIF / WebP (P11) is decoded as its first frame at once ([DecodedPage.animated] set), its other frames only on
 * request ([decodeFrames], for a page that is shown) on a slot of their own. AVIF, JPEG XL and HEIF
 * (detected from the magic bytes with the desktop `ImageUtil`) are left to Skia too, which may decode
 * them where its build or the OS codecs allow (unverified on macOS / Windows, PROGRESS "P5"); when it
 * cannot, they fail with [UnsupportedPageFormatException] naming the format instead of a generic error.
 *
 * The encoded image is decoded once at full size, then drawn into strips of at most
 * [DecodeRequest.maxTileHeight] rows at the requested width (mipmapped linear filtering), so the
 * retained memory is the downsampled strips; the full-size decode is released immediately. Work runs
 * on [dispatcher] (a limited IO view by default, so only a few full-size decodes exist at a time) and
 * checks for cancellation between steps.
 */
class SkiaPageDecoder(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2),
    /**
     * Where the frames of animated pages are decoded ([decodeFrames]): one slot of its own, so that an animation never
     * holds up the page decodes on [dispatcher].
     */
    private val animationDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    /** The Skia decode step, replaceable in tests to simulate a platform without a codec. */
    private val decodeImage: (ByteArray) -> Image = { Image.makeFromEncoded(it) },
    /** The Skia codec step (size and frame count), replaceable in tests like [decodeImage]. */
    private val openCodec: (ByteArray) -> Codec = { Codec.makeFromData(Data.makeFromBytes(it)) },
) : PageImageDecoder<ImageBitmap> {

    override suspend fun readSize(open: () -> InputStream): PixelSize = withContext(dispatcher) {
        val bytes = open().use { it.readBytes() }
        mapUnsupported(bytes) { openCodec(bytes).use { PixelSize(it.width, it.height) } }
    }

    override suspend fun decode(open: () -> InputStream, request: DecodeRequest): DecodedPage<ImageBitmap> =
        withContext(dispatcher) {
            val bytes = open().use { it.readBytes() }
            coroutineContext.ensureActive()
            val frames = try {
                openCodec(bytes).use { it.frameCount }
            } catch (_: IllegalArgumentException) {
                1
            }
            val image = try {
                mapUnsupported(bytes) { decodeImage(bytes) }
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

    private fun fitsAnimation(target: PixelSize, frames: Int, request: DecodeRequest): Boolean =
        target.height <= request.maxTileHeight &&
            target.width.toLong() * target.height * 4 * frames <= request.maxAnimationBytes

    /**
     * Every frame of the animated [page] (decoded by [decode] with [request]) at its decoded size, when they fit
     * [DecodeRequest.maxAnimationBytes] and one strip, else empty (the first frame stays). Frames are decoded in order
     * into one bitmap: a frame that builds on the previous one gets it as Skia's prior frame (unless that one is
     * restored afterwards, then Skia decodes the frames it needs itself). Durations of 10 ms or less become 100 ms, as
     * browsers and Android's decoders do. Cancellable between frames; any failure other than a cancellation (a frame
     * Skia cannot decode, an allocation failure) gives an empty list, so the page keeps its first frame.
     */
    override suspend fun decodeFrames(
        open: () -> InputStream,
        page: DecodedPage<ImageBitmap>,
        request: DecodeRequest,
    ): List<AnimationFrame<ImageBitmap>> = withContext(animationDispatcher) {
        try {
            val bytes = open().use { it.readBytes() }
            coroutineContext.ensureActive()
            readFrames(bytes, page.decoded, request)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Also an OutOfMemoryError: the frames are dropped, the page keeps its first frame
            emptyList()
        }
    }

    private suspend fun readFrames(bytes: ByteArray, target: PixelSize, request: DecodeRequest): List<AnimationFrame<ImageBitmap>> =
        openCodec(bytes).use { codec ->
            if (codec.frameCount <= 1 || !fitsAnimation(target, codec.frameCount, request)) return emptyList()
            val infos = codec.framesInfo
            Bitmap().use { bitmap ->
                if (!bitmap.allocPixels(ImageInfo.makeN32Premul(codec.width, codec.height))) return emptyList()
                (0 until codec.frameCount).map { i ->
                    coroutineContext.ensureActive()
                    val info = infos[i]
                    val prior = if (i > 0 && info.requiredFrame != -1 && infos[i - 1].disposalMethod != AnimationDisposalMode.RESTORE_PREVIOUS) {
                        i - 1
                    } else {
                        -1
                    }
                    if (prior == -1) bitmap.erase(0)
                    codec.readPixels(bitmap, i, prior)
                    val frame = Image.makeFromBitmap(bitmap).use { image ->
                        Surface.makeRasterN32Premul(target.width, target.height).use { surface ->
                            surface.canvas.drawImageRect(
                                image,
                                Rect.makeWH(codec.width.toFloat(), codec.height.toFloat()),
                                Rect.makeWH(target.width.toFloat(), target.height.toFloat()),
                                FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                                null,
                                true,
                            )
                            surface.makeImageSnapshot().toComposeImageBitmap()
                        }
                    }
                    AnimationFrame(frame, info.duration.takeIf { it > 10 } ?: 100)
                }
            }
        }

    /** Runs the Skia [step]; when it fails for an AVIF / JPEG XL / HEIF image, the failure names the format. */
    private inline fun <T> mapUnsupported(bytes: ByteArray, step: () -> T): T = try {
        step()
    } catch (e: IllegalArgumentException) {
        throw unsupportedFormat(ImageUtil.findImageType(bytes.inputStream()), e) ?: e
    }

    companion object {
        /**
         * Formats Android decodes through its own decoder and the desktop's Skia may not (no codec in the Linux
         * Skiko build; macOS / Windows unverified). Skia always gets the first try; this set only names its failures.
         */
        val UNSUPPORTED = setOf(ImageUtil.ImageType.AVIF, ImageUtil.ImageType.JXL, ImageUtil.ImageType.HEIF)

        /**
         * The [UnsupportedPageFormatException] for a failed decode ([cause]) of an image of the detected [type] when
         * it is one of the [UNSUPPORTED] formats, else `null` (the caller keeps its own error).
         */
        fun unsupportedFormat(type: ImageUtil.ImageType?, cause: Throwable): UnsupportedPageFormatException? =
            if (type != null && type in UNSUPPORTED) UnsupportedPageFormatException(type.name, cause) else null
    }
}

/**
 * KMK: the frames of animated pages (P11), bounded by their bytes (default 192 MB), one frame set per page: frames for
 * another width replace the page's earlier ones.
 */
class AnimationFrameCache(maxBytes: Long = 192L * 1024 * 1024) {
    private class Entry(val width: Int, val frames: List<AnimationFrame<ImageBitmap>>, val bytes: Long)

    private val cache = ByteSizedLruCache<String, Entry>(maxBytes, sizeOf = { it.bytes })

    /** The frames of the page [pageKey] decoded for the width [width], if they are kept. */
    operator fun get(pageKey: String, width: Int): List<AnimationFrame<ImageBitmap>>? =
        cache[pageKey]?.takeIf { it.width == width }?.frames

    fun put(pageKey: String, width: Int, frames: List<AnimationFrame<ImageBitmap>>, frameSize: PixelSize) =
        cache.put(pageKey, Entry(width, frames, frameSize.width.toLong() * frameSize.height * 4 * frames.size))

    val size: Int get() = cache.size

    val currentBytes: Long get() = cache.currentBytes

    fun clear() = cache.clear()
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
