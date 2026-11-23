package app.cash.quickjs

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import com.dokar.quickjs.QuickJs as Engine
import com.dokar.quickjs.QuickJsException as EngineException

/**
 * Completion values of `evaluate` / `execute` are converted like quickjs-android without quickjs-kt's native
 * conversion seeing objects (docs/kmp-desktop/PROGRESS.md, "Review fixes (I2b)"). A regression may hang (an
 * `Object.prototype.then` thenable), hence the timeout.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class QuickJsCompletionTest {

    @Test
    fun `objects quickjs-kt cannot stringify are null, untouched`() {
        QuickJs.create().use { js ->
            js.evaluate("var window = this; window").shouldBeNull()
            js.evaluate("var a = { n: 1 }; a.self = a; a").shouldBeNull()
            js.evaluate("({ big: 10n })").shouldBeNull()
            js.evaluate("new Map([[1, this]])").shouldBeNull()
            js.evaluate("new Set([this, 1n])").shouldBeNull()
            js.evaluate("(class Foo { static get boom() { throw new Error('getter ran'); } })").shouldBeNull()
            // Neither getters nor toJSON run.
            js.evaluate(
                "var touched = []; ({ get a() { touched.push('get'); return 1; }, toJSON: function () { touched.push('toJSON'); } })",
            ).shouldBeNull()
            js.evaluate("touched.length") shouldBe 0
            // The state survives: the completion object is still the script's own.
            js.evaluate("window === this && a.self === a") shouldBe true
        }
    }

    @Test
    fun `a page script whose completion is an object leaves the instance usable (Kuaikanmanhua)`() {
        QuickJs.create().use { js ->
            js.evaluate("var window = {};").shouldBeNull()
            js.evaluate("window.__NUXT__ = (function (a) { var o = { data: [a], id: 9007199254740993n }; o.root = window; return o; })(1)")
                .shouldBeNull()
            js.evaluate("JSON.stringify(window.__NUXT__.data)") shouldBe "[1]"
        }
    }

    @Test
    fun `a throwing getter met while converting does not crash the JVM`() {
        QuickJs.create().use { js ->
            // A non-Throwable class name: quickjs-kt's native conversion used to throw it through JNI (SIGSEGV).
            val array = assertThrows<QuickJsException> {
                js.evaluate(
                    "var a = []; Object.defineProperty(a, 0, { get: function () { throw { name: 'java.lang.StringBuilder', message: 'x' }; } }); a",
                )
            }
            array.message shouldBe "x"
            js.evaluate("({ get a() { throw { name: 'java.lang.StringBuilder', message: 'x' }; } })").shouldBeNull()
            js.evaluate("1 + 1") shouldBe 2
        }
    }

    @Test
    fun `arrays keep number tags and reject what quickjs-android could not marshal`() {
        QuickJs.create().use { js ->
            (js.evaluate("[1, 0.5 * 2, 2 ** 40, -0, 'x', null, undefined, true, [2, [3]]]") as Array<*>).let { array ->
                array[0].shouldBeInstanceOf<Int>()
                array[1] shouldBe 1.0
                array[2] shouldBe 1099511627776.0
                array[3] shouldBe -0.0
                array.toList().subList(4, 8) shouldBe listOf("x", null, null, true)
                ((array[8] as Array<*>)[1] as Array<*>).toList() shouldBe listOf(3)
            }
            (js.evaluate("var sparse = [1]; sparse[2] = 3; sparse") as Array<*>).toList() shouldBe listOf(1, null, 3)
            assertThrows<QuickJsException> { js.evaluate("[1, Symbol()]") }.message shouldBe
                "Cannot marshal value Symbol() to Java"
            assertThrows<QuickJsException> { js.evaluate("[[1n]]") }.message shouldBe "Cannot marshal value 1 to Java"
            assertThrows<QuickJsException> { js.evaluate("[Promise.resolve(1)]") }.message shouldBe
                "Cannot marshal value [object Promise] to Java"
            assertThrows<QuickJsException> { js.evaluate("[function f() { return 1 }]") }.message shouldBe
                "Cannot marshal value function f() { return 1 } to Java"
            // Top-level values quickjs-android dropped.
            js.evaluate("Symbol('s')").shouldBeNull()
            js.evaluate("10n").shouldBeNull()
            js.evaluate("new String('boxed')").shouldBeNull()
        }
    }

    @Test
    fun `strings that look like quickjs-kt placeholders or errors are kept as they are`() {
        QuickJs.create().use { js ->
            js.evaluate("'Promise { <state>: \"fulfilled\" }'") shouldBe "Promise { <state>: \"fulfilled\" }"
            (js.evaluate("['Promise { <state>: \"pending\" }']") as Array<*>).toList() shouldBe
                listOf("Promise { <state>: \"pending\" }")
            assertThrows<QuickJsException> { js.evaluate("throw 'Unsupported js value type: x'") }.message shouldBe
                "Unsupported js value type: x"
        }
    }

    @Test
    fun `the appended statement changes neither completion values nor syntax errors`() {
        QuickJs.create().use { js ->
            js.evaluate("1; var later = 2;") shouldBe 1
            js.evaluate("'last'; function declared() {}") shouldBe "last"
            js.evaluate("let kept = 5; kept") shouldBe 5
            js.evaluate("kept + 1") shouldBe 6
            js.evaluate("// trailing comment").shouldBeNull()
            js.evaluate("3 // trailing comment without newline") shouldBe 3
            js.evaluate("var v = await Promise.resolve([4]); v").let { (it as Array<*>).toList() shouldBe listOf(4) }
            js.evaluate("Object.prototype.then = function () {}; 7") shouldBe 7
            js.evaluate("typeof Object.prototype.then") shouldBe "function"
            js.evaluate("delete Object.prototype.then; typeof Object.prototype.then") shouldBe "undefined"
            // Incomplete scripts stay syntax errors instead of getting the statement as their body.
            listOf("if (true)", "while (true)", "label:", "for (;;)", "do", "x =", "function f()").forEach { text ->
                assertThrows<QuickJsException> { js.evaluate(text) }
            }
            listOf("var a = 1;\nfunction (", "({", "'unterminated", "`template", "/* comment").forEach { text ->
                val error = assertThrows<QuickJsException> { js.evaluate(text, "bad.js") }
                error.message shouldBe rawSyntaxError(text)
                assertThrows<QuickJsException> { js.compile(text, "bad.js") }.message shouldBe rawSyntaxError(text)
            }
            // A run-time SyntaxError is reported as such, and the script ran once.
            js.evaluate("var runs = 0;")
            assertThrows<QuickJsException> { js.evaluate("runs++; JSON.parse('{')") }
            js.evaluate("runs") shouldBe 1
        }
    }

    @Test
    fun `compiled scripts convert their completion values in any instance`() {
        val bytecode = QuickJs.create().use { it.compile("var self = this; [self === globalThis, { a: 1 }][0]", "c.js") }
        QuickJs.create().use { js -> js.execute(bytecode) shouldBe true }
        val objectCode = QuickJs.create().use { it.compile("var w = this; w", "o.js") }
        QuickJs.create().use { js -> js.execute(objectCode).shouldBeNull() }
    }

    @Test
    fun `without the helper quickjs-kt's conversion failures still give null`() {
        // A frozen Object.prototype keeps the helper from running; the old conversion path applies.
        QuickJs.create().use { js ->
            js.evaluate("Object.freeze(Object.prototype); var w = this; w").shouldBeNull()
            js.evaluate("Symbol('s')").shouldBeNull()
            js.evaluate("({ a: 1 })").shouldBeNull()
            assertThrows<QuickJsException> { js.evaluate("throw 'Unsupported js value type: x'") }.message shouldBe
                "Unsupported js value type: x"
            assertThrows<QuickJsException> { js.evaluate("[1, { a: 1 }]") }.message shouldBe
                "Cannot marshal value [object Object] to Java"
            js.evaluate("'still works'") shouldBe "still works"
        }
        QuickJs.create().use { js ->
            js.evaluate("Object.defineProperty(Object.prototype, 'then', { value: undefined }); ({ a: 1n })").shouldBeNull()
        }
    }

    /** What QuickJS says about [text] alone, as `QuickJsException` reports it (no `SyntaxError: ` prefix). */
    private fun rawSyntaxError(text: String): String {
        val engine = Engine.create(Dispatchers.Default)
        try {
            val error = assertThrows<EngineException> { runBlocking { engine.compile(text, "bad.js", false) } }
            return error.message.orEmpty().lineSequence().first().removePrefix("SyntaxError: ")
        } finally {
            engine.close()
        }
    }
}
