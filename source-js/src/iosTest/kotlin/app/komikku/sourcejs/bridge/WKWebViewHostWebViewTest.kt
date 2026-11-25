package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.bridge.LoopbackHttpServer.Response
import app.komikku.sourcejs.mainRunLoopTest
import app.komikku.sourcejs.webKitTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSURL
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * [WKWebViewHostWebView] with a real `WKWebView` in the simulator test process, against [LoopbackHttpServer] on
 * `127.0.0.1` (no network). The test process has no `UIApplication`, so the web views run offscreen; the tests run in
 * [mainRunLoopTest], which serves the main queue the web view is driven on.
 */
class WKWebViewHostWebViewTest {

    /** This test's cookie names (the shared cookie storage outlives the test). */
    private val cookie = "kmkwv${Random.nextInt(0, Int.MAX_VALUE)}"
    private val requests = AtomicReference(emptyList<String>())
    private val pings = AtomicInt(0)
    private val logs = AtomicReference(emptyList<String>())

    private val server = LoopbackHttpServer { request ->
        requests.update { it + "${request.target} ${request.headers["x-token"]}" }
        when (request.target) {
            "/page", "/dir%20%C3%A9/page" -> html(
                """
                <html><head><title>Fixture</title><script>
                window.readyFired = false;
                window.addEventListener('flutterInAppWebViewPlatformReady', function () { window.readyFired = true; });
                </script></head><body><p id="v">from page</p></body></html>
                """,
            )
            "/later" -> html(
                """
                <html><body><script>
                setTimeout(function () { window.flutter_inappwebview.callHandler('setResponse', 'later'); }, 300);
                </script></body></html>
                """,
            )
            "/outer" -> html("""<html><body><iframe src="/frame"></iframe></body></html>""")
            "/frame" -> html(
                """<html><body><script>window.flutter_inappwebview.callHandler('setResponse', 'from frame');</script></body></html>""",
            )
            "/missing" -> Response(404, listOf("Content-Type" to "text/html"), "<html><body>gone</body></html>".encodeToByteArray())
            "/silent" -> html("<html><body>nothing to report</body></html>")
            "/ticker" -> html(
                """
                <html><body><script>
                setInterval(function () { fetch('/ping?' + Date.now()).catch(function () {}); }, 100);
                </script></body></html>
                """,
            )
            "/cookies" -> html("""<html><body><p id="c">${request.headers["cookie"].orEmpty()}</p></body></html>""")
            "/slow-report" -> html(
                """
                <html><body><script>
                setTimeout(function () { window.flutter_inappwebview.callHandler('setResponse', 'done'); }, 300);
                </script></body></html>
                """,
            )
            else -> {
                if (request.target.startsWith("/ping")) pings.incrementAndGet()
                Response(404)
            }
        }
    }
    private val base = server.baseUrl
    private val webView = WKWebViewHostWebView(log = { message -> logs.update { it + message } })

    @AfterTest
    fun stop() {
        server.close()
        val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
        storage.cookiesForURL(NSURL(string = base)).orEmpty()
            .filterIsInstance<NSHTTPCookie>()
            .filter { it.name.startsWith(cookie) }
            .forEach { storage.deleteCookie(it) }
    }

    @Test
    fun runsTheScriptsAfterLoadAndReturnsTheReportedValue() = webKitTest {
        val value = webView.evaluate(
            url = "$base/dir é/page",
            headers = mapOf("X-Token" to "t1"),
            scripts = listOf(
                "window.order = ['a'];",
                "window.order.push('b'); window.flutter_inappwebview.callHandler('setResponse', " +
                    "[document.title, document.getElementById('v').textContent, window.readyFired, window.order.join('')].join('|'));",
            ),
            timeoutSeconds = 20,
        )
        assertEquals("Fixture|from page|true|ab", value)
        // The space and the non-ASCII letter were percent-encoded; the header was sent.
        assertEquals(listOf("/dir%20%C3%A9/page t1"), requests.value)
        assertEquals(0, webView.liveWebViews)
    }

    @Test
    fun aPageReportsLaterWithItsOwnTimer() = webKitTest {
        assertEquals("later", webView.evaluate("$base/later", emptyMap(), emptyList(), 20))
    }

