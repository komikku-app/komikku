package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Headers
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.Locale

/**
 * KMK: Cloudflare challenge detection and the browser-safe request headers, shared by Android's WebView
 * [CloudflareInterceptor] / [WebViewInterceptor] (src/androidMain, the upstream code moved here unchanged) and the
 * desktop interceptor (src/desktopMain), so both platforms decide alike what a challenge is.
 */
object CloudflareChallenge {

    /** Status codes of a Cloudflare challenge page. */
    val ERROR_CODES = listOf(403, 503)

    /** `Server` header values of a Cloudflare response. */
    val SERVER_CHECK = listOf("cloudflare-nginx", "cloudflare")

    /** The cookie Cloudflare sets once its check is passed. */
    const val CLEARANCE_COOKIE = "cf_clearance"

    /** The cookies removed before a challenge is solved again. */
    val COOKIE_NAMES = listOf(CLEARANCE_COOKIE)

    /**
     * Whether [response] is a Cloudflare anti-bot challenge that a browser can solve: a 403 / 503 from Cloudflare whose
     * page has the challenge markers. A geo block or another 403 from Cloudflare is not one.
     */
    fun isChallenge(response: Response): Boolean {
        // Check if Cloudflare anti-bot is on
        return if (response.code in ERROR_CODES && response.header("Server") in SERVER_CHECK) {
            val document = Jsoup.parse(
                response.peekBody(Long.MAX_VALUE).string(),
                response.request.url.toString(),
            )

            // solve with webview only on captcha, not on geo block
            document.getElementById("challenge-error-title") != null ||
                document.getElementById("challenge-error-text") != null
        } else {
            false
        }
    }

    /**
     * The request headers a browser may be given for a page load: the first value per name, without the headers
     * Chromium refuses ([net::ERR_INVALID_ARGUMENT]).
     */
    fun browserHeaders(headers: Headers): Map<String, String> {
        return headers
            // Keeping unsafe header makes webview throw [net::ERR_INVALID_ARGUMENT]
            .filter { (name, value) ->
                isRequestHeaderSafe(name, value)
            }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { it.value.getOrNull(0).orEmpty() }
    }
}

// Based on [IsRequestHeaderSafe] in
// https://source.chromium.org/chromium/chromium/src/+/main:services/network/public/cpp/header_util.cc
private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
    val name = _name.lowercase(Locale.ENGLISH)
    val value = _value.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}
private val unsafeHeaderNames = listOf(
    "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive", "transfer-encoding", "set-cookie",
)
