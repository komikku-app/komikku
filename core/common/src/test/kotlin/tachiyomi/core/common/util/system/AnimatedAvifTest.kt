package tachiyomi.core.common.util.system

import io.kotest.matchers.shouldBe
import okio.Buffer
import org.junit.jupiter.api.Test

class AnimatedAvifTest {

    @Test
    fun `avis major brand is animated`() {
        AnimatedAvif.isAnimated(avif("avis", "avif", "mif1")).shouldBe(true)
    }

    @Test
    fun `avis compatible brand is animated`() {
        AnimatedAvif.isAnimated(avif("avif", "mif1", "avis")).shouldBe(true)
    }

    @Test
    fun `still avif is not animated`() {
        AnimatedAvif.isAnimated(avif("avif", "mif1", "miaf", "MA1B")).shouldBe(false)
    }

    @Test
    fun `non avif data is not animated`() {
        AnimatedAvif.isAnimated(Buffer().writeUtf8("GIF89a")).shouldBe(false)
    }

    @Test
    fun `truncated ftyp is not animated`() {
        AnimatedAvif.isAnimated(Buffer().writeInt(64).writeUtf8("ftyp").writeUtf8("avis")).shouldBe(false)
    }

    @Test
    fun `source is not consumed`() {
        val source = avif("avis", "avif")
        val size = source.size

        AnimatedAvif.isAnimated(source).shouldBe(true)

        source.size.shouldBe(size)
    }

    private fun avif(majorBrand: String, vararg compatibleBrands: String): Buffer {
        val boxSize = 8 + 4 + 4 + compatibleBrands.size * 4
        return Buffer()
            .writeInt(boxSize)
            .writeUtf8("ftyp")
            .writeUtf8(majorBrand)
            .writeInt(0)
            .apply { compatibleBrands.forEach { writeUtf8(it) } }
    }
}
