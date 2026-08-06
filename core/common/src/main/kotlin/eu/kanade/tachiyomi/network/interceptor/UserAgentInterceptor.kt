package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor(
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val userAgent = originalRequest.header("User-Agent") ?: defaultUserAgentProvider()

        val builder = originalRequest.newBuilder()
        if (originalRequest.header("User-Agent").isNullOrEmpty()) {
            builder.header("User-Agent", userAgent)
        }

        // KMK -->
        // Add Client Hints for better Cloudflare bypass
        if (userAgent.contains("Chrome/")) {
            val chromeVersionMatch = """.*Chrome/(\d+)\..*""".toRegex().find(userAgent)
            if (chromeVersionMatch != null) {
                val version = chromeVersionMatch.groupValues[1]
                if (originalRequest.header("Sec-CH-UA").isNullOrEmpty()) {
                    builder.header("Sec-CH-UA", "\"Google Chrome\";v=\"$version\", \"Chromium\";v=\"$version\", \"Not?A_Brand\";v=\"24\"")
                }
                if (originalRequest.header("Sec-CH-UA-Mobile").isNullOrEmpty()) {
                    val isMobile = userAgent.contains("Mobile")
                    builder.header("Sec-CH-UA-Mobile", if (isMobile) "?1" else "?0")
                }
                if (originalRequest.header("Sec-CH-UA-Platform").isNullOrEmpty()) {
                    val platform = when {
                        userAgent.contains("Android") -> "Android"
                        userAgent.contains("Windows") -> "Windows"
                        userAgent.contains("Macintosh") -> "macOS"
                        userAgent.contains("iPhone") || userAgent.contains("iPad") -> "iOS"
                        userAgent.contains("Linux") -> "Linux"
                        else -> "Unknown"
                    }
                    builder.header("Sec-CH-UA-Platform", "\"$platform\"")
                }
            }
        }
        // KMK <--

        return chain.proceed(builder.build())
    }
}
