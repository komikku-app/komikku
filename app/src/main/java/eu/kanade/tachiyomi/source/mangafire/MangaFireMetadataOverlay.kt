package eu.kanade.tachiyomi.source.mangafire

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** App-side MangaFire extras. Prefer extension [OkHttpClient] (VRF/CF) for [enrich]. */
data class MangaFireOverlayMetadata(
    val rating: Float? = null,
    val voteCount: Int? = null,
    val follows: Int? = null,
    val year: Int? = null,
    val contentRating: String? = null,
    val altTitles: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = rating == null && voteCount == null && follows == null &&
            year == null && contentRating.isNullOrBlank() && altTitles.isEmpty()
}

object MangaFireMetadataOverlay {

    private const val DEFAULT_TTL_MS = 15L * 60L * 1000L
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private data class CacheEntry(val metadata: MangaFireOverlayMetadata, val expiresAtMs: Long)
    private val cache = ConcurrentHashMap<Long, CacheEntry>()

    private val RATING_TEXT = Regex("""(\d+(?:\.\d+)?)\s*/\s*10(?:\s*\((\d+)\))?""", RegexOption.IGNORE_CASE)
    private val FOLLOWS_TEXT = Regex("""([\d.,]+\s*[KkMm]?)\s*follows?""", RegexOption.IGNORE_CASE)
    private val COMPACT_COUNT = Regex("""([\d.]+)\s*([KkMm])?""")
    private val YEAR_ONLY = Regex("""^(19|20)\d{2}$""")
    private val ALT_SPLIT = Regex("""\s*[·|;]\s*""")
    private val CONTENT_LABELS = listOf("safe", "suggestive", "erotica", "pornographic", "nsfw")

    fun extractHid(mangaUrl: String): String? {
        val last = mangaUrl.substringBefore('?').removeSuffix("/").substringAfterLast('/')
        if (last.isBlank()) return null
        return when {
            '.' in last -> last.substringAfterLast('.').ifBlank { null }
            '-' in last -> last.substringBefore('-').ifBlank { null }
            else -> last
        }
    }

