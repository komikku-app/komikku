package app.komikku.desktop.webview

import app.komikku.desktop.platform.DesktopBrowser
import app.komikku.network.browser.BrowserAvailability
import app.komikku.network.browser.BrowserBackend
import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.BrowserCondition
import app.komikku.network.browser.BrowserSession
import app.komikku.network.browser.BrowserSessionResult
import app.komikku.network.browser.CloudflareHostState
import app.komikku.network.browser.CloudflareHosts
import app.komikku.network.browser.browserCookies
import app.komikku.network.browser.removeBrowserCookies
import app.komikku.network.browser.storeBrowserCookies
import app.komikku.network.browser.storePastedCookies
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.interceptor.CloudflareChallenge
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.log.xLogE
import exh.log.xLogW
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * KMK: the desktop's "Open in WebView" (Android: `WebViewScreen` / `WebViewScreenModel`, P7). A source's home page or an
 * entry's page is opened so that the user can pass a Cloudflare check or log in, and the cookies end up in the app's
 * cookie jar (`NetworkHelper.cookieJar`, the desktop `CookieManager`), where the sources' requests read them.
 *
 * - With a usable [BrowserBackend] ([BrowserBackends.current]): an interactive browser session with the source's
 *   headers and User-Agent, until the user closes it; its cookies are stored ([Opened.Embedded]).
 * - Without one (the desktop default): the page opens in the system browser and the user pastes the site's cookies and,
 *   optionally, that browser's User-Agent ([Opened.Manual], [saveManual]). The User-Agent is kept for the host
 *   ([CloudflareHostState], persisted), bound to the clearance cookie in the jar: the Cloudflare interceptor sends it
 *   to that host at once, also for sources with their own User-Agent header. Only when asked, it also becomes Android's
 *   `default_user_agent` preference ([NetworkPreferences.defaultUserAgent]), which sources read when they are created,
 *   so that applies after a restart (Android's "Requires app restart" too).
 */
class DesktopWebView(
    private val network: NetworkHelper,
    private val networkPreferences: NetworkPreferences,
    private val sourceManager: SourceManager,
    private val backendProvider: () -> BrowserBackend = { BrowserBackends.current },
    private val browserOpener: (String) -> Boolean = DesktopBrowser::open,
    private val clock: () -> Long = System::currentTimeMillis,
    private val interactiveTimeout: Duration = INTERACTIVE_TIMEOUT,
    private val hostStateProvider: () -> CloudflareHostState = { CloudflareHosts.state },
) {

    sealed interface Opened {
        /** The embedded browser ran; its cookies are stored. */
        data class Embedded(val result: BrowserSessionResult) : Opened

        /** No embedded browser: [ManualEntry.browserOpened] says whether the system browser shows the page. */
        data class Manual(val entry: ManualEntry) : Opened

        /** [url] is no web address. */
        data class InvalidUrl(val url: String) : Opened
    }

    data class ManualEntry(
        val url: String,
        val host: String,
        val browserOpened: Boolean,
        /** The User-Agent sent to [host] now: the host's override for its clearance, else [sourceUserAgent]. */
        val userAgent: String,
        /** Names of the cookies the app holds for [url] now. */
        val storedCookies: List<String>,
        /** The User-Agent of the source itself (its own header, else the app's default). */
        val sourceUserAgent: String = userAgent,
    )

    sealed interface SaveResult {
        /**
         * [cookies] were stored; [skipped] names / entries could not be used. [userAgentChanged]: the User-Agent sent to
         * the host changed (at once); [defaultUserAgentChanged]: the app's default changed too (restart for sources).
         */
        data class Saved(
            val cookies: Int,
            val userAgentChanged: Boolean,
            val defaultUserAgentChanged: Boolean = false,
            val skipped: List<String> = emptyList(),
        ) : SaveResult

        /** Nothing usable was pasted; [entry] names what was skipped. */
        data class InvalidCookie(val entry: String) : SaveResult

        data object UserAgentInvalid : SaveResult
    }

    /** Blocks while an embedded browser runs: call it off the UI thread. */
    fun open(url: String, sourceId: Long?): Opened {
        val httpUrl = url.toHttpUrlOrNull() ?: return Opened.InvalidUrl(url)
        val headers = sourceHeaders(sourceId)
        val userAgent = headers["User-Agent"]?.takeIf { it.isNotBlank() } ?: network.defaultUserAgentProvider()
        val hostUserAgent = hostStateProvider().userAgentFor(httpUrl.host) { clearance(httpUrl) }

        val backend = backendProvider()
        // A backend that cannot show pages (an external solver, P8) is not used here: the manual flow stays.
        val available = backend.showsPages && try {
            backend.availability() == BrowserAvailability.Available
        } catch (e: Exception) {
            xLogW("Browser backend ${backend.name} unavailable", e)
            false
        }
        if (available) {
            try {
                val result = backend.run(
                    BrowserSession(
                        url = httpUrl,
                        headers = CloudflareChallenge.browserHeaders(headers)
                            .filterKeys { !it.equals("User-Agent", ignoreCase = true) },
                        userAgent = userAgent,
                        cookies = network.cookieJar.browserCookies(httpUrl),
                        until = BrowserCondition.UntilClosed,
                        timeout = interactiveTimeout,
                        interactive = true,
                    ),
                )
                network.cookieJar.storeBrowserCookies(result.cookies)
                return Opened.Embedded(result)
            } catch (e: Exception) {
                xLogE("Browser backend ${backend.name} failed for ${httpUrl.host}, falling back to the system browser", e)
            }
        }

        return Opened.Manual(
            ManualEntry(
                url = httpUrl.toString(),
                host = httpUrl.host,
                browserOpened = openInBrowser(httpUrl.toString()),
                userAgent = hostUserAgent ?: userAgent,
                sourceUserAgent = userAgent,
                storedCookies = network.cookieJar.get(httpUrl).map { it.name }.distinct(),
            ),
        )
    }

    /**
     * Stores the pasted [cookiesText] for [entry]'s site (see [ManualCookies]), replacing cookies of the same names and
     * skipping unusable entries, and [userAgentText] (blank: [ManualEntry.userAgent], unchanged) as the User-Agent sent
     * to the host while its clearance cookie is in the jar; with [alsoDefault] also as the app's default User-Agent.
     * Nothing is stored when the User-Agent is invalid or no pasted cookie is usable.
     */
    fun saveManual(entry: ManualEntry, cookiesText: String, userAgentText: String, alsoDefault: Boolean = false): SaveResult {
        val url = entry.url.toHttpUrlOrNull() ?: return SaveResult.InvalidCookie(entry.url)
        val parsed = ManualCookies.parse(cookiesText)
        val cookies = ManualCookies.toCookies(url, parsed.pairs, clock())
        val skipped = parsed.skipped + parsed.pairs.map { it.first }.filter { name -> cookies.none { it.name == name } }
        if (cookies.isEmpty() && skipped.isNotEmpty()) return SaveResult.InvalidCookie(skipped.joinToString())
        val userAgent = userAgentText.trim().ifEmpty { entry.userAgent }
        try {
            // OkHttp checks for valid values internally (Android's settings check)
            Headers.Builder().add("User-Agent", userAgent)
        } catch (_: IllegalArgumentException) {
            return SaveResult.UserAgentInvalid
        }

        if (cookies.isNotEmpty()) {
            network.cookieJar.removeBrowserCookies(url, cookies.map { it.name })
            network.cookieJar.storePastedCookies(cookies)
        }
        val hosts = hostStateProvider()
        // The pasted clearance (or the one already stored) is bound to this User-Agent.
        if (userAgent != entry.sourceUserAgent) {
            hosts.setUserAgent(entry.host, userAgent, clearance(url))
        } else {
            hosts.removeUserAgent(entry.host)
        }
        // A new manual attempt: the interceptor tries this host again at once.
        hosts.clearFailure(entry.host)

        val defaultChanged = alsoDefault && userAgent != networkPreferences.defaultUserAgent().get()
        if (defaultChanged) networkPreferences.defaultUserAgent().set(userAgent)
        return SaveResult.Saved(
            cookies = cookies.size,
            userAgentChanged = userAgent != entry.userAgent,
            defaultUserAgentChanged = defaultChanged,
            skipped = skipped,
        )
    }

    /** Opens [url] in the system browser again; `false` when it could not be opened. */
    fun openInBrowser(url: String): Boolean = browserOpener(url)

    /** Android's WebView "Clear cookies": removes the cookies the app sends to [url]; returns how many. */
    fun clearCookies(url: String): Int {
        val httpUrl = url.toHttpUrlOrNull() ?: return 0
        hostStateProvider().run {
            removeUserAgent(httpUrl.host)
            clearFailure(httpUrl.host)
        }
        return network.cookieJar.removeBrowserCookies(httpUrl)
    }

    private fun clearance(url: HttpUrl): String? =
        network.cookieJar.get(url).firstOrNull { it.name == CloudflareChallenge.CLEARANCE_COOKIE }?.value

    /** The source's headers, as Android's `WebViewScreenModel` gives them to the WebView. */
    private fun sourceHeaders(sourceId: Long?): Headers {
        val source = sourceId?.let { sourceManager.get(it) } as? HttpSource ?: return Headers.headersOf()
        return try {
            source.headers
        } catch (e: Exception) {
            xLogE("Failed to build headers", e)
            Headers.headersOf()
        }
    }

    companion object {
        /** How long an interactive embedded browser may stay open. */
        val INTERACTIVE_TIMEOUT: Duration = 30.minutes
    }
}
