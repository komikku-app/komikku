package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.engine.BytecodeCache
import app.komikku.sourcejs.engine.JsEngine
import app.komikku.sourcejs.engine.JsEngineException
import app.komikku.sourcejs.engine.JsEngineFactory
import app.komikku.sourcejs.engine.JsEngineLimits
import app.komikku.sourcejs.engine.QuickJsEngine
import app.komikku.sourcejs.model.JsFilter
import app.komikku.sourcejs.model.JsPreference
import app.komikku.sourcejs.model.JsSourceMetadata
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Bytecode cache, the metadata snapshot and the DOM / source shortcuts. */
class HostEfficiencyTest {

    /** Counts what the host asks of the engine; loading bytecode throws [bytecodeFailure] when set. */
    private class CountingFactory(private val bytecodeFailure: JsEngineException? = null) : JsEngineFactory {
        var created = 0
        var compiles = 0
        var bytecodeLoads = 0

        override suspend fun create(limits: JsEngineLimits): JsEngine {
            created++
            val engine = QuickJsEngine.create(limits)
            return object : JsEngine by engine {
                override suspend fun compile(script: String, fileName: String): ByteArray {
                    compiles++
                    return engine.compile(script, fileName)
                }

                override suspend fun evaluate(bytecode: ByteArray) {
                    bytecodeLoads++
                    bytecodeFailure?.let { throw it }
                    engine.evaluate(bytecode)
                }
            }
        }
    }

    private fun host(factory: JsEngineFactory, capacity: Int = 32): Pair<JsExtensionHost, TestHost> {
        val test = TestHost()
        return JsExtensionHost(test.services, factory, JsHostLimits(callTimeoutMillis = 20_000), BytecodeCache(capacity)) to test
    }

    private fun code(name: String) = provider("""async getPopular(page) { return { list: [{ name: "$name", link: "x" }] }; }""")

    @Test
    fun preludeAndExtensionsAreCompiledOnce() = runTest {
        val factory = CountingFactory()
        val (host) = host(factory)
        val extension = host.extension(testSource, code("a"))
        repeat(3) { assertEquals("a", extension.getPopular(1).list.single().name) }
        // Prelude, the extension's syntax check and its wrapped form, once; a fresh context per call.
        assertEquals(3, factory.compiles)
        assertEquals(6, factory.bytecodeLoads)
        assertEquals(3, factory.created)
    }

    @Test
    fun theExtensionCacheIsBounded() = runTest {
        val factory = CountingFactory()
        val (host) = host(factory, capacity = 1)
        for (name in listOf("a", "b", "a")) assertEquals(name, host.extension(testSource, code(name)).getPopular(1).list.single().name)
        assertEquals(1 + 2 * 3, factory.compiles)
    }

    @Test
    fun bytecodeThatFailsToLoadFallsBackToTheSource() = runTest {
        val (host, test) = host(CountingFactory(JsEngineException.Script("Error: bad bytecode", null)))
        assertEquals("a", host.extension(testSource, code("a")).getPopular(1).list.single().name)
        assertTrue(test.log.lines.any { it.first == HostLog.Level.WARN && "Bytecode failed" in it.second }, test.log.lines.toString())
    }

    @Test
    fun runningOutOfMemoryOrBeingInterruptedIsNotABytecodeFailure() = runTest {
        val failures = listOf(JsEngineException.OutOfMemory("InternalError: out of memory"), JsEngineException.Interrupted("interrupted"))
        for (failure in failures) {
            val cache = BytecodeCache()
            val test = TestHost()
            val factory = CountingFactory()
            val warm = JsExtensionHost(test.services, factory, JsHostLimits(callTimeoutMillis = 20_000), cache)
            warm.extension(testSource, code("a")).getPopular(1)
            val failing = JsExtensionHost(test.services, CountingFactory(failure), JsHostLimits(callTimeoutMillis = 20_000), cache)
            val error = assertFailsWith<JsSourceException> { failing.extension(testSource, code("a")).getPopular(1) }
            val expected = if (failure is JsEngineException.OutOfMemory) JsSourceException.OutOfMemory::class else JsSourceException.BridgeError::class
            assertEquals(expected, error::class, "$error")
            // No fallback to the source and the shared cache kept.
            assertTrue(test.log.lines.none { "Bytecode failed" in it.second }, test.log.lines.toString())
            assertTrue(cache.prelude() != null)
        }
    }

    @Test
    fun bytecodeIsCachedPerFileName() = runTest {
        val (host) = host(CountingFactory())
        val text = provider("async getPopular(page) { throw new Error('where'); }")
        for (file in listOf("first.js", "second.js")) {
            val source = testSource.copy(sourceCodeUrl = "https://raw.example.org/javascript/manga/src/en/$file")
            val error = assertFailsWith<JsSourceException.ScriptError> { host.extension(source, text).getPopular(1) }
            assertTrue(error.jsStack.orEmpty().contains(file), "${error.jsStack}")
        }
    }

