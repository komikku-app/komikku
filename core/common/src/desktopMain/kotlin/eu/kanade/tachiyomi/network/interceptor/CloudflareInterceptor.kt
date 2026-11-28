package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import app.komikku.network.browser.BrowserAvailability
import app.komikku.network.browser.BrowserBackend
import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.BrowserCondition
import app.komikku.network.browser.BrowserSession
import app.komikku.network.browser.BrowserSessionResult
import app.komikku.network.browser.BrowserUnavailableException
import app.komikku.network.browser.CloudflareHostState
import app.komikku.network.browser.CloudflareHosts
import app.komikku.network.browser.browserCookies
import app.komikku.network.browser.removeBrowserCookies
import app.komikku.network.browser.storeBrowserCookies
import eu.kanade.tachiyomi.network.AndroidCookieJar
import logcat.LogPriority
import okhttp3.HttpUrl
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
 * [BrowserBackend] (P7). Android's detection (`cf-mitigated: challenge` from Cloudflare, the desktop copy
 * [CloudflareChallenge.isChallenge]) decides what a challenge is; then, like
 * Android, the old `cf_clearance` is removed, a browser loads the page with the request's headers and User-Agent until
 * a new `cf_clearance` is set (30 s by default), the browser's cookies go into the shared cookie jar and the request is
 * sent once more; that response is returned whatever it is. A request of another method than GET is solved on the
 * site's root page ([browserUrl], `master`'s `14c94f5cec`), since a browser loads pages with GET only. A challenge that
 * turns interactive (one only a person can pass) ends the session at once and fails the request, as Android's WebView
 * gives up (`master`'s `b672f8028e`; [BrowserSession.endOnInteractiveChallenge], for the backends that can see it).
 *
 * Desktop differences:
 * - Without a usable backend (the desktop default, [BrowserBackends]) the request fails with a
 *   [CloudflareBypassException] that tells the user to pass the check by hand ("Open in WebView" and paste the cookies);
 *   Android returns the challenge page after a "WebView is required" toast.
 * - One challenge per host is solved at a time: a request that waited for another one's solution is sent again with the
 *   new cookie instead of opening a second browser. A failed solve is remembered for a short time
 *   ([CloudflareHostState.FAILURE_MEMORY]): the requests behind it and right after it fail at once with the same reason
 *   (not a backend that could not be used, [BrowserUnavailableException], P8 review). A cancelled call starts no solve
 *   and ends a running one through [BrowserSession.isCancelled].
 * - The challenge that counts is the one of the last request (after redirects): its host is solved and its clearance is
 *   compared.
 * - When the browser reports another User-Agent than the source's, the retry and the host's later requests (while that
 *   clearance is in the jar) use the browser's, since the clearance is bound to it ([CloudflareHostState]); a pasted
 *   User-Agent ("Open in WebView") works the same way.
 */
actual class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    private val defaultUserAgentProvider: () -> String,
    private val backendProvider: () -> BrowserBackend,
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val hostStateProvider: () -> CloudflareHostState = { CloudflareHosts.state },
) : Interceptor {

    actual constructor(
        context: Context,
        cookieManager: AndroidCookieJar,
        defaultUserAgentProvider: () -> String,
    ) : this(context, cookieManager, defaultUserAgentProvider, { BrowserBackends.current })

    override fun intercept(chain: Interceptor.Chain): Response {
        val hosts = hostStateProvider()
        val original = chain.request()
        // The source's own User-Agent (or the app's default), before an override for a clearance replaces it.
        val sourceUserAgent = original.header("User-Agent")?.takeIf { it.isNotBlank() } ?: defaultUserAgentProvider()
        val request = original.withUserAgentFor(original.url, hosts)
        val response = chain.proceed(request)
        if (!CloudflareChallenge.isChallenge(response)) return response

        // The request that was challenged: after redirects the last one, which may be another host than [request]'s.
        val challengedUrl = response.request.url
        val host = challengedUrl.host
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
            val current = currentClearance(challengedUrl)
            val clearanceUserAgent = current?.let { clearance -> hosts.userAgentFor(host) { clearance } }
            if (current != null && current != sentClearance) {
                // Another request solved this host's challenge while this one waited: send it with that clearance's
                // User-Agent too.
                request.withUserAgentFor(challengedUrl, hosts)
            } else if (clearanceUserAgent != null && clearanceUserAgent != request.header("User-Agent")) {
                // The clearance is there but went out with another User-Agent than the one it belongs to (a redirect
                // from a host without the override): send it again with that one, no browser needed.
                request.withUserAgentFor(challengedUrl, hosts)
            } else {
                // A solve that failed a moment ago fails the same way at once (no second browser run per waiter).
                hosts.recentFailure(host, current)?.let { throw it }
                // P8 review: a call cancelled while it waited for the host starts no solve.
                if (chain.call().isCanceled()) throw IOException("Canceled")
                try {
                    solve(
                        backend,
                        request,
                        challengedUrl,
                        browserUrl(response.request),
                        sourceUserAgent,
                        hosts,
                        staleClearance = current ?: sentClearance,
                        isCancelled = { chain.call().isCanceled() },
                    )
                } catch (e: CloudflareBypassException) {
                    // P8 review: a backend that cannot be used (no solver at the address, its error answer) is not a
                    // failed solve of this host: a corrected set-up must work with the next request.
                    if (e.cause !is BrowserUnavailableException) {
                        hosts.recordFailure(host, e, currentClearance(challengedUrl))
                    }
                    throw e
                }
            }
        }
        return chain.proceed(retry)
    }

    /**
     * Runs the browser on [browserUrl] until the challenged [url] has a new clearance; returns [request] to send again
     * (with the browser's User-Agent when it used another one), or throws a [CloudflareBypassException].
     */
    private fun solve(
        backend: BrowserBackend,
        request: Request,
        url: HttpUrl,
        browserUrl: HttpUrl,
        sourceUserAgent: String,
        hosts: CloudflareHostState,
        staleClearance: String?,
        isCancelled: () -> Boolean,
    ): Request {
        val host = url.host
        // Android: `cookieManager.remove(request.url, COOKIE_NAMES, 0)`; on desktop that misses a site-wide cookie.
        cookieManager.removeBrowserCookies(url, CloudflareChallenge.COOKIE_NAMES)
        val userAgent = request.header("User-Agent")?.takeIf { it.isNotBlank() } ?: defaultUserAgentProvider()
        val result = try {
            backend.run(
                BrowserSession(
                    url = browserUrl,
                    headers = CloudflareChallenge.browserHeaders(request.headers)
                        .filterKeys { !it.equals("User-Agent", ignoreCase = true) },
                    userAgent = userAgent,
                    cookies = cookieManager.browserCookies(browserUrl),
                    // A new clearance: not the one that was refused (Android's `it != oldCookie`).
                    until = BrowserCondition.cookieSet(url, CloudflareChallenge.CLEARANCE_COOKIE, staleClearance),
                    timeout = timeout,
                    isCancelled = isCancelled,
                    endOnInteractiveChallenge = true,
                ),
            )
        } catch (e: Exception) {
            // P8 review: a cancelled call (or an interrupted thread) is no failed solve.
            if (isCancelled() || Thread.currentThread().isInterrupted) throw IOException("Canceled", e)
            logcat(LogPriority.ERROR, e) { "Cloudflare challenge for $host: ${backend.name} failed" }
            throw failure(host, CloudflareBypassException.Reason.BROWSER_FAILED, e)
        }

        // Like Android, where the WebView shares the app's cookie store, whatever the browser got is kept.
        cookieManager.storeBrowserCookies(result.cookies)

        val clearance = cookieManager.get(url)
            .firstOrNull { it.name == CloudflareChallenge.CLEARANCE_COOKIE && it.value != staleClearance }
            ?.value
        if (result.outcome != BrowserSessionResult.Outcome.CONDITION_MET || clearance == null) {
            logcat(LogPriority.WARN) { "Cloudflare challenge for $host not solved by ${backend.name}: ${result.outcome}" }
            throw failure(
                host,
                when (result.outcome) {
                    BrowserSessionResult.Outcome.TIMED_OUT -> CloudflareBypassException.Reason.TIMED_OUT
                    BrowserSessionResult.Outcome.INTERACTIVE_CHALLENGE -> CloudflareBypassException.Reason.INTERACTIVE
                    else -> CloudflareBypassException.Reason.NOT_SOLVED
                },
            )
        }
        hosts.clearFailure(host)

        val solvedUserAgent = result.userAgent?.takeIf { it.isNotBlank() } ?: userAgent
        // The host's later requests send the User-Agent this clearance belongs to.
        if (solvedUserAgent != sourceUserAgent) {
            hosts.setUserAgent(host, solvedUserAgent, clearance)
        } else {
            hosts.removeUserAgent(host)
        }
        return if (solvedUserAgent != userAgent) {
            logcat(LogPriority.WARN) { "Cloudflare challenge for $host: the browser used another User-Agent, retrying with it" }
            request.newBuilder().header("User-Agent", solvedUserAgent).build()
        } else {
            request
        }
    }

    /**
     * The page the browser loads for the challenged [request]: its own address for a GET, else the site's root
     * (`master`'s `14c94f5cec`: loading another method's address usually reaches an endpoint that answers GET without a
     * challenge, and the clearance covers the whole site).
     */
    private fun browserUrl(request: Request): HttpUrl = if (request.method == "GET") {
        request.url
    } else {
        request.url.newBuilder().encodedPath("/").query(null).fragment(null).build()
    }

    /** This request with the User-Agent of the clearance the jar holds for [url], when there is such an override. */
    private fun Request.withUserAgentFor(url: HttpUrl, hosts: CloudflareHostState): Request {
        val userAgent = hosts.userAgentFor(url.host) { currentClearance(url) } ?: return this
        return if (header("User-Agent") == userAgent) this else newBuilder().header("User-Agent", userAgent).build()
    }

    private fun currentClearance(url: HttpUrl): String? =
        cookieManager.get(url).firstOrNull { it.name == CloudflareChallenge.CLEARANCE_COOKIE }?.value

    private fun failure(host: String, reason: CloudflareBypassException.Reason, cause: Throwable? = null) =
        CloudflareBypassException(
            host = host,
            reason = reason,
            // A backend that says why (an unreachable solver, its error answer, P8) is quoted.
            // Its own final period is dropped: the message goes on after it (P8 review).
            message = (cause as? BrowserUnavailableException)?.message?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() }
                ?.let { context.stringResource(KMR.strings.desktop_cloudflare_solver_failed, host, it) }
                ?: context.stringResource(KMR.strings.desktop_cloudflare_failed, host),
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

        /** The challenge turned interactive: only a person can pass it ("Open in WebView"). */
        INTERACTIVE,

        /** The browser could not be started or failed. */
        BROWSER_FAILED,
    }
}
