package app.komikku.sourcejs

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What the prelude's lockdown (a non-extensible `Object.prototype`, locked `Promise.prototype.constructor` / `then`)
 * costs extensions; kept on purpose (security, PROGRESS.md "Review fixes (I2b)"). Class bodies are strict, the
 * extension's top level is not.
 */
class PrototypeLockdownTest {

    private fun name(body: String, header: String = "") = provider(
        "async getPopular(page) { $body return { list: [{ name: String(result), link: 'l' }] }; }",
        header = header,
    )

    @Test
    fun objectPrototypeAdditionsFailInStrictCodeAndAreDroppedElsewhere() = runTest {
        val strict = TestHost().extension(name("Object.prototype.foo = function () { return 1; }; const result = 'added';"))
        assertEquals("TypeError: object is not extensible", assertFailsWith<JsSourceException.ScriptError> { strict.getPopular(1) }.jsMessage)
        val sloppy = TestHost().extension(name("const result = typeof ({}).bar;", header = "Object.prototype.bar = function () { return 2; };"))
        assertEquals("undefined", sloppy.getPopular(1).list.single().name)
        // Existing properties stay writable.
        val existing = TestHost().extension(name("Object.prototype.toString = function () { return 'mine'; }; const result = String({});"))
        assertEquals("mine", existing.getPopular(1).list.single().name)
    }

    @Test
    fun promiseHeirsCannotAssignConstructorOrThen() = runTest {
        val strict = TestHost().extension(
            name("function P() {} P.prototype = Object.create(Promise.prototype); P.prototype.constructor = P; const result = 'assigned';"),
        )
        assertEquals("TypeError: 'constructor' is read-only", assertFailsWith<JsSourceException.ScriptError> { strict.getPopular(1) }.jsMessage)
        val sloppy = TestHost().extension(
            name(
                "const result = P.prototype.constructor === P;",
                header = "function P() {} P.prototype = Object.create(Promise.prototype); P.prototype.constructor = P;",
            ),
        )
        assertEquals("false", sloppy.getPopular(1).list.single().name)
        // defineProperty still works on the heir itself.
        val defined = TestHost().extension(
            name(
                "function P() {} P.prototype = Object.create(Promise.prototype); " +
                    "Object.defineProperty(P.prototype, 'constructor', { value: P }); const result = P.prototype.constructor === P;",
            ),
        )
        assertEquals("true", defined.getPopular(1).list.single().name)
    }
}
