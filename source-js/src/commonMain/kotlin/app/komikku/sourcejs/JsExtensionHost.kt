package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.bridge.JsHostServices
import app.komikku.sourcejs.engine.BytecodeCache
import app.komikku.sourcejs.engine.JsEngine
import app.komikku.sourcejs.engine.JsEngineException
import app.komikku.sourcejs.engine.JsEngineFactory
import app.komikku.sourcejs.engine.QuickJsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Runs Mangayomi JavaScript manga extensions (docs/kmp-desktop/MANGAYOMI_CONTRACT.md). [extension] binds a source
 * and its extension text; its functions are the provider calls.
 *
 * Every call runs in a fresh context (Mangayomi's behaviour for data calls, contract 2.3: an extension never sees
 * state from an earlier call) within [JsHostLimits]; the prelude and each extension text are compiled to bytecode
 * once and reused. Cancelling the calling coroutine interrupts the script. Failures are [JsSourceException]s.
 *
 * Thread-safe: concurrent calls use separate contexts.
 */
class JsExtensionHost internal constructor(
    internal val services: JsHostServices,
    private val engineFactory: JsEngineFactory,
    private val limits: JsHostLimits,
    private val bytecode: BytecodeCache,
) {
    constructor(
        services: JsHostServices,
        engineFactory: JsEngineFactory = QuickJsEngine,
        limits: JsHostLimits = JsHostLimits(),
    ) : this(services, engineFactory, limits, if (engineFactory === QuickJsEngine) BytecodeCache.shared else BytecodeCache())

    /** The provider calls of [source], whose extension text is [code]. */
    fun extension(source: JsSourceDescriptor, code: String): JsExtension = JsExtension(this, source, code)

    internal enum class CallMode(val js: String) {
        ASYNC("async"),
        SYNC("sync"),
        GETTER("getter"),
        METADATA("metadata"),

        /** `search` whose third argument (`null`) is replaced by the default filter list read in the same context. */
        SEARCH_DEFAULT_FILTERS("search_default_filters"),
    }

    /** How one member ended: its JSON result (`null` for `undefined`) or the error, already classified. */
    internal sealed interface CallOutcome {
        class Value(val value: JsonElement?) : CallOutcome
        class Failed(val error: JsSourceException, val notImplemented: Boolean = false) : CallOutcome
    }

    /** One member call of [extension]. */
    internal suspend fun call(
        extension: JsExtension,
        operation: String,
        member: String,
        mode: CallMode,
        args: List<JsonElement>,
    ): CallOutcome = callInContext(extension, operation, member, mode, args).values.single()

    /** The sync members of [extension] in one context, keyed by member name; a load failure is the only entry then. */
    internal suspend fun callMetadata(extension: JsExtension): Map<String, CallOutcome> = callInContext(
        extension,
        "reading the source's metadata",
        "metadata",
        CallMode.METADATA,
        listOf(JsonPrimitive(extension.source.baseUrl)),
    )

    /**
     * Runs one entry in a fresh context. The host's own limit is told apart from the caller's timeouts and
     * cancellation: only it becomes [JsSourceException.Timeout]; a `CancellationException` while the caller is still
     * active (a service's, or one the script forged) is an error of the call.
     */
    private suspend fun callInContext(
        extension: JsExtension,
        operation: String,
        member: String,
        mode: CallMode,
        args: List<JsonElement>,
    ): Map<String, CallOutcome> {
        val source = extension.source
        var session: JsSession? = null
        suspend fun attempt(useBytecode: Boolean) = withEngine { engine ->
            val current = JsSession(source, services, limits).also { session = it }
            run(engine, current, extension, member, mode, args, operation, useBytecode)
        }
        // Dispatchers.Default: the timeout must run on a real clock, also under test dispatchers.
        val results = withContext(Dispatchers.Default) {
            withTimeoutOrNull(limits.callTimeoutMillis) {
                try {
                    try {
                        attempt(useBytecode = true)
                    } catch (e: BytecodeFailure) {
                        // Not expected with bytecode compiled in this process; start over from the source text.
                        bytecode.clear()
                        services.log.log(HostLog.Level.WARN, source, "Bytecode failed to load (${e.cause?.message}); using the source")
                        attempt(useBytecode = false)
                    }
                } catch (e: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw e
                    mapOf(member to CallOutcome.Failed(scriptError(source, operation(session, operation), e.message ?: "cancelled", null)))
                } catch (e: Throwable) {
                    // Not the script's (creating the engine, the host's own decoding): still a JsSourceException.
                    val detail = e.message ?: e::class.simpleName.orEmpty()
                    mapOf(member to CallOutcome.Failed(JsSourceException.BridgeError(source.id, source.name, operation(session, operation), "engine", detail, e)))
                }
            }
        }
        return results ?: mapOf(
            member to CallOutcome.Failed(JsSourceException.Timeout(source.id, source.name, operation(session, operation), limits.callTimeoutMillis)),
        )
    }

    private suspend fun <T> withEngine(block: suspend (JsEngine) -> T): T {
        val engine = engineFactory.create(limits.engine)
        try {
            return block(engine)
        } finally {
            engine.close()
        }
    }

    private class BytecodeFailure(cause: JsEngineException) : Exception(cause)

    /** Loads the prelude and the extension: cached bytecode when [useBytecode], compiled on a miss. */
    private suspend fun load(engine: JsEngine, extension: JsExtension, useBytecode: Boolean) {
        val source = extension.source
        val code = extension.code
        if (!useBytecode) {
            engine.evaluate(JsPrelude.text, JsPrelude.FILE_NAME)
            // Compiling the text alone first reports syntax errors as such and keeps it from closing the wrapper early.
            engine.compile(code, source.scriptFileName)
            engine.evaluate(JsPrelude.wrapExtension(code), source.scriptFileName)
            return
        }
        val prelude = bytecode.prelude() ?: engine.compile(JsPrelude.text, JsPrelude.FILE_NAME).also { bytecode.putPrelude(it) }
        evaluateBytecode(engine, prelude)
        val key = extension.bytecodeKey
        val compiled = bytecode.extension(key) ?: run {
            engine.compile(code, source.scriptFileName)
            engine.compile(JsPrelude.wrapExtension(code), source.scriptFileName).also { bytecode.putExtension(key, it) }
        }
        evaluateBytecode(engine, compiled)
    }

    /**
     * Only a script error means the bytecode itself failed (the prelude and the wrapper only define functions);
     * running out of memory or being interrupted is the call's failure and keeps the cache.
     */
    private suspend fun evaluateBytecode(engine: JsEngine, bytecode: ByteArray) {
        try {
            engine.evaluate(bytecode)
        } catch (e: JsEngineException.Script) {
            throw BytecodeFailure(e)
        }
    }

    private suspend fun run(
        engine: JsEngine,
        session: JsSession,
        extension: JsExtension,
        member: String,
        mode: CallMode,
        args: List<JsonElement>,
        operation: String,
        useBytecode: Boolean,
    ): Map<String, CallOutcome> {
        val source = extension.source
        val escaped: Throwable? = try {
            session.install(engine)
            load(engine, extension, useBytecode)
            val sourceJson = source.toJsSourceObject().toString()
            evaluateUntilSettled(engine, session, JsPrelude.entryCall(member, JsonArray(args).toString(), mode.js, sourceJson), "<call $member>")
            null
        } catch (e: BytecodeFailure) {
            throw e
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            e
        } catch (e: Throwable) {
            e
        }
        val reported = session.outcome
        if (reported != null) {
            if (escaped != null) {
                services.log.log(HostLog.Level.WARN, source, "Ignoring an error after $operation settled: ${escaped.message}")
            }
            if (reported.startsWith("m")) {
                val members = Json.parseToJsonElement(reported.drop(1)) as JsonObject
                return members.mapValues { (name, envelope) ->
                    decodeOutcome(envelope.jsonPrimitive.content, engine, session, source, name, name)
                }
            }
            return mapOf(member to decodeOutcome(reported, engine, session, source, operation, member))
        }
        val currentOperation = operation(session, operation)
        val error = when (escaped) {
            null -> scriptError(source, currentOperation, "$member did not settle (its promise stayed pending)", null)
            is JsEngineException.Script -> scriptError(source, currentOperation, escaped.jsMessage, escaped.jsStack)
            is JsEngineException.OutOfMemory -> outOfMemory(source, currentOperation)
            is JsEngineException.Interrupted -> JsSourceException.BridgeError(source.id, source.name, currentOperation, "engine", "interrupted", escaped)
            is CancellationException -> scriptError(source, currentOperation, escaped.message ?: "cancelled", null)
            else -> JsSourceException.BridgeError(source.id, source.name, currentOperation, "host", escaped.message ?: escaped::class.simpleName.orEmpty(), escaped)
        }
        return mapOf(member to CallOutcome.Failed(error))
    }

    /**
     * Evaluates the call [script] until it is done or the entry reported its outcome, whichever comes first: stray
     * JavaScript still running then (a busy job, a pending timer) is interrupted instead of turning a result into a
     * timeout.
     */
    private suspend fun evaluateUntilSettled(engine: JsEngine, session: JsSession, script: String, fileName: String) = coroutineScope {
        val evaluation = async { engine.evaluate(script, fileName) }
        val settledFirst = select {
            evaluation.onAwait { false }
            session.settled.onAwait { true }
        }
        if (settledFirst) evaluation.cancelAndJoin()
    }

    /** Turns an envelope (`JsPrelude` ENTRY) into an outcome; the classification uses its markers only. */
    private suspend fun decodeOutcome(
        reported: String,
        engine: JsEngine,
        session: JsSession,
        source: JsSourceDescriptor,
        operation: String,
        member: String,
    ): CallOutcome {
        val body = reported.drop(1)
        when (reported.firstOrNull()) {
            'o' -> return CallOutcome.Value(body.takeIf { it.isNotEmpty() }?.let(Json::parseToJsonElement))
            'e' -> Unit
            else -> return CallOutcome.Failed(
                if (isOutOfMemory(engine)) {
                    outOfMemory(source, operation(session, operation))
                } else {
                    scriptError(source, operation(session, operation), "the error could not be reported", null)
                },
            )
        }
        val envelope = Json.parseToJsonElement(body) as JsonObject
        fun field(name: String) = (envelope[name] as? JsonPrimitive)?.contentOrNull
        val op = if (field("phase") == "load") LOADING else operation
        val message = field("message").orEmpty()
        val error = when (field("kind")) {
            "unsupported" -> JsSourceException.Unsupported(source.id, source.name, op, field("feature").orEmpty(), field("reason").orEmpty())
            "host" -> envelope["hostId"]?.jsonPrimitive?.intOrNull?.let(session::failure)?.toSourceException(source, op)
                ?: scriptError(source, op, message, field("stack"))
            "oom" -> outOfMemory(source, op)
            // QuickJS throws `null` when it cannot even allocate its error; a script's own `throw null` is an error.
            "nullish" -> if (isOutOfMemory(engine)) outOfMemory(source, op) else scriptError(source, op, message, null)
            // While loading (a constructor reading a default) it is the load's error.
            "not_implemented" -> return if (op == LOADING) {
                CallOutcome.Failed(scriptError(source, op, message, field("stack")))
            } else {
                val missing = field("member") ?: member
                CallOutcome.Failed(JsSourceException.NotImplemented(source.id, source.name, op, missing), notImplemented = true)
            }
            else -> scriptError(source, op, message, field("stack"))
        }
        return CallOutcome.Failed(error)
    }

    private suspend fun isOutOfMemory(engine: JsEngine): Boolean {
        val used = try {
            engine.memoryUsedBytes()
        } catch (e: Exception) {
            null
        } ?: return false
        val limit = limits.engine.memoryLimitBytes
        return limit > 0 && used >= limit - limit / 16
    }

    private fun operation(session: JsSession?, operation: String) = if (session?.loaded == true) operation else LOADING

    private fun scriptError(source: JsSourceDescriptor, operation: String, message: String, stack: String?) =
        JsSourceException.ScriptError(source.id, source.name, operation, message, stack?.takeIf { it.isNotBlank() })

    private fun outOfMemory(source: JsSourceDescriptor, operation: String) =
        JsSourceException.OutOfMemory(source.id, source.name, operation, limits.engine.memoryLimitBytes)

    private fun HostFunctionFailure.toSourceException(source: JsSourceDescriptor, operation: String): JsSourceException = when (this) {
        is HostFunctionFailure.Unsupported -> JsSourceException.Unsupported(source.id, source.name, operation, feature, reason)
        is HostFunctionFailure.Bridge -> JsSourceException.BridgeError(source.id, source.name, operation, channel, message.orEmpty(), cause)
    }

    private companion object {
        const val LOADING = "loading the source"
    }
}
