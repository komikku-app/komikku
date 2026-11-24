package app.komikku.network.browser

import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl

/**
 * KMK: stores cookies a browser handed back (or a user pasted) in the app's cookie jar, each under an address of its own
 * domain and path so that the jar accepts it whatever page it came from (`Cookie.parse` refuses a cookie whose domain
 * does not match the address). Expired cookies delete the stored ones, like a `Set-Cookie` would.
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
