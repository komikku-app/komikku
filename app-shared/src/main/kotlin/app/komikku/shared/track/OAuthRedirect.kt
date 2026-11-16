package app.komikku.shared.track

import java.net.URLDecoder

/**
 * KMK: parses what an OAuth tracker login hands back, for platforms that cannot receive the app-scheme
 * redirect (`komikku://anilist-auth#access_token=…`, `komikku://myanimelist-auth?code=…`) themselves:
 * the user pastes the redirected URL (or just the code / token) into the desktop app.
 *
 * Same rules as Android's `TrackLoginActivity.handleResult`: parameters come from the query, else from the
 * fragment; `key=value` pairs split on `&`, percent-decoded; the host names the tracker.
 */
data class OAuthRedirect(
    /** Redirect host (`anilist-auth`, …) or `null` when only parameters / a bare value were pasted. */
    val host: String?,
    val parameters: Map<String, String?>,
    /** The pasted text when it is a bare value (no URL, no `key=value`), e.g. just an authorization code. */
    val bareValue: String?,
) {

    /** Value of [key], falling back to a bare pasted value. */
    fun value(key: String): String? = parameters[key]?.takeIf { it.isNotBlank() } ?: bareValue

    companion object {
        fun parse(input: String): OAuthRedirect {
            val text = input.trim()
            val schemeEnd = text.indexOf("://")
            if (schemeEnd < 0 && '=' !in text) {
                return OAuthRedirect(host = null, parameters = emptyMap(), bareValue = text.takeIf { it.isNotEmpty() })
            }
            val host = if (schemeEnd >= 0) {
                text.substring(schemeEnd + 3).takeWhile { it != '/' && it != '?' && it != '#' }.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val query = text.substringAfter('?', "").substringBefore('#')
            val fragment = text.substringAfter('#', "")
            val raw = when {
                query.isNotBlank() -> query
                fragment.isNotBlank() -> fragment
                // "code=…" pasted without a URL
                schemeEnd < 0 -> text
                else -> ""
            }
            val parameters = raw.split("&")
                .filter { it.isNotBlank() }
                .associate {
                    val parts = it.split("=", limit = 2).map(::decode)
                    parts[0] to parts.getOrNull(1)
                }
            return OAuthRedirect(host, parameters, bareValue = null)
        }

        /** Percent-decoding like `android.net.Uri.decode` (`+` stays `+`). */
        private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
    }
}
