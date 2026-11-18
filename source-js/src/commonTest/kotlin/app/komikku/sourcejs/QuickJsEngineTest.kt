package app.komikku.sourcejs

import app.komikku.sourcejs.engine.JsEngine
import app.komikku.sourcejs.engine.JsEngineException
import app.komikku.sourcejs.engine.JsEngineLimits
import app.komikku.sourcejs.engine.QuickJsEngine
import app.komikku.sourcejs.engine.newJsEngineThread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuickJsEngineTest {

    private fun withEngine(
        limits: JsEngineLimits = JsEngineLimits(),
        block: suspend (JsEngine, MutableList<String?>) -> Unit,
    ) = runTest {
        val engine = QuickJsEngine.create(limits)
        val reports = mutableListOf<String?>()
        try {
            engine.defineFunction("report") { args ->
                reports += args.firstOrNull()
                null
            }
            block(engine, reports)
        } finally {
            engine.close()
        }
    }

    @Test
    fun evaluatesScriptsAndWaitsForPromiseJobs() = withEngine { engine, reports ->
        engine.evaluate("function add(a, b) { return a + b }\nreport(String(add(2, 3)))", "add.js")
        engine.evaluate("(async () => { await null; report('later') })()", "a.js")
        assertEquals<List<String?>>(listOf("5", "later"), reports)
    }

    @Test
    fun callsSyncAndSuspendHostFunctions() = withEngine { engine, reports ->
        engine.defineFunction("hostUpper") { args -> args.single()?.uppercase() }
        engine.defineSuspendFunction("hostLater") { args ->
            delay(10)
            "later:" + args.joinToString(",")
        }
        engine.evaluate("(async () => report(hostUpper('abc') + '|' + await hostLater('a', null, 3)))()", "host.js")
        assertEquals<List<String?>>(listOf("ABC|later:a,null,3"), reports)
    }

    @Test
    fun hostFunctionsCanBeDeletedFromTheGlobalObjectAndStillBeCalled() = withEngine { engine, reports ->
        engine.defineFunction("hidden") { "ok" }
        engine.evaluate("const f = globalThis.hidden; delete globalThis.hidden; report(typeof hidden + ':' + f())", "d.js")
        assertEquals<List<String?>>(listOf("undefined:ok"), reports)
    }

    @Test
    fun compilesWithoutRunningAndEvaluatesBytecode() = withEngine { engine, reports ->
        val bytecode = engine.compile("report('ran')", "b.js")
        assertEquals<List<String?>>(emptyList(), reports)
        engine.evaluate(bytecode)
        assertEquals<List<String?>>(listOf("ran"), reports)
        val error = assertFailsWith<JsEngineException.Script> { engine.compile("function (", "s.js") }
        assertTrue(error.jsMessage.startsWith("SyntaxError"), error.jsMessage)
    }

    @Test
    fun scriptErrorsCarryMessageAndStack() = withEngine { engine, _ ->
        val error = assertFailsWith<JsEngineException.Script> {
            engine.evaluate("function inner() { throw new TypeError('bad value') }\ninner()", "e.js")
        }
        assertEquals("TypeError: bad value", error.jsMessage)
        assertTrue(error.jsStack.orEmpty().contains("inner"), error.jsStack)
    }

    @Test
    fun nullIsAScriptErrorWhenMemoryIsNotExhausted() = withEngine { engine, _ ->
        assertFailsWith<JsEngineException.Script> { engine.evaluate("throw null", "n.js") }
        assertFailsWith<JsEngineException.Script> { engine.evaluate("Promise.reject(); 1", "r.js") }
    }

    @Test
    fun memoryStillHeldAfterTheFailureIsAnOutOfMemoryError() = withEngine(JsEngineLimits(memoryLimitBytes = 16L * 1024 * 1024)) { engine, _ ->
        // Recognised from the engine state (QuickJS may only manage to throw `null`).
        assertFailsWith<JsEngineException.OutOfMemory> {
            engine.evaluate("globalThis.a = []; while (true) a.push('x'.repeat(1024) + a.length)", "m.js")
        }
    }

    @Test
    fun memoryFreedDuringUnwindingLeavesTheContextUsable() = withEngine(JsEngineLimits(memoryLimitBytes = 16L * 1024 * 1024)) { engine, reports ->
        // QuickJS throws its InternalError, or `null` when it cannot even allocate that; the script may catch either.
        engine.evaluate(
            "try { (() => { const b = []; while (true) b.push('x'.repeat(1024) + b.length) })() } catch (e) { report('caught') }",
            "m.js",
        )
        engine.evaluate("report('ok')", "ok.js")
        assertEquals<List<String?>>(listOf("caught", "ok"), reports)
    }

    @Test
    fun deepRecursionIsAJsErrorNotACrash() = withEngine { engine, _ ->
        val error = assertFailsWith<JsEngineException.Script> {
            engine.evaluate("function deep(n) { return deep(n + 1) + 1 }\ndeep(0)", "r.js")
        }
        assertTrue(error.jsMessage.contains("stack overflow"), error.jsMessage)
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun aRuntimeCreatedForACancelledCallerIsClosed() = runTest {
        withContext(Dispatchers.Default) {
            val before = QuickJsEngine.liveRuntimes.load()
            val thread = newJsEngineThread("test-js", 1024 * 1024)
            // Keep the engine thread busy so the creation waits until the caller is cancelled.
            val release = CompletableDeferred<Unit>()
            launch(thread.dispatcher) { kotlinx.coroutines.runBlocking { release.await() } }
            val creation = async { QuickJsEngine.create(JsEngineLimits(), thread) }
            delay(100)
            creation.cancel()
            release.complete(Unit)
            withTimeout(5_000) { creation.join() }
            assertTrue(creation.isCancelled)
            withTimeout(5_000) {
                while (QuickJsEngine.liveRuntimes.load() != before) delay(10)
            }
        }
    }
}
