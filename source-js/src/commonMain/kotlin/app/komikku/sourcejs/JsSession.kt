package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.CryptoFunctions
import app.komikku.sourcejs.bridge.DomBridge
import app.komikku.sourcejs.bridge.HostLog
import app.komikku.sourcejs.bridge.HttpBridge
import app.komikku.sourcejs.bridge.JsHostServices
import app.komikku.sourcejs.bridge.JsPreferenceValue
import app.komikku.sourcejs.engine.JsEngine
import app.komikku.sourcejs.model.JsResultParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
 * `__kmk_host_async` (JSON array of arguments in, JSON value out) and the per-context state (DOM handles).
 */
internal class JsSession(
    private val source: JsSourceDescriptor,
    private val services: JsHostServices,
) {
    private val dom = DomBridge()
    private val preferences by lazy { services.preferences.forSource(source) }
    private val crypto = CryptoFunctions(services.crypto)

    /** Host failures raised in this context, to recognise them when they reach the host as a script error. */
    private val failures = mutableListOf<HostFunctionFailure>()

    /**
     * The host failure behind a script error: a host function error the script did not handle reaches the host as
     * a JS error with the same message (the original exception only when thrown straight from the call).
     */
    fun failureFor(jsMessage: String): HostFunctionFailure? = failures.lastOrNull {
        // QuickJS appends the Kotlin stack trace of the host exception to the message.
        jsMessage == it.message || jsMessage.startsWith(it.message + "\n")
    }

    private fun record(failure: HostFunctionFailure): HostFunctionFailure = failure.also { failures += it }

    suspend fun install(engine: JsEngine) {
        engine.defineFunction(SYNC_FUNCTION) { args -> handle(args) { channel, params -> sync(channel, params) } }
        engine.defineSuspendFunction(ASYNC_FUNCTION) { args -> handleSuspend(args) }
        engine.evaluate(JsPrelude.text, JsPrelude.FILE_NAME)
    }

    private inline fun handle(args: List<String?>, block: (String, JsonArray) -> JsonElement?): String? {
        val channel = args.getOrNull(0).orEmpty()
        return try {
            block(channel, parseArgs(args)).toResult()
        } catch (e: HostFunctionFailure) {
            throw record(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw record(HostFunctionFailure.Bridge(channel, "$channel failed: ${e.message ?: e::class.simpleName}", e))
        }
    }

    private suspend fun handleSuspend(args: List<String?>): String? {
        val channel = args.getOrNull(0).orEmpty()
        return try {
            async(channel, parseArgs(args)).toResult()
        } catch (e: HostFunctionFailure) {
            throw record(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw record(HostFunctionFailure.Bridge(channel, "$channel failed: ${e.message ?: e::class.simpleName}", e))
        }
    }

    private fun parseArgs(args: List<String?>): JsonArray =
        args.getOrNull(1)?.let { Json.parseToJsonElement(it) as? JsonArray } ?: JsonArray(emptyList())

    private fun JsonElement?.toResult(): String? = if (this == null || this is JsonNull) null else toString()

    private fun sync(channel: String, args: JsonArray): JsonElement? = when (channel) {
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
        "unsupported" -> throw HostFunctionFailure.Unsupported(args.string(0).orEmpty(), args.string(1).orEmpty())

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
            val declared = JsResultParser.preferences(args.getOrNull(1)).firstOrNull { it.key == key }
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

    private suspend fun async(channel: String, args: JsonArray): JsonElement? = when (channel) {
        "http" -> {
            val request = HttpBridge.request(
                method = args.string(0).orEmpty(),
                options = args.getOrNull(1),
                url = args.string(2).orEmpty(),
                headers = args.getOrNull(3),
                body = args.getOrNull(4),
            )
            val response = try {
                services.http.execute(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw HostFunctionFailure.Bridge(
                    "http",
                    "HTTP ${request.method} ${request.url} failed: ${e.message ?: e::class.simpleName}",
                    e,
                )
            }
            HttpBridge.response(request, response)
        }
        "sleep" -> {
            delay((args.getOrNull(0) as? JsonPrimitive)?.doubleOrNull?.toLong()?.coerceAtLeast(0) ?: 0)
            null
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
            JsonPrimitive(result)
        }
        else -> {
            services.log.log(HostLog.Level.WARN, source, "No channel \"$channel\" registered")
            null
        }
    }

    companion object {
        const val SYNC_FUNCTION = "__kmk_host_sync"
        const val ASYNC_FUNCTION = "__kmk_host_async"

        private fun JsonArray.string(index: Int): String? = when (val element = getOrNull(index)) {
            null, is JsonNull -> null
            is JsonPrimitive -> element.content
            else -> element.toString()
        }

        private fun JsonArray.int(index: Int): Int = (getOrNull(index) as? JsonPrimitive)?.doubleOrNull?.toInt() ?: -1

        private fun JsonArray.boolean(index: Int): Boolean = (getOrNull(index) as? JsonPrimitive)?.booleanOrNull ?: false

        private fun JsPreferenceValue.toJson(): JsonElement = when (this) {
            is JsPreferenceValue.Text -> JsonPrimitive(value)
            is JsPreferenceValue.Bool -> JsonPrimitive(value)
            is JsPreferenceValue.TextSet -> JsonArray(values.map(::JsonPrimitive))
        }
    }
}
