package android.graphics

import android.graphics.TestImages.argb
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** `ColorMatrixColorFilter` on a paint, as `tenkaiscan` uses it to undo colour-inverted page pieces. */
class ColorMatrixTest {

    private val invert = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun drawWith(filter: ColorFilter?, colors: IntArray, width: Int): IntArray {
        val height = colors.size / width
        val source = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { setColorFilter(filter) }
        Canvas(target).drawBitmap(source, 0f, 0f, paint)
        return with(TestImages) { target.argb() }.also {
            source.recycle()
            target.recycle()
        }
    }

    @Test
    fun `an inverting matrix inverts colours and keeps alpha, with Android's 0 to 255 offsets`() {
        val colors = TestImages.pattern(16, 9)
        val inverted = drawWith(ColorMatrixColorFilter(ColorMatrix(invert)), colors, 16)
        inverted.forEachIndexed { i, c -> c shouldBe (colors[i] xor 0x00ffffff) }

        val translucent = drawWith(ColorMatrixColorFilter(invert), intArrayOf(argb(102, 255, 51, 0)), 1)[0]
        (translucent ushr 24) shouldBe 102
        listOf((translucent shr 16) and 0xff to 0, (translucent shr 8) and 0xff to 204, translucent and 0xff to 255)
            .forEach { (actual, expected) -> (abs(actual - expected) <= 1).shouldBeTrue() }
    }

    @Test
    fun `the identity matrix and no filter leave pixels alone`() {
        val colors = TestImages.pattern(16, 9)
        drawWith(ColorMatrixColorFilter(ColorMatrix()), colors, 16) shouldBe colors
        drawWith(null, colors, 16) shouldBe colors
        ColorMatrix(invert).getArray().toList() shouldBe invert.toList()
    }
}
