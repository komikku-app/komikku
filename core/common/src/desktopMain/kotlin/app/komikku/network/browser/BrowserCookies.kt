package app.komikku.network.browser

import android.webkit.CookieManager
import app.komikku.desktop.compat.DesktopCookieManager
import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl

/*
 * KMK: cookie jar helpers for browser sessions on desktop (P7). `AndroidCookieJar` only sees `name=value` pairs through
 * `CookieManager.getCookie` and removes through a `Max-Age=0` cookie of the address's own host and path, which misses
 * site-wide cookies such as Cloudflare's `cf_clearance` (`Domain=example.com`). On desktop the jar's `CookieManager` is
 * the compat `DesktopCookieManager`, which these helpers use for the full cookies; another `CookieManager` falls back to
 * the jar's own methods.
 */

private val desktopCookieManager: DesktopCookieManager?
    get() = CookieManager.getInstance() as? DesktopCookieManager

/**
 * Stores cookies a browser handed back (or a user pasted) in the jar, each under an address of its own domain and path
 * so that the jar accepts it whatever page it came from (`Cookie.parse` refuses a cookie whose domain does not match
 * the address). An expired cookie deletes the stored one, like a `Set-Cookie` would.
 */
fun AndroidCookieJar.storeBrowserCookies(cookies: List<Cookie>) {
    cookies.forEach { cookie ->
        val url = HttpUrl.Builder()
            .scheme(if (cookie.secure) "https" else "http")
            .host(cookie.domain)
            .encodedPath(cookie.path.ifEmpty { "/" })
            .build()
        saveFromResponse(url, listOf(cookie))
    }
}

/** The jar's cookies sent to [url], with their domain, path and expiry (for a browser to start with). */
fun AndroidCookieJar.browserCookies(url: HttpUrl): List<Cookie> =
    desktopCookieManager?.cookiesFor(url.toString()) ?: get(url)

/** Removes the jar's cookies sent to [url] named [names] (all when `null`), whatever their domain; returns how many. */
fun AndroidCookieJar.removeBrowserCookies(url: HttpUrl, names: Collection<String>? = null): Int =
    desktopCookieManager?.removeFor(url.toString(), names) ?: remove(url, names?.toList(), 0)

/**
 * Stores cookies a user pasted ("Open in WebView" without a browser), marked so that a cookie of the same name the site
 * sets later replaces them whatever its domain (`DesktopCookieManager.setPastedCookies`); without the desktop
 * `CookieManager` like [storeBrowserCookies].
 */
fun AndroidCookieJar.storePastedCookies(cookies: List<Cookie>) {
    desktopCookieManager?.setPastedCookies(cookies) ?: storeBrowserCookies(cookies)
}
