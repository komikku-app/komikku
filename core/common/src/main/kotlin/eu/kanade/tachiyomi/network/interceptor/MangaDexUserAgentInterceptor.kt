package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Gives MangaDex requests that don't set a User-Agent of their own a non-browser one.
 *
 * MangaDex's edge (`api.`, `auth.`, `uploads.mangadex.org`...) sends any request with a browser-like User-Agent
 * to a bot challenge page (`302` to `mangadex.org/edge/challenge`), and asks apps not to send one. MD@Home image
 * nodes (`*.mangadex.network`) answer `404` to such requests for images not already in their cache. The app's
 * default User-Agent is a Chrome one, so requests that rely on [UserAgentInterceptor] (track search covers,
 * logout...) would get the challenge page instead of the content. The MangaDex extension already sends
 * `Tachiyomi <http.agent>`, so the same is used here.
 *
 * Must be added before [UserAgentInterceptor], which only fills in the User-Agent when it is still missing.
 */
class MangaDexUserAgentInterceptor(
    private val userAgent: String = defaultUserAgent(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isMangaDexHost(request.url.host) || !request.header("User-Agent").isNullOrEmpty()) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", userAgent)
                .build(),
        )
    }

    companion object {
        /** The site, API, auth and covers (`mangadex.org`), and MD@Home image nodes (`mangadex.network`). */
        private val MANGADEX_HOSTS = listOf("mangadex.org", "mangadex.network")

        fun isMangaDexHost(host: String): Boolean {
            return MANGADEX_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        fun defaultUserAgent(): String {
            val httpAgent = System.getProperty("http.agent")?.takeIf { it.isNotBlank() } ?: "Dalvik/2.1.0"
            return "Tachiyomi $httpAgent"
        }
    }
}
