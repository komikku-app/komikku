package app.komikku.desktop.webview

import app.komikku.network.browser.BrowserAvailability
import app.komikku.network.browser.BrowserBackend
import app.komikku.network.browser.BrowserPage
import app.komikku.network.browser.BrowserSession
import app.komikku.network.browser.BrowserSessionResult
import app.komikku.network.browser.BrowserUnavailableException
import exh.log.xLogW
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * KMK: an automatic Cloudflare backend without an embedded browser (P8): a FlareSolverr-compatible service the user runs
 * (FlareSolverr, Byparr, …) solves the challenge in its own browser and hands back the cookies and its User-Agent.
 *
 * [endpoint] is the user's address of the service (`desktop_flaresolverr_url`, empty = off, the default): its base
 * address (`http://localhost:8191`) or the API address (`…/v1`); only `http(s)`. A session is one
 * `POST /v1 {"cmd": "request.get", "url": …, "maxTimeout": …, "cookies": […]}`; the answer's `solution` (cookies with
 * their domain / path / expiry, `userAgent`, `url`, `status`) becomes the [BrowserSessionResult], tested against the
 * session's condition like a browser's page. The service sees the page addresses and the cookies sent with them.
 *
 * It shows no pages ([showsPages] `false`): "Open in WebView" keeps the manual flow (system browser + pasted cookies).
 * Errors: not configured / an address that is no `http(s)` → [availability] `Unavailable` (the interceptor's "pass the
 * check by hand" message); unreachable, an error answer or an unreadable answer → [BrowserUnavailableException] with a
 * message for the user; a timeout (the service's own or no answer in time) → `TIMED_OUT`. User info in the address
 * is sent as basic authentication (a solver behind a proxy); the endpoint's user info, path and query are never logged
 * or shown.
 */
class FlareSolverrBackend(
    private val endpoint: () -> String,
    private val client: OkHttpClient = DEFAULT_CLIENT,
    /** The least time the service gets: solving takes longer than a WebView load (FlareSolverr's default is 60 s). */
    private val minTimeout: Duration = MIN_TIMEOUT,
    /** Extra time for the HTTP answer beyond the solve time given to the service. */
    private val answerMargin: Duration = ANSWER_MARGIN,
) : BrowserBackend {

    override val name: String = "FlareSolverr"

    override val showsPages: Boolean = false

    override fun availability(): BrowserAvailability {
        val configured = endpoint().trim()
        if (configured.isEmpty()) {
            return BrowserAvailability.Unavailable(BrowserAvailability.Reason.NOT_INSTALLED, "FlareSolverr address not set")
        }
        if (apiUrl(configured) == null) {
            return BrowserAvailability.Unavailable(BrowserAvailability.Reason.NOT_SUPPORTED, "FlareSolverr address is not an http(s) address")
        }
        return BrowserAvailability.Available
    }

    override fun run(session: BrowserSession): BrowserSessionResult {
        val api = apiUrl(endpoint().trim()) ?: throw BrowserUnavailableException("FlareSolverr address not set or invalid")
        val solveTime = maxOf(session.timeout, minTimeout)
        val body = buildJsonObject {
            put("cmd", "request.get")
            put("url", session.url.toString())
            put("maxTimeout", solveTime.inWholeMilliseconds)
            if (session.cookies.isNotEmpty()) {
                put(
                    "cookies",
                    buildJsonArray {
                        session.cookies.forEach { cookie ->
                            add(
                                buildJsonObject {
                                    put("name", cookie.name)
                                    put("value", cookie.value)
                                    put("domain", if (cookie.hostOnly) cookie.domain else ".${cookie.domain}")
                                    put("path", cookie.path)
                                },
                            )
                        }
                    },
                )
            }
        }
        val request = Request.Builder()
            .url(api)
            .withCredentialsOf(api)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON))
            .build()
        val call = client.newBuilder()
            .callTimeout((solveTime + answerMargin).inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .readTimeout((solveTime + answerMargin).inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)

        val (code, text) = try {
            call.execute().use { response -> response.code to response.body.string() }
        } catch (e: InterruptedIOException) {
            // No answer in time (or the session was cancelled): like a browser that did not finish.
            if (Thread.currentThread().isInterrupted) throw e
            xLogW("FlareSolverr at ${api.safeName()} did not answer in time for ${session.url.host}")
            return BrowserSessionResult(BrowserSessionResult.Outcome.TIMED_OUT, session.url, emptyList(), null)
        } catch (e: IOException) {
            throw BrowserUnavailableException("FlareSolverr is unreachable at ${api.safeName()}: ${e.message ?: e::class.simpleName}", e)
        }

        val answer = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            throw BrowserUnavailableException("FlareSolverr at ${api.safeName()} gave no readable answer (HTTP $code)")
        }
        val status = answer["status"]?.jsonPrimitive?.contentOrNull
        val message = answer["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (status != "ok") {
            if (message.contains("timeout", ignoreCase = true)) {
                xLogW("FlareSolverr timed out for ${session.url.host}: $message")
                return BrowserSessionResult(BrowserSessionResult.Outcome.TIMED_OUT, session.url, emptyList(), null)
            }
            throw BrowserUnavailableException("FlareSolverr error (HTTP $code): ${message.ifEmpty { status ?: "no status" }}")
        }

        val solution = answer["solution"]?.jsonObject
            ?: throw BrowserUnavailableException("FlareSolverr answer without a solution (HTTP $code)")
        val finalUrl = solution["url"]?.jsonPrimitive?.contentOrNull?.toHttpUrlOrNull() ?: session.url
        val cookies = solution["cookies"]?.jsonArray.orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.toCookie(finalUrl)
        }
        val userAgent = solution["userAgent"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val page = BrowserPage(url = finalUrl, statusCode = solution["status"]?.jsonPrimitive?.intOrNull, cookies = cookies)
        // The service answers "ok" also when the page had no challenge: only the condition says whether it is solved.
        val outcome = if (session.until.isMet(page)) {
            BrowserSessionResult.Outcome.CONDITION_MET
        } else {
            BrowserSessionResult.Outcome.CLOSED
        }
        return BrowserSessionResult(outcome, finalUrl, cookies, userAgent)
    }

    /** What "Test connection" in the settings found. */
    sealed interface ConnectionTest {
        data object NotConfigured : ConnectionTest

        data object InvalidAddress : ConnectionTest

        /** The service answered as a FlareSolverr-compatible one; [version] when it says. */
        data class Ok(val version: String?) : ConnectionTest

        data class Unreachable(val detail: String) : ConnectionTest

        /** Something answered, but not like the service (wrong address, another server). */
        data class Failed(val detail: String) : ConnectionTest
    }

    /**
     * Asks the service's base address (FlareSolverr: `{"msg": "FlareSolverr is ready!", "version": …}`) whether it is
     * there. Blocking: not on the UI thread.
     */
    fun testConnection(): ConnectionTest {
        val configured = endpoint().trim()
        if (configured.isEmpty()) return ConnectionTest.NotConfigured
        val base = baseUrl(configured) ?: return ConnectionTest.InvalidAddress
        val (code, text) = try {
            client.newCall(Request.Builder().url(base).withCredentialsOf(base).get().build()).execute()
                .use { it.code to it.body.string() }
        } catch (e: IOException) {
            return ConnectionTest.Unreachable("${base.safeName()}: ${e.message ?: e::class.simpleName}")
        }
        val answer = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            null
        }
        if (code !in 200..299 || answer == null || ("msg" !in answer && "version" !in answer)) {
            return ConnectionTest.Failed("HTTP $code from ${base.safeName()}")
        }
        return ConnectionTest.Ok(answer["version"]?.jsonPrimitive?.contentOrNull)
    }

    /** A FlareSolverr cookie (Chrome's: `.example.com` = domain cookie, `expires` in seconds, -1 = session). */
    private fun JsonObject.toCookie(pageUrl: HttpUrl): Cookie? = try {
        val name = get("name")?.jsonPrimitive?.contentOrNull ?: return null
        val value = get("value")?.jsonPrimitive?.contentOrNull ?: return null
        val domain = get("domain")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: pageUrl.host
        Cookie.Builder()
            .name(name)
            .value(value)
            .apply { if (domain.startsWith('.')) domain(domain.removePrefix(".")) else hostOnlyDomain(domain) }
            .path(get("path")?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith('/') } ?: "/")
            .apply {
                val expires = get("expires")?.jsonPrimitive?.doubleOrNull
                if (expires != null && expires > 0) expiresAt((expires * 1000).toLong())
                if (get("secure")?.jsonPrimitive?.booleanOrNull == true) secure()
                if (get("httpOnly")?.jsonPrimitive?.booleanOrNull == true) httpOnly()
            }
            .build()
    } catch (_: IllegalArgumentException) {
        null
    }

    /** User info in the address (a solver behind a proxy with basic auth) as an `Authorization` header. */
    private fun Request.Builder.withCredentialsOf(url: HttpUrl): Request.Builder = apply {
        if (url.username.isNotEmpty()) header("Authorization", Credentials.basic(url.username, url.password))
    }

    companion object {
        val MIN_TIMEOUT: Duration = 60.seconds
        val ANSWER_MARGIN: Duration = 15.seconds

        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        /** Its own client: not the app's (whose Cloudflare interceptor would call this backend again). */
        private val DEFAULT_CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
        }

        /**
         * The API address for a configured [endpoint]: `…/v1` as given, else the base address with `/v1` appended;
         * `null` for anything but an `http(s)` address.
         */
        fun apiUrl(endpoint: String): HttpUrl? {
            val url = endpoint.trim().toHttpUrlOrNull() ?: return null
            if (url.scheme != "http" && url.scheme != "https") return null
            val segments = url.pathSegments.filter { it.isNotEmpty() }
            if (segments.lastOrNull() == "v1") return url.newBuilder().query(null).fragment(null).build()
            // A trailing empty segment ("http://host:8191/") is replaced by the added one.
            return url.newBuilder().query(null).fragment(null).addPathSegment("v1").build()
        }

        /** The service's base address (its status page) for a configured [endpoint]. */
        fun baseUrl(endpoint: String): HttpUrl? = apiUrl(endpoint)?.let { api ->
            api.newBuilder().removePathSegment(api.pathSegments.size - 1).build()
        }

        /** `scheme://host:port` only: no user info, path or query in logs and messages. */
        fun HttpUrl.safeName(): String = "$scheme://$host:$port"
    }
}
