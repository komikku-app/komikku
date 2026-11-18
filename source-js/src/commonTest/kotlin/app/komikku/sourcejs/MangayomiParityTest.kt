package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostLog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Behaviour that follows Mangayomi's host (`service.dart`) rather than a stricter reading. */
class MangayomiParityTest {

    @Test
    fun syncMembersOfTheWrongTypeFallBackToTheirDefaults() = runTest {
        for (members in listOf(
            """get supportsLatest() { return "false"; } getHeaders(url) { return "x"; } getSourcePreferences() { return {}; }""",
            """get supportsLatest() { return 0; } getHeaders(url) { return null; } getSourcePreferences() { return "[]"; }""",
            // A promise is stringified to {} (Mangayomi does not await sync members).
            """get supportsLatest() { return Promise.resolve(false); } async getHeaders(url) { return { a: "1" }; }
               async getSourcePreferences() { throw new Error("ignored"); }""",
        )) {
            val host = TestHost()
            val extension = host.extension(provider(members))
            assertTrue(extension.supportsLatest(), members)
            assertEquals(emptyMap(), extension.getHeaders(), members)
            assertEquals(emptyList(), extension.getSourcePreferences(), members)
            assertTrue(host.log.lines.count { it.first == HostLog.Level.WARN } >= 2, "${host.log.lines} $members")
        }
        assertFalse(TestHost().extension(provider("""get supportsLatest() { return false; }""")).supportsLatest())
    }

    @Test
    fun getFilterListFailuresGiveAnEmptyListAndSearchStillRuns() = runTest {
        val host = TestHost()
        val extension = host.extension(
            provider(
                """
                getFilterList() { throw new TypeError("broken filters"); }
                async search(query, page, filters) { return { list: [{ name: query + ":" + JSON.stringify(filters), link: "x" }] }; }
                """,
            ),
        )
        assertEquals(emptyList(), extension.getFilterList())
        assertEquals("q:[]", extension.search("q", 1).list.single().name)
        assertTrue(host.log.lines.all { it.first == HostLog.Level.WARN } && host.log.lines.isNotEmpty(), host.log.lines.toString())
        for (body in listOf(
            """getFilterList() { return unpackJs("x"); }""",
            """getFilterList() { return "not a list"; }""",
            """getFilterList() { return [{ type_name: "SelectFilter", name: "x", values: 3 }]; }""",
        )) {
            assertEquals(emptyList(), TestHost().extension(provider(body)).getFilterList(), body)
        }
    }

    @Test
    fun pendingTimersDoNotDelayTheResult() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                async getPopular(page) {
                  setTimeout(() => console.log("never"), 90000);
                  return { list: [{ name: "done", link: "x" }] };
                }
                """,
            ),
        )
        val start = TimeSource.Monotonic.markNow()
        assertEquals("done", extension.getPopular(1).list.single().name)
        assertTrue(start.elapsedNow().inWholeMilliseconds < 10_000, start.elapsedNow().toString())
    }

    @Test
    fun throwingTimerCallbacksAreLoggedAndClearedTimersDoNotRun() = runTest {
        val host = TestHost()
        val extension = host.extension(
            provider(
                """
                async getPopular(page) {
                  const order = [];
                  setTimeout(() => { order.push("throws"); throw new Error("tick failed"); }, 1);
                  const cleared = setTimeout(() => order.push("cleared"), 5);
                  clearTimeout(cleared);
                  await new Promise((resolve) => setTimeout(resolve, 30));
                  return { list: [{ name: order.join(","), link: "x" }] };
                }
                """,
            ),
        )
        assertEquals("throws", extension.getPopular(1).list.single().name)
        assertEquals(listOf(HostLog.Level.ERROR to "setTimeout callback failed: Error: tick failed"), host.log.lines)
    }
}
