package app.komikku.shared.reader.image

import java.io.InputStream

/**
 * KMK: decodes a reader page into platform bitmaps of type [T] (Skia images on desktop; Android keeps
 * its view-based decoders for now). Implementations decode off the main thread, honour coroutine
 * cancellation, downsample to [DecodeRequest] and cut tall images into strips ([PageScaling.tiles]).
 */
interface PageImageDecoder<T> {

    /** Reads only the image header. */
    suspend fun readSize(open: () -> InputStream): PixelSize

    suspend fun decode(open: () -> InputStream, request: DecodeRequest): DecodedPage<T>

    /**
     * The frames of an animated [page] (P11) as [decode] gave it for [request], for playback, decoded only when asked
     * (for a page that is shown); empty when the platform does not play animations or they do not fit.
     */
    suspend fun decodeFrames(open: () -> InputStream, page: DecodedPage<T>, request: DecodeRequest): List<AnimationFrame<T>> =
        emptyList()
}

/**
 * @param maxWidth decode width limit in pixels (the image is never upscaled).
 * @param maxTileHeight maximum strip height, see [PageScaling.tiles].
 */
data class DecodeRequest(
    val maxWidth: Int,
    val maxTileHeight: Int = PageScaling.DEFAULT_MAX_TILE_HEIGHT,
    val maxPixels: Long = 40_000_000L,
    /**
     * Memory limit for the frames of an animated page (P11, [PageImageDecoder.decodeFrames]); a larger animation (or
     * one taller than a strip) shows its first frame only.
     */
    val maxAnimationBytes: Long = 128L * 1024 * 1024,
)

/** KMK: one frame of an animated page (P11): [image] at the page's decoded size, shown for [durationMillis]. */
data class AnimationFrame<T>(val image: T, val durationMillis: Int)

/**
 * @param source the encoded image's size; [decoded] the size of the strips stacked together.
 * @param animated `true` when the image has several frames ([tiles] hold the first one; the others come from
 *   [PageImageDecoder.decodeFrames]).
 */
data class DecodedPage<T>(
    val source: PixelSize,
    val decoded: PixelSize,
    val tiles: List<T>,
    val animated: Boolean = false,
) {
    /** Approximate memory of the decoded pixels (RGBA). */
    val byteCount: Long get() = decoded.width.toLong() * decoded.height * 4
}

/**
 * The page uses an image format this platform cannot decode (e.g. AVIF / JPEG XL / HEIF when the desktop's Skia has
 * no codec for it); [cause] is the platform decoder's failure.
 */
class UnsupportedPageFormatException(
    val format: String,
    cause: Throwable? = null,
) : Exception("Unsupported image format: $format", cause)
