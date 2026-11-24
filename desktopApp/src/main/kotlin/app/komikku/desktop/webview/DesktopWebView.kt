package app.komikku.desktop.webview

import app.komikku.desktop.platform.DesktopBrowser
import app.komikku.network.browser.BrowserAvailability
import app.komikku.network.browser.BrowserBackend
import app.komikku.network.browser.BrowserBackends
import app.komikku.network.browser.BrowserCondition
import app.komikku.network.browser.BrowserSession
import app.komikku.network.browser.BrowserSessionResult
import app.komikku.network.browser.browserCookies
import app.komikku.network.browser.removeBrowserCookies
import app.komikku.network.browser.storeBrowserCookies
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.interceptor.CloudflareChallenge
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.log.xLogE
import exh.log.xLogW
import okhttp3.Headers
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
 *   optionally, that browser's User-Agent ([Opened.Manual], [saveManual]). The User-Agent goes to Android's
 *   `default_user_agent` preference ([NetworkPreferences.defaultUserAgent]), which sources read when they are created,
 *   so it applies after a restart (Android's "Requires app restart" too).
 */
class DesktopWebView(
    private val network: NetworkHelper,
    private val networkPreferences: NetworkPreferences,
    private val sourceManager: SourceManager,
    private val backendProvider: () -> BrowserBackend = { BrowserBackends.current },
    private val browserOpener: (String) -> Boolean = DesktopBrowser::open,
    private val clock: () -> Long = System::currentTimeMillis,
    private val interactiveTimeout: Duration = INTERACTIVE_TIMEOUT,
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
        /** The User-Agent the source sends now (its own header, else the app's default). */
        val userAgent: String,
        /** Names of the cookies the app holds for [url] now. */
        val storedCookies: List<String>,
    )

    sealed interface SaveResult {
        data class Saved(val cookies: Int, val userAgentChanged: Boolean) : SaveResult

        data class InvalidCookie(val entry: String) : SaveResult

        data object UserAgentBlank : SaveResult

        data object UserAgentInvalid : SaveResult
    }

    /** Blocks while an embedded browser runs: call it off the UI thread. */
    fun open(url: String, sourceId: Long?): Opened {
        val httpUrl = url.toHttpUrlOrNull() ?: return Opened.InvalidUrl(url)
        val headers = sourceHeaders(sourceId)
        val userAgent = headers["User-Agent"]?.takeIf { it.isNotBlank() } ?: network.defaultUserAgentProvider()

        val backend = backendProvider()
        val available = try {
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
                userAgent = userAgent,
                storedCookies = network.cookieJar.get(httpUrl).map { it.name }.distinct(),
            ),
        )
    }

    /**
     * Stores the pasted [cookiesText] for [entry]'s site (see [ManualCookies]), replacing cookies of the same names, and
     * [userAgentText] as the default User-Agent when it differs from [ManualEntry.userAgent]. Nothing is stored when
     * either is invalid.
     */
    fun saveManual(entry: ManualEntry, cookiesText: String, userAgentText: String): SaveResult {
        val url = entry.url.toHttpUrlOrNull() ?: return SaveResult.InvalidCookie(entry.url)
        val pairs = when (val parsed = ManualCookies.parse(cookiesText)) {
            is ManualCookies.Parsed.Invalid -> return SaveResult.InvalidCookie(parsed.entry)
            is ManualCookies.Parsed.Cookies -> parsed.pairs
        }
        val userAgent = userAgentText.trim()
        if (userAgent.isEmpty()) return SaveResult.UserAgentBlank
        try {
            // OkHttp checks for valid values internally (Android's settings check)
            Headers.Builder().add("User-Agent", userAgent)
        } catch (_: IllegalArgumentException) {
            return SaveResult.UserAgentInvalid
        }

        if (pairs.isNotEmpty()) {
            network.cookieJar.removeBrowserCookies(url, pairs.map { it.first })
            network.cookieJar.storeBrowserCookies(ManualCookies.toCookies(url, pairs, clock()))
        }
        val userAgentChanged = userAgent != entry.userAgent
        if (userAgentChanged) networkPreferences.defaultUserAgent().set(userAgent)
        return SaveResult.Saved(cookies = pairs.size, userAgentChanged = userAgentChanged)
    }

    /** Opens [url] in the system browser again; `false` when it could not be opened. */
    fun openInBrowser(url: String): Boolean = browserOpener(url)

    /** Android's WebView "Clear cookies": removes the cookies the app sends to [url]; returns how many. */
    fun clearCookies(url: String): Int =
        url.toHttpUrlOrNull()?.let { network.cookieJar.removeBrowserCookies(it) } ?: 0

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
