package app.komikku.desktop.image

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.komikku.desktop.reader.TestImages
import app.komikku.desktop.ui.CoverImage
import app.komikku.shared.coil.DesktopImageLoader
import app.komikku.shared.coil.UnsupportedImageFormatDecoder
import app.komikku.shared.reader.image.DecodeRequest
import app.komikku.shared.reader.image.PixelSize
import app.komikku.shared.reader.image.SkiaPageDecoder
import app.komikku.shared.reader.image.UnsupportedPageFormatException
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.util.system.ImageUtil
import java.io.File
import java.util.Base64

/**
 * P5: AVIF / JPEG XL on desktop. Skia (Skiko) gets the first try everywhere; on Linux its build has no codec for them
 * (and no acceptable JVM decoder was found, PROGRESS "P5"), on macOS / Windows it may decode some through the OS. So the
 * platform tests accept both outcomes, and the mapping of a failure to [UnsupportedPageFormatException] is tested with
 * an injected failing decode step. Real 8×8 samples, generated with Pillow (AVIF, libavif / aom) and ffmpeg (JPEG XL,
 * libjxl), embedded as Base64.
 */
@OptIn(ExperimentalTestApi::class)
class UnsupportedImageFormatTest {

    private val avif: ByteArray = Base64.getDecoder().decode(
        "AAAAIGZ0eXBhdmlmAAAAAGF2aWZtaWYxbWlhZk1BMUIAAADrbWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAcGljdAAAAAAAAAAAAAAAAAAAAAAOcGl0" +
            "bQAAAAAAAQAAAB5pbG9jAAAAAEQAAAEAAQAAAAEAAAETAAAAJwAAAChpaW5mAAAAAAABAAAAGmluZmUCAAAAAAEAAGF2MDFDb2xvcgAAAABqaXBy" +
            "cAAAAEtpcGNvAAAAFGlzcGUAAAAAAAAACAAAAAgAAAAQcGl4aQAAAAADCAgIAAAADGF2MUOBAAwAAAAAE2NvbHJuY2x4AAEADQAGgAAAABdpcG1h" +
            "AAAAAAAAAAEAAQQBAoMEAAAAL21kYXQSAAoIGAi/aICGg0IyGRlHh4Yhh5555oAAAJBAyRxhSgnBjgTNEYw=",
    )
    private val jxl: ByteArray = Base64.getDecoder().decode(
        "/wpBBgASiAIA1ABd5RIAABUqo4wbvJzr+fJDh8W0jesMbbVtYUmiyDg2SSdwSBJrwMYFMwMDYJi5MKSBEleSAA==",
    )
    private val samples = mapOf("AVIF" to avif, "JXL" to jxl)

    @Test
    fun `the samples are detected by their magic bytes`() {
        ImageUtil.findImageType(avif.inputStream()) shouldBe ImageUtil.ImageType.AVIF
        ImageUtil.findImageType(jxl.inputStream()) shouldBe ImageUtil.ImageType.JXL
    }

    /** Whatever this platform's Skia can do: an 8×8 page, or a failure naming the format (never a generic error). */
    @Test
    fun `reader pages in these formats decode or fail as unsupported, with the format`() = runBlocking {
        val decoder = SkiaPageDecoder()
        samples.forEach { (format, bytes) ->
            val page = runCatching { decoder.decode({ bytes.inputStream() }, DecodeRequest(100)) }
            page.exceptionOrNull()?.let { it.shouldBeInstanceOf<UnsupportedPageFormatException>().format shouldBe format }
            page.getOrNull()?.source?.let { it shouldBe PixelSize(8, 8) }
            println("Skia on ${System.getProperty("os.name")}: $format ${if (page.isSuccess) "decoded" else "unsupported"}")
            val size = runCatching { decoder.readSize { bytes.inputStream() } }
            size.exceptionOrNull()?.let { it.shouldBeInstanceOf<UnsupportedPageFormatException>().format shouldBe format }
            size.getOrNull()?.let { it shouldBe PixelSize(8, 8) }
        }
    }

