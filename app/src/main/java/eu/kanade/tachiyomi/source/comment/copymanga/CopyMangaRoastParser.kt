package eu.kanade.tachiyomi.source.comment.copymanga

import eu.kanade.tachiyomi.source.comment.CommentHtml
import eu.kanade.tachiyomi.source.comment.SourceComment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.ZoneId

/** Parsed `GET /api/v3/roasts` page. */
internal data class RoastPage(
    val comments: List<SourceComment>,
    val total: Int?,
    val limit: Int?,
    val offset: Int?,
)

/** Latest chapter reference used to scope CopyManga comments. */
internal data class CopyMangaChapterRef(val uuid: String, val name: String?)

/**
 * Pure parsing / URL helpers for CopyManga "roasts" (chapter comments). No network.
 * Tree-based JSON parsing keeps it tolerant to id type / key drift between API versions.
 */
internal object CopyMangaRoastParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
    private val UUID_REGEX =
        Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")

    /** Prefer known-good hosts first; derived extension host is tried after. */
    private val FALLBACK_API_HOSTS = listOf("https://api.mangacopy.com", "https://api.2025copy.com")

    fun extractChapterUuid(chapterUrl: String?): String? =
        chapterUrl?.let { UUID_REGEX.find(it)?.value?.lowercase() }

    /** `manga.url` is `/comic/{path_word}`; returns the path word. */
    fun extractPathWord(mangaUrl: String): String? =
        mangaUrl.substringBefore('?').trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }

    fun apiHosts(baseUrl: String?): List<String> {
        val derived = baseUrl?.trim()?.trimEnd('/')
            ?.takeIf { it.startsWith("http") }
            ?.replaceFirst("://www.", "://api.")
            ?.takeIf { it.contains("://api.") }
        // Known hosts first so a flaky derived mirror does not win on a bad body
        return (FALLBACK_API_HOSTS + listOfNotNull(derived)).distinct()
    }

    /** Throws [IllegalStateException] with the API message when `code != 200`. */
    fun parse(body: String): RoastPage {
        val root = parseObjectRoot(body)
        checkCode(root)
        val results = root["results"]?.asObjectOrNull()
            ?: return RoastPage(emptyList(), total = 0, limit = null, offset = null)
        val list = results["list"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
        val comments = list.mapNotNull { element -> element.asObjectOrNull()?.let(::parseRoast) }
        return RoastPage(
            comments = comments,
            total = results.int("total"),
            limit = results.int("limit"),
            offset = results.int("offset"),
        )
    }

    /** `GET /api/v3/comic2/{path_word}` → `results.comic.last_chapter`. */
    fun parseLastChapter(body: String): CopyMangaChapterRef? {
        val root = parseObjectRoot(body)
        checkCode(root)
        val last = root["results"]?.asObjectOrNull()
            ?.get("comic")?.asObjectOrNull()
            ?.get("last_chapter")?.asObjectOrNull()
            ?: return null
        val uuid = last.string("uuid")?.let(::extractChapterUuid) ?: return null
        return CopyMangaChapterRef(uuid = uuid, name = last.string("name")?.takeIf { it.isNotBlank() })
    }

    fun parseDate(raw: String?): Long? = CommentHtml.parseLocalDate(raw, SHANGHAI)

    /**
     * Accepts a JSON object, or a JSON string whose content is the object
     * (extension decrypt can leave a string root).
     */
    internal fun parseObjectRoot(body: String): JsonObject {
        val element = runCatching { json.parseToJsonElement(body.trim()) }.getOrElse {
            throw IllegalStateException("CopyManga response was not JSON", it)
        }
        return when (element) {
            is JsonObject -> element
            is JsonPrimitive -> {
                val inner = element.contentOrNull?.trim().orEmpty()
                if (inner.startsWith("{")) {
                    runCatching { json.parseToJsonElement(inner).jsonObject }.getOrElse {
                        throw IllegalStateException("CopyManga response string was not a JSON object", it)
                    }
                } else {
                    throw IllegalStateException("CopyManga response was a JSON string, not an object")
                }
            }
            else -> throw IllegalStateException("CopyManga response was not a JSON object")
        }
    }

    private fun parseRoast(obj: JsonObject): SourceComment? {
        val id = obj.string("id") ?: return null
        val body = obj.string("comment")?.trim().orEmpty()
        if (body.isEmpty()) return null
        return SourceComment(
            id = id,
            username = obj.string("user_name")?.takeIf { it.isNotBlank() } ?: "anonymous",
            body = body,
            dateEpochMillis = parseDate(obj.string("create_at")),
        )
    }

    private fun checkCode(root: JsonObject) {
        val code = root.int("code") ?: return
        if (code != 200) {
            val message = root.string("message")?.takeIf { it.isNotBlank() } ?: "CopyManga API error $code"
            throw IllegalStateException(message)
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
    }
}
