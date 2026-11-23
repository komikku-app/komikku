package app.komikku.sourcejs.engine

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * [JsEngine] on QuickJS through `io.github.dokar3:quickjs-kt` (the same QuickJS release as Mangayomi, so `Date`
 * parsing, regular expressions and error texts match).
 *
 * Each engine owns a dedicated thread ([newJsEngineThread]) whose native stack holds the JS stack limit, and every
 * call runs on it (quickjs-kt re-bases QuickJS's stack limit on the current thread at every entry into the engine,
 * so the thread may be replaced while the engine is idle). Coroutine cancellation interrupts
 * a running script (`quickjs-kt` installs an interrupt handler), so `withTimeout` around a call stops even an
 * endless loop. `quickjs-kt`'s `evaluationTimeoutMillis` is not used: it is a wall-clock deadline from the start of
 * the evaluation that is only checked while JavaScript runs, so it would also count network waits.
 */
@OptIn(ExperimentalAtomicApi::class)
class QuickJsEngine private constructor(
    private val quickJs: QuickJs,
    private val thread: JsEngineThread,
    private val memoryLimitBytes: Long,
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

    override suspend fun compile(script: String, fileName: String): ByteArray = onThread { quickJs.compile(script, fileName) }

    override suspend fun evaluate(script: String, fileName: String) {
        onThread { quickJs.evaluate<Any?>(script, fileName) }
    }

    override suspend fun evaluate(bytecode: ByteArray) {
        onThread { quickJs.evaluate<Any?>(bytecode) }
    }

    override suspend fun memoryUsedBytes(): Long? = withContext(thread.dispatcher) {
        if (quickJs.isClosed) null else quickJs.memoryUsage.mallocSize
    }

    override suspend fun close() {
        withContext(NonCancellable) {
            try {
                withContext(thread.dispatcher) { quickJsLifecycle { quickJs.close() } }
            } finally {
                liveRuntimes.decrementAndFetch()
                thread.dispatcher.close()
            }
        }
    }

    /**
     * Runs [block] on the engine thread. Anything but cancellation leaves as a [JsEngineException]: besides QuickJS's
     * own errors, quickjs-kt throws an instance of the Java class a JavaScript error names (PLAN.md 8.7 item 5), which
     * may be any `Throwable` (a `java.lang.Error`, a forged [JsEngineException]); those are script errors.
     */
    private suspend fun <T> onThread(block: suspend () -> T): T = try {
        withContext(thread.dispatcher) { block() }
    } catch (e: QuickJsInterruptedException) {
        throw JsEngineException.Interrupted(e.message ?: "interrupted")
    } catch (e: QuickJsException) {
        throw e.toEngineException()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw JsEngineException.Script(e.message ?: e::class.simpleName ?: "Unknown JavaScript error", null)
    }

    /**
     * Out of memory is recognised by QuickJS's own error, or — when QuickJS could not even allocate that error and
     * threw `null` — by the context still being at its limit. Anything else is a script error.
     */
    private suspend fun QuickJsException.toEngineException(): JsEngineException {
        val text = message ?: "Unknown JavaScript error"
        if (text.startsWith(OUT_OF_MEMORY) || isNearMemoryLimit()) return JsEngineException.OutOfMemory(text)
        return JsEngineException.Script(text.substringBefore("\n    at "), stack?.takeIf { it.isNotBlank() })
    }

    /** Whether the context's allocations are within 1/16 of the memory limit. */
    internal suspend fun isNearMemoryLimit(): Boolean {
        if (memoryLimitBytes <= 0) return false
        val used = try {
            memoryUsedBytes()
        } catch (e: QuickJsException) {
            null
        } ?: return false
        return used >= memoryLimitBytes - memoryLimitBytes / 16
    }

    companion object Factory : JsEngineFactory {
        private const val OUT_OF_MEMORY = "InternalError: out of memory"

        /** QuickJS runtimes created and not yet closed (test hook for leak checks). */
        internal val liveRuntimes = AtomicInt(0)

        override suspend fun create(limits: JsEngineLimits): JsEngine =
            create(limits, newJsEngineThread("komikku-js", limits.maxStackSizeBytes))

        /**
         * Creates the runtime on [thread]. The creation itself is not cancellable; if the caller was cancelled
         * meanwhile, the runtime is closed instead of being lost.
         */
        internal suspend fun create(limits: JsEngineLimits, thread: JsEngineThread): JsEngine {
            var created: QuickJs? = null
            try {
                withContext(NonCancellable + thread.dispatcher) {
                    val quickJs = quickJsLifecycle { QuickJs.create(thread.dispatcher) }
                    created = quickJs
                    liveRuntimes.incrementAndFetch()
                    quickJs.memoryLimit = limits.memoryLimitBytes
                    quickJs.maxStackSize = minOf(limits.maxStackSizeBytes, thread.maxJsStackBytes)
                }
                currentCoroutineContext().ensureActive()
                return QuickJsEngine(created!!, thread, limits.memoryLimitBytes)
            } catch (e: Throwable) {
                withContext(NonCancellable) {
                    try {
                        created?.let { quickJs ->
                            withContext(thread.dispatcher) { quickJsLifecycle { quickJs.close() } }
                            liveRuntimes.decrementAndFetch()
                        }
                    } finally {
                        thread.dispatcher.close()
                    }
                }
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
    }
}