    @Test
    fun aFrameCanReport() = webKitTest {
        assertEquals("from frame", webView.evaluate("$base/outer", emptyMap(), emptyList(), 20))
    }

    @Test
    fun anErrorStatusPageStillRunsTheScripts() = webKitTest {
        val value = webView.evaluate(
            "$base/missing",
            emptyMap(),
            listOf("window.flutter_inappwebview.callHandler('setResponse', document.body.textContent)"),
            20,
        )
        assertEquals("gone", value)
    }

    @Test
    fun aValueThatIsNotAStringRejectsAndTheCallGoesOn() = webKitTest {
        val bridge = "window.flutter_inappwebview"
        val value = webView.evaluate(
            "$base/silent",
            emptyMap(),
            listOf(
                // A script that throws is logged; the next one still runs.
                "throw new Error('broken script');",
                "$bridge.callHandler('other', 1).then(function (v) {" +
                    " return $bridge.callHandler('setResponse', 42); }).then(function () {" +
                    " $bridge.callHandler('setResponse', 'resolved'); }, function (e) {" +
                    " $bridge.callHandler('setResponse', 'rejected: ' + e.message); });",
            ),
            20,
        )
        assertTrue(value.startsWith("rejected: setResponse expects a string"), value)
        assertTrue(logs.value.any { "broken script" in it }, logs.value.toString())
    }

    @Test
    fun timesOutWithAnEmptyValue() = mainRunLoopTest {
        val start = TimeSource.Monotonic.markNow()
        assertEquals("", webView.evaluate("$base/silent", emptyMap(), listOf("1 + 1"), 1))
        val elapsed = start.elapsedNow().inWholeMilliseconds
        assertTrue(elapsed in 900..6_000, "took $elapsed ms")
        assertEquals(0, webView.liveWebViews)
    }

    @Test
    fun aFailedNavigationEndsTheCallWithAnEmptyValue() = webKitTest {
        val closed = LoopbackHttpServer { Response(200) }
        val url = closed.baseUrl + "/page"
        closed.close()
        val start = TimeSource.Monotonic.markNow()
        assertEquals("", webView.evaluate(url, emptyMap(), emptyList(), 25))
        val elapsed = start.elapsedNow().inWholeMilliseconds
        assertTrue(elapsed < 15_000, "took $elapsed ms")
        assertTrue(logs.value.any { "failed" in it }, logs.value.toString())
        assertEquals(0, webView.liveWebViews)
    }

    @Test
    fun refusesUrlsThatAreNotHttpBeforeCreatingAWebView() = mainRunLoopTest {
        var created = 0
        webView.onWebViewCreated = { created++ }
        for (url in listOf(
            "file:///etc/hosts",
            "data:text/html,<p>x</p>",
            "javascript:alert(1)",
            "about:blank",
            "ftp://127.0.0.1/x",
            "/relative/page",
            "",
            "http://",
        )) {
            val error = assertFailsWith<IllegalArgumentException>(url) { webView.evaluate(url, emptyMap(), emptyList(), 5) }
            assertTrue("http or https URL" in error.message.orEmpty() || "invalid URL" in error.message.orEmpty(), error.message)
        }
        assertEquals(0, created)
        assertEquals(0, webView.liveWebViews)
        // Upper-case schemes are http too.
        assertEquals("http", WKWebViewHostWebView.webViewUrl("HTTP://127.0.0.1/x").scheme?.lowercase())
    }

    @Test
    fun cancellationTearsTheWebViewDown() = webKitTest {
        val call = async { webView.evaluate("$base/ticker", emptyMap(), emptyList(), 25) }
        withTimeout(15_000) {
            while (pings.value < 3) delay(50)
        }
        val start = TimeSource.Monotonic.markNow()
        call.cancel()
        call.join()
        assertTrue(start.elapsedNow().inWholeMilliseconds < 3_000, "cancelling took ${start.elapsedNow()}")
        assertEquals(0, webView.liveWebViews)
        // The page is gone: its timer stopped (one request may still have been on its way).
        delay(300)
        val after = pings.value
        delay(1_000)
        assertTrue(pings.value - after <= 1, "pings went on: $after then ${pings.value}")
    }

