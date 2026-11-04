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
        listOf(DATE_ONLY, SLASH_DATE_ONLY).firstNotNullOfOrNull { pattern ->
            runCatching { LocalDate.parse(value, pattern) }.getOrNull()
        }?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.let { return it }

        // Unpadded `2024-5-1` / `2024/5/1`
        UNPADDED_DATE.find(value)?.let { match ->
            val y = match.groupValues[1].toIntOrNull() ?: return@let
            val m = match.groupValues[2].toIntOrNull() ?: return@let
            val d = match.groupValues[3].toIntOrNull() ?: return@let
            return runCatching {
                LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }
        return null
    }

    /**
     * Best-effort date parse: unix seconds/millis, absolute local/offset, then relative
     * Chinese/English phrases used by Jinman mirrors (`3天前`, `5分鐘前`, …).
     */
    fun parseFlexibleDate(
        raw: String?,
        zone: ZoneId,
        nowMs: Long = System.currentTimeMillis(),
    ): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        parseUnixEpoch(value)?.let { return it }
        parseOffsetDate(value)?.let { return it }
        parseLocalDate(value, zone)?.let { return it }
        return parseRelativeDate(value, nowMs)
    }

    /** Digits-only unix seconds (10) or millis (13). */
    fun parseUnixEpoch(raw: String): Long? {
        if (!raw.all { it.isDigit() }) return null
        return when (raw.length) {
            10 -> raw.toLongOrNull()?.times(1000)
            13 -> raw.toLongOrNull()
            else -> null
        }
    }

    /**
     * Relative phrases: 剛剛 / N秒前 / N分鐘前 / N小時前 / N天前 / N週前 / N個月前 / N年前
     * (simplified + traditional), plus common English forms.
     */
    fun parseRelativeDate(raw: String, nowMs: Long = System.currentTimeMillis()): Long? {
        val value = raw.trim()
        if (value in JUST_NOW) return nowMs
        RELATIVE.find(value)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return null
            val unit = match.groupValues[2]
            val deltaMs = when {
                unit.startsWith("秒") || unit.startsWith("sec") -> amount * 1_000L
                unit.startsWith("分") || unit.startsWith("min") -> amount * 60_000L
                unit.startsWith("時") || unit.startsWith("时") || unit.startsWith("hour") ->
                    amount * 3_600_000L
                unit.startsWith("天") || unit.startsWith("day") -> amount * 86_400_000L
                unit.startsWith("週") || unit.startsWith("周") || unit.startsWith("week") ->
                    amount * 604_800_000L
                unit.startsWith("個") || unit.startsWith("个") || unit.startsWith("月") ||
                    unit.startsWith("month") -> amount * 2_592_000_000L // 30d
                unit.startsWith("年") || unit.startsWith("year") -> amount * 31_536_000_000L
                else -> return null
            }
            return nowMs - deltaMs
        }
        return null
    }

    private val UNPADDED_DATE = Regex("""^((?:19|20)\d{2})[-/](\d{1,2})[-/](\d{1,2})$""")
    private val JUST_NOW = setOf("剛剛", "刚刚", "剛才", "刚才", "just now")
    private val RELATIVE = Regex(
        "^(\\d+)\\s*(" +
            "秒前|分鐘前|分钟前|小時前|小时前|天前|週前|周前|個月前|个月前|月前|年前|" +
            "seconds?\\s*ago|mins?\\s*ago|minutes?\\s*ago|hours?\\s*ago|days?\\s*ago|" +
            "weeks?\\s*ago|months?\\s*ago|years?\\s*ago" +
            ")$",
        RegexOption.IGNORE_CASE,
    )
}
