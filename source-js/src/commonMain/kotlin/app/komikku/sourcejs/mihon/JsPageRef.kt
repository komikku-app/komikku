package app.komikku.sourcejs.mihon

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A page image of a JavaScript source as the adapter stores it in Mihon's `Page.url`: the image URL and the extra
 * request headers the source attached to the page (contract 3.3). `Page.url` is kept by everything that handles pages
 * (the chapter cache's page lists, the downloader, the data saver, the reader's retry), so the headers reach the image
 * request wherever the page goes, and the image URL can be recovered when `Page.imageUrl` was cleared.
 */
@Serializable
data class JsPageRef(val url: String, val headers: Map<String, String> = emptyMap()) {

    /** The `Page.url` text for this page. */
    fun encode(): String = PREFIX + json.encodeToString(serializer(), this)

    companion object {
        /** Marks a `Page.url` written by [encode]; anything else is not a JavaScript source page. */
        const val PREFIX = "mangayomi-page:"

        private val json = Json { ignoreUnknownKeys = true }

        /** The page encoded in [pageUrl] by [encode], or `null` when it is not one. */
        fun decode(pageUrl: String?): JsPageRef? {
            if (pageUrl == null || !pageUrl.startsWith(PREFIX)) return null
            return try {
                json.decodeFromString(serializer(), pageUrl.substring(PREFIX.length))
            } catch (_: SerializationException) {
                // Also covers malformed JSON (a SerializationException too).
                null
            }
        }
    }
}
