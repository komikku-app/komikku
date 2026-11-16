package app.komikku.shared.track

import java.io.ByteArrayOutputStream

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
    /**
     * The pasted text when it is a bare value (no URL, not a `key=value` list), e.g. just an authorization
     * code, which may end with base64 `=` padding.
     */
    val bareValue: String?,
) {

    /** Value of [key], falling back to a bare pasted value. */
    fun value(key: String): String? = parameters[key]?.takeIf { it.isNotBlank() } ?: bareValue

    companion object {
        /** A `key=value` parameter name (`code`, `access_token`, `state`, …). */
        private val PARAMETER_NAME = Regex("[A-Za-z0-9_.~-]+")

        /** Never throws: malformed input gives an empty result or a bare value. */
        fun parse(input: String): OAuthRedirect {
            val text = input.trim()
            val schemeEnd = text.indexOf("://")
            if (schemeEnd < 0 && !isParameterList(text)) {
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

        /**
         * Whether [text] (no URL) is a `key=value[&…]` list rather than a bare code / token. A bare value may
         * contain `=` (base64 padding: `abc==`, `YWJj=`), so a single segment whose value is empty or only `=`
         * is a bare value, and every key must look like a parameter name.
         */
        private fun isParameterList(text: String): Boolean {
            if ('=' !in text) return false
            val segments = text.split("&").filter { it.isNotBlank() }
            if (segments.isEmpty()) return false
            if (segments.size == 1 && segments[0].substringAfter('=').all { it == '=' }) return false
            return segments.all { '=' in it && PARAMETER_NAME.matches(it.substringBefore('=')) }
        }

        /**
         * Percent-decoding like `android.net.Uri.decode` (`+` stays `+`), but lenient: a malformed escape
         * (`%zz`, a trailing `%`) is kept as is and invalid UTF-8 becomes U+FFFD instead of throwing.
         */
        private fun decode(value: String): String {
            if ('%' !in value) return value
            val bytes = ByteArrayOutputStream(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                val high = if (c == '%' && i + 2 <= value.lastIndex) Character.digit(value[i + 1], 16) else -1
                val low = if (high >= 0) Character.digit(value[i + 2], 16) else -1
                if (high >= 0 && low >= 0) {
                    bytes.write(high * 16 + low)
                    i += 3
                } else {
                    // Copy one code point (surrogate pairs together) as UTF-8
                    val end = i + Character.charCount(value.codePointAt(i))
                    bytes.write(value.substring(i, end).toByteArray(Charsets.UTF_8))
                    i = end
                }
            }
            return bytes.toByteArray().toString(Charsets.UTF_8)
        }
    }
}
