package app.komikku.desktop.reader

import app.komikku.shared.reader.image.ByteSizedLruCache
import app.komikku.shared.reader.image.DecodeRequest
import app.komikku.shared.reader.image.PageFit
import app.komikku.shared.reader.image.PageScaling
import app.komikku.shared.reader.image.PixelSize
import app.komikku.shared.reader.image.SkiaPageDecoder
import app.komikku.shared.reader.image.UnsupportedPageFormatException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/** 7.2: sizing / tiling math, the byte-bounded LRU and the Skia decoder on generated images. */
class ReaderImageTest {

    @Test
    fun `display size per fit mode`() {
        val image = PixelSize(1000, 2000)
        val viewport = PixelSize(500, 500)
        PageScaling.displaySize(image, viewport, PageFit.Screen) shouldBe (250f to 500f)
        PageScaling.displaySize(image, viewport, PageFit.Width) shouldBe (500f to 1000f)
        PageScaling.displaySize(image, viewport, PageFit.Height) shouldBe (250f to 500f)
        PageScaling.displaySize(image, viewport, PageFit.Original) shouldBe (1000f to 2000f)
        PageScaling.displaySize(image, viewport, PageFit.Stretch) shouldBe (500f to 1000f)
        // Smart: a tall strip fits the width, a normal page the screen
        PageScaling.displaySize(PixelSize(100, 1000), viewport, PageFit.Smart) shouldBe (500f to 5000f)
        PageScaling.displaySize(PixelSize(400, 500), viewport, PageFit.Smart) shouldBe (400f to 500f)
        PageFit.fromPreference(3) shouldBe PageFit.Width
        PageFit.fromPreference(99) shouldBe PageFit.Screen
    }

    @Test
    fun `decode size never upscales and respects the pixel budget`() {
        PageScaling.decodeSize(PixelSize(1000, 2000), displayWidth = 500f) shouldBe PixelSize(500, 1000)
        PageScaling.decodeSize(PixelSize(1000, 2000), displayWidth = 500f, maxZoom = 4f) shouldBe PixelSize(1000, 2000)
        PageScaling.decodeSize(PixelSize(300, 300), displayWidth = 2000f) shouldBe PixelSize(300, 300)
        val budget = PageScaling.decodeSize(PixelSize(1000, 100_000), displayWidth = 1000f, maxPixels = 10_000_000)
        (budget.width.toLong() * budget.height <= 10_100_000) shouldBe true
        budget.width shouldBe 316
    }

    @Test
    fun `tiles cover the height in bounded strips`() {
        PageScaling.tiles(10_000, 4096) shouldBe listOf(0 until 4096, 4096 until 8192, 8192 until 10_000)
        PageScaling.tiles(100, 4096) shouldBe listOf(0 until 100)
        PageScaling.tiles(0) shouldBe emptyList()
        PageScaling.sourceRows(4096 until 8192, decodedHeight = 10_000, sourceHeight = 20_000) shouldBe (8192f to 16384f)
    }

    @Test
    fun `lru evicts least recently used by bytes`() {
        val evicted = mutableListOf<String>()
        val cache = ByteSizedLruCache<String, ByteArray>(maxBytes = 100, sizeOf = { it.size.toLong() }, onEvicted = { k, _ -> evicted += k })
        cache.put("a", ByteArray(40))
        cache.put("b", ByteArray(40))
        cache["a"] // a is now most recent
        cache.put("c", ByteArray(40))
        evicted shouldBe listOf("b")
        cache.keys shouldBe listOf("a", "c")
        cache.currentBytes shouldBe 80
        cache.put("huge", ByteArray(500))
        cache["huge"] shouldBe null
        cache.currentBytes shouldBe 80
    }

    private val decoder = SkiaPageDecoder()

    @Test
    fun `decodes png, jpeg and gif, downsampled`() = runBlocking {
        listOf("png", "jpg", "gif", "bmp").forEach { format ->
            val bytes = TestImages.encode(400, 600, format)
            decoder.readSize { bytes.inputStream() } shouldBe PixelSize(400, 600)
            val page = decoder.decode({ bytes.inputStream() }, DecodeRequest(maxWidth = 200))
            page.source shouldBe PixelSize(400, 600)
            page.decoded shouldBe PixelSize(200, 300)
            page.tiles shouldHaveSize 1
            page.tiles.single().width shouldBe 200
            page.tiles.single().height shouldBe 300
            page.animated shouldBe false
        }
    }

    @Test
    fun `tall strips are tiled`() = runBlocking {
        val bytes = TestImages.encode(100, 5000)
        val page = decoder.decode({ bytes.inputStream() }, DecodeRequest(maxWidth = 100, maxTileHeight = 2048))
        page.decoded shouldBe PixelSize(100, 5000)
        page.tiles.map { it.height } shouldBe listOf(2048, 2048, 904)
        page.tiles.all { it.width == 100 } shouldBe true
        Unit
    }

    @Test
    fun `avif is reported as unsupported, junk as a decode error`() = runBlocking {
        val avif = TestImages.avifHeader()
        val e = shouldThrow<UnsupportedPageFormatException> { decoder.decode({ avif.inputStream() }, DecodeRequest(100)) }
        e.format shouldBe "AVIF"
        shouldThrow<IllegalStateException> { decoder.decode({ ByteArray(32) { 7 }.inputStream() }, DecodeRequest(100)) }
        Unit
    }
}
