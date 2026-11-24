package app.komikku.desktop.webview

import okhttp3.Cookie
import okhttp3.HttpUrl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * KMK: cookies a user copied from a browser and pastes into the desktop app (P7, the "Open in WebView" stand-in without
 * an embedded browser). Accepted forms, mixed freely, one or several per line:
 * - a `Cookie` request header (`a=1; b=2`, also with a leading `Cookie:`), as the browser's developer tools show it;
 * - a `Set-Cookie`-like line (`a=1; Path=/; Secure; HttpOnly`): the attributes are ignored;
 * - rows copied from the developer tools' cookie table (tab-separated: name, value, then domain, path, … ignored); a
 *   header row (`Name	Value …`) is skipped.
 */
object ManualCookies {

    sealed interface Parsed {
        data class Cookies(val pairs: List<Pair<String, String>>) : Parsed

        /** [entry] is not a `name=value` cookie. */
        data class Invalid(val entry: String) : Parsed
    }

    /** How long a pasted cookie is kept (the browser does not say); a later `Set-Cookie` from the site replaces it. */
    val LIFETIME: Duration = 365.days

    fun parse(text: String): Parsed {
        val pairs = mutableListOf<Pair<String, String>>()
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { rawLine ->
            val line = rawLine.removePrefixIgnoreCase("Cookie:").trim()
            if ('\t' in line) {
                val columns = line.split('\t').map { it.trim() }
                val name = columns[0]
                val value = columns.getOrElse(1) { "" }
                if (name.equals("Name", ignoreCase = true) && value.equals("Value", ignoreCase = true)) return@forEach
                if (!isValid(name, value)) return Parsed.Invalid(rawLine)
                pairs += name to value
            } else {
                line.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
                    val name = entry.substringBefore('=', missingDelimiterValue = entry).trim()
                    if (name.lowercase() in ATTRIBUTES) return@forEach
                    if ('=' !in entry) return Parsed.Invalid(entry)
                    val value = entry.substringAfter('=').trim().removeSurrounding("\"")
                    if (!isValid(name, value)) return Parsed.Invalid(entry)
                    pairs += name to value
                }
            }
        }
        // A name pasted twice keeps its last value.
        return Parsed.Cookies(pairs.associate { it }.toList())
    }

    /**
     * The cookies to store for [url]: for the site's registrable domain (`www.example.com` → `example.com`, where
     * Cloudflare sets `cf_clearance`), so that its other hosts get them too; host-only for an IP address or a host
     * without a public suffix. Secure on `https`, path `/`, kept for [LIFETIME].
     */
    fun toCookies(url: HttpUrl, pairs: List<Pair<String, String>>, now: Long): List<Cookie> {
        val domain = url.topPrivateDomain()
        return pairs.map { (name, value) ->
            Cookie.Builder()
                .name(name)
                .value(value)
                .apply { if (domain != null) domain(domain) else hostOnlyDomain(url.host) }
                .path("/")
                .expiresAt(now + LIFETIME.inWholeMilliseconds)
                .apply { if (url.isHttps) secure() }
                .build()
        }
    }

    private fun isValid(name: String, value: String): Boolean {
        if (name.isEmpty() || name.any { it.code <= 0x20 || it.code >= 0x7f || it in SEPARATORS }) return false
        return value.none { it.code < 0x20 || it.code == 0x7f || it == ';' || it == ',' || it == '"' || it == '\\' }
    }

    private fun String.removePrefixIgnoreCase(prefix: String) =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    /** `Set-Cookie` attribute names (RFC 6265 and the common extensions). */
    private val ATTRIBUTES = setOf("path", "domain", "expires", "max-age", "secure", "httponly", "samesite", "priority", "partitioned")

    /** RFC 2616 token separators, not allowed in a cookie name. */
    private const val SEPARATORS = "()<>@,;:\\\"/[]?={} \t"
}