    @Test
    fun `a failing Skia decode of these formats becomes unsupported, of others stays a decode error`() = runBlocking {
        val noCodec = IllegalArgumentException("no codec")
        val decoder = SkiaPageDecoder(decodeImage = { throw noCodec }, openCodec = { throw noCodec })
        samples.forEach { (format, bytes) ->
            val e = shouldThrow<UnsupportedPageFormatException> { decoder.decode({ bytes.inputStream() }, DecodeRequest(100)) }
            e.format shouldBe format
            e.hasCause("no codec") shouldBe true
            shouldThrow<UnsupportedPageFormatException> { decoder.readSize { bytes.inputStream() } }.format shouldBe format
        }
        val png = TestImages.encode(8, 8)
        shouldThrow<IllegalStateException> { decoder.decode({ png.inputStream() }, DecodeRequest(100)) }
            .hasCause("no codec") shouldBe true
        shouldThrow<IllegalArgumentException> { decoder.readSize { png.inputStream() } }.message shouldBe "no codec"
        Unit
    }

    /** Covers through the real desktop loader: decoded by this platform's Skia, or an error naming the format. */
    @Test
    fun `the desktop image loader decodes these covers or reports them as unsupported`(@TempDir dir: File) = runBlocking {
        val loader = desktopLoader(dir)
        samples.forEach { (format, bytes) ->
            when (val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(bytes).build())) {
                is SuccessResult -> (result.image.width to result.image.height) shouldBe (8 to 8)
                is ErrorResult -> result.throwable.shouldBeInstanceOf<UnsupportedPageFormatException>().format shouldBe format
            }
        }
        loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(TestImages.encode(8, 8)).build())
            .shouldBeInstanceOf<SuccessResult>()
        Unit
    }

    @Test
    fun `a failing Skia decode of these covers becomes unsupported, other covers are not touched`() = runBlocking {
        val loader = failingSkiaLoader()
        samples.forEach { (format, bytes) ->
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(bytes).build())
            result.shouldBeInstanceOf<ErrorResult>()
            val e = result.throwable.shouldBeInstanceOf<UnsupportedPageFormatException>()
            e.format shouldBe format
            e.hasCause("no codec") shouldBe true
        }
        // A PNG never reaches the injected decoder: Coil's own Skia decoder handles it
        val png = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(TestImages.encode(8, 8)).build())
        png.shouldBeInstanceOf<SuccessResult>()
        Unit
    }

    @Test
    fun `a cover in an unsupported format shows the format's name`() = runDesktopComposeUiTest {
        val loader = failingSkiaLoader()
        setContent { CoverImage(model = avif, contentDescription = null, imageLoader = loader, modifier = Modifier.size(40.dp, 56.dp)) }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("cover-unsupported-format").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("cover-unsupported-format").assertTextEquals("AVIF")
    }

    // By message along the cause chain: coroutines' stack-trace recovery may hand back wrapped copies of exceptions
    private fun Throwable.hasCause(message: String) = generateSequence(cause) { it.cause }.any { it.message == message }

    // The network client is only used for network requests; these load byte arrays
    private fun desktopLoader(dir: File): ImageLoader =
        DesktopImageLoader.create(PlatformContext.INSTANCE, mockk<NetworkHelper>(), sourceManager = null, cacheDir = dir)

    /** A loader whose Skia step for AVIF / JPEG XL / HEIF always fails, like a platform without those codecs. */
    private fun failingSkiaLoader(): ImageLoader {
        val failing = object : Decoder.Factory {
            override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
                object : Decoder {
                    override suspend fun decode(): DecodeResult = throw IllegalArgumentException("no codec")
                }
        }
        return ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(UnsupportedImageFormatDecoder.Factory(delegate = failing)) }
            .memoryCache(null)
            .diskCache(null)
            .build()
    }
}