    @Test
    fun metadataIsReadInOneContext() = runTest {
        val factory = CountingFactory()
        val (host) = host(factory)
        val extension = host.extension(
            testSource,
            provider(
                """
                get supportsLatest() { return false; }
                getHeaders(url) { return { Referer: url + "/", "X-Pref": new SharedPreferences().get("q") }; }
                getFilterList() { return [{ type_name: "TextFilter", name: "Title", state: "" }]; }
                getSourcePreferences() { return [{ key: "q", listPreference: { title: "Q", entries: ["Hi"], entryValues: ["hi"], valueIndex: 0 } }]; }
                """,
            ),
        )
        val metadata = extension.metadata()
        assertEquals(1, factory.created)
        assertEquals(false, metadata.supportsLatest)
        assertEquals("Title", (metadata.filters.single() as JsFilter.Text).name)
        assertEquals(listOf(JsPreference.List("q", "Q", "", listOf("Hi"), listOf("hi"), 0)), metadata.preferences)
        assertEquals(mapOf("Referer" to "https://example.org/", "X-Pref" to "hi"), metadata.headers)

        val defaults = host.extension(testSource, provider("")).metadata()
        assertEquals(JsSourceMetadata(true, emptyList(), emptyList(), emptyMap()), defaults)
    }

    @Test
    fun metadataKeepsTheMembersThatWork() = runTest {
        val (host, test) = host(CountingFactory())
        val broken = host.extension(
            testSource,
            provider(
                """
                getHeaders(url) { throw new TypeError("no headers"); }
                getFilterList() { throw 1; }
                get supportsLatest() { return false; }
                getSourcePreferences() { return [{ key: "q", listPreference: { title: "Q", entries: ["Hi"], entryValues: ["hi"], valueIndex: 0 } }]; }
                """,
            ),
        )
        val metadata = broken.metadata()
        assertEquals(emptyMap(), metadata.headers)
        assertEquals(emptyList(), metadata.filters)
        assertEquals(false, metadata.supportsLatest)
        assertEquals("q", metadata.preferences.single().key)
        assertTrue(test.log.lines.any { it.first == HostLog.Level.WARN && "TypeError: no headers" in it.second }, test.log.lines.toString())
        // The single call still reports it.
        assertEquals("TypeError: no headers", assertFailsWith<JsSourceException.ScriptError> { broken.getHeaders() }.jsMessage)
        val notLoading = host.extension(testSource, "class DefaultExtension extends MProvider { constructor() { super(); throw 'down'; } }")
        assertEquals("loading the source", assertFailsWith<JsSourceException.ScriptError> { notLoading.metadata() }.operation)
    }

    @Test
    fun searchWithoutFiltersReadsTheDefaultListInTheSameContext() = runTest {
        val factory = CountingFactory()
        val (host, test) = host(factory)
        val extension = host.extension(
            testSource,
            provider(
                """
                getFilterList() { return [{ type_name: "SelectFilter", name: "Sort", state: 1, values: [{ type_name: "SelectOption", name: "A", value: "a" }, { type_name: "SelectOption", name: "B", value: "b" }] }, { type_name: "TextFilter", name: "Title" }]; }
                async search(query, page, filters) { return { list: [{ name: JSON.stringify(filters.map((f) => [f.name, f.state])), link: "l" }] }; }
                """,
            ),
        )
        assertEquals("""[["Sort",1],["Title",""]]""", extension.search("q", 1).list.single().name)
        assertEquals(1, factory.created)
        // A failing getFilterList gives [] (Mangayomi), with a warning.
        val failing = host.extension(
            testSource,
            provider("""getFilterList() { throw new Error("no filters"); } async search(query, page, filters) { return { list: [{ name: String(filters.length), link: "l" }] }; }"""),
        )
        assertEquals("0", failing.search("q", 1).list.single().name)
        assertTrue(test.log.lines.any { it.first == HostLog.Level.WARN && "no filters" in it.second }, test.log.lines.toString())
    }

    @Test
    fun searchFailsWhenReadingTheDefaultFiltersRunsOutOfMemory() = runTest {
        val test = TestHost(limits = JsHostLimits(callTimeoutMillis = 20_000, engine = JsEngineLimits(memoryLimitBytes = 16L * 1024 * 1024)))
        val extension = test.extension(
            provider(
                """
                getFilterList() { const a = []; while (true) a.push("x".repeat(1000) + a.length); }
                async search(query, page, filters) { return { list: [] }; }
                """,
            ),
        )
        assertFailsWith<JsSourceException.OutOfMemory> { extension.search("q", 1) }
    }

    @Test
    fun selectFirstStopsAtTheFirstDescendantAndSkipsTheElementItself() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                async getPopular(page) {
                  const doc = new Document('<div class="a" id="o"><p>1</p><div class="a"><p>2</p></div></div><p>3</p>');
                  const outer = doc.getElementById("o");
                  const r = [outer.selectFirst("div.a").selectFirst("p").text, outer.selectFirst("div").text, doc.selectFirst("p").text,
                    outer.selectFirst("#o").text, outer.selectFirst("div.#bad").text, outer.selectFirst("div.#bad").text];
                  return { list: [{ name: JSON.stringify(r), link: "x" }] };
                }
                """,
            ),
        )
        assertEquals("""["2","2","1","","",""]""", extension.getPopular(1).list.single().name)
    }

    @Test
    fun theSourceObjectIsFreshOnEachRead() = runTest {
        val extension = TestHost().extension(
            provider(
                """
                constructor() { super(); this.base = this.source.baseUrl; }
                async getPopular(page) {
                  const first = this.source;
                  first.name = "changed";
                  return { list: [{ name: this.source.name + "|" + (first === this.source) + "|" + this.base, link: "x" }] };
                }
                """,
            ),
        )
        assertEquals("Test Source|false|https://example.org", extension.getPopular(1).list.single().name)
    }

    @Test
    fun sha256MatchesKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
        // UTF-8 bytes of non-ASCII text (Python hashlib).
        assertEquals("1184d1f608158eea09d297565575892231550c403aaa913008d867a97cfd5c76", sha256Hex("é😀"))
    }
}
