package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.engine.JsEngineLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class HostCoreTest {

    @Test
    fun callsAsyncAndSyncMembers() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                get supportsLatest() { return false; }
                getHeaders(url) { return { Referer: url + "/", "X-Number": 7 }; }
                async getPopular(page) {
                  await new Promise((resolve) => setTimeout(resolve, 5));
                  return { list: [{ name: "Popular " + page, link: "/m/" + page, imageUrl: "/c.jpg" }], hasNextPage: page < 3 };
                }
                """,
            ),
        )
        val page = extension.getPopular(2)
        assertEquals("Popular 2", page.list.single().name)
        assertEquals("/m/2", page.list.single().link)
        assertTrue(page.hasNextPage)
        assertFalse(extension.supportsLatest())
        assertEquals(mapOf("Referer" to "https://example.org/", "X-Number" to "7"), extension.getHeaders())
    }

    @Test
    fun exposesTheSourceObject() = runTest {
        val extension = TestHost().extension(
            provider("""async getPopular(page) { return { list: [{ name: JSON.stringify(this.source), link: "x" }], hasNextPage: false }; }"""),
        )
        val json = extension.getPopular(1).list.single().name
        assertEquals(
            """{"apiUrl":"https://api.example.org","baseUrl":"https://example.org","dateFormat":"MMMM dd, yyyy",""" +
                """"dateFormatLocale":"en_us","hasCloudflare":false,"id":652112892,"isFullData":false,"lang":"en",""" +
                """"name":"Test Source","additionalParams":"extra","notes":null}""",
            json,
        )
    }

    @Test
    fun usesMangayomiDefaultsForMembersThatAreNotImplemented() = runTest {
        val extension = TestHost().extension(provider(""))
        assertTrue(extension.supportsLatest())
        assertEquals(emptyMap(), extension.getHeaders())
        assertEquals(emptyList(), extension.getFilterList())
        assertEquals(emptyList(), extension.getSourcePreferences())
        val error = assertFailsWith<JsSourceException.ScriptError> { extension.getPopular(1) }
        assertEquals("Error: getPopular not implemented", error.jsMessage)
    }

    @Test
    fun thrownErrorsCarryTheJsMessageAndStack() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                parseList(text) { throw new TypeError("cannot parse " + text); }
                async getPopular(page) { return this.parseList("page" + page); }
                """,
            ),
        )
        val error = assertFailsWith<JsSourceException.ScriptError> { extension.getPopular(4) }
        assertEquals("TypeError: cannot parse page4", error.jsMessage)
        assertEquals("getPopular(4)", error.operation)
        assertEquals("Extension \"Test Source\" failed while getPopular(4): TypeError: cannot parse page4", error.message)
        val stack = error.jsStack.orEmpty()
        assertTrue("parseList" in stack && "test.js" in stack, stack)
    }

    @Test
    fun rejectedPromisesAreScriptErrors() = runTest {
        val extension = TestHost().extension(
            provider("""async getDetail(url) { return Promise.reject(new Error("gone: " + url)); }"""),
        )
        val error = assertFailsWith<JsSourceException.ScriptError> { extension.getDetail("/m/1") }
        assertEquals("Error: gone: /m/1", error.jsMessage)
    }

    @Test
    fun loadFailuresAreReportedAsLoading() = runTest {
        val syntax = assertFailsWith<JsSourceException.ScriptError> { TestHost().extension("class DefaultExtension extends MProvider {").getPopular(1) }
        assertEquals("loading the source", syntax.operation)
        assertTrue(syntax.jsMessage.startsWith("SyntaxError"), syntax.jsMessage)

        val missing = assertFailsWith<JsSourceException.ScriptError> { TestHost().extension("const x = 1;").getPopular(1) }
        assertEquals("loading the source", missing.operation)
        assertTrue("DefaultExtension" in missing.jsMessage, missing.jsMessage)
    }

    @Test
    fun endlessLoopsTimeOut() = runTest {
        val host = TestHost(limits = JsHostLimits(callTimeoutMillis = 500))
        val extension = host.extension(provider("""async getPopular(page) { while (true) {} }"""))
        val start = TimeSource.Monotonic.markNow()
        val error = assertFailsWith<JsSourceException.Timeout> { extension.getPopular(1) }
        assertEquals(500, error.timeoutMillis)
        assertTrue(start.elapsedNow().inWholeMilliseconds < 10_000)
        // The host keeps working afterwards.
        val ok = host.extension(provider("""async getPopular(page) { return { list: [], hasNextPage: false }; }"""))
        assertEquals(0, ok.getPopular(1).list.size)
    }

    @Test
    fun hangingNetworkWaitsTimeOut() = runTest {
        val never = CompletableDeferred<Nothing>()
        val host = TestHost(
            http = { never.await() },
            limits = JsHostLimits(callTimeoutMillis = 300),
        )
        val extension = host.extension(provider("""async getPopular(page) { await new Client().get("https://example.org"); }"""))
        assertFailsWith<JsSourceException.Timeout> { extension.getPopular(1) }
    }

    @Test
    fun runawayAllocationIsAnOutOfMemoryError() = runTest {
        val host = TestHost(limits = JsHostLimits(engine = JsEngineLimits(memoryLimitBytes = 16L * 1024 * 1024)))
        val extension = host.extension(
            provider("""async getPopular(page) { const a = []; while (true) a.push("x".repeat(1000) + a.length); }"""),
        )
        val error = assertFailsWith<JsSourceException.OutOfMemory> { extension.getPopular(1) }
        assertEquals(16L * 1024 * 1024, error.memoryLimitBytes)
    }

    @Test
    fun cancellationInterruptsTheScript() = runTest {
        val extension = TestHost().extension(provider("""async getPopular(page) { while (true) {} }"""))
        withContext(Dispatchers.Default) {
            val call = async { extension.getPopular(1) }
            kotlinx.coroutines.delay(300)
            val start = TimeSource.Monotonic.markNow()
            call.cancel()
            withTimeout(5_000) { call.join() }
            assertTrue(call.isCancelled)
            assertTrue(start.elapsedNow().inWholeMilliseconds < 5_000)
            assertFailsWith<CancellationException> { call.await() }
        }
    }

    @Test
    fun stateDoesNotLeakBetweenCalls() = runTest {
        val extension = TestHost().extension(
            provider(
                body = """async getPopular(page) { calls++; globalThis.leaked = (globalThis.leaked || 0) + 1; return { list: [{ name: calls + "/" + leaked, link: "x" }], hasNextPage: false }; }""",
                header = "let calls = 0;",
            ),
        )
        repeat(3) { assertEquals("1/1", extension.getPopular(1).list.single().name) }
    }

    @Test
    fun unsupportedFeaturesNameTheFeature() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                async getPopular(page) { unpackJs("eval(function(p,a,c,k,e,d){})"); }
                async getLatestUpdates(page) { await evaluateJavascriptViaWebview("https://example.org", {}, []); }
                async getDetail(url) { return new Document("<p>").xpath("//p"); }
                """,
            ),
        )
        assertEquals("unpackJs", assertFailsWith<JsSourceException.Unsupported> { extension.getPopular(1) }.feature)
        val webView = assertFailsWith<JsSourceException.Unsupported> { extension.getLatestUpdates(1) }
        assertEquals("evaluateJavascriptViaWebview", webView.feature)
        assertTrue("WebView" in webView.message.orEmpty(), webView.message)
        assertEquals("XPath (Document.xpath)", assertFailsWith<JsSourceException.Unsupported> { extension.getDetail("x") }.feature)
    }

    @Test
    fun unhandledHostErrorsAreBridgeErrorsAndCatchableInJs() = runTest {
        val http = FakeHostHttp { throw IllegalStateException("connection refused") }
        val extension = TestHost(http = http).extension(
            provider(
                """
                async getPopular(page) { await new Client().get("https://example.org/popular"); }
                async getLatestUpdates(page) {
                  try { await new Client().get("https://example.org/latest"); } catch (e) { return { list: [{ name: e.message, link: "x" }], hasNextPage: false }; }
                }
                """,
            ),
        )
        val error = assertFailsWith<JsSourceException.BridgeError> { extension.getPopular(1) }
        assertEquals("http", error.channel)
        assertEquals("HTTP GET https://example.org/popular failed: connection refused", error.detail)
        assertEquals(
            "HTTP GET https://example.org/latest failed: connection refused",
            extension.getLatestUpdates(1).list.single().name,
        )
    }

    @Test
    fun consoleAndSetTimeoutWork() = runTest {
        val host = TestHost()
        val extension = host.extension(
            provider(
                """
                async getPopular(page) {
                  console.log({ page: page });
                  console.warn("careful", "dropped");
                  console.error(undefined);
                  const order = [];
                  await new Promise((resolve) => { setTimeout(() => { order.push("timer"); resolve(); }, 20); order.push("sync"); });
                  return { list: [{ name: order.join(","), link: "x" }], hasNextPage: false };
                }
                """,
            ),
        )
        assertEquals("sync,timer", extension.getPopular(3).list.single().name)
        assertEquals(
            listOf(HostLog.Level.INFO to """{"page":3}""", HostLog.Level.WARN to "careful", HostLog.Level.ERROR to "undefined"),
            host.log.lines,
        )
    }

    @Test
    fun extensionsMayRedeclarePreludeHelpers() = runTest {
        val extension = TestHost().extension(
            provider(
                body = """async getPopular(page) { return { list: [{ name: atob("aGk=") + unpackJs("x"), link: "x" }], hasNextPage: false }; }""",
                header = """
                    function atob(s) { return "hi"; }
                    const unpackJs = (s) => "!";
                """.trimIndent(),
            ),
        )
        assertEquals("hi!", extension.getPopular(1).list.single().name)
    }
}
