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
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.shared.reader.image.DecodeRequest
import app.komikku.shared.reader.image.SkiaPageDecoder
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
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
        val page = SkiaPageDecoder().decode({ gif.inputStream() }, DecodeRequest(maxWidth = 40))
        page.animated shouldBe true
        page.decoded.width shouldBe 40
        page.frames.map { it.durationMillis } shouldBe listOf(70, 70, 70)
        page.frames.map { frame ->
            val rgb = frame.image.toAwtImage().getRGB(20, 30)
            listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF).map { it > 128 }
        } shouldBe listOf(listOf(true, false, false), listOf(false, true, false), listOf(false, false, true))
        page.byteCount shouldBe 40L * 60 * 4 * 4
    }

    @Test
    fun `an animation over the memory limit and a still image keep one frame`() = runBlocking<Unit> {
        val limited = SkiaPageDecoder().decode({ gif.inputStream() }, DecodeRequest(maxWidth = 40, maxAnimationBytes = 1000))
        limited.animated shouldBe true
        limited.frames shouldBe emptyList()
        limited.tiles.size shouldBe 1
        val still = SkiaPageDecoder().decode({ TestImages.encode(40, 60).inputStream() }, DecodeRequest(maxWidth = 40))
        still.animated shouldBe false
        still.frames shouldBe emptyList()
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
}
