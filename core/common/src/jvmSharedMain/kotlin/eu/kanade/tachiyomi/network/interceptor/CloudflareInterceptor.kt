package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.Interceptor

/**
 * KMK: Cloudflare challenge interceptor added to every `NetworkHelper` client.
 *
 * - Android (`src/androidMain`, the upstream implementation): solves the challenge in a WebView.
 * - Desktop (`src/desktopMain`): no WebView yet; passes responses through and reports challenges.
 */
expect class CloudflareInterceptor(
    context: Context,
    cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : Interceptor