    fun parseApiResponse(body: String): MangaFireOverlayMetadata? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return metadataFromApiObject(root["data"]?.jsonObject ?: root)
    }

    fun parseTitleHtml(html: String): MangaFireOverlayMetadata? {
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return null
        val box = doc.selectFirst("div.rating-box")
        val ratingMatch = RATING_TEXT.find(box?.text().orEmpty()) ?: RATING_TEXT.find(doc.text())
        val rating = box?.attr("data-score")?.toFloatOrNull()
            ?: ratingMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()
        val voteCount = ratingMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
        val follows = FOLLOWS_TEXT.find(doc.text())?.groupValues?.getOrNull(1)?.let(::parseCompactCount)
        val year = doc.select("span, a, div, li").asSequence()
            .map { it.ownText().trim() }
            .firstOrNull { it.matches(YEAR_ONLY) }
            ?.toIntOrNull()
        val contentRating = CONTENT_LABELS.firstOrNull { label ->
            doc.select("span, a, div, li").any { it.ownText().trim().equals(label, ignoreCase = true) }
        }?.replaceFirstChar { it.uppercase() }
        val altTitles = doc.select(".info > h6, .manga-detail .info h6")
            .flatMap { it.text().split(ALT_SPLIT) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return MangaFireOverlayMetadata(rating, voteCount, follows, year, contentRating, altTitles)
            .takeUnless { it.isEmpty }
    }

    /**
     * Soft-fail fetch with TTL cache keyed by [mangaId]. Prefer MangaFire HttpSource.client.
     */
    suspend fun enrich(
        mangaId: Long,
        mangaUrl: String,
        baseUrl: String,
        client: OkHttpClient,
        headers: Headers = headersOf("Accept", "application/json"),
        nowMs: Long = System.currentTimeMillis(),
        ttlMs: Long = DEFAULT_TTL_MS,
    ): MangaFireOverlayMetadata? {
        cache[mangaId]?.takeIf { it.expiresAtMs > nowMs }?.let { return it.metadata }
        val hid = extractHid(mangaUrl) ?: return null
        val root = baseUrl.trimEnd('/')

        val fromApi = softFetch {
            parseApiResponse(client.newCall(GET("$root/api/titles/$hid", headers)).awaitSuccess().body.string())
        }

        val needsHtml = fromApi == null ||
            (fromApi.rating == null && fromApi.follows == null && fromApi.contentRating == null)
        val fromHtml = if (!needsHtml) {
            null
        } else {
            softFetch {
                val htmlHeaders = headers.newBuilder().set("Accept", "text/html,application/xhtml+xml").build()
                val path = if (mangaUrl.startsWith("http")) mangaUrl else "$root$mangaUrl"
                parseTitleHtml(client.newCall(GET(path, htmlHeaders)).awaitSuccess().body.string())
            }
        }

        val merged = merge(fromApi, fromHtml) ?: return null
        cache[mangaId] = CacheEntry(merged, nowMs + ttlMs)
        return merged
    }

    fun clearCache() {
        cache.clear()
    }

    internal fun metadataFromApiObject(data: JsonObject): MangaFireOverlayMetadata {
        val rating = firstNumber(data, "score", "rating", "mal_score", "malScore", "ratedAvg")?.toFloat()
        val voteCount = firstInt(
            data,
            "score_count", "scoreCount", "rating_count", "ratingCount", "ratings_count", "votes", "ratedCount",
        )
        val follows = firstInt(
            data,
            "follows", "follows_total", "followsTotal", "bookmarks", "bookmark_count", "bookmarkCount",
        )
        val year = firstInt(data, "year")
        val contentRating = firstString(data, "content_rating", "contentRating")
            ?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
            ?: nsfwFlag(data)
        val altTitles = stringList(data["altTitles"] ?: data["alt_titles"])
        return MangaFireOverlayMetadata(rating, voteCount, follows, year, contentRating, altTitles)
    }

    internal fun parseCompactCount(raw: String): Int? {
        val match = COMPACT_COUNT.find(raw.trim()) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        return when (match.groupValues[2].uppercase()) {
            "K" -> (number * 1_000).toInt()
            "M" -> (number * 1_000_000).toInt()
            else -> number.toInt()
        }
    }

    private suspend fun <T> softFetch(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun merge(a: MangaFireOverlayMetadata?, b: MangaFireOverlayMetadata?): MangaFireOverlayMetadata? {
        if (a == null) return b
        if (b == null) return a.takeUnless { it.isEmpty }
        return MangaFireOverlayMetadata(
            rating = a.rating ?: b.rating,
            voteCount = a.voteCount ?: b.voteCount,
            follows = a.follows ?: b.follows,
            year = a.year ?: b.year,
            contentRating = a.contentRating ?: b.contentRating,
            altTitles = a.altTitles.ifEmpty { b.altTitles },
        ).takeUnless { it.isEmpty }
    }

    private fun nsfwFlag(data: JsonObject): String? {
        val nsfw = listOf("is_nsfw", "isNsfw", "nsfw").any { key ->
            data[key]?.asPrimitive()?.booleanOrNull == true ||
                data[key]?.asPrimitive()?.contentOrNull.equals("true", ignoreCase = true)
        }
        return if (nsfw) "Erotica" else null
    }

    private fun firstNumber(obj: JsonObject, vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { obj[it]?.asPrimitive()?.doubleOrNull }

    private fun firstInt(obj: JsonObject, vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            val p = obj[key]?.asPrimitive() ?: return@firstNotNullOfOrNull null
            p.intOrNull ?: p.doubleOrNull?.toInt() ?: p.contentOrNull?.let(::parseCompactCount)
        }

    private fun firstString(obj: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { obj[it]?.asPrimitive()?.contentOrNull }

    private fun JsonElement.asPrimitive(): JsonPrimitive? = this as? JsonPrimitive

    private fun stringList(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.isNotBlank() }
        is JsonPrimitive -> element.contentOrNull?.split(ALT_SPLIT)?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        else -> emptyList()
    }
}
