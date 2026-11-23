package app.cash.quickjs

import app.komikku.quickjs.jsString
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
            "var DIR = ${jsString(dir.absolutePath)};" +
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
    fun `raw bindings cannot be taken through a replaced globalThis or a patched intrinsic`() {
        QuickJs.create().use { js ->
            js.definePayload()
            // Collects every hidden function the generated code touches while it installs the bindings.
            js.evaluate(
                """
                var realGlobal = globalThis, realIsArray = Array.isArray, stolen = [];
                function grab(v) { if (typeof v === 'function' && stolen.indexOf(v) < 0) stolen.push(v); }
                Object.defineProperty(Array, 'isArray', {
                  configurable: true,
                  get: function () {
                    var names = Object.getOwnPropertyNames(realGlobal);
                    for (var i = 0; i < names.length; i++) if (names[i].indexOf('__kmk_qjs_') === 0) grab(realGlobal[names[i]]);
                    return realIsArray;
                  },
                });
                globalThis = new Proxy(realGlobal, {
                  get: function (t, k) { var v = Reflect.get(t, k); if (typeof k === 'string' && k.indexOf('__kmk_qjs_') === 0) grab(v); return v; },
                });
                void 0;
                """.trimIndent(),
            )
            js.set("host", Sink::class.java, Sink { _, _, _ -> })
            js.evaluate("var target = { take: function () {} };")
            js.get("target", Sink::class.java).take(null, null, null)
            js.evaluate(
                """
                for (var i = 0; i < stolen.length; i++) {
                  try { stolen[i](payload(DIR + '/a' + i), payload(DIR + '/b' + i)); } catch (e) {}
                  try { stolen[i](0, 0, payload(DIR + '/c' + i)); } catch (e) {}
                }
                void 0;
                """.trimIndent(),
            )
            dir.list()!!.toList() shouldBe emptyList()
        }
    }

    @Test
    fun `errors returned as or inside the completion value never reach quickjs-kt`() {
        QuickJs.create().use { js ->
            js.definePayload()
            js.evaluate("payload(DIR + '/returned')") shouldBe null
            runCatching { js.evaluate("[1, payload(DIR + '/nested')]") }
            runCatching { js.execute(js.compile("payload(DIR + '/compiled')", "c.js")) }
        }
        dir.list()!!.toList() shouldBe emptyList()
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
}
