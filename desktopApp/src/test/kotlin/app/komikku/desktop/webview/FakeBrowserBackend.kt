package app.komikku.desktop.webview

import app.komikku.network.browser.BrowserAvailability
import app.komikku.network.browser.BrowserBackend
import app.komikku.network.browser.BrowserPage
import app.komikku.network.browser.BrowserSession
import app.komikku.network.browser.BrowserSessionResult
import okhttp3.Cookie
import java.util.Collections

/**
 * A [BrowserBackend] for headless tests: no browser is started. [browse] plays the page load and returns the cookies the
 * "browser" holds afterwards; the session ends as a real backend would, by testing [BrowserSession.until] against the
 * page (met → `CONDITION_MET`, else [unmetOutcome]).
 */
class FakeBrowserBackend(
    var availability: BrowserAvailability = BrowserAvailability.Available,
    var userAgent: String? = null,
    var unmetOutcome: BrowserSessionResult.Outcome = BrowserSessionResult.Outcome.TIMED_OUT,
    var browse: (BrowserSession) -> List<Cookie> = { it.cookies },
) : BrowserBackend {

    val sessions: MutableList<BrowserSession> = Collections.synchronizedList(mutableListOf())

    override val name: String = "fake"

    override fun availability(): BrowserAvailability = availability

    override fun run(session: BrowserSession): BrowserSessionResult {
        sessions += session
        val cookies = browse(session)
        val page = BrowserPage(url = session.url, statusCode = 200, cookies = cookies)
        val outcome = if (session.until.isMet(page)) BrowserSessionResult.Outcome.CONDITION_MET else unmetOutcome
        return BrowserSessionResult(outcome, session.url, cookies, userAgent ?: session.userAgent)
    }

    companion object {
        fun clearance(session: BrowserSession, value: String = "solved"): Cookie = Cookie.Builder()
            .name("cf_clearance")
            .value(value)
            .hostOnlyDomain(session.url.host)
            .path("/")
            .expiresAt(System.currentTimeMillis() + 3_600_000)
            .build()
    }
}
