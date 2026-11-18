package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.bridge.JsHostServices
import app.komikku.sourcejs.engine.JsEngine
import app.komikku.sourcejs.engine.JsEngineException
import app.komikku.sourcejs.engine.JsEngineFactory
import app.komikku.sourcejs.engine.QuickJsEngine
import app.komikku.sourcejs.model.JsFilter
import app.komikku.sourcejs.model.JsManga
import app.komikku.sourcejs.model.JsMangaPage
import app.komikku.sourcejs.model.JsPage
import app.komikku.sourcejs.model.JsPreference
import app.komikku.sourcejs.model.JsResultParser
import app.komikku.sourcejs.model.MalformedResultFailure
import app.komikku.sourcejs.model.toJsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Runs Mangayomi JavaScript manga extensions (docs/kmp-desktop/MANGAYOMI_CONTRACT.md).
 *
 * Each function is one provider call (contract 3) for the source [JsSourceDescriptor] whose extension text is
 * `code`, and returns typed models parsed leniently from the JSON the extension returns. Every call runs in a fresh
 * context ([EnginePolicy.FreshContextPerCall]) within [JsHostLimits]; cancelling the calling coroutine interrupts the
 * script. Failures are [JsSourceException]s.
 *
 * Thread-safe: concurrent calls use separate contexts.
 */
