package app.komikku.sourcejs

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * quickjs-kt throws an instance of whatever Java class an unhandled rejection's `name` names (PLAN.md 8.7 item 5);
 * none of them may leave `JsExtension` as itself (PROGRESS.md "Review fixes (I2b)").
 */
class ForgedThrowableTest {

    private val names = listOf(
        "java.lang.Error",
        "kotlin.NotImplementedError",
        "java.lang.StackOverflowError",
        "java.util.concurrent.CancellationException",
        "app.komikku.sourcejs.engine.JsEngineException\$OutOfMemory",
        "app.komikku.sourcejs.engine.JsEngineException\$Interrupted",
    )

    private fun rejection(name: String) = "const e = new Error('forged'); e.name = '$name'; Promise.reject(e);"

    @Test
    fun quickJsKtInstantiatesTheNamedClass() = runBlocking {
        // Precondition of the test below: without it, it would pass whatever the host does.
        for (name in names) {
            val engine = QuickJs.create(Dispatchers.Default)
            try {
                val thrown = runCatching { engine.evaluate<Any?>(rejection(name) + " 1", "p.js", false) }.exceptionOrNull()
                assertEquals(name, thrown?.javaClass?.name, "$thrown")
            } finally {
                engine.close()
            }
        }
        Unit
    }

    @Test
    fun forgedThrowablesAreScriptErrors() = runBlocking {
        for (name in names) {
            val extension = TestHost().extension(
                provider("async getPopular(page) { ${rejection(name)} await new Promise((resolve) => setTimeout(resolve, 20)); return { list: [] }; }"),
            )
            val error = assertFailsWith<JsSourceException> { extension.getPopular(1) }
            assertTrue(error is JsSourceException.ScriptError && error.jsMessage == "forged", "$name: $error")
        }
    }
}
