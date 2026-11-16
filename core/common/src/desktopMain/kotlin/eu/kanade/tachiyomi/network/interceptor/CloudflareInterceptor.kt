package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import eu.kanade.tachiyomi.network.AndroidCookieJar
import logcat.LogPriority
import okhttp3.Interceptor
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat

/**
 * KMK: desktop Cloudflare interceptor. There is no WebView on desktop yet (PLAN.md D4: stub first,
 * KCEF/JCEF later), so responses pass through unchanged; a detected challenge is logged so the
 * failure that follows (usually HTTP 403/503) can be explained.
 */
actual class CloudflareInterceptor actual constructor(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") cookieManager: AndroidCookieJar,
    @Suppress("UNUSED_PARAMETER") defaultUserAgentProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code in ERROR_CODES && response.header("Server") in SERVER_CHECK) {
            logcat(LogPriority.WARN) {
                "Cloudflare challenge for ${response.request.url.host} cannot be solved on desktop yet (no WebView)"
            }
        }
        return response
    }
}

private val ERROR_CODES = listOf(403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