class JsExtensionHost(
    private val services: JsHostServices,
    private val engineFactory: JsEngineFactory = QuickJsEngine,
    private val limits: JsHostLimits = JsHostLimits(),
    private val policy: EnginePolicy = EnginePolicy.FreshContextPerCall,
) {

    /** `getPopular(page)`, page 1-based. */
    suspend fun getPopular(source: JsSourceDescriptor, code: String, page: Int): JsMangaPage =
        callAsync(source, code, "getPopular($page)", "getPopular", JsonPrimitive(page)) { JsResultParser.mangaPage(it) }

    /** `getLatestUpdates(page)`; only meaningful when [supportsLatest] is true. */
    suspend fun getLatestUpdates(source: JsSourceDescriptor, code: String, page: Int): JsMangaPage =
        callAsync(source, code, "getLatestUpdates($page)", "getLatestUpdates", JsonPrimitive(page)) { JsResultParser.mangaPage(it) }

    /**
     * `search(query, page, filters)`. [filters] is the list from [getFilterList] with the user's state, in the
     * original order; `null` sends the source's default list (never `[]`, which breaks sources that read filters
     * by position, contract 5.1).
     */
    suspend fun search(source: JsSourceDescriptor, code: String, query: String, page: Int, filters: List<JsFilter>? = null): JsMangaPage {
        val filterList = filters ?: getFilterList(source, code)
        val operation = "search(${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(query))}, $page)"
        return callAsync(source, code, operation, "search", JsonPrimitive(query), JsonPrimitive(page), filterList.toJsonArray()) {
            JsResultParser.mangaPage(it)
        }
    }

    /** `getDetail(url)`: details and chapters (newest first) of the manga whose `link` is [url]. */
    suspend fun getDetail(source: JsSourceDescriptor, code: String, url: String): JsManga =
        callAsync(source, code, "getDetail", "getDetail", JsonPrimitive(url)) { JsResultParser.mangaDetail(it) }

    /** `getPageList(url)` for the chapter whose `url` is [url]; empty and duplicate URLs removed. */
    suspend fun getPageList(source: JsSourceDescriptor, code: String, url: String): List<JsPage> =
        callAsync(source, code, "getPageList", "getPageList", JsonPrimitive(url)) { JsResultParser.pages(it) }

    /**
     * `getFilterList()`. Like Mangayomi, any failure (not implemented, an error, a result of the wrong shape) gives
     * `[]`, logged as a warning, so [search] still runs.
     */
    suspend fun getFilterList(source: JsSourceDescriptor, code: String): List<JsFilter> = try {
        callSync(source, code, "getFilterList", "getFilterList", CallMode.SYNC, emptyList()) { result ->
            parse(source, "getFilterList", "getFilterList") { JsResultParser.filters(result) }
        }
    } catch (e: JsSourceException) {
        services.log.log(HostLog.Level.WARN, source, "Ignoring getFilterList failure: ${e.message}")
        emptyList()
    }

    /** `getSourcePreferences()`; `[]` when not implemented or not an array. */
    suspend fun getSourcePreferences(source: JsSourceDescriptor, code: String): List<JsPreference> =
        callSync(source, code, "getSourcePreferences", "getSourcePreferences", CallMode.SYNC, emptyList()) { result ->
            if (result !is JsonArray) {
                wrongType(source, "getSourcePreferences", result, emptyList())
            } else {
                parse(source, "getSourcePreferences", "getSourcePreferences") { JsResultParser.preferences(result) }
            }
        }

    /** `getHeaders(url)` (always called with the base URL in Mangayomi); `{}` when not implemented or not an object. */
    suspend fun getHeaders(source: JsSourceDescriptor, code: String, url: String = source.baseUrl): Map<String, String> =
        callSync(source, code, "getHeaders", "getHeaders", CallMode.SYNC, emptyMap(), JsonPrimitive(url)) { result ->
            if (result !is JsonObject) {
                wrongType(source, "getHeaders", result, emptyMap())
            } else {
                parse(source, "getHeaders", "getHeaders") { JsResultParser.headers(result) }
            }
        }

    /** The `supportsLatest` getter; `true` when not implemented or not a boolean. */
    suspend fun supportsLatest(source: JsSourceDescriptor, code: String): Boolean =
        callSync(source, code, "supportsLatest", "supportsLatest", CallMode.GETTER, true) { result ->
            (result as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: wrongType(source, "supportsLatest", result, true)
        }

    /** Mangayomi's `_extensionCall`: a sync member's result of the wrong type means the default. */
    private fun <T> wrongType(source: JsSourceDescriptor, member: String, result: JsonElement?, default: T): T {
        val got = when (result) {
            null, is JsonNull -> "nothing"
            is JsonObject -> "an object"
            is JsonArray -> "an array"
            is JsonPrimitive -> if (result.isString) "a string" else result.toString()
        }
        services.log.log(HostLog.Level.WARN, source, "$member returned $got; using the default $default")
        return default
    }

    /** The same calls bound to one source. */
    fun extension(source: JsSourceDescriptor, code: String): JsExtension = JsExtension(this, source, code)

    private enum class CallMode(val js: String) { ASYNC("async"), SYNC("sync"), GETTER("getter") }

    private suspend fun <T> callAsync(
        source: JsSourceDescriptor,
        code: String,
        operation: String,
        member: String,
        vararg args: JsonElement,
        decode: (JsonElement?) -> T,
    ): T = when (val outcome = call(source, code, operation, member, CallMode.ASYNC, args.toList())) {
        is CallOutcome.Value -> parse(source, operation, member) { decode(outcome.value) }
        is CallOutcome.Failed -> throw outcome.error
    }

    /** A sync member with Mangayomi's [default] when the base class says it is not implemented (contract 2.4). */
    private suspend fun <T> callSync(
        source: JsSourceDescriptor,
        code: String,
        operation: String,
        member: String,
        mode: CallMode,
        default: T,
        vararg args: JsonElement,
        decode: (JsonElement?) -> T,
    ): T = when (val outcome = call(source, code, operation, member, mode, args.toList())) {
        is CallOutcome.Value -> decode(outcome.value)
        is CallOutcome.Failed -> if (outcome.notImplemented) default else throw outcome.error
    }

    private inline fun <T> parse(source: JsSourceDescriptor, operation: String, method: String, block: () -> T): T = try {
        block()
    } catch (e: MalformedResultFailure) {
        throw JsSourceException.MalformedResult(source.name, operation, method, e.field, e.problem)
    }

    private sealed interface CallOutcome {
        class Value(val value: JsonElement?) : CallOutcome
        class Failed(val error: JsSourceException, val notImplemented: Boolean = false) : CallOutcome
    }

    /**
     * One provider call in a fresh context. The host's own limit is told apart from the caller's timeouts and
     * cancellation: only it becomes [JsSourceException.Timeout]; a `CancellationException` while the caller is still
     * active (a service's or one the script forged) is an error of the call.
     */
    private suspend fun call(
        source: JsSourceDescriptor,
        code: String,
        operation: String,
        member: String,
        mode: CallMode,
        args: List<JsonElement>,
    ): CallOutcome {
        var session: JsSession? = null
        // Dispatchers.Default: the timeout must run on a real clock, also under test dispatchers.
        val outcome = withContext(Dispatchers.Default) {
            withTimeoutOrNull(limits.callTimeoutMillis) {
                try {
                    policy.withEngine(engineFactory, limits.engine) { engine ->
                        val current = JsSession(source, services, limits).also { session = it }
                        run(engine, current, source, code, member, mode, args, operation)
                    }
                } catch (e: CancellationException) {
                    if (currentCoroutineContext().isActive) {
                        CallOutcome.Failed(scriptError(source, operation(session, operation), e.message ?: "cancelled", null))
                    } else {
                        throw e
                    }
                }
            }
        }
        return outcome ?: CallOutcome.Failed(JsSourceException.Timeout(source.name, operation(session, operation), limits.callTimeoutMillis))
    }

    private suspend fun run(
        engine: JsEngine,
        session: JsSession,
        source: JsSourceDescriptor,
        code: String,
        member: String,
        mode: CallMode,
        args: List<JsonElement>,
        operation: String,
    ): CallOutcome {
        val escaped: Exception? = try {
            session.install(engine)
            engine.evaluate(JsPrelude.text, JsPrelude.FILE_NAME)
            // Compiling the text alone first reports syntax errors as such and keeps the text from closing the
            // wrapper function early.
            engine.compile(code, source.scriptFileName)
            engine.evaluate(JsPrelude.wrapExtension(code), source.scriptFileName)
            engine.evaluate(JsPrelude.entryCall(member, JsonArray(args).toString(), mode.js), "<call $member>")
            null
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            e
        } catch (e: Exception) {
            e
        }
        val reported = session.outcome
        if (reported != null) {
            if (escaped != null) {
                services.log.log(HostLog.Level.WARN, source, "Ignoring an error after $operation settled: ${escaped.message}")
            }
            return decodeOutcome(reported, engine, session, source, operation)
        }
        val currentOperation = operation(session, operation)
        return CallOutcome.Failed(
            when (escaped) {
                null -> scriptError(source, currentOperation, "$member did not settle (its promise stayed pending)", null)
                is JsEngineException.Script -> scriptError(source, currentOperation, escaped.jsMessage, escaped.jsStack)
                is JsEngineException.OutOfMemory -> outOfMemory(source, currentOperation)
                is JsEngineException.Interrupted ->
                    JsSourceException.BridgeError(source.name, currentOperation, "engine", "interrupted", escaped)
                is CancellationException -> scriptError(source, currentOperation, escaped.message ?: "cancelled", null)
                else -> JsSourceException.BridgeError(
                    source.name,
                    currentOperation,
                    "host",
                    escaped.message ?: escaped::class.simpleName.orEmpty(),
                    escaped,
                )
            },
        )
    }

    /** Turns the entry's envelope (`JsPrelude` ENTRY) into an outcome; classification uses its markers only. */
    private suspend fun decodeOutcome(
        reported: String,
        engine: JsEngine,
        session: JsSession,
        source: JsSourceDescriptor,
        operation: String,
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
            "unsupported" -> JsSourceException.Unsupported(source.name, op, field("feature").orEmpty(), field("reason").orEmpty())
            "host" -> envelope["hostId"]?.jsonPrimitive?.intOrNull?.let(session::failure)?.toSourceException(source, op)
                ?: scriptError(source, op, message, field("stack"))
            "oom" -> outOfMemory(source, op)
            "nullish" -> if (isOutOfMemory(engine)) outOfMemory(source, op) else scriptError(source, op, message, null)
            "not_implemented" -> return CallOutcome.Failed(scriptError(source, op, message, field("stack")), notImplemented = op != LOADING)
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
        JsSourceException.ScriptError(source.name, operation, message, stack?.takeIf { it.isNotBlank() })

    private fun outOfMemory(source: JsSourceDescriptor, operation: String) =
        JsSourceException.OutOfMemory(source.name, operation, limits.engine.memoryLimitBytes)

    private fun HostFunctionFailure.toSourceException(source: JsSourceDescriptor, operation: String): JsSourceException = when (this) {
        is HostFunctionFailure.Unsupported -> JsSourceException.Unsupported(source.name, operation, feature, reason)
        is HostFunctionFailure.Bridge -> JsSourceException.BridgeError(source.name, operation, channel, message.orEmpty(), cause)
    }

    private companion object {
        const val LOADING = "loading the source"
    }
}

/** [JsExtensionHost] calls bound to one source and its extension text. */
class JsExtension(
    private val host: JsExtensionHost,
    val source: JsSourceDescriptor,
    val code: String,
) {
    suspend fun getPopular(page: Int) = host.getPopular(source, code, page)
    suspend fun getLatestUpdates(page: Int) = host.getLatestUpdates(source, code, page)
    suspend fun search(query: String, page: Int, filters: List<JsFilter>? = null) = host.search(source, code, query, page, filters)
    suspend fun getDetail(url: String) = host.getDetail(source, code, url)
    suspend fun getPageList(url: String) = host.getPageList(source, code, url)
    suspend fun getFilterList() = host.getFilterList(source, code)
    suspend fun getSourcePreferences() = host.getSourcePreferences(source, code)
    suspend fun getHeaders(url: String = source.baseUrl) = host.getHeaders(source, code, url)
    suspend fun supportsLatest() = host.supportsLatest(source, code)
}
