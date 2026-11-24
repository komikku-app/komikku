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

    /** The usable cookies ([pairs]) and the names (or entries without `=`) that were [skipped] as unusable. */
    data class Parsed(val pairs: List<Pair<String, String>>, val skipped: List<String> = emptyList())

    /**
     * How long a pasted cookie is kept: the browser's copy does not say. When the site sets a cookie of the same name
     * later, that one replaces the pasted one whatever its domain (`DesktopCookieManager.setPastedCookies`).
     */
    val LIFETIME: Duration = 365.days

    fun parse(text: String): Parsed {
        val pairs = mutableListOf<Pair<String, String>>()
        val skipped = mutableListOf<String>()
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { rawLine ->
            val line = rawLine.removePrefixIgnoreCase("Cookie:").trim()
            if ('\t' in line) {
                val columns = line.split('\t').map { it.trim() }
                val name = columns[0]
                val value = columns.getOrElse(1) { "" }
                if (name.equals("Name", ignoreCase = true) && value.equals("Value", ignoreCase = true)) return@forEach
                if (isValid(name, value)) pairs += name to value else skipped += name.shortened()
            } else {
                line.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
                    val name = entry.substringBefore('=', missingDelimiterValue = entry).trim()
                    if (name.lowercase() in ATTRIBUTES) return@forEach
                    if ('=' !in entry) {
                        skipped += entry.shortened()
                        return@forEach
                    }
                    val value = entry.substringAfter('=').trim().removeSurrounding("\"")
                    if (isValid(name, value)) pairs += name to value else skipped += name.shortened()
                }
            }
        }
        // A name pasted twice keeps its last value.
        return Parsed(pairs.associate { it }.toList(), skipped.distinct())
    }

    /**
     * The cookies to store for [url]: host-only for the entered host, like a cookie the site sets without a domain;
     * Cloudflare's own cookies ([isCloudflareCookie]) for the site's registrable domain (`www.example.com` →
     * `example.com`, where Cloudflare sets `cf_clearance`), so that the site's other hosts get them too (host-only for
     * an IP address or a host without a public suffix). Secure on `https`, path `/`, kept for [LIFETIME].
     */
    fun toCookies(url: HttpUrl, pairs: List<Pair<String, String>>, now: Long): List<Cookie> {
        val siteDomain = url.topPrivateDomain()
        return pairs.mapNotNull { (name, value) ->
            try {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .apply {
                        if (siteDomain != null && isCloudflareCookie(name)) domain(siteDomain) else hostOnlyDomain(url.host)
                    }
                    .path("/")
                    .expiresAt(now + LIFETIME.inWholeMilliseconds)
                    .apply { if (url.isHttps) secure() }
                    .build()
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /** `cf_clearance`, `cf_chl_*`, `__cf_bm`, `_cfuvid`, …: set by Cloudflare for the whole site. */
    fun isCloudflareCookie(name: String): Boolean =
        name.startsWith("cf_") || name.startsWith("__cf") || name.startsWith("_cf")

    /**
     * A cookie name must be an RFC 2616 token. A value may hold anything a `Cookie` header can carry (commas, quotes,
     * backslashes are sent as they are), but no `;` (it would split the header) and no control or non-ASCII characters
     * (OkHttp refuses them in header values, which would fail every request to the site).
     */
    private fun isValid(name: String, value: String): Boolean {
        if (name.isEmpty() || name.any { it.code <= 0x20 || it.code >= 0x7f || it in SEPARATORS }) return false
        return value.none { it.code < 0x20 || it.code >= 0x7f || it == ';' }
    }

    private fun String.shortened() = if (length > 40) take(40) + "…" else this

    private fun String.removePrefixIgnoreCase(prefix: String) =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    /** `Set-Cookie` attribute names (RFC 6265 and the common extensions). */
    private val ATTRIBUTES = setOf("path", "domain", "expires", "max-age", "secure", "httponly", "samesite", "priority", "partitioned")

    /** RFC 2616 token separators, not allowed in a cookie name. */
    private const val SEPARATORS = "()<>@,;:\\\"/[]?={} \t"
}
