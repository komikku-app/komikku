package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostHttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The typed error envelope: classification by markers, load errors, nullish throws, timeouts and cancellation. */
class ErrorProtocolTest {

    @Test
    fun syncMembersThatHitAnUnsupportedStubAreUnsupportedNotDefaults() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                get supportsLatest() { return parseDates(["today"], "", ""); }
                getHeaders(url) { return { a: unpackJs("x") }; }
                getSourcePreferences() { return new Document("<p>").xpathFirst("//p"); }
                """,
            ),
        )
        assertEquals("parseDates", assertFailsWith<JsSourceException.Unsupported> { extension.supportsLatest() }.feature)
        assertEquals("unpackJs", assertFailsWith<JsSourceException.Unsupported> { extension.getHeaders() }.feature)
        assertEquals("XPath (Document.xpathFirst)", assertFailsWith<JsSourceException.Unsupported> { extension.getSourcePreferences() }.feature)
    }

    @Test
    fun onlyTheBaseClassDefaultsCountAsNotImplemented() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                getHeaders(url) { throw new Error("headers not implemented"); }
                getSourcePreferences() { return super.getSourcePreferences(); }
                """,
            ),
        )
        // Mangayomi matches the text "not implemented"; Komikku only the base class's own errors.
        val error = assertFailsWith<JsSourceException.ScriptError> { extension.getHeaders() }
        assertEquals("Error: headers not implemented", error.jsMessage)
        assertEquals(emptyList(), extension.getSourcePreferences())
    }

    @Test
    fun readingAPreferenceThatIsNotDeclaredFailsLikeMangayomi() = runTest {
        val extension = TestHost().extension(provider("""getHeaders(url) { return { Referer: new SharedPreferences().get("domain") }; }"""))
        val error = assertFailsWith<JsSourceException.BridgeError> { extension.getHeaders() }
        assertEquals("pref_default", error.channel)
        assertTrue(error.detail.startsWith("Error when getting source preference"), error.detail)
    }

    @Test
    fun hostFailuresKeepTheirClassificationWhenRethrown() = runTest {
        val extension = TestHost(http = FakeHostHttp { error("refused") }).extension(
            provider(
                """
                async getPopular(page) {
                  try { await new Client().get("https://example.org/a"); } catch (e) { e.message = "changed"; throw e; }
                }
                """,
            ),
        )
        val error = assertFailsWith<JsSourceException.BridgeError> { extension.getPopular(1) }
        assertEquals("http", error.channel)
        assertEquals("HTTP GET https://example.org/a failed: refused", error.detail)
    }

    @Test
    fun topLevelAndConstructorErrorsAreLoadErrors() = runTest {
        val topLevel = assertFailsWith<JsSourceException.ScriptError> {
            TestHost().extension(provider("", header = """throw new RangeError("bad top level");""")).getPopular(1)
        }
        assertEquals("loading the source", topLevel.operation)
        assertEquals("RangeError: bad top level", topLevel.jsMessage)
        assertTrue("test.js" in topLevel.jsStack.orEmpty(), topLevel.jsStack)

        val constructor = assertFailsWith<JsSourceException.ScriptError> {
            TestHost().extension(provider("""constructor() { super(); throw "no"; }""")).supportsLatest()
        }
        assertEquals("loading the source", constructor.operation)
        assertEquals("no", constructor.jsMessage)
    }

    @Test
    fun nullishThrowsAreScriptErrors() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                async getPopular(page) { throw null; }
                async getLatestUpdates(page) { throw undefined; }
                async getDetail(url) { return Promise.reject(); }
                async getPageList(url) { Promise.reject(); await new Promise((resolve) => setTimeout(resolve, 50)); return []; }
                """,
            ),
        )
        assertEquals("null", assertFailsWith<JsSourceException.ScriptError> { extension.getPopular(1) }.jsMessage)
        assertEquals("undefined", assertFailsWith<JsSourceException.ScriptError> { extension.getLatestUpdates(1) }.jsMessage)
        assertEquals("undefined", assertFailsWith<JsSourceException.ScriptError> { extension.getDetail("x") }.jsMessage)
        // An unhandled rejection aborts the evaluation in quickjs-kt; it is a script error too.
        assertFailsWith<JsSourceException.ScriptError> { extension.getPageList("x") }
    }

    @Test
    fun aPromiseThatNeverSettlesIsAScriptError() = runTest {
        val extension = TestHost().extension(provider("""async getPopular(page) { await new Promise(() => {}); }"""))
        val error = assertFailsWith<JsSourceException.ScriptError> { extension.getPopular(1) }
        assertTrue("did not settle" in error.jsMessage, error.jsMessage)
    }

    @Test
    fun onlyTheHostsOwnLimitIsATimeout() = runTest {
        val never = CompletableDeferred<Nothing>()
        val code = provider("""async getPopular(page) { await new Client().get("https://example.org"); }""")

        val hostLimit = TestHost(http = { never.await() }, limits = JsHostLimits(callTimeoutMillis = 300)).extension(code)
        assertFailsWith<JsSourceException.Timeout> { hostLimit.getPopular(1) }

        // The caller's own timeout propagates unchanged.
        val callerLimit = TestHost(http = { never.await() }).extension(code)
        withContext(Dispatchers.Default) {
            assertFailsWith<TimeoutCancellationException> { withTimeout(300) { callerLimit.getPopular(1) } }
        }
    }

    @Test
    fun cancellationExceptionsFromServicesAreErrorsOfTheCall() = runTest {
        val extension = TestHost(http = FakeHostHttp { throw CancellationException("service gave up") }).extension(
            provider(
                """
                async getPopular(page) { await new Client().get("https://example.org/p"); }
                async getLatestUpdates(page) {
                  try { await new Client().get("https://example.org/l"); } catch (e) { return { list: [{ name: e.message, link: "x" }] }; }
                }
                """,
            ),
        )
        val error = assertFailsWith<JsSourceException.BridgeError> { extension.getPopular(1) }
        assertEquals("HTTP GET https://example.org/p failed: service gave up", error.detail)
        assertEquals("HTTP GET https://example.org/l failed: service gave up", extension.getLatestUpdates(1).list.single().name)
    }

    @Test
    fun forgedCancellationsAreScriptErrors() = runTest {
        // quickjs-kt instantiates the class an unhandled rejection's `name` names (PROGRESS.md, review fixes); the
        // host must not take such an exception for its own timeout or for the caller's cancellation.
        for (name in listOf("java.util.concurrent.CancellationException", "kotlinx.coroutines.TimeoutCancellationException")) {
            val extension = TestHost().extension(
                provider(
                    """
                    async getPopular(page) {
                      const e = new Error("forged"); e.name = "$name";
                      Promise.reject(e);
                      await new Promise((resolve) => setTimeout(resolve, 50));
                      return { list: [] };
                    }
                    """,
                ),
            )
            val error = assertFailsWith<JsSourceException.ScriptError>(name) { extension.getPopular(1) }
            // The script's own error, not the host's "did not settle" or a timeout (desktopTest ForgedThrowableTest
            // checks that quickjs-kt really instantiates the named class).
            assertEquals("forged", error.jsMessage, name)
            assertEquals("getPopular(1)", error.operation, name)
        }
    }

    @Test
    fun errorsOfHostServicesAreBridgeErrors() = runTest {
        // An Error, not an Exception (a service's TODO(), a stack overflow): still a host failure the script sees.
        val http = FakeHostHttp { throw NotImplementedError("not yet") }
        val extension = TestHost(http = http).extension(
            provider(
                """
                async getPopular(page) { await new Client().get("https://example.org/x"); }
                async getLatestUpdates(page) {
                  try { await new Client().get("https://example.org/x"); } catch (e) { return { list: [{ name: e.message, link: "l" }] }; }
                }
                """,
            ),
        )
        val error = assertFailsWith<JsSourceException.BridgeError> { extension.getPopular(1) }
        assertEquals("http", error.channel)
        assertEquals("HTTP GET https://example.org/x failed: not yet", error.detail)
        assertEquals("HTTP GET https://example.org/x failed: not yet", extension.getLatestUpdates(1).list.single().name)
    }

    @Test
    fun hugeResponseBodiesAreRejected() = runTest {
        val http = FakeHostHttp { HostHttpResponse(200, emptyList(), ByteArray(2048)) }
        val extension = TestHost(http = http, limits = JsHostLimits(callTimeoutMillis = 20_000, maxResponseBodyBytes = 1024)).extension(
            provider("""async getPopular(page) { await new Client().get("https://example.org/big"); }"""),
        )
        val error = assertFailsWith<JsSourceException.BridgeError> { extension.getPopular(1) }
        assertEquals("HTTP GET https://example.org/big failed: response body larger than 1024 bytes", error.detail)
        assertEquals(1024L, http.requests.single().maxBodyBytes)
    }

    @Test
    fun theDefaultCallLimitIsAboveTheHttpClientTimeout() {
        assertEquals(180_000, JsHostLimits().callTimeoutMillis)
        assertEquals(50L * 1024 * 1024, JsHostLimits().maxResponseBodyBytes)
    }
}
