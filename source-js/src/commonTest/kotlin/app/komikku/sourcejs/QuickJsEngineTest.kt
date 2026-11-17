package app.komikku.sourcejs

import app.komikku.sourcejs.engine.JsEngine
import app.komikku.sourcejs.engine.JsEngineException
import app.komikku.sourcejs.engine.JsEngineLimits
import app.komikku.sourcejs.engine.QuickJsEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickJsEngineTest {

    private fun withEngine(
        limits: JsEngineLimits = JsEngineLimits(),
        block: suspend (JsEngine) -> Unit,
    ) = runTest {
        val engine = QuickJsEngine.create(limits)
        try {
            block(engine)
        } finally {
            engine.close()
        }
    }

    @Test
    fun evaluatesScriptsAndCallsGlobalFunctions() = withEngine { engine ->
        engine.evaluate("function add(a, b) { return { sum: a + b } }", "add.js")
        assertEquals("""{"sum":5}""", engine.callFunction("add", "[2, 3]"))
    }

    @Test
    fun awaitsAsyncFunctionsAndReturnsNullForUndefined() = withEngine { engine ->
        engine.evaluate("async function later(x) { return [x, 'done'] }\nfunction nothing() {}", "a.js")
        assertEquals("""["x","done"]""", engine.callFunction("later", """["x"]"""))
        assertNull(engine.callFunction("nothing", "[]"))
    }

    @Test
    fun callsSyncAndSuspendHostFunctions() = withEngine { engine ->
        engine.defineFunction("hostUpper") { args -> args.single()?.uppercase() }
        engine.defineSuspendFunction("hostLater") { args ->
            delay(10)
            "later:" + args.joinToString(",")
        }
        engine.evaluate(
            "async function run() { return hostUpper('abc') + '|' + await hostLater('a', null, 3) }",
            "host.js",
        )
        assertEquals("\"ABC|later:a,null,3\"", engine.callFunction("run", "[]"))
    }

    @Test
    fun hostFunctionErrorsCanBeCaughtByScripts() = withEngine { engine ->
        engine.defineFunction("hostFail") { error("boom") }
        engine.evaluate("function run() { try { hostFail() } catch (e) { return 'caught' } }", "c.js")
        assertEquals("\"caught\"", engine.callFunction("run", "[]"))
    }

    @Test
    fun scriptErrorsCarryMessageAndStack() = withEngine { engine ->
        engine.evaluate("function inner() { throw new TypeError('bad value') }\nfunction outer() { inner() }", "e.js")
        val error = assertFailsWith<JsEngineException.Script> { engine.callFunction("outer", "[]") }
        assertEquals("TypeError: bad value", error.jsMessage)
        assertTrue(error.jsStack.orEmpty().contains("inner"), error.jsStack)
    }

    @Test
    fun memoryLimitIsEnforced() = withEngine(JsEngineLimits(memoryLimitBytes = 16L * 1024 * 1024)) { engine ->
        engine.evaluate("function grow() { const a = []; while (true) a.push('x'.repeat(1024) + a.length) }", "m.js")
        assertFailsWith<JsEngineException.OutOfMemory> { engine.callFunction("grow", "[]") }
        // The context stays usable after the failed allocation.
        engine.evaluate("function ok() { return 1 }", "ok.js")
        assertEquals("1", engine.callFunction("ok", "[]"))
    }

    @Test
    fun deepRecursionIsAJsErrorNotACrash() = withEngine { engine ->
        engine.evaluate("function deep(n) { return deep(n + 1) + 1 }", "r.js")
        val error = assertFailsWith<JsEngineException.Script> { engine.callFunction("deep", "[0]") }
        assertTrue(error.jsMessage.contains("stack overflow"), error.jsMessage)
    }
}
