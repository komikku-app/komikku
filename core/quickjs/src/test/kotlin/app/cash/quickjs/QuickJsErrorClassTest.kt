package app.cash.quickjs

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * quickjs-kt turns a JavaScript `Error` that reaches the JVM into `new <error.name>(error.message)` for any Java
 * class (docs/kmp-desktop/PLAN.md 8.7 item 5). `java.io.FileOutputStream(String)` creates the file, so a created file
 * proves the instantiation. Only temporary directories are used.
 */
class QuickJsErrorClassTest {

    @TempDir
    lateinit var dir: File

    /** Defines `DIR` and `payload(path)`, which returns an `Error` named after `java.io.FileOutputStream`. */
    private fun QuickJs.definePayload() {
        evaluate(
            "var DIR = ${jsLiteral(dir.absolutePath)};" +
                "var payload = function (path) { var e = new Error(path); e.name = 'java.io.FileOutputStream'; return e; };",
        )
    }

    @Test
    fun `errors passed to Java bindings never reach quickjs-kt`() {
        QuickJs.create().use { js ->
            js.set("host", Sink::class.java, Sink { _, _, _ -> })
            js.definePayload()
            // Installs the hidden `get` invoker too, so the scan below reaches it.
            js.evaluate("var target = { take: function () {} };")
            js.get("target", Sink::class.java).take(null, null, null)
            listOf(
                "host.take(payload(DIR + '/a'), null, null)",
                "host.take(null, payload(DIR + '/b'), null)",
                "host.take(null, [payload(DIR + '/c')], null)",
                "host.take(null, null, [payload(DIR + '/d')])",
            ).forEach { call ->
                runCatching { js.evaluate(call) }
            }
            // Every function reachable from the global object, called with an error argument.
            js.evaluate(
                """
                var names = Object.getOwnPropertyNames(globalThis);
                for (var i = 0; i < names.length; i++) {
                  var f;
                  try { f = globalThis[names[i]]; } catch (e) { continue; }
                  if (typeof f !== 'function' || f === payload) continue;
                  try { f(payload(DIR + '/g' + i), payload(DIR + '/h' + i)); } catch (e) {}
                  try { f(0, 0, payload(DIR + '/k' + i)); } catch (e) {}
                }
                void 0;
                """.trimIndent(),
            )
            dir.list()!!.toList() shouldBe emptyList()
        }
    }

    @Test
    @Disabled("quickjs-kt converts unhandled rejections natively (PLAN.md 8.7 item 5); enable once it is patched")
    fun `unhandled rejections do not instantiate Java classes`() {
        QuickJs.create().use { js ->
            js.definePayload()
            runCatching { js.evaluate("Promise.reject(payload(DIR + '/rejected')); 1") }
            runCatching { js.evaluate("(async function () { throw payload(DIR + '/async'); })(); 1") }
        }
        dir.list()!!.toList() shouldBe emptyList()
    }

    @Test
    @Disabled("quickjs-kt converts a thrown or returned Error natively (PLAN.md 8.7 item 5); enable once it is patched")
    fun `thrown and returned errors do not instantiate Java classes`() {
        QuickJs.create().use { js ->
            js.definePayload()
            runCatching { js.evaluate("throw payload(DIR + '/thrown')") }
            runCatching { js.evaluate("payload(DIR + '/returned')") }
        }
        dir.list()!!.toList() shouldBe emptyList()
    }

    fun interface Sink {
        fun take(text: String?, value: Any?, values: Array<Any?>?)
    }

    private fun jsLiteral(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
