package android.webkit

import app.komikku.desktop.compat.DesktopCookieManager
import io.kotest.matchers.shouldBe
import okhttp3.Cookie
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CookieManagerTest {

    @TempDir
    lateinit var dir: File

    // `Max-Age` is resolved against the real clock while parsing; tests then move this clock.
    private var now = System.currentTimeMillis()
    private val file get() = dir.resolve("cookies.json")

    private fun manager() = DesktopCookieManager(file) { now }

    @Test
    fun `matches domain, path and secure`() {
        val manager = manager()
        manager.setCookie("https://www.example.com/", "a=1")
        manager.setCookie("https://www.example.com/", "b=2; Domain=example.com; Path=/manga")
        manager.setCookie("https://www.example.com/", "c=3; Secure")

        manager.getCookie("https://www.example.com/") shouldBe "a=1; c=3"
        manager.getCookie("https://www.example.com/manga/1") shouldBe "b=2; a=1; c=3"
        manager.getCookie("http://www.example.com/") shouldBe "a=1"
        manager.getCookie("https://cdn.example.com/manga") shouldBe "b=2"
        manager.getCookie("https://other.com/") shouldBe null
        manager.hasCookies() shouldBe true
    }

    @Test
    fun `full cookies and removal whatever the domain and path (P7)`() {
        val manager = manager()
        manager.setCookie("https://www.example.com/", "cf_clearance=x; Domain=example.com; Path=/")
        manager.setCookie("https://www.example.com/manga/1", "deep=1; Path=/manga")
        manager.setCookie("https://www.example.com/", "keep=1")
        manager.setCookie("https://other.com/", "cf_clearance=other")

        val full = manager.cookiesFor("https://www.example.com/manga/1")
        full.map { Triple(it.name, it.domain, it.hostOnly) } shouldBe listOf(
            Triple("cf_clearance", "example.com", false),
            Triple("deep", "www.example.com", true),
            Triple("keep", "www.example.com", true),
        )
        manager.cookiesFor("not a url") shouldBe emptyList()

        // A `Max-Age=0` for the page's own host and default path (`AndroidCookieJar.remove`) misses a site-wide cookie.
        manager.setCookie("https://www.example.com/manga/1", "cf_clearance=; Max-Age=0")
        manager.getCookie("https://www.example.com/manga/1") shouldBe "deep=1; cf_clearance=x; keep=1"

        manager.removeFor("https://www.example.com/manga/1", listOf("cf_clearance", "deep")) shouldBe 2
        manager.getCookie("https://www.example.com/manga/1") shouldBe "keep=1"
        manager.removeFor("https://www.example.com/") shouldBe 1
        manager.getCookie("https://www.example.com/") shouldBe null
        manager.getCookie("https://other.com/") shouldBe "cf_clearance=other"
    }

    @Test
    fun `host-wide removal takes every path and parent-domain cookie of the host and nothing else (P23)`() {
        val manager = manager()
        manager.setCookie("https://www.example.com/", "root=1")
        manager.setCookie("https://www.example.com/manga/1", "deep=1; Path=/manga")
        manager.setCookie("https://www.example.com/", "secure=1; Secure; Path=/other")
        manager.setCookie("https://www.example.com/", "cf_clearance=x; Domain=example.com; Path=/")
        manager.setCookie("https://www.example.com/", "wide=1; Domain=example.com; Path=/reader")
        // Not the host's: a sibling host's own cookie, a subdomain's, another site's.
        manager.setCookie("https://api.example.com/", "api=1")
        manager.setCookie("https://cdn.www.example.com/", "cdn=1")
        manager.setCookie("https://other.com/", "root=other; Path=/manga")
        manager.setCookie("https://notexample.com/", "near=1")

        // Only the root-path cookies are sent to the base address, so `removeFor` would leave the others.
        manager.cookiesFor("http://www.example.com/").map { it.name } shouldBe listOf("root", "cf_clearance")

        manager.removeForHost("http://www.example.com/") shouldBe 5

        manager.cookiesFor("https://www.example.com/manga/1") shouldBe emptyList()
        manager.cookiesFor("https://www.example.com/other/x") shouldBe emptyList()
        manager.cookiesFor("https://www.example.com/reader/x") shouldBe emptyList()
        manager.getCookie("https://api.example.com/") shouldBe "api=1"
        manager.getCookie("https://cdn.www.example.com/") shouldBe "cdn=1"
        manager.getCookie("https://other.com/manga/1") shouldBe "root=other"
        manager.getCookie("https://notexample.com/") shouldBe "near=1"
        manager.removeForHost("not a url") shouldBe 0
    }

    @Test
    fun `host-wide removal with subdomains also takes the subdomains' cookies and nothing else (P23 review)`() {
        val manager = manager()
        manager.setCookie("https://example.com/", "root=1; Path=/manga")
        manager.setCookie("https://www.example.com/", "www=1")
        manager.setCookie("https://www.example.com/", "wwwWide=1; Domain=www.example.com")
        manager.setCookie("https://cdn.www.example.com/", "cdn=1")
        manager.setCookie("https://other.com/", "www=other")
        manager.setCookie("https://notexample.com/", "near=1")
        manager.setCookie("https://www.notexample.com/", "nearWww=1")

        manager.removeForHost("https://www.example.com/") shouldBe 2
        manager.getCookie("https://cdn.www.example.com/") shouldBe "cdn=1"
        manager.removeForHost("https://example.com/", includeSubdomains = true) shouldBe 2

        manager.cookiesFor("https://cdn.www.example.com/") shouldBe emptyList()
        manager.cookiesFor("https://example.com/manga/1") shouldBe emptyList()
        manager.getCookie("https://other.com/") shouldBe "www=other"
        manager.getCookie("https://notexample.com/") shouldBe "near=1"
        manager.getCookie("https://www.notexample.com/") shouldBe "nearWww=1"
    }

    @Test
    fun `a cookie the site sets replaces a pasted one of the same name on another domain (P7 review)`() {
        val manager = manager()
        fun pasted(name: String, value: String, domain: String, hostOnly: Boolean) = Cookie.Builder()
            .name(name).value(value).path("/").expiresAt(now + 3_600_000)
            .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
            .build()
        manager.setPastedCookies(
            listOf(
                pasted("cf_clearance", "pasted", "example.com", hostOnly = false),
                pasted("session", "pasted", "www.example.com", hostOnly = true),
            ),
        )
        manager.setCookie("https://www.example.com/", "keep=1; Domain=example.com")
        manager.isPasted("https://www.example.com/", "cf_clearance") shouldBe true

        // The site's host-only clearance and domain-wide session: the pasted ones would otherwise be sent too.
        manager.setCookie("https://www.example.com/", "cf_clearance=site")
        manager.setCookie("https://www.example.com/", "session=site; Domain=example.com")

        manager.getCookie("https://www.example.com/") shouldBe "keep=1; cf_clearance=site; session=site"
        manager.isPasted("https://www.example.com/", "cf_clearance") shouldBe false
        // An ordinary cookie of the same name on another domain is not removed: only pasted ones are.
        manager.setCookie("https://www.example.com/", "keep=2")
        manager.getCookie("https://www.example.com/") shouldBe "keep=1; cf_clearance=site; session=site; keep=2"

        // The mark survives a restart (the site's cookies above are session cookies and do not).
        manager.setPastedCookies(listOf(pasted("theme", "dark", "www.example.com", hostOnly = true)))
        manager.flush()
        val reloaded = manager()
        reloaded.isPasted("https://www.example.com/", "theme") shouldBe true
        reloaded.setCookie("https://www.example.com/", "theme=light; Domain=example.com")
        reloaded.getCookie("https://www.example.com/") shouldBe "theme=light"
    }

    @Test
    fun `replaces, expires and deletes cookies`() {
        val manager = manager()
        manager.setCookie("https://example.com/", "a=1")
        manager.setCookie("https://example.com/", "a=2")
        manager.getCookie("https://example.com/") shouldBe "a=2"

        manager.setCookie("https://example.com/", "a=; Max-Age=-1")
        manager.getCookie("https://example.com/") shouldBe null

        manager.setCookie("https://example.com/", "t=1; Max-Age=60")
        manager.getCookie("https://example.com/") shouldBe "t=1"
        now += 61_000
        manager.getCookie("https://example.com/") shouldBe null
    }

    @Test
    fun `remove calls report through callbacks`() {
        val manager = manager()
        manager.setCookie("https://example.com/", "session=1")
        manager.setCookie("https://example.com/", "kept=1; Max-Age=3600")

        var result: Boolean? = null
        manager.removeSessionCookies { result = it }
        result shouldBe true
        manager.getCookie("https://example.com/") shouldBe "kept=1"

        manager.removeAllCookies { result = it }
        result shouldBe true
        manager.hasCookies() shouldBe false

        manager.setCookie("not a url", "x=1") { result = it }
        result shouldBe false
    }

    @Test
    fun `persists only persistent cookies`() {
        val manager = manager()
        manager.setCookie("https://example.com/", "session=1")
        manager.setCookie("https://example.com/", "kept=1; Max-Age=3600; HttpOnly")
        manager.setCookie("https://sub.example.com/", "wide=1; Domain=example.com; Expires=Wed, 01 Jan 2200 00:00:00 GMT")
        manager.flush()

        val reloaded = manager()
        reloaded.getCookie("https://example.com/") shouldBe "kept=1; wide=1"
        // Host-only cookies stay host-only after a reload.
        reloaded.getCookie("https://sub.example.com/") shouldBe "wide=1"
    }

    @Test
    fun `accept flag`() {
        val manager = manager()
        manager.setAcceptCookie(false)
        manager.acceptCookie() shouldBe false
        manager.setCookie("https://example.com/", "a=1")
        manager.getCookie("https://example.com/") shouldBe null
    }
}
