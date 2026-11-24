package app.komikku.shared.coil

import app.komikku.shared.reader.image.SkiaPageDecoder
import app.komikku.shared.reader.image.UnsupportedPageFormatException
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.SkiaImageDecoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.system.ImageUtil

/**
 * KMK: desktop Coil decoder for the formats Android decodes through `TachiyomiImageDecoder` (AVIF, JPEG XL, HEIF,
 * [SkiaPageDecoder.UNSUPPORTED]; see PROGRESS "P5"). Such an image (detected from the magic bytes) still goes to
 * Skia first ([delegate], Coil's [SkiaImageDecoder] by default), which may decode it where its build or the OS codecs
 * allow (unverified on macOS / Windows). Only when Skia fails does the result become an [UnsupportedPageFormatException]
 * with the format name instead of Skia's generic decode error, so covers can show the format (`CoverImage`) like the
 * reader does. Other formats are not handled here and take Coil's normal decoder chain.
 */
class UnsupportedImageFormatDecoder(
    private val type: ImageUtil.ImageType,
    private val delegate: Decoder,
) : Decoder {

    override suspend fun decode(): DecodeResult? = try {
        delegate.decode()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SkiaPageDecoder.unsupportedFormat(type, e) ?: e
    }

    /** @param delegate the decoder that gets the first try, Coil's Skia decoder unless a test injects a failing one. */
    class Factory(
        private val delegate: Decoder.Factory = SkiaImageDecoder.Factory(),
    ) : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val type = ImageUtil.findImageType(result.source.source().peek().inputStream()) ?: return null
            if (type !in SkiaPageDecoder.UNSUPPORTED) return null
            val decoder = delegate.create(result, options, imageLoader) ?: return null
            return UnsupportedImageFormatDecoder(type, decoder)
        }
    }
}
