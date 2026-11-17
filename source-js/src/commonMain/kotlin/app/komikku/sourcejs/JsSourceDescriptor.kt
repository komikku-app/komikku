package app.komikku.sourcejs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One entry of a Mangayomi index (contract 1.2), i.e. one source. A file can serve several entries (one per
 * language); each entry is its own source with its own [id] and preferences.
 *
 * Decodable straight from the legacy JSON index with `ignoreUnknownKeys`; every field has a default so partial
 * entries decode too.
 */
@Serializable
data class JsSourceDescriptor(
    /** Source identity from the index; never recomputed (contract 1.4). */
    val id: Long,
    val name: String,
    val lang: String = "",
    val baseUrl: String = "",
    val apiUrl: String = "",
    val iconUrl: String = "",
    val typeSource: String = "",
    val itemType: Int = 0,
    val sourceCodeLanguage: Int = 1,
    val sourceCodeUrl: String = "",
    val version: String = "",
    val appMinVerReq: String = "",
    val isNsfw: Boolean = false,
    val hasCloudflare: Boolean = false,
    val isFullData: Boolean = false,
    val dateFormat: String = "",
    val dateFormatLocale: String = "",
    val additionalParams: String = "",
    val notes: String = "",
) {
    /**
     * The object extensions see as `this.source` (contract 2.2 step 3). `notes` is always `null` at runtime in
     * Mangayomi, so it is here too.
     */
    fun toJsSourceObject(): JsonObject = buildJsonObject {
        put("apiUrl", apiUrl)
        put("baseUrl", baseUrl)
        put("dateFormat", dateFormat)
        put("dateFormatLocale", dateFormatLocale)
        put("hasCloudflare", hasCloudflare)
        put("id", id)
        put("isFullData", isFullData)
        put("lang", lang)
        put("name", name)
        put("additionalParams", additionalParams)
        put("notes", JsonPrimitive(null as String?))
    }

    /** File name used in JS stack traces. */
    internal val scriptFileName: String
        get() = sourceCodeUrl.substringAfterLast('/').substringBefore('?').ifBlank { "extension.js" }
}
