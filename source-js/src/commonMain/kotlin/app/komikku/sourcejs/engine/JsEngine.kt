package app.komikku.sourcejs.engine

/**
 * A JavaScript context, kept small so the engine can be swapped (QuickJS today, JavaScriptCore possible on iOS)
 * without touching the host.
 *
 * Only strings cross this interface: host functions receive their arguments as strings (`null` for JS `null` /
 * `undefined`; other values are converted with their string form) and return a string or `null`; calls return
 * JSON text. Structured values are JSON-encoded by the JS prelude, like Mangayomi's `sendMessage` channel.
 *
 * An engine is confined to one thread: implementations switch to it internally, so callers may use any
 * dispatcher, but must not call one engine concurrently.
 */
interface JsEngine {

    /** Defines a global synchronous function [name] that calls [function]. */
    suspend fun defineFunction(name: String, function: (args: List<String?>) -> String?)

    /** Defines a global function [name] returning a promise that settles with the result of [function]. */
    suspend fun defineSuspendFunction(name: String, function: suspend (args: List<String?>) -> String?)

    /** Evaluates [script] as a global (non-module) script for its side effects. */
    suspend fun evaluate(script: String, fileName: String)

    /**
     * Calls the global function [function] with the elements of the JSON array [argsJson] as arguments, awaits the
     * result if it is a promise, and returns `JSON.stringify` of it (`null` for `undefined`).
     */
    suspend fun callFunction(function: String, argsJson: String): String?

    /** Releases the context and its thread. The engine cannot be used afterwards. */
    suspend fun close()
}

/** Creates engines; one engine is one JavaScript context. */
fun interface JsEngineFactory {
    suspend fun create(limits: JsEngineLimits): JsEngine
}

/**
 * Per-context limits enforced by the engine itself.
 *
 * @property memoryLimitBytes heap limit of the context; exceeding it fails the evaluation with
 *   [JsEngineException.OutOfMemory].
 * @property maxStackSizeBytes JS stack limit (Mangayomi uses 4 MiB); exceeding it is a JS `InternalError`.
 *   Platforms may lower it to fit their thread stacks.
 */
data class JsEngineLimits(
    val memoryLimitBytes: Long = 64L * 1024 * 1024,
    val maxStackSizeBytes: Long = 4L * 1024 * 1024,
)

/** Failures raised by a [JsEngine]. Errors thrown by host functions propagate unchanged instead. */
sealed class JsEngineException(message: String) : Exception(message) {

    /** The script threw (or a promise rejected) with [jsMessage]; [jsStack] is the JS stack when known. */
    class Script(val jsMessage: String, val jsStack: String?) : JsEngineException(jsMessage)

    /** The context ran out of its memory limit. */
    class OutOfMemory(message: String) : JsEngineException(message)

    /** The evaluation was interrupted (engine-side timeout). */
    class Interrupted(message: String) : JsEngineException(message)
}
