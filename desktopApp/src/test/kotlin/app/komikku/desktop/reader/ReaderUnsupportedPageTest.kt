package app.komikku.desktop.reader

import android.graphics.Paint
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.compat.UnsupportedOnDesktop
import app.komikku.desktop.extension.testing.FixtureClasses
import app.komikku.desktop.ui.reader.ReaderPageSlot
import app.komikku.desktop.ui.reader.UnsupportedPage
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.kmk.KMR
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger

/**
 * ANSWER 017: a page whose extension draws it from text (Keiyoushi's text-interceptor library: `StaticLayout`,
 * `TextPaint`, `Typeface`, `Paint`'s text members) shows the reader's "unsupported" state saying so, without a retry
 * button and without being loaded again each time it is shown; another missing Android class or member (a class
 * like `RectF`, a member like `Canvas.drawPath`, REVIEW_GRAPHICS 4.1) is named the same way. Any other failure keeps
 * the error with its retry.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderUnsupportedPageTest {

    /** The error an image fetch through [interceptor] fails with, the way sources fetch pages (an async call). */
    private fun fetchError(interceptor: Interceptor): Throwable {
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return shouldThrow<IOException> {
            runBlocking { client.newCall(Request.Builder().url("https://example.invalid/page.png").build()).await() }
        }
    }

    /**
     * Runs a generated class's static `touch()`, which calls static `()V` [method] of the compat layer's [owner]
     * (internal name): the call fails with the JVM's own linkage error for a member the compat class lacks.
     */
    private fun callMissing(owner: String, method: String): Nothing {
        val name = "fixture/Calls${owner.substringAfterLast('/')}${method.replaceFirstChar(Char::uppercase)}"
        val bytes = FixtureClasses.classCalling(name, owner, method, "()V")
        val loader = object : ClassLoader(javaClass.classLoader) {
            fun define(): Class<*> = defineClass(name.replace('/', '.'), bytes, 0, bytes.size)
        }
        try {
            loader.define().getMethod("touch").invoke(null)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
        error("$owner.$method exists")
    }

    @Test
    fun `a member a compat class lacks is named at page time, text members as text drawing`() {
        UnsupportedPage.of(fetchError { callMissing("android/graphics/Canvas", "drawPath") }) shouldBe
            UnsupportedPage.Missing("android.graphics.Canvas.drawPath()")
        UnsupportedPage.of(fetchError { callMissing("android/graphics/Paint", "setTypeface") }) shouldBe
            UnsupportedPage.TextDrawing("android.graphics.Paint.setTypeface()")
        UnsupportedPage.of(fetchError { callMissing("android/graphics/Paint", "getFontMetrics") }) shouldBe
            UnsupportedPage.TextDrawing("android.graphics.Paint.getFontMetrics()")
        // The JVM's message for the member with its parameters (as the generated call above shows it, with types)
        val matrix = "'void android.graphics.Canvas.drawBitmap(android.graphics.Bitmap, android.graphics.Matrix, " +
            "android.graphics.Paint)'"
        UnsupportedPage.of(fetchError { throw NoSuchMethodError(matrix) }) shouldBe
            UnsupportedPage.Missing("android.graphics.Canvas.drawBitmap(Bitmap, Matrix, Paint)")
        UnsupportedPage.of(fetchError { throw NoClassDefFoundError("android/graphics/RectF") }) shouldBe
            UnsupportedPage.Missing("android.graphics.RectF")
        UnsupportedPage.of(fetchError { Paint().setTextSize(1f).let { error("text size set") } }) shouldBe
            UnsupportedPage.TextDrawing("android.graphics.Paint.setTextSize")

        UnsupportedPage.of(fetchError { throw NoSuchMethodError("'void com.example.Helper.run()'") }) shouldBe null
    }

    @Test
    fun `a text drawing failure inside an interceptor is told apart from other failures`() {
        UnsupportedPage.of(fetchError { throw NoClassDefFoundError("android/text/StaticLayout") }) shouldBe
            UnsupportedPage.TextDrawing("android.text.StaticLayout")
        UnsupportedPage.of(fetchError { throw NoClassDefFoundError("android/graphics/Typeface") }) shouldBe
            UnsupportedPage.TextDrawing("android.graphics.Typeface")
        // The compat layer's own Paint
        UnsupportedPage.of(fetchError { error("width ${Paint().measureText("title")}") }) shouldBe
            UnsupportedPage.TextDrawing("android.graphics.Paint.measureText")
        UnsupportedPage.of(fetchError { throw NoClassDefFoundError("android/graphics/Point") }) shouldBe
            UnsupportedPage.Missing("android.graphics.Point")
        UnsupportedPage.of(fetchError { throw UnsupportedOnDesktop("android.os.Looper", "loop()") }) shouldBe
            UnsupportedPage.Missing("android.os.Looper.loop()")

        UnsupportedPage.of(fetchError { throw IOException("connection reset") }) shouldBe null
        UnsupportedPage.of(fetchError { throw NoClassDefFoundError("com/example/Missing") }) shouldBe null
        UnsupportedPage.of(HttpException(404)) shouldBe null
    }

    /** A loader that counts the pages it is asked to load, and loads nothing. */
    private class CountingLoader : PageLoader() {
        val loads = AtomicInteger()
        override var isLocal = false
        override suspend fun getPages(): List<ReaderPage> = emptyList()
        override suspend fun loadPage(page: ReaderPage) {
            loads.incrementAndGet()
        }
    }

    private fun failedPage(error: Throwable): Pair<ReaderPage, CountingLoader> {
        val loader = CountingLoader()
        val chapter = ReaderChapter(Chapter.create().copy(id = 1, mangaId = 1, url = "/chapter/1", name = "Chapter 1"))
        val page = ReaderPage(0).also { it.chapter = chapter }
        chapter.state = ReaderChapter.State.Loaded(listOf(page))
        chapter.pageLoader = loader
        page.status = Page.State.Error(error)
        return page to loader
    }

    @Test
    fun `a page drawn as text says so, with no retry, and is not loaded again`() = runDesktopComposeUiTest {
        val (page, loader) = failedPage(fetchError { throw NoClassDefFoundError("android/text/TextPaint") })
        setContent { ReaderPageSlot(page, maxWidth = 800, colorFilter = null, onRetry = {}) { Text("image") } }

        onNodeWithText(stringResource(KMR.strings.desktop_page_drawn_as_text)).assertExists()
        onNodeWithTag("page-unsupported").assertExists()
        onNodeWithTag("page-retry").assertDoesNotExist()
        waitForIdle()
        loader.loads.get() shouldBe 0
    }

    @Test
    fun `a page that needs another missing member names it, with no retry`() = runDesktopComposeUiTest {
        val (page, loader) = failedPage(fetchError { throw NoClassDefFoundError("android/graphics/Point") })
        setContent { ReaderPageSlot(page, maxWidth = 800, colorFilter = null, onRetry = {}) { Text("image") } }

        onNodeWithText(stringResource(KMR.strings.desktop_page_needs_unsupported, "android.graphics.Point")).assertExists()
        onNodeWithTag("page-retry").assertDoesNotExist()
        waitForIdle()
        loader.loads.get() shouldBe 0
    }

    @Test
    fun `a page that needs a member a compat class lacks names it, with no retry`() = runDesktopComposeUiTest {
        val (page, loader) = failedPage(fetchError { callMissing("android/graphics/Canvas", "drawPath") })
        setContent { ReaderPageSlot(page, maxWidth = 800, colorFilter = null, onRetry = {}) { Text("image") } }

        onNodeWithText(stringResource(KMR.strings.desktop_page_needs_unsupported, "android.graphics.Canvas.drawPath()"))
            .assertExists()
        onNodeWithTag("page-unsupported").assertExists()
        onNodeWithTag("page-retry").assertDoesNotExist()
        waitForIdle()
        loader.loads.get() shouldBe 0
    }

    @Test
    fun `any other failure keeps the error, its retry and the loader's retry when shown`() = runDesktopComposeUiTest {
        val (page, loader) = failedPage(fetchError { throw IOException("connection reset") })
        setContent { ReaderPageSlot(page, maxWidth = 800, colorFilter = null, onRetry = {}) { Text("image") } }

        onNodeWithTag("page-error").assertExists()
        onNodeWithTag("page-retry").assertExists()
        waitForIdle()
        loader.loads.get() shouldBe 1
    }
}
