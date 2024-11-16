package eu.kanade.tachiyomi.data.coil

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.bitmapConfig
import com.hippo.unifile.UniFile
import mihon.core.archive.CbzCrypto
import mihon.core.archive.CbzCrypto.getCoverStream
import mihon.core.archive.archiveReader
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.decoder.ImageDecoder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.BufferedInputStream

/**
 * A [Decoder] that uses built-in [ImageDecoder] to decode images that is not supported by the system.
 */
class TachiyomiImageDecoder(private val resources: ImageSource, private val options: Options) : Decoder {
    private val context = Injekt.get<Application>()

    override suspend fun decode(): DecodeResult {
        // SY -->
        var coverStream: BufferedInputStream? = null
        if (resources.sourceOrNull()?.peek()?.use { CbzCrypto.detectCoverImageArchive(it.inputStream()) } == true) {
            if (resources.source().peek().use { ImageUtil.findImageType(it.inputStream()) == null }) {
                coverStream = UniFile.fromFile(resources.file().toFile())
                    ?.archiveReader(context = context)
                    ?.getCoverStream()
            }
        }
        val decoder = resources.sourceOrNull()?.use {
            coverStream.use { coverStream ->
                ImageDecoder.newInstance(coverStream ?: it.inputStream(), options.cropBorders, displayProfile)
            }
        }
        // SY <--

        check(decoder != null && decoder.width > 0 && decoder.height > 0) { "Failed to initialize decoder" }

        val srcWidth = decoder.width
        val srcHeight = decoder.height

        val dstWidth = options.size.widthPx(options.scale) { srcWidth }
        val dstHeight = options.size.heightPx(options.scale) { srcHeight }

        val sampleSize = DecodeUtils.calculateInSampleSize(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
        )

        var bitmap = decoder.decode(sampleSize = sampleSize)
        decoder.recycle()

        check(bitmap != null) { "Failed to decode image" }

        if (options.bitmapConfig == Bitmap.Config.HARDWARE && ImageUtil.canUseHardwareBitmap(bitmap)) {
            val hwBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false)
            if (hwBitmap != null) {
                bitmap.recycle()
                bitmap = hwBitmap
            }
        }

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = sampleSize > 1,
        )
    }

    class Factory : Decoder.Factory {

        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (options.customDecoder || isApplicable(result.source.source())) {
                TachiyomiImageDecoder(result.source, options)
            } else {
                null
            }
        }

        private fun isApplicable(source: BufferedSource): Boolean {
            val type = source.peek().inputStream().buffered().use { stream ->
                ImageUtil.findImageType(stream)
            }
            // SY -->
            source.peek().inputStream().use { stream ->
                if (CbzCrypto.detectCoverImageArchive(stream)) return true
            }
            // SY <--
            return when (type) {
                ImageUtil.ImageType.AVIF, ImageUtil.ImageType.JXL, ImageUtil.ImageType.HEIF -> true
                else -> false
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }

    companion object {
        var displayProfile: ByteArray? = null
    }
}
