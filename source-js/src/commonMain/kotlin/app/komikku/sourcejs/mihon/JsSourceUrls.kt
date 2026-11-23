package app.komikku.sourcejs.mihon

/**
 * URL rules of the source adapter. Mangayomi sources return covers, links and pages as absolute URLs, paths or
 * opaque ids (contract 3.1); Komikku needs absolute URLs for covers, page images and the WebView.
 */
object JsSourceUrls {

    /** [baseUrl] without surrounding blanks and trailing slashes (Mihon's `HttpSource.baseUrl` contract). */
    fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')

    /**
     * An absolute URL for the cover or page image [url] of a source whose base URL is [baseUrl]:
     * - blank → `null`;
     * - a URL with a scheme (`https://…`, `data:…`) → unchanged;
     * - protocol-relative (`//host/x`) → with the scheme of [baseUrl] (`https` when it has none);
     * - anything else is a path: `baseUrl + "/" + path` (leading slashes removed); `null` when [baseUrl] is blank.
     */
    fun resolveCoverUrl(baseUrl: String, url: String?): String? {
        val value = url?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.startsWith("//")) return "${schemeOf(baseUrl)}:$value"
        if (hasScheme(value)) return value
        val base = normalizeBaseUrl(baseUrl)
        if (base.isEmpty()) return null
        return base + "/" + value.trimStart('/')
    }

    /**
     * The web page of the manga or chapter whose key is [link], for "open in WebView" and sharing: absolute and
     * protocol-relative URLs as in [resolveCoverUrl], paths starting with `/` appended to [baseUrl]; any other key is
     * opaque to Komikku (an id the source turns into requests itself), so the source's home page [baseUrl] is used.
     */
    fun resolveWebUrl(baseUrl: String, link: String): String {
        val value = link.trim()
        val base = normalizeBaseUrl(baseUrl)
        return when {
            value.startsWith("//") -> "${schemeOf(baseUrl)}:$value"
            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("/") && base.isNotEmpty() -> base + value
            else -> base
        }
    }

    private fun schemeOf(baseUrl: String): String =
        baseUrl.trim().substringBefore("://", missingDelimiterValue = "").lowercase().ifEmpty { "https" }

    /** RFC 3986 scheme followed by `:` (`https:`, `data:`); a Windows-style `C:` is not expected from sources. */
    private fun hasScheme(value: String): Boolean {
        val colon = value.indexOf(':')
        if (colon <= 0) return false
        val scheme = value.substring(0, colon)
        return scheme[0].isLetter() && scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
    }
}
