package eu.kanade.tachiyomi.source.comment.jinman

import eu.kanade.tachiyomi.source.comment.CommentHtml
import eu.kanade.tachiyomi.source.comment.SourceComment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.ZoneId

/**
 * Pure helpers for Jinman Tiantang (18comic mirrors) album comments. No network.
 * Markup differs slightly between mirrors, so selectors are candidate lists; the first hit wins.
 */
internal object JinmanCommentParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
    private val ALBUM_ID = Regex("""/album/(\d+)""")
    private val HTML_KEYS = listOf("message", "html", "data", "code", "content")

    private const val ROOT_SELECTOR = "div.timeline, div.comment-item, li.comment-item"
    private const val REPLY_CONTAINER_SELECTOR = ".timeline-reply, .reply-list, .comment-replies"

    // Priority-ordered candidates (not a comma group): document order would otherwise pick e.g.
    // the empty avatar link before the username, or a `timeline-*` wrapper before the date.
    private val USERNAME_SELECTORS = listOf(
        ".timeline-username", ".timeline-name", ".comment-username",
        "[class*=username]", "[class*=user-name]", "a[href*=/user/]",
    )
    private val DATE_SELECTORS = listOf(".timeline-date", ".comment-date", "time", "[class*=date]")
    private val BODY_SELECTORS = listOf(
        ".timeline-content-comment", ".timeline-content-text", ".comment-content", ".comment-text",
        "[class*=content-comment]", "[class*=comment-content]", "[class*=comment-text]",
    )
    private const val HEADER_SELECTOR =
        ".timeline-header, .timeline-left, .timeline-avatar, .timeline-date, .timeline-username, " +
            ".timeline-like, .timeline-reply-btn, .timeline-reply, .comment-header, .comment-actions, " +
            "button, script, style"
    /** Chrome that often sits inside / beside the body node (reply CTA, chapter index). */
    private const val BODY_CHROME_SELECTOR =
        ".timeline-reply-btn, .timeline-like, .timeline-footer, .comment-actions, .comment-reply, " +
            ".reply-btn, .timeline-chapter, .comment-chapter, button, " +
            "[class*=reply-btn], [class*=comic-index]"
    private const val SPOILER_SELECTOR = "[class*=spoiler], [data-spoiler], .text-spoiler"
    private val BODY_CHROME_LINES = setOf("回覆", "回复", "reply", "評論", "评论")

    /** `/album/{id}/`, `/album/{id}/{slug}` or absolute mirror URLs. */
    fun extractAlbumId(mangaUrl: String): String? =
        ALBUM_ID.find(mangaUrl)?.groupValues?.getOrNull(1)
            ?: mangaUrl.substringBefore('?').trim('/').substringAfterLast('/')
                .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    /**
     * Pagination endpoints return JSON wrapping an HTML fragment (key varies by mirror);
     * the album page itself is plain HTML. Returns the HTML either way.
     */
    fun extractHtml(body: String): String {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return trimmed
        val root = runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull() ?: return trimmed
        return HTML_KEYS.firstNotNullOfOrNull { key ->
            (root[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.contains('<') }
        } ?: ""
    }

    /** [page] only scopes fallback ids so LazyColumn keys stay unique across loaded pages. */
    fun parse(html: String, page: Int = 1): List<SourceComment> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val roots = doc.select(ROOT_SELECTOR).filter { element ->
            element.parents().none { it.`is`(ROOT_SELECTOR) }
        }
        return roots.mapIndexedNotNull { index, root ->
            parseComment(root, "p$page-root-$index", withReplies = true)
        }
    }

    fun parseDate(raw: String?): Long? = CommentHtml.parseLocalDate(raw, SHANGHAI)

    private fun parseComment(element: Element, fallbackId: String, withReplies: Boolean): SourceComment? {
        val replyNodes = if (withReplies) {
            element.select(ROOT_SELECTOR).filter { it !== element }
        } else {
            emptyList()
        }
        val own = element.clone().apply {
            select(ROOT_SELECTOR).filter { it !== this }.forEach(Element::remove)
            select(REPLY_CONTAINER_SELECTOR).remove()
        }
        val username = firstText(own, USERNAME_SELECTORS) ?: "anonymous"
        val date = firstMatch(own, DATE_SELECTORS)?.let { it.attr("datetime").ifBlank { it.text() } }
        val bodyElement = (firstMatch(own, BODY_SELECTORS)
            ?: own.clone().apply { select(HEADER_SELECTOR).remove() })
            .clone()
            .apply { select(BODY_CHROME_SELECTOR).remove() }
        val isSpoiler = bodyElement.selectFirst(SPOILER_SELECTOR) != null || own.hasAttr("data-spoiler")
        val body = scrubBodyChrome(CommentHtml.toPlainText(bodyElement))
        if (body.isBlank()) return null

        val id = listOf("data-id", "data-cid", "data-comment-id", "id")
            .firstNotNullOfOrNull { attr -> element.attr(attr).takeIf { it.isNotBlank() } }
            ?: own.selectFirst("[data-id], [data-cid]")?.let { it.attr("data-id").ifBlank { it.attr("data-cid") } }
            ?: fallbackId
        val replies = replyNodes
            .filter { reply -> reply.parents().none { it !== element && it.`is`(ROOT_SELECTOR) } }
            .mapIndexedNotNull { index, reply -> parseComment(reply, "$id-reply-$index", withReplies = false) }

        return SourceComment(
            id = id,
            username = username,
            body = body,
            dateEpochMillis = parseDate(date),
            isSpoiler = isSpoiler,
            replies = replies,
        )
    }

    /** First selector (in priority order) that yields an element with non-blank text. */
    private fun firstMatch(scope: Element, selectors: List<String>): Element? =
        selectors.firstNotNullOfOrNull { selector ->
            scope.select(selector).firstOrNull { it.text().isNotBlank() }
        }

    private fun firstText(scope: Element, selectors: List<String>): String? =
        firstMatch(scope, selectors)?.text()?.trim()?.takeIf { it.isNotEmpty() }

    /** Drops leftover reply CTAs and chapter index lines (e.g. `回覆`, `第1話 1`). */
    private fun scrubBodyChrome(text: String): String {
        val chapterIndex = Regex("""^第\s*\d+\s*[话話章]\s*\d*\s*$""")
        return text.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    line.lowercase() !in BODY_CHROME_LINES &&
                    !chapterIndex.matches(line)
            }
            .joinToString("\n")
            .trim()
    }
}
