package android.graphics

import app.komikku.desktop.compat.NativeBitmaps
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Native memory and threads (ANSWER 016): recycle frees, a cleaner frees what is forgotten, no UI thread is needed. */
class BitmapMemoryTest {

    @Test
    fun `recycle frees the native pixels at once`() {
        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        val pixels = bitmap.pixels
        val before = NativeBitmaps.liveBytes()
        pixels.closed.shouldBeFalse()
        bitmap.recycle()
        pixels.closed.shouldBeTrue()
        pixels.skia.isClosed.shouldBeTrue()
        NativeBitmaps.liveBytes() shouldBe before - 100 * 50 * 4
    }

    @Test
    fun `drawing and compressing keep one native buffer per bitmap`() {
        val width = 300
        val height = 200
        val start = settledLiveBytes()
        val source = Bitmap.createBitmap(TestImages.pattern(width, height), width, height, Bitmap.Config.ARGB_8888)
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val oneBuffer = width * height * 4L
        NativeBitmaps.liveBytes() shouldBe start + 2 * oneBuffer

        Canvas(target).drawBitmap(source, 0f, 0f, null)
        target.compress(Bitmap.CompressFormat.PNG, 100, ByteArrayOutputStream())
        // Drawn onto itself: a copy for the draw only
        Canvas(target).drawBitmap(target, Rect(0, 0, 100, 100), Rect(50, 50, 150, 150), null)
        NativeBitmaps.liveBytes() shouldBe start + 2 * oneBuffer

        // The image a draw or an encode reads shares the bitmap's pixels: a change shows in it at once
        synchronized(target.pixels) {
            target.pixels.withImage { image ->
                target.setPixel(0, 0, 0xffff0000.toInt())
                org.jetbrains.skia.Bitmap.makeFromImage(image).use { it.getColor(0, 0) } shouldBe 0xffff0000.toInt()
            }
        }

        source.recycle()
        target.recycle()
        NativeBitmaps.liveBytes() shouldBe start
    }

    @Test
    fun `two threads drawing A into B and B into A at once do not deadlock`() {
        val a = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        a.setPixels(TestImages.pattern(64, 64), 0, 64, 0, 0, 64, 64)
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val pool = Executors.newFixedThreadPool(2)
        val barrier = CyclicBarrier(2)
        try {
            listOf(a to b, b to a).map { (from, to) ->
                pool.submit {
                    barrier.await(10, TimeUnit.SECONDS)
                    val canvas = Canvas(to)
                    repeat(500) { canvas.drawBitmap(from, Rect(0, 0, 32, 32), Rect(32, 32, 64, 64), null) }
                }
            }.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        a.recycle()
        b.recycle()
    }

    @Test
    fun `a recycle on another thread during a draw waits for it, and later draws are refused`() {
        val source = Bitmap.createBitmap(TestImages.pattern(1500, 1500), 1500, 1500, Bitmap.Config.ARGB_8888)
        val target = Bitmap.createBitmap(3000, 3000, Bitmap.Config.ARGB_8888)
        val started = java.util.concurrent.CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        val drawing = pool.submit<Int> {
            val canvas = Canvas(target)
            val filtering = Paint()
            var draws = 0
            try {
                while (true) {
                    started.countDown()
                    canvas.drawBitmap(source, null, Rect(0, 0, 3000, 3000), filtering)
                    draws++
                }
            } catch (e: RuntimeException) {
                e.message!! shouldBe "Canvas: trying to use a recycled bitmap $source"
            }
            draws
        }
        try {
            started.await(10, TimeUnit.SECONDS).shouldBeTrue()
            Thread.sleep(30)
            source.recycle()
            drawing.get(30, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
        source.pixels.closed.shouldBeTrue()
        target.recycle()
    }

    @Test
    fun `a bitmap an extension forgets is freed by the cleaner`() {
        // The pixels (not the Bitmap) are kept to watch them; the Bitmap and its canvas become unreachable
        val pixels = forgottenBitmap()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!pixels.closed && System.nanoTime() < deadline) {
            System.gc()
            Thread.sleep(50)
        }
        pixels.closed.shouldBeTrue()
        pixels.skia.isClosed.shouldBeTrue()
    }

    /** [NativeBitmaps.liveBytes] once bitmaps other tests forgot have been cleaned (it no longer changes). */
    private fun settledLiveBytes(): Long {
        var last = -1L
        var stable = 0
        while (stable < 3) {
            System.gc()
            Thread.sleep(50)
            val now = NativeBitmaps.liveBytes()
            if (now == last) stable++ else stable = 0
            last = now
        }
        return last
    }

    private fun forgottenBitmap(): NativeBitmaps.Pixels {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(0xff123456.toInt())
        return bitmap.pixels
    }

    @Test
    fun `page interceptors on several threads at once get the same pixels as one at a time, off any UI thread`() {
        val width = 257
        val height = 371
        val base = TestImages.pattern(width, height)
        val pages = List(8) { page -> IntArray(width * height) { i -> base[i] xor (page * 0x010101) } }
        val png = pages.map { TestImages.png(it, width, height) }
        // One source bitmap shared by every thread too (each draws a part of it)
        val shared = Bitmap.createBitmap(pages[0], width, height, Bitmap.Config.ARGB_8888)

        fun intercept(page: Int): Pair<ByteArray, IntArray> {
            val decoded = BitmapFactory.decodeByteArray(png[page], 0, png[page].size)!!
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(decoded, 0f, 0f, null)
            for (row in 0 until 4) {
                val top = row * height / 4
                val bottom = (row + 1) * height / 4
                canvas.drawBitmap(decoded, Rect(0, top, width, bottom), Rect(0, height - bottom, width, height - top), null)
            }
            canvas.drawBitmap(shared, Rect(0, 0, 16, 16), Rect(0, 0, 16, 16), null)
            val pixels = with(TestImages) { result.argb() }
            val out = ByteArrayOutputStream().also { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            decoded.recycle()
            result.recycle()
            return out to pixels
        }

        val sequential = pages.indices.map(::intercept)
        val pool = Executors.newFixedThreadPool(8)
        val barrier = CyclicBarrier(8)
        val uiThreads = mutableListOf<String>()
        val concurrent = try {
            (0 until 8 * 4).map { job ->
                pool.submit<Pair<ByteArray, IntArray>> {
                    if (job < 8) barrier.await(10, TimeUnit.SECONDS)
                    if (java.awt.EventQueue.isDispatchThread()) synchronized(uiThreads) { uiThreads += Thread.currentThread().name }
                    intercept(job % 8)
                }
            }.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }
        concurrent.forEachIndexed { job, (jpeg, pixels) ->
            pixels shouldBe sequential[job % 8].second
            jpeg shouldBe sequential[job % 8].first
        }
        uiThreads.shouldBeEmpty()
        Thread.getAllStackTraces().keys.map { it.name }.filter { it.startsWith("AWT-EventQueue") }.shouldBeEmpty()
        shared.recycle()
    }
}
