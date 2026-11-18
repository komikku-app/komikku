package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.JsSourceDescriptor

/**
 * What the embedding app provides to JavaScript sources. One instance serves all sources; per-source state
 * (preferences) is looked up through [preferences].
 */
class JsHostServices(
    val http: HostHttp,
    val preferences: HostPreferencesProvider,
    val crypto: HostCrypto = CommonHostCrypto,
    val log: HostLog = HostLog.None,
    val webView: HostWebView = UnsupportedHostWebView,
)

/**
 * HTTP for the `Client` wrapper (contract 4.2). The embedding app supplies the client, so cookies, rate limits,
 * DNS-over-HTTPS and Cloudflare handling are the app's (on desktop / Android: `NetworkHelper.client`).
 * Certificates are always verified, unlike Mangayomi's default (deviation recorded in PROGRESS.md).
 *
 * Implementations must be cancellable and must not throw for HTTP error statuses; network failures throw.
 */
fun interface HostHttp {
    suspend fun execute(request: HostHttpRequest): HostHttpResponse
}

/**
 * @property method upper-case HTTP method.
 * @property headers request headers in the order the source gave them.
 * @property body request body bytes, `null` for none. The `Content-Type` is in [headers].
 * @property timeoutMillis overall call timeout requested by the source (`reqcopyWith.timeout`), `null` for the
 *   client's default.
 * @property followRedirects `false` when the source asked not to follow redirects.
 * @property maxBodyBytes largest response body the host accepts, `null` for no limit. Implementations should stop
 *   reading and throw once the body is larger, so a huge response cannot exhaust the app's heap; the bridge rejects
 *   larger bodies in any case.
 */
class HostHttpRequest(
    val method: String,
    val url: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timeoutMillis: Long? = null,
    val followRedirects: Boolean = true,
    val maxBodyBytes: Long? = null,
)

/**
 * @property headers response headers as received (names in any case, repeated names allowed).
 * @property body the raw body; the bridge decodes it (charset from `Content-Type`, UTF-8 otherwise).
 */
class HostHttpResponse(
    val statusCode: Int,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
)

/** Values stored for one source's declared preferences (contract 4.6, 5.2). */
sealed interface JsPreferenceValue {
    data class Text(val value: String) : JsPreferenceValue
    data class Bool(val value: Boolean) : JsPreferenceValue
    data class TextSet(val values: List<String>) : JsPreferenceValue
}

/**
 * Persistent per-source storage behind `SharedPreferences` (contract 4.6): declared preference values
 * ([getValue] / [setValue], keyed by the preference `key`) and the free string store behind
 * `getString` / `setString`, which is a separate namespace.
 */
interface HostPreferences {
    fun getValue(key: String): JsPreferenceValue?
    fun setValue(key: String, value: JsPreferenceValue)
    fun getString(key: String): String?
    fun setString(key: String, value: String)
}

fun interface HostPreferencesProvider {
    fun forSource(source: JsSourceDescriptor): HostPreferences
}

/** Log output of `console.log/warn/error` and of the host itself. */
fun interface HostLog {
    fun log(level: Level, source: JsSourceDescriptor, message: String)

    enum class Level { INFO, WARN, ERROR }

    companion object {
        val None = HostLog { _, _, _ -> }
    }
}

/** `evaluateJavascriptViaWebview` (contract 4.4). */
fun interface HostWebView {
    /** Loads [url], runs [scripts] after load and resolves with the value the page reports (contract 4.4). */
    suspend fun evaluate(url: String, headers: Map<String, String>, scripts: List<String>, timeoutSeconds: Int): String
}

/** No WebView yet: every call fails with an "unsupported" error naming the function. */
object UnsupportedHostWebView : HostWebView {
    override suspend fun evaluate(
        url: String,
        headers: Map<String, String>,
        scripts: List<String>,
        timeoutSeconds: Int,
    ): String = throw UnsupportedOperationException("needs a WebView, which is not supported yet")
}
