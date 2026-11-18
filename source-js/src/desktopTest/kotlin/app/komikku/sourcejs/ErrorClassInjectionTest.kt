package app.komikku.sourcejs

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Security regression: `quickjs-kt` 1.0.15 turns a JS `Error` that reaches the JVM into
 * `new <error.name>(error.message)` for any class (`js_error_to_java_error`, no `Throwable` check). An extension that
 * names `java.io.FileOutputStream` would create the file named by its message. The host must never let an
 * extension's error object (or any non-string) reach Kotlin on its own code paths.
 *
 * Every payload targets a file inside a fresh temporary directory, nothing else.
 */
class ErrorClassInjectionTest {
    private lateinit var directory: File

    @BeforeTest
    fun createDirectory() {
        directory = createTempDirectory("kmk-js-security").toFile()
    }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    /** The JS helper `evil(name)` making the error that would create `<directory>/<name>`. */
    private val header get() = """
        const EVIL_DIR = ${JsonPrimitive(directory.absolutePath + File.separator)};
        function evil(name) { const e = new Error(EVIL_DIR + name); e.name = "java.io.FileOutputStream"; return e; }
    """.trimIndent()

    private fun file(name: String) = File(directory, name)

    private fun attack(body: String, header: String = "", call: suspend (JsExtension) -> Unit) = runBlocking {
        val extension = TestHost().extension(provider(body, header = this@ErrorClassInjectionTest.header + "\n" + header))
        try {
            call(extension)
        } catch (e: JsSourceException) {
            // Expected for most payloads; only the file matters.
        }
        assertTrue(directory.exists())
    }

    @Test
    fun errorsThrownByProviderMembersDoNotReachTheJvm() {
        attack(
            """
            async getPopular(page) { await new Client().get("https://example.org"); throw evil("popular"); }
            async getLatestUpdates(page) { await null; return { then(resolve, reject) { reject(evil("latest")); } }; }
            getHeaders(url) { throw evil("headers"); }
            get supportsLatest() { throw evil("supportsLatest"); }
            getFilterList() { return [{ get type_name() { throw evil("filterGetter"); } }]; }
            """,
        ) { extension ->
            runCatching { extension.getPopular(1) }
            runCatching { extension.getLatestUpdates(1) }
            runCatching { extension.getHeaders() }
            runCatching { extension.supportsLatest() }
            runCatching { extension.getFilterList() }
        }
        for (name in listOf("popular", "latest", "headers", "supportsLatest", "filterGetter")) assertFalse(file(name).exists(), name)
    }

    @Test
    fun errorsThrownWhileLoadingDoNotReachTheJvm() {
        attack("", header = """throw evil("topLevel");""") { it.getPopular(1) }
        attack("""constructor() { super(); throw evil("constructor"); }""") { it.getPopular(1) }
        assertFalse(file("topLevel").exists())
        assertFalse(file("constructor").exists())
    }

    @Test
    fun errorsPassedToTheBridgeDoNotReachTheJvm() {
        attack(
            """
            async getPopular(page) {
              const e = evil("bridge");
              const attempts = [
                () => console.log(e), () => console.warn([e]), () => new Document(e).text, () => new Document("<p>").selectFirst(e),
                () => new Document("<p>").getElementById(e), () => new SharedPreferences().getString(e, e),
                () => new SharedPreferences().setString(e, e), () => new SharedPreferences().get(e),
                () => cryptoHandler(e, e, e, e), () => decryptAESCryptoJS(e, e), () => deobfuscateJsPassword(e),
                () => setTimeout(e, e), () => clearTimeout(e), () => jsonStringify(() => e),
                () => new Client(e).get(e, { h: e }), () => new Client().post("https://example.org", e, e),
                () => evaluateJavascriptViaWebview(e, e, [e], e),
              ];
              for (const attempt of attempts) { try { await attempt(); } catch (error) {} }
              return { list: [] };
            }
            """,
        ) { it.getPopular(1) }
        assertFalse(file("bridge").exists())
    }

    @Test
    fun rawBindingsAreNotReachable() {
        attack(
            """
            async getPopular(page) {
              const e = evil("raw");
              for (const name of ["__kmk_host_sync", "__kmk_host_async"]) {
                try { globalThis[name](e, e); } catch (error) {}
                try { eval(name)(e, e); } catch (error) {}
              }
              for (const name of Object.getOwnPropertyNames(globalThis)) {
                const value = globalThis[name];
                if (typeof value === "function" && name.startsWith("__")) { try { await value(e, e, e); } catch (error) {} }
              }
              return { list: [] };
            }
            """,
        ) { it.getPopular(1) }
        assertFalse(file("raw").exists())
    }

    @Test
    fun patchedIntrinsicsCannotTurnTheResultIntoAnError() {
        attack(
            """
            async getPopular(page) { return { list: [] }; }
            """,
            header = """
                try { Object.defineProperty(Object.prototype, "then", { get() { throw evil("then"); } }); } catch (error) {}
                try { Object.prototype.then = function (resolve, reject) { reject(evil("thenFunction")); }; } catch (error) {}
                try { Object.defineProperty(Object.prototype, "value", { set(v) { throw evil("value"); } }); } catch (error) {}
                try { Object.defineProperty(Promise.prototype, "constructor", { get() { throw evil("constructor"); } }); } catch (error) {}
                try { Promise.prototype.then = function () { throw evil("promiseThen"); }; } catch (error) {}
                JSON.stringify = () => evil("stringify");
                JSON.parse = () => evil("parse");
                try { globalThis.__kmk_entry = () => { throw evil("entry"); }; } catch (error) {}
                try { globalThis.__kmk_define = () => { throw evil("define"); }; } catch (error) {}
            """.trimIndent(),
        ) { it.getPopular(1) }
        for (name in listOf("then", "thenFunction", "value", "constructor", "promiseThen", "stringify", "parse", "entry", "define")) {
            assertFalse(file(name).exists(), name)
        }
    }

    /**
     * Not closable from JavaScript: `quickjs-kt`'s native rejection tracker converts the reason of every rejection
     * that has no handler *at the moment it happens* with `js_error_to_java_error` — a stray `Promise.reject(e)`, but
     * also an `async` function that throws before its first `await` (its promise is rejected before the caller can
     * attach a handler). Enable once `quickjs-kt` checks that the class is a `Throwable` (or stops instantiating
     * classes by name); PROGRESS.md, review fixes.
     */
    @Ignore
    @Test
    fun unhandledRejectionsDoNotReachTheJvm() {
        attack(
            """
            async getPopular(page) {
              Promise.reject(evil("unhandled"));
              await new Promise((resolve) => setTimeout(resolve, 50));
              return { list: [] };
            }
            async getLatestUpdates(page) { throw evil("syncThrowInAsync"); }
            async getDetail(url) { return Promise.reject(evil("rejectedResult")); }
            """,
        ) { extension ->
            runCatching { extension.getPopular(1) }
            runCatching { extension.getLatestUpdates(1) }
            runCatching { extension.getDetail("x") }
        }
        for (name in listOf("unhandled", "syncThrowInAsync", "rejectedResult")) assertFalse(file(name).exists(), name)
    }
}
