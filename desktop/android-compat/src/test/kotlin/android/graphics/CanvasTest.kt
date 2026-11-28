package android.graphics

import android.graphics.TestImages.argb
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** `Canvas` drawing into a `Bitmap`: rectangle copies must be bit for bit (unscrambling depends on it). */
class CanvasTest {

    private val width = 30
    private val height = 20
    private val colors = TestImages.pattern(width, height)

    private fun source() = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)

    @Test
    fun `drawBitmap copies a source rectangle to a same-size destination exactly, with or without a paint`() {
        for (paint in listOf(null, Paint(), Paint(Paint.ANTI_ALIAS_FLAG))) {
            val source = source()
            val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(target).drawBitmap(source, Rect(4, 3, 14, 11), Rect(17, 9, 27, 17), paint)

            val out = target.argb()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val expected = if (x in 17 until 27 && y in 9 until 17) colors[(y - 6) * width + x - 13] else 0
                    out[y * width + x] shouldBe expected
                }
            }
            source.recycle()
            target.recycle()
        }
    }

    @Test
    fun `a bitmap drawn onto itself with overlapping rectangles reads its pixels from before the draw`() {
        val row = Bitmap.createBitmap(8, 1, Bitmap.Config.ARGB_8888)
        row.setPixels(IntArray(8) { TestImages.argb(255, it * 30, 0, 0) }, 0, 8, 0, 0, 8, 1)

        Canvas(row).drawBitmap(row, Rect(0, 0, 6, 1), Rect(2, 0, 8, 1), null)

        row.argb().map { (it shr 16) and 0xff } shouldBe listOf(0, 30, 0, 30, 60, 90, 120, 150)
        row.recycle()
    }

    @Test
    fun `a source rectangle reaching past the image is clipped and the destination shrunk in proportion`() {
        val row = Bitmap.createBitmap(IntArray(8) { TestImages.argb(255, it * 30, 0, 0) }, 8, 1, Bitmap.Config.ARGB_8888)
        val target = Bitmap.createBitmap(8, 1, Bitmap.Config.ARGB_8888)

        Canvas(target).drawBitmap(row, Rect(4, 0, 12, 1), Rect(0, 0, 8, 1), null)

        target.argb().map { (it shr 16) and 0xff } shouldBe listOf(120, 150, 180, 210, 0, 0, 0, 0)
        target.argb()[4] shouldBe 0
        row.recycle()
        target.recycle()
    }

    @Test
    fun `drawBitmap at a position, and with a null source rectangle, copies the whole bitmap`() {
        val source = source()
        val target = Bitmap.createBitmap(width + 5, height + 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawBitmap(source, 5f, 2f, null)
        val out = target.argb()
        out[2 * (width + 5) + 5] shouldBe colors[0]
        out[(height + 1) * (width + 5) + width + 4] shouldBe colors.last()
        out[0] shouldBe 0

        val same = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(same).drawBitmap(source, null, Rect(0, 0, width, height), null)
        same.argb() shouldBe colors
        listOf(source, target, same).forEach { it.recycle() }
    }

    @Test
    fun `scaling samples the nearest pixel without a paint and filters with a new paint`() {
        val source = source()
        val nearest = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
        Canvas(nearest).drawBitmap(source, null, Rect(0, 0, width * 2, height * 2), null)
        val doubled = nearest.argb()
        for (y in 0 until height * 2) {
            for (x in 0 until width * 2) doubled[y * width * 2 + x] shouldBe colors[(y / 2) * width + x / 2]
        }

        val filtered = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
        Canvas(filtered).drawBitmap(source, null, Rect(0, 0, width * 2, height * 2), Paint())
        filtered.argb() shouldNotBe doubled

        val flat = Bitmap.createBitmap(IntArray(16) { argb(255, 90, 120, 150) }, 4, 4, Bitmap.Config.ARGB_8888)
        val flatScaled = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        Canvas(flatScaled).drawBitmap(flat, null, Rect(0, 0, 10, 10), Paint())
        flatScaled.argb().toList() shouldContainOnly listOf(argb(255, 90, 120, 150))
        listOf(source, nearest, filtered, flat, flatScaled).forEach { it.recycle() }
    }

    @Test
    fun `a translucent bitmap and drawColor blend source-over`() {
        val target = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawColor(argb(255, 0, 0, 200))
        val half = Bitmap.createBitmap(intArrayOf(argb(128, 255, 0, 0)), 1, 1, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(half, 0f, 0f, null)
        val blended = target.getPixel(0, 0)
        (blended ushr 24) shouldBe 255
        (abs((blended shr 16 and 0xff) - 128) <= 1) shouldBe true
        (abs((blended and 0xff) - 100) <= 1) shouldBe true
        target.getPixel(1, 0) shouldBe argb(255, 0, 0, 200)
        listOf(target, half).forEach { it.recycle() }
    }

    @Test
    fun `translate, save and restore move later drawing`() {
        val dot = Bitmap.createBitmap(intArrayOf(argb(255, 1, 2, 3)), 1, 1, Bitmap.Config.ARGB_8888)
        val target = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.save()
        canvas.translate(3f, 2f)
        canvas.drawBitmap(dot, 0f, 0f, null)
        canvas.restore()
        canvas.drawBitmap(dot, 1f, 1f, null)
        target.getPixel(3, 2) shouldBe argb(255, 1, 2, 3)
        target.getPixel(1, 1) shouldBe argb(255, 1, 2, 3)
        target.getPixel(0, 0) shouldBe 0
        shouldThrow<IllegalStateException> { canvas.restore() }
        canvas.getWidth() shouldBe 5
        listOf(dot, target).forEach { it.recycle() }
    }

    @Test
    fun `a canvas needs a mutable bitmap and refuses recycled sources, like Android`() {
        val immutable = source()
        shouldThrow<IllegalStateException> { Canvas(immutable) }
        val target = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        immutable.recycle()
        shouldThrow<RuntimeException> { Canvas(target).drawBitmap(immutable, 0f, 0f, null) }
        target.recycle()
    }
}
