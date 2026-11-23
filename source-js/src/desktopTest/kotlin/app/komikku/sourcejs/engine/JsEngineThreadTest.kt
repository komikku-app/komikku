package app.komikku.sourcejs.engine

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** Idle engine threads end; the engine carries on on a new one (PROGRESS.md "Review fixes (I2b)"). */
class JsEngineThreadTest {

    @Test
    fun anIdleEngineThreadEndsAndTheEngineCarriesOn() = runBlocking {
        val limits = JsEngineLimits()
        val thread = newJsEngineThread("test-js", limits.maxStackSizeBytes, idleMillis = 100)
        val engine = QuickJsEngine.create(limits, thread)
        try {
            val reports = mutableListOf<String?>()
            engine.defineFunction("report") { args ->
                reports += args.firstOrNull()
                null
            }
            val first = withContext(thread.dispatcher) { Thread.currentThread() }
            engine.evaluate("var kept = 41;", "a.js")
            first.join(10_000)
            assertFalse(first.isAlive)
            engine.evaluate("report(String(kept + 1)); function f(n) { return f(n + 1) + 1 } try { f(0) } catch (e) { report(String(e)) }", "b.js")
            // State kept, and deep recursion is still a JavaScript error on the new thread.
            assertTrue(reports.size == 2 && reports[0] == "42" && reports[1].orEmpty().contains("stack"), "$reports")
            assertNotSame(first, withContext(thread.dispatcher) { Thread.currentThread() })
        } finally {
            engine.close()
        }
    }
}
