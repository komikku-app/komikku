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
}

/**
 * @param maxWidth decode width limit in pixels (the image is never upscaled).
 * @param maxTileHeight maximum strip height, see [PageScaling.tiles].
 */
data class DecodeRequest(
    val maxWidth: Int,
    val maxTileHeight: Int = PageScaling.DEFAULT_MAX_TILE_HEIGHT,
    val maxPixels: Long = 40_000_000L,
)

/**
 * @param source the encoded image's size; [decoded] the size of the strips stacked together.
 * @param animated `true` when the image has several frames; only the first one is decoded.
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

/** The page uses an image format this platform cannot decode (e.g. AVIF / JPEG XL / HEIF on desktop). */
class UnsupportedPageFormatException(val format: String) : Exception("Unsupported image format: $format")
