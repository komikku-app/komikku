package app.komikku.sourcejs.engine

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * [JsEngine] on QuickJS through `io.github.dokar3:quickjs-kt` (the same QuickJS release as Mangayomi, so `Date`
 * parsing, regular expressions and error texts match).
 *
 * Each engine owns a dedicated thread ([newJsEngineThread]): the QuickJS runtime is created on it (QuickJS
 * measures its stack limit from the creating thread) and every call runs on it. Coroutine cancellation interrupts
 * a running script (`quickjs-kt` installs an interrupt handler), so `withTimeout` around a call stops even an
 * endless loop.
 */
class QuickJsEngine private constructor(
    private val quickJs: QuickJs,
    private val thread: JsEngineThread,
) : JsEngine {

    override suspend fun defineFunction(name: String, function: (args: List<String?>) -> String?) = onThread {
        quickJs.function<String?>(name) { args -> function(args.map(::toArgument)) }
    }

    override suspend fun defineSuspendFunction(
        name: String,
        function: suspend (args: List<String?>) -> String?,
    ) = onThread {
        quickJs.asyncFunction<String?>(name) { args -> function(args.map(::toArgument)) }
    }

    override suspend fun evaluate(script: String, fileName: String) {
        onThread { quickJs.evaluate<Any?>(script, fileName) }
    }

    override suspend fun callFunction(function: String, argsJson: String): String? {
        val callee = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(function))
        val script = "JSON.stringify(await globalThis[$callee](...$argsJson))"
        return onThread { quickJs.evaluate<Any?>(script, "<call $function>") } as String?
    }

    override suspend fun close() {
        withContext(NonCancellable) {
            try {
                withContext(thread.dispatcher) { quickJs.close() }
            } finally {
                thread.dispatcher.close()
            }
        }
    }

    private suspend fun <T> onThread(block: suspend () -> T): T = try {
        withContext(thread.dispatcher) { block() }
    } catch (e: QuickJsInterruptedException) {
        throw JsEngineException.Interrupted(e.message ?: "interrupted")
    } catch (e: QuickJsException) {
        throw e.toEngineException()
    }

    companion object Factory : JsEngineFactory {

        override suspend fun create(limits: JsEngineLimits): JsEngine {
            val thread = newJsEngineThread("komikku-js", limits.maxStackSizeBytes)
            return try {
                val quickJs = withContext(thread.dispatcher) {
                    QuickJs.create(thread.dispatcher).apply {
                        memoryLimit = limits.memoryLimitBytes
                        maxStackSize = minOf(limits.maxStackSizeBytes, thread.maxJsStackBytes)
                    }
                }
                QuickJsEngine(quickJs, thread)
            } catch (e: Throwable) {
                thread.dispatcher.close()
                throw e
            }
        }

        private fun toArgument(value: Any?): String? = when (value) {
            null, Unit -> null
            is String -> value
            is Double -> if (value % 1.0 == 0.0 && value in -9.0E15..9.0E15) value.toLong().toString() else value.toString()
            is Float -> toArgument(value.toDouble())
            else -> value.toString()
        }

        private fun QuickJsException.toEngineException(): JsEngineException {
            val text = message ?: "Unknown JavaScript error"
            // When QuickJS cannot even allocate the InternalError, the pending exception is `null`.
            val outOfMemory = text.startsWith("InternalError: out of memory") || (text == "null" && stack.isNullOrBlank())
            return if (outOfMemory) {
                JsEngineException.OutOfMemory(text)
            } else {
                JsEngineException.Script(text.substringBefore("\n    at "), stack?.takeIf { it.isNotBlank() })
            }
        }
    }
}
