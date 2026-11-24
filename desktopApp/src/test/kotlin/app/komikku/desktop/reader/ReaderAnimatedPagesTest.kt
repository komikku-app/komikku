package app.komikku.desktop.reader

import android.app.Application
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.reader.ReaderImages
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.shared.reader.image.AnimationFrameCache
import app.komikku.shared.reader.image.DecodeRequest
import app.komikku.shared.reader.image.SkiaPageDecoder
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.awt.Color
import java.io.File

/**
 * P11: animated GIF pages play in the desktop reader. Skia's codec gives every frame (at the decoded size, with the
 * file's durations); an animation over the memory limit and still images keep a single frame; the paged viewer cycles
 * through the frames.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderAnimatedPagesTest {

    private lateinit var app: Application
    private val server = FakeMangaServer(pagesPerChapter = 2, animatedPages = setOf(1))
    private val gif = TestImages.animatedGif(80, 120, listOf(Color.RED, Color.GREEN, Color.BLUE), delayCentis = 7)

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "reader-animated").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource(server.baseUrl, chapterCount = 1))) }
        Injekt.get<ReaderPreferences>().pageLayout().set(PagerConfig.PageLayout.SINGLE_PAGE)
    }

    @AfterAll
    fun stop() = server.close()

    @Test
    fun `an animated GIF is decoded frame by frame with its durations`() = runBlocking<Unit> {
        val decoder = SkiaPageDecoder()
        val request = DecodeRequest(maxWidth = 40)
        val page = decoder.decode({ gif.inputStream() }, request)
        page.animated shouldBe true
        page.decoded.width shouldBe 40
        val frames = decoder.decodeFrames({ gif.inputStream() }, page, request)
        frames.map { it.durationMillis } shouldBe listOf(70, 70, 70)
        frames.map { frame ->
            val rgb = frame.image.toAwtImage().getRGB(20, 30)
            listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF).map { it > 128 }
        } shouldBe listOf(listOf(true, false, false), listOf(false, true, false), listOf(false, false, true))
        page.byteCount shouldBe 40L * 60 * 4
    }

    @Test
    fun `decoding an animated page returns its first frame without decoding the others`() = runBlocking<Unit> {
        var codecs = 0
        val decoder = SkiaPageDecoder(openCodec = { bytes ->
            codecs++
            Codec.makeFromData(Data.makeFromBytes(bytes))
        })
        val page = decoder.decode({ gif.inputStream() }, DecodeRequest(maxWidth = 40))
        page.animated shouldBe true
        page.tiles.size shouldBe 1
        codecs shouldBe 1
    }

    @Test
    fun `a failure while decoding the frames keeps the first frame`() = runBlocking<Unit> {
        val request = DecodeRequest(maxWidth = 40)
        val page = SkiaPageDecoder().decode({ gif.inputStream() }, request)
        listOf(IllegalStateException("codec"), OutOfMemoryError("frames"), RuntimeException("other")).forEach { failure ->
            val decoder = SkiaPageDecoder(openCodec = { throw failure })
            decoder.decodeFrames({ gif.inputStream() }, page, request) shouldBe emptyList()
        }
        // A stream that cannot be read either
        SkiaPageDecoder().decodeFrames({ throw java.io.IOException("gone") }, page, request) shouldBe emptyList()
    }

    @Test
    fun `the frame cache keeps one frame set per page`() = runBlocking<Unit> {
        val decoder = SkiaPageDecoder()
        val small = decoder.decode({ gif.inputStream() }, DecodeRequest(maxWidth = 40))
        val large = decoder.decode({ gif.inputStream() }, DecodeRequest(maxWidth = 80))
        val cache = AnimationFrameCache()
        cache.put("1-0", 256, decoder.decodeFrames({ gif.inputStream() }, small, DecodeRequest(maxWidth = 40)), small.decoded)
        cache.put("1-0", 512, decoder.decodeFrames({ gif.inputStream() }, large, DecodeRequest(maxWidth = 80)), large.decoded)
        cache.size shouldBe 1
        cache["1-0", 256] shouldBe null
        cache["1-0", 512]!!.size shouldBe 3
        cache.currentBytes shouldBe large.decoded.width.toLong() * large.decoded.height * 4 * 3
    }

    @Test
    fun `an animation over the memory limit and a still image keep one frame`() = runBlocking<Unit> {
        val request = DecodeRequest(maxWidth = 40, maxAnimationBytes = 1000)
        val limited = SkiaPageDecoder().decode({ gif.inputStream() }, request)
        limited.animated shouldBe true
        SkiaPageDecoder().decodeFrames({ gif.inputStream() }, limited, request) shouldBe emptyList()
        limited.tiles.size shouldBe 1
        val stillBytes = TestImages.encode(40, 60)
        val still = SkiaPageDecoder().decode({ stillBytes.inputStream() }, DecodeRequest(maxWidth = 40))
        still.animated shouldBe false
        SkiaPageDecoder().decodeFrames({ stillBytes.inputStream() }, still, DecodeRequest(maxWidth = 40)) shouldBe emptyList()
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    @Test
    fun `the paged viewer plays an animated page`() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/animated", "Animated manga")
        val chapter = TestLibrary.insertChapters(manga, "/chapter/1" to "Chapter 1").single()
        setContent { KomikkuApp(context = app) }
        runOnIdle { Injekt.get<ReaderLauncher>().open(manga.id, chapter.id, 0) }
        waitForTag("paged-viewer")
        waitForTag("page-frame-0")
        waitForTag("page-frame-1")
        waitForTag("page-frame-2")
        // and around again
        waitForTag("page-frame-0")
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader", present = false)
    }

    @Test
    fun `a preloaded animated page gets no frames until it is shown`() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/animated-preload", "Animated preload manga")
        val chapter = TestLibrary.insertChapters(manga, "/chapter/1" to "Chapter 1").single()
        ReaderImages.animations.clear()
        setContent { KomikkuApp(context = app) }
        // On the still page 2: the animated page 1 before it is loaded and decoded ahead (its first frame only)
        runOnIdle { Injekt.get<ReaderLauncher>().open(manga.id, chapter.id, 1) }
        waitForTag("paged-viewer")
        waitForTag("page-tile-0")
        waitUntil(timeoutMillis = 15_000) { ReaderImages.cache.currentBytes > 0 }
        Thread.sleep(500)
        ReaderImages.animations.size shouldBe 0
        onNodeWithTag("reader").performKeyInput { pressKey(Key.PageUp) }
        waitForTag("page-frame-1")
        ReaderImages.animations.size shouldBe 1
        onNodeWithTag("reader").performKeyInput { pressKey(Key.Escape) }
        waitForTag("reader", present = false)
    }
}
