package eu.kanade.tachiyomi.source.comment.mangadex

import eu.kanade.tachiyomi.source.comment.CommentHtml
import eu.kanade.tachiyomi.source.comment.SourceComment
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** One XenForo thread page in forum order (oldest post first within the page). */
internal data class ForumThreadPage(
    val posts: List<SourceComment>,
    val currentPage: Int,
    val totalPages: Int,
)

/**
 * Parses `forums.mangadex.org/threads/{id}/page-{n}` (XenForo 2) into [SourceComment]s.
 * Empty posts are skipped (covers the auto-generated bot OP when it has no body).
 */
internal object MangaDexForumParser {

    private val PAGE_OF = Regex("""of\s+(\d+)""", RegexOption.IGNORE_CASE)
    private const val NOISE_SELECTOR =
        ".bbCodeSpoiler-button, .bbCodeBlock-expandLink, .bbCodeBlock-title, script, style, .js-expandLink"

    /** True when the body is MangaDex edge challenge / bot interstitial instead of a thread. */
    fun looksLikeChallenge(html: String): Boolean {
        val lower = html.lowercase()
        return "edge/challenge" in lower ||
            "bot challenge" in lower ||
            "redirecting to bot challenge" in lower ||
            ("challenge-assets" in lower && "message--post" !in lower)
    }

    fun parseThreadPage(html: String): ForumThreadPage {
        val doc = Jsoup.parse(html)
        val posts = doc.select("article.message--post, article.message[data-author]")
            .distinct()
            .mapNotNull(::parsePost)
        return ForumThreadPage(
            posts = posts,
            currentPage = doc.selectFirst(".pageNav-page--current")?.text()?.trim()?.toIntOrNull() ?: 1,
            totalPages = parseTotalPages(doc),
        )
    }

    private fun parsePost(article: Element): SourceComment? {
        val id = article.attr("data-content").removePrefix("post-")
            .ifBlank { article.id().removePrefix("js-post-") }
            .ifBlank { return null }
        val author = article.attr("data-author").trim()
            .ifBlank { article.selectFirst(".message-name, .username")?.text()?.trim().orEmpty() }
            .ifBlank { "unknown" }
        val bodyElement = article.selectFirst(".message-body .bbWrapper")
            ?: article.selectFirst(".message-body")
            ?: return null
        val isSpoiler = bodyElement.selectFirst(".bbCodeSpoiler, .bbCodeInlineSpoiler") != null
        val cleaned = bodyElement.clone().apply { select(NOISE_SELECTOR).remove() }
        val body = CommentHtml.toPlainText(cleaned)
        if (body.isBlank()) return null
        val time = article.selectFirst("time.u-dt, time[datetime]")
        val date = time?.attr("data-time")?.toLongOrNull()?.times(1000)
            ?: CommentHtml.parseOffsetDate(time?.attr("datetime"))
        return SourceComment(
            id = id,
            username = author,
            body = body,
            dateEpochMillis = date,
            isSpoiler = isSpoiler,
        )
    }

    private fun parseTotalPages(doc: Document): Int {
        val fromNav = doc.select(".pageNav-page").mapNotNull { it.text().trim().toIntOrNull() }.maxOrNull()
        if (fromNav != null) return fromNav.coerceAtLeast(1)
        val simple = doc.selectFirst(".pageNavSimple-el--current")?.text().orEmpty()
        return PAGE_OF.find(simple)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    }
}
