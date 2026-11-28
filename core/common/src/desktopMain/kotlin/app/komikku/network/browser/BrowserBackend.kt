package app.komikku.network.browser

import okhttp3.Cookie
import okhttp3.HttpUrl
import kotlin.time.Duration

/**
 * KMK: a browser the desktop app can drive, standing in for Android's `android.webkit.WebView` in the Cloudflare
 * interceptor and in "Open in WebView" (P7, docs/kmp-desktop/PROGRESS.md "P7 — WebView and Cloudflare").
 *
 * The desktop ships no embedded browser yet ([NoBrowserBackend] is the default in [BrowserBackends]); a backend
 * (JCEF, an external solver, a test fake) is registered with [BrowserBackends.current]. The browser has its own
 * cookie store: a session gets the app's cookies for the page in [BrowserSession.cookies] and hands back the
 * browser's in [BrowserSessionResult.cookies]; callers store them in the app's cookie jar.
 */
interface BrowserBackend {

    /** Shown in logs and errors. */
    val name: String

    /**
     * Whether the backend can show pages to the user ([BrowserSession.interactive], "Open in WebView"); an external
     * solver (FlareSolverr, P8) cannot, so "Open in WebView" keeps the manual flow with it.
     */
    val showsPages: Boolean get() = true

    /** Whether [run] can be used now. Must be cheap and must not start the browser. */
    fun availability(): BrowserAvailability

    /**
     * Loads [BrowserSession.url] and runs until [BrowserSession.until] is met for a page, the user closes an
     * interactive session or [BrowserSession.timeout] passes, whichever comes first. Blocks the calling thread (OkHttp
     * interceptors cannot suspend); never call it on the UI thread. Should stop when the calling thread is interrupted
     * (a cancelled "Open in WebView").
     *
     * @throws BrowserUnavailableException when the browser cannot be started.
     */
    fun run(session: BrowserSession): BrowserSessionResult
}

/** What a [BrowserBackend] loads and until when. */
data class BrowserSession(
    val url: HttpUrl,
    /** Request headers for the first load, already filtered for a browser (`CloudflareChallenge.browserHeaders`). */
    val headers: Map<String, String> = emptyMap(),
    /** The User-Agent the browser should send, so that cookies bound to it (`cf_clearance`) work for the app. */
    val userAgent: String,
    /** The app's cookies for [url], to start the browser with. */
    val cookies: List<Cookie> = emptyList(),
    val until: BrowserCondition,
    val timeout: Duration,
    /** Shown to the user (a window the user works in) rather than hidden (the Cloudflare interceptor). */
    val interactive: Boolean = false,
    val title: String? = null,
    /**
     * Whether whoever waits for the session gave up (the Cloudflare interceptor: its OkHttp call was cancelled, P8
     * review). A backend that cannot be interrupted (an HTTP call to a solver) checks it and stops early.
     */
    val isCancelled: () -> Boolean = { false },
    /**
     * End the session at once, with [BrowserSessionResult.Outcome.INTERACTIVE_CHALLENGE], when a page posts the message
     * of a Cloudflare challenge that turned interactive (`CloudflareChallenge.isInteractiveBegin`): a hidden browser
     * cannot pass it, and waiting for [timeout] would hold the host's other requests. Set by the Cloudflare
     * interceptor, like Android's WebView (`master`'s `b672f8028e`); a backend that cannot see the page's messages (an
     * external solver) ignores it.
     */
    val endOnInteractiveChallenge: Boolean = false,
)

/** The browser's state after a page load or a cookie change, which a [BrowserCondition] is tested against. */
data class BrowserPage(
    val url: HttpUrl?,
    /** The main frame's HTTP status, when the backend knows it. */
    val statusCode: Int?,
    /** All cookies the browser holds for the session's pages. */
    val cookies: List<Cookie>,
)

/** When a [BrowserSession] is done. */
fun interface BrowserCondition {
    fun isMet(page: BrowserPage): Boolean

    companion object {
        /** Never met: an interactive session that runs until the user closes it (or the timeout). */
        val UntilClosed = BrowserCondition { false }

        /** A cookie [name] for [url] is present and its value is not [notValue] (a new clearance cookie). */
        fun cookieSet(url: HttpUrl, name: String, notValue: String? = null) = BrowserCondition { page ->
            page.cookies.any { it.name == name && it.matches(url) && it.value != notValue }
        }

        /** The browser reached a page whose address satisfies [predicate]. */
        fun urlReached(predicate: (HttpUrl) -> Boolean) = BrowserCondition { page ->
            page.url?.let(predicate) == true
        }
    }
}

/** What a [BrowserBackend.run] ended with. */
data class BrowserSessionResult(
    val outcome: Outcome,
    /** The last page's address. */
    val finalUrl: HttpUrl?,
    /** The browser's cookies when the session ended, for the app's cookie jar. */
    val cookies: List<Cookie>,
    /** The User-Agent the browser actually sent; `null` when unknown. */
    val userAgent: String?,
) {
    enum class Outcome {
        /** [BrowserSession.until] was met. */
        CONDITION_MET,

        /** [BrowserSession.timeout] passed first. */
        TIMED_OUT,

        /** The user closed the interactive browser first. */
        CLOSED,

        /** A Cloudflare challenge turned interactive in a session with [BrowserSession.endOnInteractiveChallenge]. */
        INTERACTIVE_CHALLENGE,
    }
}

sealed interface BrowserAvailability {
    data object Available : BrowserAvailability

    data class Unavailable(val reason: Reason, val detail: String? = null) : BrowserAvailability

    enum class Reason {
        /** No browser backend is built in or installed (the desktop default). */
        NOT_INSTALLED,

        /** A backend exists but cannot run here (no display, missing native files, wrong OS / CPU, …). */
        NOT_SUPPORTED,
    }
}

/**
 * A [BrowserBackend] could not start the browser, or the service behind it could not be used (an unreachable solver,
 * its error answer). The Cloudflare interceptor does not remember these as a failed solve of the host (P8 review): a
 * corrected backend set-up takes effect with the next request. Open for backends that carry a typed reason.
 */
open class BrowserUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The desktop default: no embedded browser. */
object NoBrowserBackend : BrowserBackend {
    override val name: String = "none"

    override fun availability(): BrowserAvailability =
        BrowserAvailability.Unavailable(BrowserAvailability.Reason.NOT_INSTALLED)

    override fun run(session: BrowserSession): BrowserSessionResult =
        throw BrowserUnavailableException("No browser backend is installed")
}

/** The [BrowserBackend] the desktop app uses ([NoBrowserBackend] until one is registered). */
object BrowserBackends {
    @Volatile
    var current: BrowserBackend = NoBrowserBackend
}
