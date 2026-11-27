package eu.kanade.tachiyomi.data.coil

import android.os.Build
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.request.Options
import tachiyomi.core.common.util.system.AnimatedAvif

/**
 * Decodes animated AVIF (AVIF image sequences) with the platform `ImageDecoder`.
 *
 * Coil's regular [AnimatedImageDecoder] factory only advertises GIF, WebP and HEIF, so this
 * factory claims AVIF sequences first. It must be registered before [TachiyomiImageDecoder],
 * which would otherwise decode only the first frame of every AVIF.
 */
class AnimatedAvifDecoderFactory : Decoder.Factory {

    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (options.customDecoder) return null
        if (!AnimatedAvif.isAnimated(result.source.source())) return null
        return createDecoder(result.source, options)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createDecoder(source: ImageSource, options: Options): Decoder {
        return AnimatedImageDecoder(source, options)
    }

    override fun equals(other: Any?) = other is AnimatedAvifDecoderFactory

    override fun hashCode() = javaClass.hashCode()
}
