package app.komikku.sourcejs.bridge

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
 * The host side of `Client` (contract 4.2): turns the script's arguments into a [HostHttpRequest] and the
 * [HostHttpResponse] into Mangayomi's response object.
 */
internal object HttpBridge {

    /**
     * Body encoding (4.2): with a `Content-Type` containing `application/json` the body is always JSON-encoded (an
     * object becomes JSON, a string becomes a JSON string literal: Mangayomi's double encoding, kept); otherwise a
     * string is sent as UTF-8 text (`text/plain; charset=utf-8` unless set), an object form-URL-encoded and an
     * array of numbers as raw bytes.
     *
     * `reqcopyWith` (4.2): `timeout` (seconds) and `followRedirects` are honoured; certificate verification
     * cannot be turned off and the other options are ignored.
     */
    fun request(
        method: String,
        options: JsonElement?,
        url: String,
        headers: JsonElement?,
        body: JsonElement?,
        maxBodyBytes: Long? = null,
    ): HostHttpRequest {
        val headerList = (headers as? JsonObject).orEmpty().mapNotNull { (name, value) ->
            value.text()?.let { name to it }
        }.toMutableList()
        val contentType = headerList.firstOrNull { it.first.equals("content-type", ignoreCase = true) }?.second
        val bytes: ByteArray? = when {
            body == null || body is JsonNull -> null
            contentType?.contains("application/json", ignoreCase = true) == true ->
                Json.encodeToString(JsonElement.serializer(), body).encodeToByteArray()
            body is JsonObject -> {
                if (contentType == null) headerList += "Content-Type" to "application/x-www-form-urlencoded; charset=utf-8"
                body.entries.joinToString("&") { (key, value) ->
                    formEncode(key) + "=" + formEncode(value.text().orEmpty())
                }.encodeToByteArray()
            }
            body is JsonArray -> ByteArray(body.size) { index ->
                ((body[index] as? JsonPrimitive)?.doubleOrNull ?: 0.0).toInt().toByte()
            }
            else -> {
                if (contentType == null) headerList += "Content-Type" to "text/plain; charset=utf-8"
                body.text().orEmpty().encodeToByteArray()
            }
        }
        val optionObject = options as? JsonObject
        return HostHttpRequest(
            method = method.uppercase(),
            url = url,
            headers = headerList,
            body = bytes,
            timeoutMillis = (optionObject?.get("timeout") as? JsonPrimitive)?.doubleOrNull
                ?.takeIf { it > 0 }
                ?.let { (it * 1000).toLong() },
            followRedirects = (optionObject?.get("followRedirects") as? JsonPrimitive)?.booleanOrNull ?: true,
            maxBodyBytes = maxBodyBytes,
        )
    }

    /** The failure detail for a response body over [maxBodyBytes]. */
    fun tooLargeMessage(maxBodyBytes: Long): String = "response body larger than $maxBodyBytes bytes"

    /**
     * Mangayomi's response object (4.2). Header names are lower-cased, repeated headers joined with `", "`;
     * `request.url` is the original URL. The body is decoded with the `Content-Type` charset; without one (or with
     * a charset other than Latin-1 / ASCII) it is decoded as UTF-8, where Mangayomi falls back to Latin-1.
     */
    fun response(request: HostHttpRequest, response: HostHttpResponse): JsonObject {
        val responseHeaders = LinkedHashMap<String, String>()
        response.headers.forEach { (name, value) ->
            val key = name.lowercase()
            responseHeaders[key] = responseHeaders[key]?.let { "$it, $value" } ?: value
        }
        return buildJsonObject {
            put("body", decodeBody(response.body, responseHeaders["content-type"]))
            put("statusCode", response.statusCode)
            put("headers", JsonObject(responseHeaders.mapValues { JsonPrimitive(it.value) }))
            put("isRedirect", false)
            put("persistentConnection", true)
            put("reasonPhrase", JsonNull)
            put(
                "request",
                buildJsonObject {
                    put("contentLength", request.body?.size?.let(::JsonPrimitive) ?: JsonNull)
                    put("finalized", true)
                    put("followRedirects", request.followRedirects)
                    put("headers", JsonObject(request.headers.associate { it.first to JsonPrimitive(it.second) }))
                    put("maxRedirects", 5)
                    put("method", request.method)
                    put("persistentConnection", true)
                    put("url", request.url)
                },
            )
        }
    }

    fun decodeBody(body: ByteArray, contentType: String?): String {
        val charset = contentType
            ?.split(';')
            ?.drop(1)
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim('"', '\'', ' ')
            ?.lowercase()
        return when (charset) {
            "iso-8859-1", "latin1", "latin-1", "l1", "us-ascii", "ascii" ->
                buildString(body.size) { body.forEach { append((it.toInt() and 0xFF).toChar()) } }
            else -> body.decodeToString()
        }
    }

    private fun JsonElement.text(): String? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content
        else -> toString()
    }

    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()

    /** `application/x-www-form-urlencoded` component encoding (space → `+`), as Dart's `Uri.encodeQueryComponent`. */
    fun formEncode(text: String): String = buildString {
        text.encodeToByteArray().forEach { byte ->
            val c = byte.toInt() and 0xFF
            when {
                c == ' '.code -> append('+')
                c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
                    c == '-'.code || c == '_'.code || c == '.'.code || c == '*'.code -> append(c.toChar())
                else -> append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
