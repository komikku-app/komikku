package app.komikku.sourcejs.model

import kotlinx.serialization.Serializable

/** Result of `getPopular`, `getLatestUpdates` and `search` (contract 3). */
@Serializable
data class JsMangaPage(
    val list: List<JsManga>,
    /** Missing in the result → `false` (Mangayomi's decoder would fail instead). */
    val hasNextPage: Boolean,
)

/**
 * A manga as list item or detail (contract 3.1). Optional texts are `null` when the source left them out or empty
 * (Mangayomi then keeps the stored value); [status] is [JsMangaStatus.UNKNOWN] in the same case.
 *
 * @property link opaque key the source understands (absolute URL, path or id), passed back to `getDetail`.
 * @property imageUrl cover as the source gave it; relative values are resolved against `baseUrl` by the adapter.
 * @property genre trimmed, non-blank, de-duplicated.
 * @property chapters detail only (`episodes` accepted as an alias), newest first as the source returned them.
 */
@Serializable
data class JsManga(
    val name: String = "",
    val link: String = "",
    val imageUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: JsMangaStatus = JsMangaStatus.UNKNOWN,
    val genre: List<String> = emptyList(),
    val chapters: List<JsChapter> = emptyList(),
)

/** Status codes of contract 3.1; anything other than 0–4 is [UNKNOWN] (sources use 5). */
@Serializable
enum class JsMangaStatus(val code: Int) {
    ONGOING(0),
    COMPLETED(1),
    ON_HIATUS(2),
    CANCELLED(3),
    PUBLISHING_FINISHED(4),
    UNKNOWN(5),
    ;

    companion object {
        fun fromCode(code: Int?): JsMangaStatus = entries.firstOrNull { it.code == code && it != UNKNOWN } ?: UNKNOWN
    }
}

/**
 * A chapter (contract 3.2). Entries with an empty [url] are dropped by the parser.
 *
 * @property dateUpload milliseconds since the epoch; `null` when the source sent `null`, nothing, `"NaN"` or an
 *   unparsable value (Mangayomi shows "now" for `null`).
 */
@Serializable
data class JsChapter(
    val name: String,
    val url: String,
    val dateUpload: Long? = null,
    val scanlator: String? = null,
)

/** A page image (contract 3.3) with the extra request headers the source attached to it. */
@Serializable
data class JsPage(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * The sync members of a source, read in one context by `JsExtension.metadata()` with the same defaults as the single
 * calls; the source adapter caches it.
 *
 * @property headers `getHeaders(baseUrl)`.
 */
@Serializable
data class JsSourceMetadata(
    val supportsLatest: Boolean,
    val filters: List<JsFilter>,
    val preferences: List<JsPreference>,
    val headers: Map<String, String>,
)
