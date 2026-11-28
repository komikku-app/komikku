package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Headers
import okhttp3.Response
import java.util.Locale

/*
 * KMK: desktop-only copy. Follows `master`'s core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/
 * CloudflareInterceptor.kt (`shouldIntercept`, `SERVER_CHECK`, `COOKIE_NAMES`) and
 * core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt (`Headers.toWebViewHeaders`,
 * `isRequestHeaderSafe`), last synced to `master` 15610e67e4 (docs/kmp-desktop/UPSTREAM_AUDIT.md lists the desktop
 * copies to re-sync at each merge of `master`).
 */

/**
 * Cloudflare challenge detection and the browser-safe request headers of the desktop interceptor
 * (src/desktopMain `CloudflareInterceptor`) and the desktop WebView, decided as Android's WebView interceptors do.
 */
object CloudflareChallenge {

    /** `Server` header values of a Cloudflare response. */
    val SERVER_CHECK = listOf("cloudflare-nginx", "cloudflare")

    /** The cookie Cloudflare sets once its check is passed. */
    const val CLEARANCE_COOKIE = "cf_clearance"

    /** The cookies removed before a challenge is solved again. */
    val COOKIE_NAMES = listOf(CLEARANCE_COOKIE)

    /**
     * Whether [response] is a Cloudflare challenge: Cloudflare marks one with `cf-mitigated: challenge`, the official
     * way to detect it (https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/).
     */
    fun isChallenge(response: Response): Boolean {
        return response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK
    }

    /**
     * The request headers a browser may be given for a page load: the first value per name (names compared
     * case-insensitively, keys in lower case), without the headers Chromium refuses ([net::ERR_INVALID_ARGUMENT]) or
     * that kill its renderer (`Origin`).
     */
    fun browserHeaders(headers: Headers): Map<String, String> {
        return headers
            // Keeping unsafe header makes webview throw [net::ERR_INVALID_ARGUMENT]
            .filter { (name, value) ->
                isRequestHeaderSafe(name, value)
            }
            // Header names are case-insensitive: merge the spellings of one name, as toMultimap() did
            .groupBy(keySelector = { (name, _) -> name.lowercase(Locale.ENGLISH) }) { (_, value) -> value }
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
    // Renderer is killed if it doesn't match the request initiator
    "origin",
)
