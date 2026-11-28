package android.graphics

import android.graphics.TestImages.argb
import app.komikku.desktop.compat.UnsupportedOnDesktop
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pixels in and out of `android.graphics.Bitmap` (known answers; ANSWER 016). */
class BitmapTest {

    @Test
    fun `a new bitmap is mutable, transparent and ARGB_8888`() {
        val bitmap = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888)
        bitmap.getWidth() shouldBe 5
        bitmap.getHeight() shouldBe 3
        bitmap.getConfig() shouldBe Bitmap.Config.ARGB_8888
        bitmap.isMutable().shouldBeTrue()
        bitmap.argb().toList() shouldContainOnly listOf(0)
        bitmap.recycle()
    }

    @Test
    fun `opaque pixels come back exactly through setPixels, getPixels and getPixel`() {
        val width = 37
        val height = 23
        val colors = TestImages.pattern(width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(colors, 0, width, 0, 0, width, height)

        bitmap.argb() shouldBe colors
        bitmap.getPixel(36, 22) shouldBe colors[22 * width + 36]
        bitmap.getPixel(0, 0) shouldBe colors[0]
        bitmap.recycle()
    }

    @Test
    fun `getPixels and setPixels honour offset, stride and the rectangle`() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val red = argb(255, 255, 0, 0)
        val blue = argb(255, 0, 0, 255)
        // A 2 x 2 block at (1, 2), from an array with offset 3 and stride 5
        val source = IntArray(3 + 5 + 2) { 0 }.apply {
            this[3] = red
            this[4] = blue
            this[8] = blue
            this[9] = red
        }
        bitmap.setPixels(source, 3, 5, 1, 2, 2, 2)

        val out = IntArray(12) { -1 }
        bitmap.getPixels(out, 1, 6, 0, 2, 4, 2)
        out.toList() shouldBe listOf(-1, 0, red, blue, 0, -1, -1, 0, blue, red, 0, -1)
        bitmap.getPixel(0, 0) shouldBe 0
        bitmap.recycle()
    }

    @Test
    fun `translucent pixels are stored premultiplied and read back unpremultiplied like Android`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        for (alpha in listOf(1, 17, 64, 128, 200, 254)) {
            for (channel in listOf(0, 1, 77, 128, 200, 255)) {
                bitmap.setPixel(0, 0, argb(alpha, channel, 255 - channel, channel / 2))
                val read = bitmap.getPixel(0, 0)
                (read ushr 24) shouldBe alpha
                // Android: premultiplied with rounding, unpremultiplied with rounding (±1 for the float pipeline)
                fun expected(c: Int) = ((c * alpha / 255.0).roundToInt() * 255.0 / alpha).roundToInt().coerceAtMost(255)
                listOf(channel to (read shr 16 and 0xff), 255 - channel to (read shr 8 and 0xff), channel / 2 to (read and 0xff))
                    .forEach { (c, actual) -> (abs(actual - expected(c)) <= 1).shouldBeTrue() }
            }
        }
        bitmap.setPixel(0, 0, argb(0, 200, 100, 50))
        bitmap.getPixel(0, 0) shouldBe 0
        bitmap.recycle()
    }

    @Test
    fun `createBitmap copies a rectangle into an immutable bitmap, and returns an immutable source as is`() {
        val width = 20
        val height = 10
        val colors = TestImages.pattern(width, height)
        val source = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        source.isMutable().shouldBeFalse()
        source.argb() shouldBe colors

        val part = Bitmap.createBitmap(source, 3, 2, 8, 5)
        part.isMutable().shouldBeFalse()
        part.argb() shouldBe IntArray(8 * 5) { i -> colors[(2 + i / 8) * width + 3 + i % 8] }
        Bitmap.createBitmap(source, 0, 0, width, height) shouldBeSameInstanceAs source
        shouldThrow<IllegalArgumentException> { Bitmap.createBitmap(source, 15, 0, 8, 5) }
        shouldThrow<IllegalStateException> { part.setPixel(0, 0, 0) }
        part.recycle()
        source.recycle()
    }

    @Test
    fun `copy is independent, eraseColor fills`() {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(argb(255, 10, 20, 30))
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        copy.setPixel(1, 1, argb(255, 1, 2, 3))
        bitmap.getPixel(1, 1) shouldBe argb(255, 10, 20, 30)
        copy.getPixel(1, 1) shouldBe argb(255, 1, 2, 3)
        copy.getPixel(0, 0) shouldBe argb(255, 10, 20, 30)
        bitmap.copy(Bitmap.Config.ARGB_8888, false).isMutable().shouldBeFalse()
        bitmap.recycle()
        copy.recycle()
    }

    @Test
    fun `copyPixelsFromBuffer reads Android's memory layout, premultiplied RGBA, and advances the buffer`() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        // Opaque (10, 20, 30); then alpha 51 with premultiplied (10, 0, 20), i.e. (50, 0, 100)
        val buffer = ByteBuffer.wrap(byteArrayOf(10, 20, 30, -1, 10, 0, 20, 51, 99))
        bitmap.copyPixelsFromBuffer(buffer)
        buffer.position() shouldBe 8
        bitmap.getPixel(0, 0) shouldBe argb(255, 10, 20, 30)
        bitmap.getPixel(1, 0) shouldBe argb(51, 50, 0, 100)

        val ints = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
            put(byteArrayOf(1, 2, 3, -1, 4, 5, 6, -1))
        }.flip().asIntBuffer()
        bitmap.copyPixelsFromBuffer(ints)
        ints.position() shouldBe 2
        bitmap.getPixel(1, 0) shouldBe argb(255, 4, 5, 6)

        shouldThrow<RuntimeException> { bitmap.copyPixelsFromBuffer(ByteBuffer.allocate(7)) }
        bitmap.recycle()
    }

    @Test
    fun `other configs and unsupported members name themselves`() {
        shouldThrow<UnsupportedOnDesktop> { Bitmap.createBitmap(2, 2, Bitmap.Config.RGBA_F16) }.needs shouldBe
            "android.graphics.Bitmap.createBitmap(Config.RGBA_F16)"
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        shouldThrow<UnsupportedOnDesktop> { Bitmap.createScaledBitmap(bitmap, 4, 4, true) }.needs shouldBe
            "android.graphics.Bitmap.createScaledBitmap"
        shouldThrow<UnsupportedOnDesktop> { Paint().setTextSize(12f) }.needs shouldBe "android.graphics.Paint.setTextSize"
        shouldThrow<UnsupportedOnDesktop> { Canvas(bitmap).drawText("x", 0f, 0f, Paint()) }
        bitmap.recycle()
    }

    @Test
    fun `a recycled bitmap refuses pixel access like Android`() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        bitmap.recycle()
        bitmap.isRecycled().shouldBeTrue()
        bitmap.getWidth() shouldBe 2
        shouldThrow<IllegalStateException> { bitmap.getPixel(0, 0) }
        shouldThrow<IllegalStateException> { bitmap.compress(Bitmap.CompressFormat.PNG, 100, java.io.ByteArrayOutputStream()) }
    }
}
