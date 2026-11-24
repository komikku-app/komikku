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
import app.komikku.shared.reader.image.DecodeRequest
import app.komikku.shared.reader.image.SkiaPageDecoder
import app.komikku.shared.reader.image.UnsupportedPageFormatException
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.util.system.ImageUtil
import java.io.File
import java.util.Base64

/**
 * P5: AVIF / JPEG XL on desktop. Skia (Skiko) has no decoder for them and no acceptable JVM decoder was found (PROGRESS
 * "P5"), so the reader and the covers detect them and report the format instead of a generic failure. Real 8×8 samples,
 * generated with Pillow (AVIF, libavif / aom) and ffmpeg (JPEG XL, libjxl), embedded as Base64.
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

    /** The blocker: when a Skiko update brings these codecs, this fails and the format gates can go. */
    @Test
    fun `Skia on desktop cannot decode AVIF or JPEG XL`() {
        samples.values.forEach { bytes ->
            shouldThrow<IllegalArgumentException> { Image.makeFromEncoded(bytes).close() }
            shouldThrow<IllegalArgumentException> { Codec.makeFromData(Data.makeFromBytes(bytes)).close() }
        }
    }

    @Test
    fun `reader pages in these formats fail as unsupported, with the format`() = runBlocking {
        val decoder = SkiaPageDecoder()
        samples.forEach { (format, bytes) ->
            shouldThrow<UnsupportedPageFormatException> { decoder.decode({ bytes.inputStream() }, DecodeRequest(100)) }
                .format shouldBe format
            shouldThrow<UnsupportedPageFormatException> { decoder.readSize { bytes.inputStream() } }.format shouldBe format
        }
    }

    @Test
    fun `the desktop image loader reports these covers as unsupported and still decodes others`(@TempDir dir: File) = runBlocking {
        val loader = desktopLoader(dir)
        samples.forEach { (format, bytes) ->
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(bytes).build())
            result.shouldBeInstanceOf<ErrorResult>()
            result.throwable.shouldBeInstanceOf<UnsupportedPageFormatException>().format shouldBe format
        }
        loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(TestImages.encode(8, 8)).build())
            .shouldBeInstanceOf<SuccessResult>()
        Unit
    }

    @Test
    fun `a cover in an unsupported format shows the format's name`(@TempDir dir: File) = runDesktopComposeUiTest {
        val loader = desktopLoader(dir)
        setContent { CoverImage(model = avif, contentDescription = null, imageLoader = loader, modifier = Modifier.size(40.dp, 56.dp)) }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("cover-unsupported-format").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("cover-unsupported-format").assertTextEquals("AVIF")
    }

    // The network client is only used for network requests; these load byte arrays
    private fun desktopLoader(dir: File): ImageLoader =
        DesktopImageLoader.create(PlatformContext.INSTANCE, mockk<NetworkHelper>(), sourceManager = null, cacheDir = dir)
}
