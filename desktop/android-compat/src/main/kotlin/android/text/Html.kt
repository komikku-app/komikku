package android.text

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * android-compat: `android.text.Html`. [fromHtml] converts markup to plain text the way Android
 * lays it out in `FROM_HTML_MODE_LEGACY`: entities decoded, whitespace collapsed, `<br>` → line
 * break, block elements separated by a blank line. Styling spans are not produced.
 */
class Html private constructor() {

    companion object {
        const val FROM_HTML_MODE_LEGACY = 0x00000000
        const val FROM_HTML_MODE_COMPACT = 0x0000003f
        const val FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH = 0x00000001
        const val FROM_HTML_SEPARATOR_LINE_BREAK_HEADING = 0x00000002
        const val FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM = 0x00000004
        const val FROM_HTML_SEPARATOR_LINE_BREAK_LIST = 0x00000008
        const val FROM_HTML_SEPARATOR_LINE_BREAK_DIV = 0x00000010
        const val FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE = 0x00000020
        const val FROM_HTML_OPTION_USE_CSS_COLORS = 0x00000100
        const val TO_HTML_PARAGRAPH_LINES_CONSECUTIVE = 0x00000000
        const val TO_HTML_PARAGRAPH_LINES_INDIVIDUAL = 0x00000001

        @Deprecated("Use fromHtml(String, Int) instead.")
        @JvmStatic
        fun fromHtml(source: String?): Spanned = fromHtml(source, FROM_HTML_MODE_LEGACY)

        @JvmStatic
        fun fromHtml(source: String?, flags: Int): Spanned = PlainSpanned(HtmlToText(flags).convert(source.orEmpty()))

        @JvmStatic
        fun escapeHtml(text: CharSequence): String = buildString {
            text.forEach { c ->
                when (c) {
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> if (c.code > 0x7e || c.code < ' '.code) append("&#${c.code};") else append(c)
                }
            }
        }
    }
}

private class PlainSpanned(private val text: String) : Spanned, CharSequence by text {
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is PlainSpanned && other.text == text
    override fun hashCode(): Int = text.hashCode()
}

private class HtmlToText(flags: Int) {

    private val out = StringBuilder()

    // In compact mode a single line break separates blocks whose separator flag is set.
    private val compact = flags and Html.FROM_HTML_MODE_COMPACT != 0

    fun convert(html: String): String {
        appendChildren(Jsoup.parseBodyFragment(html).body())
        return out.toString()
    }

    private fun appendChildren(element: Element) {
        element.childNodes().forEach(::append)
    }

    private fun append(node: Node) {
        when (node) {
            is TextNode -> appendText(node.wholeText)
            is Element -> when (val tag = node.normalName()) {
                "br" -> out.append('\n')
                "script", "style", "head", "title" -> Unit
                in BLOCK_TAGS -> {
                    blockBoundary()
                    appendChildren(node)
                    blockBoundary()
                }
                "li" -> {
                    lineBoundary()
                    appendChildren(node)
                    lineBoundary()
                }
                else -> if (tag == "img") out.append('￼') else appendChildren(node)
            }
            else -> Unit
        }
    }

    // Whitespace collapses to one space, and is dropped at the start of a line.
    private fun appendText(text: String) {
        text.forEach { c ->
            if (c == ' ' || c == '\n' || c == '\t' || c == '\r' || c == '\u000c') {
                val previous = out.lastOrNull()
                if (previous != null && previous != ' ' && previous != '\n') out.append(' ')
            } else {
                out.append(c)
            }
        }
    }

    private fun blockBoundary() {
        if (out.isEmpty()) return
        val wanted = if (compact) 1 else 2
        val existing = out.takeLastWhile { it == '\n' }.length
        repeat(wanted - existing) { out.append('\n') }
    }

    private fun lineBoundary() {
        if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
    }

    companion object {
        val BLOCK_TAGS = setOf(
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "ul", "ol", "pre", "table", "tr",
        )
    }
}
