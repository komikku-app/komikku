package android.text

import androidx.core.text.parseAsHtml
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HtmlTest {

    @Test
    fun `converts markup to plain text`() {
        "Tom &amp; Jerry &lt;3".parseAsHtml().toString() shouldBe "Tom & Jerry <3"
        "  a   <b>bold</b>\n text ".parseAsHtml().toString() shouldBe "a bold text "
        "line<br>next".parseAsHtml().toString() shouldBe "line\nnext"
        "<p>one</p><p>two</p>".parseAsHtml().toString() shouldBe "one\n\ntwo\n\n"
        "<p>one</p><p>two</p>".parseAsHtml(Html.FROM_HTML_MODE_COMPACT).toString() shouldBe "one\ntwo\n"
        "x<script>alert(1)</script>".parseAsHtml().toString() shouldBe "x"
        Html.escapeHtml("<a & \"é\">") shouldBe "&lt;a &amp; &quot;&#233;&quot;&gt;"
    }
}
