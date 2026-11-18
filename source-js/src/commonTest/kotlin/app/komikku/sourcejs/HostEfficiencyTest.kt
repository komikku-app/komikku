package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.engine.BytecodeCache
import app.komikku.sourcejs.engine.JsEngine
import app.komikku.sourcejs.engine.JsEngineException
import app.komikku.sourcejs.engine.JsEngineFactory
import app.komikku.sourcejs.engine.JsEngineLimits
import app.komikku.sourcejs.engine.QuickJsEngine
import app.komikku.sourcejs.engine.Sha256
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

    /** Counts what the host asks of the engine; [failBytecode] makes loading bytecode fail. */
    private class CountingFactory(private val failBytecode: Boolean = false) : JsEngineFactory {
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
                    if (failBytecode) throw JsEngineException.Script("Error: bad bytecode", null)
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
        val (host, test) = host(CountingFactory(failBytecode = true))
        assertEquals("a", host.extension(testSource, code("a")).getPopular(1).list.single().name)
        assertTrue(test.log.lines.any { it.first == HostLog.Level.WARN && "Bytecode failed" in it.second }, test.log.lines.toString())
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
    fun metadataReportsErrorsLikeTheSingleCalls() = runTest {
        val (host) = host(CountingFactory())
        val broken = host.extension(testSource, provider("""getHeaders(url) { throw new TypeError("no headers"); } getFilterList() { throw 1; }"""))
        assertEquals("TypeError: no headers", assertFailsWith<JsSourceException.ScriptError> { broken.metadata() }.jsMessage)
        val notLoading = host.extension(testSource, "class DefaultExtension extends MProvider { constructor() { super(); throw 'down'; } }")
        assertEquals("loading the source", assertFailsWith<JsSourceException.ScriptError> { notLoading.metadata() }.operation)
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
        fun hex(text: String) = Sha256.digest(text.encodeToByteArray()).toHexString()
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
        assertEquals(BytecodeCache.keyOf("abc"), hex("abc"))
    }
}
