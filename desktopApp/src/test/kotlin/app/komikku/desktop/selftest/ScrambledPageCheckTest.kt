package app.komikku.desktop.selftest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/**
 * [ScrambledPageCheck] with a client whose interceptor unscrambles GigaViewer pages the way the extension does, on the
 * compat layer's `android.graphics` (the self-test runs the real extension's interceptor; CI passes its APK).
 */
class ScrambledPageCheckTest {

    /** GigaViewer's page interceptor, re-stated: draw the image, then each block of the 4 x 4 grid transposed. */
    private val gigaViewerInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (chain.request().url.fragment != "scramble") return@Interceptor response
        val image = BitmapFactory.decodeStream(response.body.byteStream())!!
        val blockWidth = image.getWidth() / 32 * 8
        val blockHeight = image.getHeight() / 32 * 8
        val result = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(image, 0f, 0f, null)
        for (e in 0 until 16) {
            val dst = (e % 4) * 4 + e / 4
            val src = Rect((e % 4) * blockWidth, (e / 4) * blockHeight, 0, 0).apply { set(left, top, left + blockWidth, top + blockHeight) }
            val to = Rect((dst % 4) * blockWidth, (dst / 4) * blockHeight, 0, 0).apply { set(left, top, left + blockWidth, top + blockHeight) }
            canvas.drawBitmap(image, src, to, null)
        }
        image.recycle()
        val out = ByteArrayOutputStream().also { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        result.recycle()
        response.newBuilder().body(out.toResponseBody("image/jpeg".toMediaType())).build()
    }

    @Test
    fun `a page unscrambled by the extension's interceptor passes`() {
        val client = OkHttpClient.Builder().addInterceptor(gigaViewerInterceptor).build()
        ScrambledPageCheck.check(client, ScrambledPageCheck.GIGAVIEWER) shouldContain "GigaViewer page unscrambled"
    }

    @Test
    fun `a page left scrambled fails`() {
        shouldThrow<IllegalStateException> { ScrambledPageCheck.check(OkHttpClient(), ScrambledPageCheck.GIGAVIEWER) }
            .message shouldContain "came back wrong"
    }

    @Test
    fun `the GigaViewer scheme is a transposition, its own inverse`() {
        val page = ScrambledPageCheck.page(ScrambledPageCheck.WIDTH, ScrambledPageCheck.HEIGHT)
        val scheme = ScrambledPageCheck.GIGAVIEWER
        scheme.scramble(scheme.scramble(page, ScrambledPageCheck.WIDTH, ScrambledPageCheck.HEIGHT), ScrambledPageCheck.WIDTH, ScrambledPageCheck.HEIGHT) shouldBe page
        ScrambledPageCheck.schemes.keys shouldBe setOf("eu.kanade.tachiyomi.extension.ja.shonenjumpplus")
    }
}
