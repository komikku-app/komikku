package app.komikku.shared.coil

import app.komikku.shared.reader.image.SkiaPageDecoder
import app.komikku.shared.reader.image.UnsupportedPageFormatException
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import tachiyomi.core.common.util.system.ImageUtil

/**
 * KMK: desktop Coil decoder for the formats Android decodes through `TachiyomiImageDecoder` (AVIF, JPEG XL, HEIF) and
 * the desktop's Skia cannot ([SkiaPageDecoder.UNSUPPORTED]; see PROGRESS "P5"). Detected from the magic bytes, such
 * an image fails with [UnsupportedPageFormatException] (format name in the error result) instead of Skia's generic
 * decode error, so covers can show the format (`CoverImage`) like the reader does.
 */
class UnsupportedImageFormatDecoder(private val type: ImageUtil.ImageType) : Decoder {

    override suspend fun decode(): DecodeResult = throw UnsupportedPageFormatException(type.name)

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val type = ImageUtil.findImageType(result.source.source().peek().inputStream()) ?: return null
            return if (type in SkiaPageDecoder.UNSUPPORTED) UnsupportedImageFormatDecoder(type) else null
        }
    }
}
