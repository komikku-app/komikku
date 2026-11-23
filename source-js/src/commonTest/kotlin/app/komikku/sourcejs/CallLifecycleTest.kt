package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostHttp
import app.komikku.sourcejs.bridge.HostHttpResponse
import app.komikku.sourcejs.bridge.HostLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How a call ends: settled results, timers, rejections handled later (PROGRESS.md "Review fixes (I2b)"). */
class CallLifecycleTest {

    private fun names(page: app.komikku.sourcejs.model.JsMangaPage) = page.list.map { it.name }

    @Test
    fun aSettledResultIsReturnedWhileStrayJavaScriptKeepsRunning() = runTest(timeout = 60.seconds) {
        val extension = TestHost(limits = JsHostLimits(callTimeoutMillis = 5_000)).extension(
            provider(
                """
                async getPopular(page) {
                  (async function () { for (let i = 0; i < 20; i++) await null; while (true) {} })();
                  setTimeout(function () { while (true) {} }, 0);
                  return { list: [{ name: "settled", link: "l" }] };
                }
                """,
            ),
        )
        val start = TimeSource.Monotonic.markNow()
        assertEquals(listOf("settled"), names(extension.getPopular(1)))
        assertTrue(start.elapsedNow().inWholeMilliseconds < 4_000, "took ${start.elapsedNow()}")
    }

    @Test
    fun clearTimeoutCancelsATimerWhoseWaitAlreadyEnded() = runTest(timeout = 60.seconds) {
        val extension = TestHost().extension(
            provider(
                """
                async getPopular(page) {
                  const log = [];
                  return await new Promise(function (resolve) {
                    let b;
                    setTimeout(function () { log.push("A"); clearTimeout(b); }, 10);
                    b = setTimeout(function () { log.push("B"); }, 10);
                    setTimeout(function () { resolve({ list: [{ name: log.join(), link: "l" }] }); }, 200);
                  });
                }
                """,
            ),
        )
        repeat(5) { assertEquals(listOf("A"), names(extension.getPopular(1))) }
    }

    @Test
    fun aHostRequestThatFailsWhileTheSourceAwaitsSomethingElseCanBeHandledLater() = runTest(timeout = 60.seconds) {
        val http = HostHttp { request ->
            if (request.url.endsWith("/bad")) throw IllegalStateException("offline")
            delay(300)
            HostHttpResponse(200, emptyList(), "ok".encodeToByteArray())
        }
        val extension = TestHost(http = http).extension(
            provider(
                """
                async getPopular(page) {
                  const failing = new Client().get("https://example.org/bad");
                  const ok = await new Client().get("https://example.org/ok");
                  try { await failing; } catch (e) { return { list: [{ name: ok.body + "|" + e.message, link: "l" }] }; }
                }
                async getLatestUpdates(page) {
                  const failing = new Client().get("https://example.org/bad");
                  await new Client().get("https://example.org/ok");
                  await failing;
                }
                async search(query, page, filters) {
                  // The source's own promise: still fails the call when it is handled only after another wait.
                  const own = Promise.reject(new Error("own"));
                  await new Client().get("https://example.org/ok");
                  try { await own; } catch (e) {}
                  return { list: [] };
                }
                """,
            ),
        )
        assertEquals(listOf("ok|HTTP GET https://example.org/bad failed: offline"), names(extension.getPopular(1)))
        // Not handled at all: the host failure keeps its channel.
        assertEquals("http", assertFailsWith<JsSourceException.BridgeError> { extension.getLatestUpdates(1) }.channel)
        assertEquals("Error: own", assertFailsWith<JsSourceException.ScriptError> { extension.search("q", 1, emptyList()) }.jsMessage)
    }

    @Test
    fun promiseReturningCallbacksAreLoggedNotFatal() = runTest(timeout = 60.seconds) {
        val test = TestHost()
        val extension = test.extension(
            provider(
                """
                async getSourcePreferences() { throw new Error("async preferences"); }
                async getPopular(page) {
                  setTimeout(async function () { throw new Error("async timer"); }, 0);
                  setTimeout(async function () { await null; throw new Error("later timer"); }, 0);
                  await new Promise((resolve) => setTimeout(resolve, 100));
                  let pref;
                  try { pref = new SharedPreferences().get("q"); } catch (e) { pref = e.message; }
                  return { list: [{ name: String(pref), link: "l" }] };
                }
                """,
            ),
        )
        assertEquals(listOf("Error when getting source preference \"q\": no such preference"), names(extension.getPopular(1)))
        val logged = test.log.lines.filter { it.first == HostLog.Level.ERROR }.map { it.second }
        assertTrue("setTimeout callback failed: Error: async timer" in logged && "setTimeout callback failed: Error: later timer" in logged, "$logged")
    }
}
