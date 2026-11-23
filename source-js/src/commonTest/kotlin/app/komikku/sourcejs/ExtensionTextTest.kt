package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.engine.BytecodeCache
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How the extension text is loaded (JsPrelude.wrapExtension; PROGRESS.md "Review fixes (I2b)"). */
class ExtensionTextTest {

    private val okList = "return { list: [{ name: 'ok', link: 'l' }], hasNextPage: false };"

    /**
     * Texts that a sync wrapper parsed differently from the standalone syntax check (`await` an identifier there, a
     * keyword in the check), so a regular expression in one became a division in the other and the text closed the
     * wrapper: the code between ran at the top level, outside the entry.
     */
    private val escapes = listOf(
        "await /\"/g; \"}); console.log('ESCAPED-TOP-LEVEL'); __kmk_define(function () {\"; /\"/g;\n",
        "await /\"(\"+x}); console.log('ESCAPED-TOP-LEVEL'); (function(){ return 1[0/*] ) [*/] /i\n",
    )

    @Test
    fun textsCannotCloseTheWrapperEarly() = runTest {
        val test = TestHost()
        test.extension(provider("async getPopular(page) { $okList }")).getPopular(1)
        for (escape in escapes) {
            val code = escape + "class DefaultExtension extends MProvider { async getPopular(page) { $okList } }\n"
            // Valid as an async body too: it loads and works, and nothing ran outside the entry.
            runCatching { test.extension(code).getPopular(1) }
        }
        assertTrue(test.log.lines.none { it.second.contains("ESCAPED-TOP-LEVEL") }, "${test.log.lines}")
        assertTrue(test.log.lines.none { it.first == HostLog.Level.WARN && it.second.contains("Bytecode") }, "${test.log.lines}")
        assertNotNull(BytecodeCache.shared.prelude(), "the shared bytecode cache was not wiped")
    }

    @Test
    fun topLevelCodeRunsWithTheGlobalObjectAsThis() = runTest {
        for (directive in listOf("", "\"use strict\";\n")) {
            val code = directive + provider(
                """
                async getPopular(page) {
                  return { list: [{ name: [topThis === globalThis, typeof globalThis.topVar, typeof globalThis.topFunction, topVar].join(), link: "l" }], hasNextPage: false };
                }
                """,
                header = "var topThis = this; var topVar = 'v'; function topFunction() {}",
            )
            // Top-level declarations are the factory's own, not properties of the global object (deviation, PROGRESS).
            assertEquals("true,undefined,undefined,v", TestHost().extension(code).getPopular(1).list.single().name, directive)
        }
    }

    @Test
    fun topLevelThrowsAndAwaitsStayInsideTheEntry() = runTest {
        val thrown = runCatching { TestHost().extension(provider("", header = "throw new TypeError('top');")).getPopular(1) }
            .exceptionOrNull()
        assertTrue(thrown is JsSourceException.ScriptError && thrown.operation == "loading the source" && thrown.jsMessage.contains("top"), "$thrown")
        val awaited = provider("async getPopular(page) { return { list: [{ name: loaded, link: 'l' }], hasNextPage: false }; }", header = "const loaded = await Promise.resolve('awaited');")
        assertEquals("awaited", TestHost().extension(awaited).getPopular(1).list.single().name)
    }
}
