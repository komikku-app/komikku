package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.CryptoFunctions
import app.komikku.sourcejs.bridge.DomBridge
import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.bridge.HttpBridge
import app.komikku.sourcejs.bridge.JsHostServices
import app.komikku.sourcejs.bridge.JsPreferenceValue
import app.komikku.sourcejs.engine.JsEngine
import app.komikku.sourcejs.model.JsResultParser
import app.komikku.sourcejs.model.MalformedResultFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * The host side of one JavaScript context: the channels the prelude calls through `__kmk_host_sync` /
 * `__kmk_host_async` and the per-context state (DOM handles, recorded host failures, timers, the call's outcome).
 *
 * Protocol (see `JsPrelude`): the bindings receive `(channel, payload)` as strings — the payload is a JSON array of
 * arguments — and always return a string reply: `"v" + JSON`, `"s" + raw text`, `"e" + {id, message}` for a host
 * failure recorded under `id`, or `"c"` for an async call dropped because the provider call already settled. They
 * never throw into the engine, so no exception object crosses the boundary.
 *
 * All callbacks run on the engine's thread (`quickjs-kt` dispatches async bindings on the engine dispatcher).
 */
internal class JsSession(
    private val source: JsSourceDescriptor,
    private val services: JsHostServices,
    private val limits: JsHostLimits,
) {
    private val dom = DomBridge()
    private val preferences by lazy { services.preferences.forSource(source) }
    private val crypto = CryptoFunctions(services.crypto)

    private val failures = mutableListOf<HostFunctionFailure>()
    private val timers = mutableMapOf<Long, CompletableDeferred<Unit>>()
    private val settledSignal = CompletableDeferred<Unit>()

    /** The envelope the entry reported (`JsPrelude` ENTRY), `null` while the call has not settled. */
    var outcome: String? = null
        private set

    /** Whether the extension was evaluated and its provider created. */
    var loaded: Boolean = false
        private set

    /** The host failure recorded under [id] (1-based), if any. */
    fun failure(id: Int): HostFunctionFailure? = failures.getOrNull(id - 1)

    suspend fun install(engine: JsEngine) {
        engine.defineFunction(SYNC_FUNCTION, ::handleSync)
        engine.defineSuspendFunction(ASYNC_FUNCTION, ::handleAsync)
    }

    private fun handleSync(args: List<String?>): String {
        val channel = args.getOrNull(0).orEmpty()
        return try {
            sync(channel, args.getOrNull(1).orEmpty())
        } catch (e: HostFunctionFailure) {
            fail(e)
        } catch (e: Exception) {
            // Includes a CancellationException thrown by a service: a sync channel has no coroutine to cancel.
            fail(HostFunctionFailure.Bridge(channel, "$channel failed: ${e.message ?: e::class.simpleName}", e))
        }
    }

    private suspend fun handleAsync(args: List<String?>): String {
        val channel = args.getOrNull(0).orEmpty()
        val payload = args.getOrNull(1).orEmpty()
        return try {
            if (channel == "sleep") return sleep(parseArgs(payload))
            untilSettled { asyncChannel(channel, payload) } ?: CANCELLED
        } catch (e: HostFunctionFailure) {
            fail(e)
        } catch (e: CancellationException) {
            // Only a cancelled binding job (engine closing) propagates; a service's own cancellation is a failure.
            if (!currentCoroutineContext().isActive) throw e
            fail(HostFunctionFailure.Bridge(channel, "$channel failed: ${e.message ?: "cancelled"}", e))
        } catch (e: Exception) {
            fail(HostFunctionFailure.Bridge(channel, "$channel failed: ${e.message ?: e::class.simpleName}", e))
        }
    }

    private fun fail(failure: HostFunctionFailure): String {
        failures += failure
        return "e" + buildJsonObject {
            put("id", failures.size)
            put("message", failure.message.orEmpty())
        }
    }

    /** Runs [block] unless the provider call settles first; `null` then (the JS side drops the result). */
    private suspend fun <T> untilSettled(block: suspend () -> T): T? = coroutineScope {
        if (settledSignal.isCompleted) return@coroutineScope null
        val work = async { block() }
        select {
            work.onAwait { it }
            settledSignal.onAwait {
                work.cancel()
                null
            }
        }
    }

    /** `setTimeout`'s wait: `true` when it fired, `false` when cleared or when the call settled first. */
    private suspend fun sleep(args: JsonArray): String {
        val id = args.long(0)
        val millis = (args.getOrNull(1) as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }?.toLong()?.coerceAtLeast(0) ?: 0
        val cleared = CompletableDeferred<Unit>()
        timers[id] = cleared
        return try {
            val fired = if (settledSignal.isCompleted) {
                false
            } else {
                select {
                    onTimeout(millis) { true }
                    cleared.onAwait { false }
                    settledSignal.onAwait { false }
                }
            }
            "v$fired"
        } finally {
            timers.remove(id)
        }
    }

    private fun parseArgs(payload: String): JsonArray =
        payload.takeIf { it.isNotEmpty() }?.let { Json.parseToJsonElement(it) as? JsonArray } ?: JsonArray(emptyList())

    private fun sync(channel: String, payload: String): String = when (channel) {
        "settle" -> {
            if (outcome == null) {
                outcome = payload
                settledSignal.complete(Unit)
            }
            NULL
        }
        "loaded" -> {
            loaded = true
            NULL
        }
        "clear_timer" -> {
            timers[parseArgs(payload).long(0)]?.complete(Unit)
            NULL
        }
        else -> syncValue(channel, parseArgs(payload)).toReply()
    }

    private fun syncValue(channel: String, args: JsonArray): JsonElement? = when (channel) {
        "source" -> source.toJsSourceObject()
        "log" -> {
            val level = when (args.string(0)) {
                "warn" -> HostLog.Level.WARN
                "error" -> HostLog.Level.ERROR
                else -> HostLog.Level.INFO
            }
            services.log.log(level, source, args.string(1).orEmpty())
            null
        }

        // DOM (contract 4.3)
        "dom_parse" -> JsonPrimitive(dom.parse(args.string(0).orEmpty()))
        "dom_get" -> JsonPrimitive(dom.element(args.int(0), args.string(1).orEmpty()))
        "dom_string" -> JsonPrimitive(dom.string(args.int(0), args.string(1).orEmpty()))
        "dom_attr" -> JsonPrimitive(dom.attr(args.int(0), args.string(1).orEmpty()))
        "dom_has_attr" -> JsonPrimitive(dom.hasAttr(args.int(0), args.string(1).orEmpty()))
        "dom_select" -> JsonArray(dom.select(args.int(0), args.string(1).orEmpty()).map(::JsonPrimitive))
        "dom_select_first" -> JsonPrimitive(dom.selectFirst(args.int(0), args.string(1).orEmpty()))
        "dom_list" -> JsonArray(dom.list(args.int(0), args.string(1).orEmpty(), args.string(2).orEmpty()).map(::JsonPrimitive))
        "dom_by_id" -> JsonPrimitive(dom.byId(args.int(0), args.string(1).orEmpty()))

        // SharedPreferences (contract 4.6)
        "pref_get" -> buildJsonObject {
            val value = preferences.getValue(args.string(0).orEmpty())
            put("found", value != null)
            if (value != null) put("value", value.toJson())
        }
        "pref_default" -> {
            val key = args.string(0).orEmpty()
            // Like Mangayomi, a declared list of the wrong shape counts as no preferences.
            val declared = try {
                JsResultParser.preferences(args.getOrNull(1))
            } catch (e: MalformedResultFailure) {
                emptyList()
            }.firstOrNull { it.key == key }
                ?: throw HostFunctionFailure.Bridge("pref_default", "Error when getting source preference \"$key\": no such preference")
            val value = declared.defaultValue
            preferences.setValue(key, value)
            value.toJson()
        }
        "pref_get_string" -> {
            val key = args.string(0).orEmpty()
            preferences.getString(key)?.let(::JsonPrimitive) ?: args.string(1)?.let { default ->
                preferences.setString(key, default)
                JsonPrimitive(default)
            }
        }
        "pref_set_string" -> {
            preferences.setString(args.string(0).orEmpty(), args.string(1).orEmpty())
            null
        }

        // Crypto and helpers (contract 4.4)
        "crypto_handler" -> JsonPrimitive(
            crypto.cryptoHandler(args.string(0).orEmpty(), args.string(1).orEmpty(), args.string(2).orEmpty(), args.boolean(3)),
        )
        "crypto_js_encrypt" -> JsonPrimitive(crypto.encryptCryptoJs(args.string(0).orEmpty(), args.string(1).orEmpty()))
        "crypto_js_decrypt" -> JsonPrimitive(crypto.decryptCryptoJs(args.string(0).orEmpty(), args.string(1).orEmpty()))
        "crypto_aes_gcm" -> JsonPrimitive(
            crypto.decryptAesGcm(args.string(0).orEmpty(), args.string(1).orEmpty(), args.string(2).orEmpty(), args.string(3).orEmpty()),
        )
        "deobfuscate_js_password" -> JsonPrimitive(CryptoFunctions.deobfuscateJsPassword(args.string(0).orEmpty()))

        else -> {
            // Like Mangayomi's unknown sendMessage channel: log and return null.
            services.log.log(HostLog.Level.WARN, source, "No channel \"$channel\" registered")
            null
        }
    }

    private suspend fun asyncChannel(channel: String, payload: String): String {
        val args = parseArgs(payload)
        return when (channel) {
            "http" -> {
                val request = HttpBridge.request(
                    method = args.string(0).orEmpty(),
                    options = args.getOrNull(1),
                    url = args.string(2).orEmpty(),
                    headers = args.getOrNull(3),
                    body = args.getOrNull(4),
                    maxBodyBytes = limits.maxResponseBodyBytes,
                )
                val response = try {
                    services.http.execute(request)
                } catch (e: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw e
                    throw httpFailure(request.method, request.url, e.message ?: "cancelled", e)
                } catch (e: Exception) {
                    throw httpFailure(request.method, request.url, e.message ?: e::class.simpleName.orEmpty(), e)
                }
                if (response.body.size > limits.maxResponseBodyBytes) {
                    throw httpFailure(request.method, request.url, HttpBridge.tooLargeMessage(limits.maxResponseBodyBytes), null)
                }
                HttpBridge.response(request, response).toReply()
            }
            "webview" -> {
                val headers = (args.getOrNull(1) as? JsonObject)?.mapValues { (_, value) ->
                    (value as? JsonPrimitive)?.content ?: value.toString()
                }.orEmpty()
                val scripts = (args.getOrNull(2) as? JsonArray)?.map { (it as? JsonPrimitive)?.content ?: it.toString() }.orEmpty()
                val result = try {
                    services.webView.evaluate(args.string(0).orEmpty(), headers, scripts, args.int(3).takeIf { it > 0 } ?: 30)
                } catch (e: UnsupportedOperationException) {
                    throw HostFunctionFailure.Unsupported("evaluateJavascriptViaWebview", e.message ?: "needs a WebView")
                }
                JsonPrimitive(result).toReply()
            }
            else -> {
                services.log.log(HostLog.Level.WARN, source, "No channel \"$channel\" registered")
                NULL
            }
        }
    }

    private fun httpFailure(method: String, url: String, detail: String, cause: Throwable?) =
        HostFunctionFailure.Bridge("http", "HTTP $method $url failed: $detail", cause)

    companion object {
        const val SYNC_FUNCTION = "__kmk_host_sync"
        const val ASYNC_FUNCTION = "__kmk_host_async"

        private const val NULL = "vnull"
        private const val CANCELLED = "c"

        private fun JsonElement?.toReply(): String = "v" + (this ?: JsonNull).toString()

        private fun JsonArray.string(index: Int): String? = when (val element = getOrNull(index)) {
            null, is JsonNull -> null
            is JsonPrimitive -> element.content
            else -> element.toString()
        }

        private fun JsonArray.int(index: Int): Int = (getOrNull(index) as? JsonPrimitive)?.doubleOrNull?.toInt() ?: -1

        private fun JsonArray.long(index: Int): Long = (getOrNull(index) as? JsonPrimitive)?.doubleOrNull?.toLong() ?: -1

        private fun JsonArray.boolean(index: Int): Boolean = (getOrNull(index) as? JsonPrimitive)?.booleanOrNull ?: false

        private fun JsPreferenceValue.toJson(): JsonElement = when (this) {
            is JsPreferenceValue.Text -> JsonPrimitive(value)
            is JsPreferenceValue.Bool -> JsonPrimitive(value)
            is JsPreferenceValue.TextSet -> JsonArray(values.map(::JsonPrimitive))
        }
    }
}
