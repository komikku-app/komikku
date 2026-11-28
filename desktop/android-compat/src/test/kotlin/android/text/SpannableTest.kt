package android.text

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SpannableTest {

    @Test
    fun `spans are accepted and dropped, the text is kept`() {
        val string = SpannableString("Chapter 12")
        string.setSpan(Any(), 0, 7, 33)
        string.removeSpan(Any())

        string.toString() shouldBe "Chapter 12"
        string.subSequence(8, 10).toString() shouldBe "12"
        SpannableString.valueOf(string) shouldBe string

        val builder: Spannable = SpannableStringBuilder("Vol. ").append("3")
        builder.setSpan(Any(), 0, 4, 33)
        builder.toString() shouldBe "Vol. 3"
    }
}
