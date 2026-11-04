package eu.kanade.tachiyomi.source.comment

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Small HTML / date helpers shared by scrape-based comment parsers. */
internal object CommentHtml {

    private val MULTI_BLANK = Regex("\n{3,}")
    private val BLOCK_TAGS = "p, div, blockquote, li, h1, h2, h3, h4, h5, h6, tr, pre"
    private val OFFSET_NO_COLON = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)
    private val LOCAL_PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ROOT) }
    private val DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val SLASH_DATE_ONLY = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)

    /** Converts an HTML fragment to readable plain text, keeping line breaks between blocks. */
    fun toPlainText(element: Element): String {
        val copy = element.clone()
        copy.select("br").forEach { it.replaceWith(TextNode("\n")) }
        copy.select(BLOCK_TAGS).forEach { it.appendChild(TextNode("\n")) }
        return copy.wholeText()
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(MULTI_BLANK, "\n\n")
            .trim()
    }

    /** Parses ISO-8601 offsets, with or without a colon in the zone (XenForo emits `+0000`). */
    fun parseOffsetDate(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value, OFFSET_NO_COLON).toInstant().toEpochMilli() }.getOrNull()
    }

    /** Parses a zone-less local timestamp (e.g. `2025-07-26 14:07:17`) in [zone]. */
    fun parseLocalDate(raw: String?, zone: ZoneId): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        LOCAL_PATTERNS.forEach { pattern ->
            runCatching { LocalDateTime.parse(value, pattern) }.getOrNull()
                ?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        }
        return listOf(DATE_ONLY, SLASH_DATE_ONLY).firstNotNullOfOrNull { pattern ->
            runCatching { LocalDate.parse(value, pattern) }.getOrNull()
        }?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    }
}