    @OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class)
    @Test
    fun noWebViewIsKeptAliveAfterTheCall() = webKitTest {
        var weak: WeakReference<Any>? = null
        webView.onWebViewCreated = { weak = WeakReference(it) }
        assertEquals("later", webView.evaluate("$base/later", emptyMap(), emptyList(), 20))
        webView.onWebViewCreated = null
        val reference = weak!!
        withTimeout(15_000) {
            while (reference.value != null) {
                GC.collect()
                delay(200)
            }
        }
        assertNull(reference.value)
    }

    @Test
    fun sharesCookiesWithTheSharedCookieStorage() = webKitTest {
        val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
        storage.setCookie(cookieOf("${cookie}_in", "v1"))
        storage.setCookie(cookieOf("${cookie}_gone", "old"))
        val value = webView.evaluate(
            "$base/cookies",
            emptyMap(),
            listOf(
                "document.cookie = '${cookie}_page=v2; path=/';" +
                    "document.cookie = '${cookie}_gone=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';" +
                    "window.flutter_inappwebview.callHandler('setResponse', document.getElementById('c').textContent);",
            ),
            20,
        )
        // The page request carried the stored cookies.
        assertTrue("${cookie}_in=v1" in value, value)
        assertTrue("${cookie}_gone=old" in value, value)
        // The page's changes are back in the shared storage.
        val stored = storedCookies()
        assertEquals("v1", stored["${cookie}_in"])
        assertEquals("v2", stored["${cookie}_page"])
        assertNull(stored["${cookie}_gone"])
    }

    @Test
    fun withoutACookieStorageEveryCallIsIsolated() = webKitTest {
        val isolated = WKWebViewHostWebView(cookieStorage = null)
        NSHTTPCookieStorage.sharedHTTPCookieStorage.setCookie(cookieOf("${cookie}_in", "v1"))
        val value = isolated.evaluate(
            "$base/cookies",
            emptyMap(),
            listOf(
                "document.cookie = '${cookie}_page=v2; path=/';" +
                    "window.flutter_inappwebview.callHandler('setResponse', document.getElementById('c').textContent + '|' + document.cookie);",
            ),
            20,
        )
        assertEquals("|${cookie}_page=v2", value)
        assertNull(storedCookies()["${cookie}_page"])
        // A second call does not see the first one's cookie.
        val again = isolated.evaluate(
            "$base/cookies",
            emptyMap(),
            listOf("window.flutter_inappwebview.callHandler('setResponse', document.cookie)"),
            20,
        )
        assertEquals("", again)
    }

    @Test
    fun atMostTwoWebViewsAtOnce() = webKitTest {
        var peak = 0
        webView.onWebViewCreated = { peak = maxOf(peak, webView.liveWebViews) }
        val values = List(4) { async { webView.evaluate("$base/slow-report", emptyMap(), emptyList(), 25) } }.awaitAll()
        assertEquals(List(4) { "done" }, values)
        assertEquals(WKWebViewHostWebView.DEFAULT_MAX_CONCURRENT, peak)
        assertEquals(0, webView.liveWebViews)
    }

    private fun storedCookies(): Map<String, String> =
        NSHTTPCookieStorage.sharedHTTPCookieStorage.cookiesForURL(NSURL(string = base)).orEmpty()
            .filterIsInstance<NSHTTPCookie>()
            .filter { it.name.startsWith(cookie) }
            .associate { it.name to it.value }

    private fun cookieOf(name: String, value: String): NSHTTPCookie = NSHTTPCookie.cookieWithProperties(
        mapOf<Any?, Any?>(
            NSHTTPCookieName to name,
            NSHTTPCookieValue to value,
            NSHTTPCookieDomain to "127.0.0.1",
            NSHTTPCookiePath to "/",
        ),
    )!!

    private fun html(text: String) =
        Response(200, listOf("Content-Type" to "text/html; charset=utf-8"), text.trimIndent().encodeToByteArray())

    private fun <T> AtomicReference<T>.update(change: (T) -> T) {
        while (true) {
            val current = value
            if (compareAndSet(current, change(current))) return
        }
    }
}
