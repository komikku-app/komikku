package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import app.komikku.network.browser.BrowserAvailability
import app.komikku.network.browser.BrowserBackend
import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.BrowserCondition
import app.komikku.network.browser.BrowserSession
import app.komikku.network.browser.BrowserSessionResult
import app.komikku.network.browser.storeBrowserCookies
import eu.kanade.tachiyomi.network.AndroidCookieJar
import logcat.LogPriority
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.kmk.KMR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * KMK: desktop Cloudflare interceptor, the counterpart of Android's WebView one (src/androidMain) on a
 * [BrowserBackend] (P7). The same detection ([CloudflareChallenge.isChallenge]) decides what a challenge is; then, like
 * Android, the old `cf_clearance` is removed, a browser loads the page with the request's headers and User-Agent until
 * a new `cf_clearance` is set (30 s by default), the browser's cookies go into the shared cookie jar and the request is
 * sent once more; that response is returned whatever it is.
 *
 * Desktop differences:
 * - Without a usable backend (the desktop default, [BrowserBackends]) the request fails with a
 *   [CloudflareBypassException] that tells the user to pass the check by hand ("Open in WebView" and paste the cookies);
 *   Android returns the challenge page after a "WebView is required" toast.
 * - One challenge per host is solved at a time: a request that waited for another one's solution is sent again with the
 *   new cookie instead of opening a second browser.
 * - When the browser reports another User-Agent than the request's, the retry uses the browser's (the clearance is
 *   bound to it).
 */
actual class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    private val defaultUserAgentProvider: () -> String,
    private val backendProvider: () -> BrowserBackend,
    private val timeout: Duration = DEFAULT_TIMEOUT,
) : Interceptor {

    actual constructor(
        context: Context,
        cookieManager: AndroidCookieJar,
        defaultUserAgentProvider: () -> String,
    ) : this(context, cookieManager, defaultUserAgentProvider, { BrowserBackends.current })

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!CloudflareChallenge.isChallenge(response)) return response

        val host = request.url.host
        val backend = backendProvider()
        val availability = try {
            backend.availability()
        } catch (e: Exception) {
            BrowserAvailability.Unavailable(BrowserAvailability.Reason.NOT_SUPPORTED, e.message)
        }
        if (availability is BrowserAvailability.Unavailable) {
            response.close()
            logcat(LogPriority.WARN) {
                "Cloudflare challenge for $host: no browser to solve it (${backend.name}: ${availability.reason}" +
                    "${availability.detail?.let { ", $it" }.orEmpty()})"
            }
            throw CloudflareBypassException(
                host = host,
                reason = CloudflareBypassException.Reason.NO_BROWSER,
                message = context.stringResource(KMR.strings.desktop_cloudflare_no_browser, host),
            )
        }

        // The clearance cookie the challenged request carried: the network request holds the jar's cookies (the
        // response's own request is the application's, before the cookie jar).
        val sentClearance = (response.networkResponse ?: response).request.clearanceCookie()
        response.close()

        val retry = synchronized(hostLocks.computeIfAbsent(host) { Any() }) {
            val current = cookieManager.get(request.url).firstOrNull { it.name == CloudflareChallenge.CLEARANCE_COOKIE }
            if (current != null && current.value != sentClearance) {
                // Another request solved this host's challenge while this one waited.
                request
            } else {
                solve(backend, request, host)
            }
        }
        return chain.proceed(retry)
    }

    /** Runs the browser for [request]; returns the request to send again, or throws a [CloudflareBypassException]. */
    private fun solve(backend: BrowserBackend, request: Request, host: String): Request {
        cookieManager.remove(request.url, CloudflareChallenge.COOKIE_NAMES, 0)
        val userAgent = request.header("User-Agent")?.takeIf { it.isNotBlank() } ?: defaultUserAgentProvider()
        val result = try {
            backend.run(
                BrowserSession(
                    url = request.url,
                    headers = CloudflareChallenge.browserHeaders(request.headers)
                        .filterKeys { !it.equals("User-Agent", ignoreCase = true) },
                    userAgent = userAgent,
                    cookies = cookieManager.get(request.url),
                    until = BrowserCondition.cookieSet(request.url, CloudflareChallenge.CLEARANCE_COOKIE),
                    timeout = timeout,
                ),
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Cloudflare challenge for $host: ${backend.name} failed" }
            throw failure(host, CloudflareBypassException.Reason.BROWSER_FAILED, e)
        }

        // Like Android, where the WebView shares the app's cookie store, whatever the browser got is kept.
        cookieManager.storeBrowserCookies(result.cookies)

        val solved = result.outcome == BrowserSessionResult.Outcome.CONDITION_MET &&
            cookieManager.get(request.url).any { it.name == CloudflareChallenge.CLEARANCE_COOKIE }
        if (!solved) {
            logcat(LogPriority.WARN) { "Cloudflare challenge for $host not solved by ${backend.name}: ${result.outcome}" }
            throw failure(
                host,
                if (result.outcome == BrowserSessionResult.Outcome.TIMED_OUT) {
                    CloudflareBypassException.Reason.TIMED_OUT
                } else {
                    CloudflareBypassException.Reason.NOT_SOLVED
                },
            )
        }

        val browserUserAgent = result.userAgent?.takeIf { it.isNotBlank() }
        return if (browserUserAgent != null && browserUserAgent != userAgent) {
            logcat(LogPriority.WARN) { "Cloudflare challenge for $host: the browser used another User-Agent, retrying with it" }
            request.newBuilder().header("User-Agent", browserUserAgent).build()
        } else {
            request
        }
    }

    private fun failure(host: String, reason: CloudflareBypassException.Reason, cause: Throwable? = null) =
        CloudflareBypassException(
            host = host,
            reason = reason,
            message = context.stringResource(KMR.strings.desktop_cloudflare_failed, host),
            cause = cause,
        )

    private fun Request.clearanceCookie(): String? = headers("Cookie")
        .asSequence()
        .flatMap { it.split(';') }
        .map { it.trim() }
        .firstOrNull { it.startsWith(CloudflareChallenge.CLEARANCE_COOKIE + "=") }
        ?.substringAfter('=')

    companion object {
        /** Android's WebView wait (`awaitFor30Seconds`). */
        val DEFAULT_TIMEOUT: Duration = 30.seconds

        private val hostLocks = ConcurrentHashMap<String, Any>()
    }
}

/**
 * KMK: a Cloudflare challenge the desktop could not get past. An [IOException], so that OkHttp's async calls and the
 * sources report it like a network error; [message] says what the user can do.
 */
class CloudflareBypassException(
    val host: String,
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    enum class Reason {
        /** No browser backend is available: pass the check by hand. */
        NO_BROWSER,

        /** The browser did not get a clearance cookie (closed, or the page was no challenge for it). */
        NOT_SOLVED,

        /** The browser did not get a clearance cookie in time. */
        TIMED_OUT,

        /** The browser could not be started or failed. */
        BROWSER_FAILED,
    }
}
