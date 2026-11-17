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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
        call(source, code, "getPopular($page)") {
            parse("getPopular") { JsResultParser.mangaPage(invoke("getPopular", JsonPrimitive(page))) }
        }

    /** `getLatestUpdates(page)`; only meaningful when [supportsLatest] is true. */
    suspend fun getLatestUpdates(source: JsSourceDescriptor, code: String, page: Int): JsMangaPage =
        call(source, code, "getLatestUpdates($page)") {
            parse("getLatestUpdates") { JsResultParser.mangaPage(invoke("getLatestUpdates", JsonPrimitive(page))) }
        }

    /**
     * `search(query, page, filters)`. [filters] is the list from [getFilterList] with the user's state, in the
     * original order; `null` sends the source's default list (never `[]`, which breaks sources that read filters
     * by position, contract 5.1).
     */
    suspend fun search(source: JsSourceDescriptor, code: String, query: String, page: Int, filters: List<JsFilter>? = null): JsMangaPage =
        call(source, code, "search(${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(query))}, $page)") {
            val filterJson = (filters ?: defaultFilters()).toJsonArray()
            parse("search") { JsResultParser.mangaPage(invoke("search", JsonPrimitive(query), JsonPrimitive(page), filterJson)) }
        }

    /** `getDetail(url)`: details and chapters (newest first) of the manga whose `link` is [url]. */
    suspend fun getDetail(source: JsSourceDescriptor, code: String, url: String): JsManga =
        call(source, code, "getDetail") {
            parse("getDetail") { JsResultParser.mangaDetail(invoke("getDetail", JsonPrimitive(url))) }
        }

    /** `getPageList(url)` for the chapter whose `url` is [url]; empty and duplicate URLs removed. */
    suspend fun getPageList(source: JsSourceDescriptor, code: String, url: String): List<JsPage> =
        call(source, code, "getPageList") {
            parse("getPageList") { JsResultParser.pages(invoke("getPageList", JsonPrimitive(url))) }
        }

    /** `getFilterList()`; `[]` when not implemented or unparsable (Mangayomi's defaults). */
    suspend fun getFilterList(source: JsSourceDescriptor, code: String): List<JsFilter> =
        call(source, code, "getFilterList") { defaultFilters() }

    /** `getSourcePreferences()`; `[]` when not implemented. */
    suspend fun getSourcePreferences(source: JsSourceDescriptor, code: String): List<JsPreference> =
        call(source, code, "getSourcePreferences") {
            parse("getSourcePreferences") {
                JsResultParser.preferences(invokeOrDefault("getSourcePreferences", JsonArray(emptyList())))
            }
        }

    /** `getHeaders(url)` (always called with the base URL in Mangayomi); `{}` when not implemented. */
    suspend fun getHeaders(source: JsSourceDescriptor, code: String, url: String = source.baseUrl): Map<String, String> =
        call(source, code, "getHeaders") {
            parse("getHeaders") { JsResultParser.headers(invokeOrDefault("getHeaders", JsonObject(emptyMap()), JsonPrimitive(url))) }
        }

    /** The `supportsLatest` getter; `true` when not implemented. */
    suspend fun supportsLatest(source: JsSourceDescriptor, code: String): Boolean =
        call(source, code, "supportsLatest") {
            parse("supportsLatest") { JsResultParser.boolean(invokeOrDefault("supportsLatest", JsonPrimitive(true)), true) }
        }

    /** The same calls bound to one source. */
    fun extension(source: JsSourceDescriptor, code: String): JsExtension = JsExtension(this, source, code)

    private inner class CallScope(val engine: JsEngine, val source: JsSourceDescriptor, val operation: String) {

        suspend fun invoke(member: String, vararg args: JsonElement): JsonElement? {
            val callArgs = JsonArray(listOf(JsonPrimitive(member), JsonArray(args.toList())))
            return engine.callFunction(INVOKE_FUNCTION, callArgs.toString())?.let(Json::parseToJsonElement)
        }

        /** Mangayomi's defaults for members whose base implementation throws "… not implemented" (contract 2.4). */
        suspend fun invokeOrDefault(member: String, default: JsonElement, vararg args: JsonElement): JsonElement? = try {
            invoke(member, *args)
        } catch (e: JsEngineException.Script) {
            if (e.jsMessage.contains("not implemented")) default else throw e
        }

        suspend fun defaultFilters(): List<JsFilter> {
            val result = invokeOrDefault("getFilterList", JsonArray(emptyList()))
            return try {
                JsResultParser.filters(result)
            } catch (e: MalformedResultFailure) {
                services.log.log(HostLog.Level.WARN, source, "Ignoring unparsable getFilterList result: ${e.message}")
                emptyList()
            }
        }

        inline fun <T> parse(method: String, block: () -> T): T = try {
            block()
        } catch (e: MalformedResultFailure) {
            throw JsSourceException.MalformedResult(source.name, operation, method, e.field, e.problem)
        }
    }

    private suspend fun <T> call(
        source: JsSourceDescriptor,
        code: String,
        operation: String,
        block: suspend CallScope.() -> T,
    ): T {
        var loading = true
        var session: JsSession? = null
        try {
            // Dispatchers.Default: the timeout must run on a real clock, also under test dispatchers.
            return withContext(Dispatchers.Default) {
                withTimeout(limits.callTimeoutMillis) {
                    policy.withEngine(engineFactory, limits.engine) { engine ->
                        session = JsSession(source, services).also { it.install(engine) }
                        engine.evaluate(code + JsPrelude.CREATE_PROVIDER, source.scriptFileName)
                        loading = false
                        CallScope(engine, source, operation).block()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw JsSourceException.Timeout(source.name, operation(loading, operation), limits.callTimeoutMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val hostFailure = (e as? JsEngineException.Script)?.let { session?.failureFor(it.jsMessage) }
            throw (hostFailure ?: e).toSourceException(source, operation(loading, operation))
        }
    }

    private fun operation(loading: Boolean, operation: String) = if (loading) "loading the source" else operation

    private fun Throwable.toSourceException(source: JsSourceDescriptor, operation: String): JsSourceException = when (this) {
        is JsSourceException -> this
        is HostFunctionFailure.Unsupported -> JsSourceException.Unsupported(source.name, operation, feature, reason)
        is HostFunctionFailure.Bridge -> JsSourceException.BridgeError(source.name, operation, channel, message.orEmpty(), cause)
        is JsEngineException.Script -> JsSourceException.ScriptError(source.name, operation, jsMessage, jsStack)
        is JsEngineException.OutOfMemory -> JsSourceException.OutOfMemory(source.name, operation, limits.engine.memoryLimitBytes)
        is JsEngineException.Interrupted -> JsSourceException.Timeout(source.name, operation, limits.callTimeoutMillis)
        else -> JsSourceException.BridgeError(source.name, operation, "host", message ?: this::class.simpleName.orEmpty(), this)
    }

    private companion object {
        const val INVOKE_FUNCTION = "__kmk_invoke"
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
