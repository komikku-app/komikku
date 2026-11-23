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
import app.komikku.sourcejs.model.jsonBooleanOrNull
import app.komikku.sourcejs.model.numberOrNull
import app.komikku.sourcejs.model.textOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The host side of one JavaScript context: the channels the prelude calls through `__kmk_host_sync` /
 * `__kmk_host_async` and the per-context state (DOM handles, recorded host failures, timers, the call's outcome).
 *
 * Protocol (see `JsPrelude`): the bindings receive `(channel, payload)` as strings — the payload is a JSON array of
 * arguments, or the raw text for `dom_parse` — and always return a string reply: `"v" + JSON`, `"s" + raw text`,
 * `"h" + response JSON + "\n" + raw body` (HTTP), `"e" + {id, message}` for a host failure recorded under `id`, or
 * `"c"` for an async call dropped because the provider call already settled. They never throw into the engine, so no
 * exception object crosses the boundary. Large strings (HTML, bodies, texts) travel raw, not inside JSON.
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

    /** Timers cleared before their wait started (async bindings start after the current JS job). */
    private val clearedTimers = mutableSetOf<Long>()
    private val settledSignal = CompletableDeferred<Unit>()

    /** Completes when the entry reported its envelope ([outcome]). */
    val settled: Deferred<Unit> get() = settledSignal

    /** The envelope the entry reported (`JsPrelude` ENTRY), `null` while the call has not settled. */
    var outcome: String? = null
        private set

    /** Whether the extension was evaluated and its provider created. */
    var loaded: Boolean = false
        private set

    /** The host failure recorded under [id] (1-based), if any. */
    fun failure(id: Int): HostFunctionFailure? = failures.getOrNull(id - 1)

    suspend fun install(engine: JsEngine) {
        engine.defineFunction(SYNC_FUNCTION) { args ->
            val channel = args.getOrNull(0).orEmpty()
            // A sync channel has no coroutine: a CancellationException thrown by a service is a failure.
            guarded(channel, isCancelled = { false }) { sync(channel, args.getOrNull(1).orEmpty()) }
        }
        engine.defineSuspendFunction(ASYNC_FUNCTION) { args ->
            val channel = args.getOrNull(0).orEmpty()
            val payload = args.getOrNull(1).orEmpty()
            // Only a cancelled binding job (the engine closing) propagates; a service's own cancellation is a failure.
            val job = currentCoroutineContext()[Job]
            guarded(channel, isCancelled = { job?.isActive == false }) {
                if (channel == "sleep") sleep(parseArgs(payload)) else untilSettled { asyncChannel(channel, payload) } ?: CANCELLED
            }
        }
    }

    private inline fun guarded(channel: String, isCancelled: () -> Boolean, block: () -> String): String = try {
        block()
    } catch (e: HostFunctionFailure) {
        fail(e)
    } catch (e: CancellationException) {
        if (isCancelled()) throw e
        fail(HostFunctionFailure.Bridge(channel, "$channel failed: ${e.message ?: "cancelled"}", e))
    } catch (e: Throwable) {
        // Errors too (a stack overflow, an out-of-memory error): nothing may be thrown into the engine.
        fail(HostFunctionFailure.Bridge(channel, "$channel failed: ${e.message ?: e::class.simpleName}", e))
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
        val millis = args.getOrNull(1).numberOrNull()?.toLong()?.coerceAtLeast(0) ?: 0
        val cleared = CompletableDeferred<Unit>()
        timers[id] = cleared
        return try {
            val fired = if (settledSignal.isCompleted || clearedTimers.remove(id)) {
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

    private fun sync(channel: String, payload: String): String {
        when (channel) {
            "settle" -> {
                if (outcome == null) {
                    outcome = payload
                    settledSignal.complete(Unit)
                }
                return NULL
            }
            "loaded" -> {
                loaded = true
                return NULL
            }
            // The HTML travels raw.
            "dom_parse" -> return dom.parse(payload).toReply()
        }
        val args = parseArgs(payload)
        return when (channel) {
            "clear_timer" -> {
                val id = args.long(0)
                timers[id]?.complete(Unit) ?: clearedTimers.add(id)
                NULL
            }
            "log" -> {
                val level = when (args.text(0)) {
                    "warn" -> HostLog.Level.WARN
                    "error" -> HostLog.Level.ERROR
                    else -> HostLog.Level.INFO
                }
                services.log.log(level, source, args.text(1).orEmpty())
                NULL
            }

            // DOM (contract 4.3)
            "dom_get" -> dom.element(args.int(0), args.text(1).orEmpty()).toReply()
            "dom_string" -> raw(dom.string(args.int(0), args.text(1).orEmpty()))
            "dom_attr" -> raw(dom.attr(args.int(0), args.text(1).orEmpty()))
            "dom_has_attr" -> JsonPrimitive(dom.hasAttr(args.int(0), args.text(1).orEmpty())).toReply()
            "dom_select" -> JsonArray(dom.select(args.int(0), args.text(1).orEmpty()).map(::JsonPrimitive)).toReply()
            "dom_select_first" -> dom.selectFirst(args.int(0), args.text(1).orEmpty()).toReply()
            "dom_list" -> JsonArray(dom.list(args.int(0), args.text(1).orEmpty(), args.text(2).orEmpty()).map(::JsonPrimitive)).toReply()
            "dom_by_id" -> dom.byId(args.int(0), args.text(1).orEmpty()).toReply()

            // SharedPreferences (contract 4.6)
            "pref_get" -> buildJsonObject {
                val value = preferences.getValue(args.text(0).orEmpty())
                put("found", value != null)
                if (value != null) put("value", value.toJson())
            }.toReply()
            "pref_default" -> {
                val key = args.text(0).orEmpty()
                // Like Mangayomi, a declared list of the wrong shape counts as no preferences.
                val declared = try {
                    JsResultParser.preferences(args.getOrNull(1))
                } catch (e: MalformedResultFailure) {
                    emptyList()
                }.firstOrNull { it.key == key }
                    ?: throw HostFunctionFailure.Bridge("pref_default", "Error when getting source preference \"$key\": no such preference")
                val value = declared.defaultValue
                preferences.setValue(key, value)
                value.toJson().toReply()
            }
            "pref_get_string" -> {
                val key = args.text(0).orEmpty()
                val value = preferences.getString(key) ?: args.text(1)?.also { default -> preferences.setString(key, default) }
                value?.let(::raw) ?: NULL
            }
            "pref_set_string" -> {
                preferences.setString(args.text(0).orEmpty(), args.text(1).orEmpty())
                NULL
            }

            // Crypto and helpers (contract 4.4)
            "crypto_handler" -> raw(crypto.cryptoHandler(args.text(0).orEmpty(), args.text(1).orEmpty(), args.text(2).orEmpty(), args.boolean(3)))
            "crypto_js_encrypt" -> raw(crypto.encryptCryptoJs(args.text(0).orEmpty(), args.text(1).orEmpty()))
            "crypto_js_decrypt" -> raw(crypto.decryptCryptoJs(args.text(0).orEmpty(), args.text(1).orEmpty()))
            "crypto_aes_gcm" -> raw(
                crypto.decryptAesGcm(args.text(0).orEmpty(), args.text(1).orEmpty(), args.text(2).orEmpty(), args.text(3).orEmpty()),
            )
            "deobfuscate_js_password" -> raw(CryptoFunctions.deobfuscateJsPassword(args.text(0).orEmpty()))

            else -> unknownChannel(channel)
        }
    }

    private suspend fun asyncChannel(channel: String, payload: String): String {
        val args = parseArgs(payload)
        return when (channel) {
            "http" -> {
                val request = HttpBridge.request(
                    method = args.text(0).orEmpty(),
                    options = args.getOrNull(1),
                    url = args.text(2).orEmpty(),
                    headers = args.getOrNull(3),
                    body = args.getOrNull(4),
                    maxBodyBytes = limits.maxResponseBodyBytes,
                )
                val response = try {
                    services.http.execute(request)
                } catch (e: CancellationException) {
                    if (currentCoroutineContext()[Job]?.isActive == false) throw e
                    throw httpFailure(request.method, request.url, e.message ?: "cancelled", e)
                } catch (e: Throwable) {
                    throw httpFailure(request.method, request.url, e.message ?: e::class.simpleName.orEmpty(), e)
                }
                if (response.body.size > limits.maxResponseBodyBytes) {
                    throw httpFailure(request.method, request.url, HttpBridge.tooLargeMessage(limits.maxResponseBodyBytes), null)
                }
                // JSON never contains a raw newline, so the first one separates the object from the body. Built in
                // one buffer of the final size (the body is the large part).
                val head = HttpBridge.responseWithoutBody(request, response).toString()
                val body = HttpBridge.body(response)
                buildString(head.length + body.length + 2) {
                    append('h').append(head).append('\n').append(body)
                }
            }
            "webview" -> {
                val headers = (args.getOrNull(1) as? JsonObject)?.mapValues { (_, value) -> value.textOrNull().orEmpty() }.orEmpty()
                val scripts = (args.getOrNull(2) as? JsonArray)?.map { it.textOrNull().orEmpty() }.orEmpty()
                val result = try {
                    services.webView.evaluate(args.text(0).orEmpty(), headers, scripts, args.int(3).takeIf { it > 0 } ?: 30)
                } catch (e: UnsupportedOperationException) {
                    throw HostFunctionFailure.Unsupported("evaluateJavascriptViaWebview", e.message ?: "needs a WebView")
                }
                raw(result)
            }
            else -> unknownChannel(channel)
        }
    }

    /** Like Mangayomi's unknown `sendMessage` channel: log and return `null`. */
    private fun unknownChannel(channel: String): String {
        services.log.log(HostLog.Level.WARN, source, "No channel \"$channel\" registered")
        return NULL
    }

    private fun httpFailure(method: String, url: String, detail: String, cause: Throwable?) =
        HostFunctionFailure.Bridge("http", "HTTP $method $url failed: $detail", cause)

    companion object {
        const val SYNC_FUNCTION = "__kmk_host_sync"
        const val ASYNC_FUNCTION = "__kmk_host_async"

        private const val NULL = "vnull"
        private const val CANCELLED = "c"

        private fun raw(text: String): String = "s$text"

        private fun JsonElement?.toReply(): String = "v" + (this ?: JsonNull).toString()

        private fun Int.toReply(): String = "v$this"

        private fun JsonArray.text(index: Int): String? = getOrNull(index).textOrNull()

        private fun JsonArray.int(index: Int): Int = getOrNull(index).numberOrNull()?.toInt() ?: -1

        private fun JsonArray.long(index: Int): Long = getOrNull(index).numberOrNull()?.toLong() ?: -1

        private fun JsonArray.boolean(index: Int): Boolean = getOrNull(index).jsonBooleanOrNull() ?: false

        private fun JsPreferenceValue.toJson(): JsonElement = when (this) {
            is JsPreferenceValue.Text -> JsonPrimitive(value)
            is JsPreferenceValue.Bool -> JsonPrimitive(value)
            is JsPreferenceValue.TextSet -> JsonArray(values.map(::JsonPrimitive))
        }
    }
}
