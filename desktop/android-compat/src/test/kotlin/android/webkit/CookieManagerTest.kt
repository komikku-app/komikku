package android.webkit

import app.komikku.desktop.compat.DesktopCookieManager
import io.kotest.matchers.shouldBe
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
