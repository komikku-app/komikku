package app.komikku.sourcejs.bridge

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.DataNode
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.select.Collector
import com.fleeksoft.ksoup.select.Evaluator
import com.fleeksoft.ksoup.select.QueryParser

/**
 * `Document` / `Element` (contract 4.3) on Ksoup, identical on every platform.
 *
 * Nodes live in a per-call handle table; `-1` is the empty element that every failed lookup returns (strings "",
 * lists empty), so scripts never see `null`. Mangayomi quirks kept on purpose:
 * - `text` is DOM `textContent`: all descendant text, not whitespace-normalised, including `<script>` / `<style>`
 *   data (jsoup's `text()` normalises and skips script data);
 * - `:contains(x)` also matches script / style data (rewritten to `:is(:contains(x), :containsData(x))`);
 * - an invalid selector gives an empty result instead of an error;
 * - `getSrc` / `getImg` / `getHref` / `getDataSrc` are the first match of Mangayomi's regex `src="([^"]+)"` etc. in
 *   the serialised outer HTML (so `getSrc` also matches `data-src`, empty values are skipped, newlines are part of
 *   the value, and values stay HTML-escaped).
 * Deliberate differences: `hasAttr` works (always `false` in Mangayomi, contract 4.3), and an element's `select`
 * matches descendants only, like the DOM's `querySelectorAll` (jsoup would include the element itself).
 */
internal class DomBridge {
    private val nodes = ArrayList<Element>()

    /** Parsed CSS queries of this context (`null`: invalid); sources repeat the same few selectors per item. */
    private val queries = HashMap<String, Evaluator?>()

    fun parse(html: String): Int {
        val document = Ksoup.parse(html)
        document.outputSettings().prettyPrint(false)
        return store(document)
    }

    fun element(key: Int, type: String): Int {
        val node = node(key) ?: return EMPTY
        val found = when (type) {
            "body" -> (node as? Document)?.body()
            "head" -> (node as? Document)?.head()
            "documentElement" -> (node as? Document)?.firstElementChild()
            "parent" -> if (node is Document) null else node.parent()
            "previousElementSibling" -> node.previousElementSibling()
            "nextElementSibling" -> node.nextElementSibling()
            else -> null
        }
        return store(found)
    }

    fun string(key: Int, type: String): String {
        val node = node(key) ?: return ""
        return when (type) {
            "text" -> textContent(node)
            "innerHtml" -> node.html()
            "outerHtml" -> node.outerHtml()
            "className" -> node.className()
            "localName" -> if (node is Document) "" else node.normalName()
            "namespaceUri" -> if (node is Document) "" else node.tag().namespace()
            "getSrc" -> firstMatch(SRC, node.outerHtml())
            "getImg" -> firstMatch(IMG, node.outerHtml())
            "getHref" -> firstMatch(HREF, node.outerHtml())
            "getDataSrc" -> firstMatch(DATA_SRC, node.outerHtml())
            else -> ""
        }
    }

    fun attr(key: Int, name: String): String {
        val node = node(key) ?: return ""
        return if (node is Document) "" else node.attr(name)
    }

    fun hasAttr(key: Int, name: String): Boolean {
        val node = node(key) ?: return false
        return node !is Document && node.hasAttr(name)
    }

    fun select(key: Int, css: String): List<Int> {
        val node = node(key) ?: return emptyList()
        val evaluator = evaluator(css) ?: return emptyList()
        return try {
            node.select(evaluator).filter { it !== node }.map(::store)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** The first match in document order, stopping there; the node itself is skipped like in [select]. */
    fun selectFirst(key: Int, css: String): Int {
        val node = node(key) ?: return EMPTY
        val evaluator = evaluator(css) ?: return EMPTY
        val found = try {
            Collector.stream(evaluator, node).firstOrNull { it !== node }
        } catch (e: Exception) {
            null
        } finally {
            evaluator.reset()
        }
        return store(found)
    }

    fun list(key: Int, type: String, name: String): List<Int> {
        val node = node(key) ?: return emptyList()
        val found: List<Element> = when (type) {
            "children" -> node.children()
            "tag", "getElementsByTagName" -> node.getElementsByTag(name).filter { it !== node }
            "class", "getElementsByClassName" -> node.getElementsByClass(name).filter { it !== node }
            else -> emptyList()
        }
        return found.map(::store)
    }

    fun byId(key: Int, id: String): Int = store(node(key)?.getElementById(id))

    private fun node(key: Int): Element? = nodes.getOrNull(key)

    private fun store(element: Element?): Int {
        if (element == null) return EMPTY
        nodes.add(element)
        return nodes.lastIndex
    }

    /** The parsed query, `null` for an invalid selector (Mangayomi then returns no match). */
    private fun evaluator(css: String): Evaluator? {
        if (queries.containsKey(css)) return queries[css]
        val parsed = try {
            QueryParser.parse(rewriteContains(css))
        } catch (e: Exception) {
            null
        }
        queries[css] = parsed
        return parsed
    }

    companion object {
        const val EMPTY = -1

        // Mangayomi's patterns (`reg_exp_matcher.dart`): non-empty values, newlines included.
        private val SRC = Regex("src=\"([^\"]+)\"")
        private val IMG = Regex("img=\"([^\"]+)\"")
        private val HREF = Regex("href=\"([^\"]+)\"")
        private val DATA_SRC = Regex("data-src=\"([^\"]+)\"")

        private fun firstMatch(regex: Regex, text: String): String = regex.find(text)?.groupValues?.get(1).orEmpty()

        /** DOM `textContent`: text and data (script / style) of all descendants, as written. */
        fun textContent(element: Element): String {
            val builder = StringBuilder()
            element.traverse { node, _ ->
                when (node) {
                    is TextNode -> builder.append(node.getWholeText())
                    is DataNode -> builder.append(node.getWholeData())
                }
            }
            return builder.toString()
        }

        /**
         * Rewrites each `:contains(x)` (not `:containsOwn`, `:containsData`, …) to `:is(:contains(x),:containsData(x))`
         * so it also matches script and style data, like Mangayomi's `textContent`-based `:contains`. Matching stays
         * case-insensitive; jsoup normalises whitespace in the text part, Mangayomi does not (edge case, accepted).
         */
        fun rewriteContains(css: String): String {
            if (!css.contains(":contains(")) return css
            val out = StringBuilder()
            var i = 0
            while (i < css.length) {
                if (css.startsWith(":contains(", i)) {
                    val argStart = i + ":contains(".length
                    val argEnd = closingParen(css, argStart) ?: return css
                    // Quotes around the text are accepted (CSS style); jsoup would search for them literally.
                    val arg = css.substring(argStart, argEnd).trim().let {
                        val quoted = it.length >= 2 && (it[0] == '"' || it[0] == '\'') && it.last() == it[0]
                        if (quoted && it.none { c -> c == '(' || c == ')' }) it.substring(1, it.length - 1) else it
                    }
                    out.append(":is(:contains(").append(arg).append("),:containsData(").append(arg).append("))")
                    i = argEnd + 1
                } else {
                    out.append(css[i])
                    i++
                }
            }
            return out.toString()
        }

        /** Index of the `)` closing the parenthesis opened before [start], honouring quotes and escapes. */
        private fun closingParen(text: String, start: Int): Int? {
            var depth = 0
            var quote: Char? = null
            var i = start
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '\\' -> i++
                    quote != null -> if (c == quote) quote = null
                    c == '"' || c == '\'' -> quote = c
                    c == '(' -> depth++
                    c == ')' -> if (depth == 0) return i else depth--
                }
                i++
            }
            return null
        }
    }
}
