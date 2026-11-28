package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.Interceptor

/**
 * KMK: Cloudflare challenge interceptor added to every `NetworkHelper` client.
 *
 * - Android (`src/androidMain`, the upstream implementation): solves the challenge in a WebView.
 * - Desktop (`src/desktopMain`): solves challenges with a `BrowserBackend` (without a usable one, fails the request
 *   with a `CloudflareBypassException`); detection is the desktop copy `CloudflareChallenge` of Android's rules.
 */
expect class CloudflareInterceptor(
    context: Context,
    cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : Interceptor
