package app.komikku.desktop.webview

import app.komikku.network.browser.BrowserAvailability
import app.komikku.network.browser.BrowserBackend
import app.komikku.network.browser.BrowserPage
import app.komikku.network.browser.BrowserSession
import app.komikku.network.browser.BrowserSessionResult
import app.komikku.network.browser.BrowserUnavailableException
import exh.log.xLogW
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Cookie
import okhttp3.Credentials
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
 * check by hand" message); unreachable (also a connect timeout), an error answer or an unreadable answer →
 * [FlareSolverrException] with its [FlareSolverrException.Reason] and a message from `KMR` strings; a timeout of the
 * solve (the service's own, or no answer in time once connected) → `TIMED_OUT`. A cancelled session
 * ([BrowserSession.isCancelled], the interceptor's call) or an interrupted thread cancels the HTTP call and ends with an
 * [InterruptedIOException]. User info in the address is sent as basic authentication (a solver behind a proxy); the
 * endpoint's user info, path and query are never logged or shown. Answers are read with safe casts: a field of another
 * type than expected counts as missing (P8 review).
 */
class FlareSolverrBackend(
    private val endpoint: () -> String,
    private val client: OkHttpClient = DEFAULT_CLIENT,
    /** The least time the service gets: solving takes longer than a WebView load (FlareSolverr's default is 60 s). */
    private val minTimeout: Duration = MIN_TIMEOUT,
    /** Extra time for the HTTP answer beyond the solve time given to the service. */
    private val answerMargin: Duration = ANSWER_MARGIN,
    /**
     * Called when the address read from [endpoint] differs from the one read before (P8 review: the app forgets the
     * hosts' failed solves then, so a corrected address takes effect at once). Seen at the next use: the interceptor
     * asks [availability] before it looks at a remembered failure.
     */
    private val onEndpointChanged: () -> Unit = {},
) : BrowserBackend {

    override val name: String = "FlareSolverr"

    override val showsPages: Boolean = false

    @Volatile
    private var lastEndpoint: String? = null

    /** The configured address, trimmed; reports a change to [onEndpointChanged]. */
    private fun currentEndpoint(): String {
        val value = endpoint().trim()
        val previous = lastEndpoint
        lastEndpoint = value
        if (previous != null && previous != value) onEndpointChanged()
        return value
    }

    override fun availability(): BrowserAvailability {
        val configured = currentEndpoint()
        if (configured.isEmpty()) {
            return BrowserAvailability.Unavailable(BrowserAvailability.Reason.NOT_INSTALLED, "FlareSolverr address not set")
        }
        if (apiUrl(configured) == null) {
            return BrowserAvailability.Unavailable(BrowserAvailability.Reason.NOT_SUPPORTED, "FlareSolverr address is not an http(s) address")
        }
        return BrowserAvailability.Available
    }

    override fun run(session: BrowserSession): BrowserSessionResult {
        val configured = currentEndpoint()
        if (configured.isEmpty()) throw FlareSolverrException(FlareSolverrException.Reason.NOT_CONFIGURED)
        val api = apiUrl(configured) ?: throw FlareSolverrException(FlareSolverrException.Reason.INVALID_ADDRESS)
        // Nobody waits any more: no solve (P8 review).
        if (session.isCancelled()) throw InterruptedIOException("Canceled")
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
        val connection = ConnectionListener()
        val call = client.newBuilder()
            .callTimeout((solveTime + answerMargin).inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .readTimeout((solveTime + answerMargin).inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .eventListener(connection)
            .build()
            .newCall(request)

        // The blocking call does not notice a cancelled outer call or an interrupt by itself: a watcher cancels it.
        // (Not `call.isCanceled()` afterwards: OkHttp's own call timeout cancels the call too.)
        val caller = Thread.currentThread()
        val cancelled = AtomicBoolean(false)
        val watch = cancelWatcher.scheduleWithFixedDelay(
            {
                if (session.isCancelled() || caller.isInterrupted) {
                    cancelled.set(true)
                    call.cancel()
                }
            },
            CANCEL_POLL_MS,
            CANCEL_POLL_MS,
            TimeUnit.MILLISECONDS,
        )
        val (code, text) = try {
            call.execute().use { response -> response.code to response.body.string() }
        } catch (e: IOException) {
            if (cancelled.get() || session.isCancelled() || caller.isInterrupted) {
                // Cancelled or interrupted: no result for anyone, and no failed solve to remember.
                throw InterruptedIOException("FlareSolverr call cancelled").apply { initCause(e) }
            }
            if (e is InterruptedIOException && connection.connected) {
                // Connected, but no answer in time: like a browser that did not finish.
                xLogW("FlareSolverr at ${api.safeName()} did not answer in time for ${session.url.host}")
                return BrowserSessionResult(BrowserSessionResult.Outcome.TIMED_OUT, session.url, emptyList(), null)
            }
            // Not even connected (refused, unknown host, a connect timeout): the service is not there.
            throw FlareSolverrException(
                FlareSolverrException.Reason.UNREACHABLE,
                detail = "${api.safeName()}: ${e.message ?: e::class.simpleName}",
                cause = e,
            )
        } finally {
            watch.cancel(false)
        }

        val answer = parseObject(text)
            ?: throw FlareSolverrException(FlareSolverrException.Reason.UNREADABLE_ANSWER, httpCode = code)
        val status = answer.string("status")
        val message = answer.string("message").orEmpty()
        if (status != "ok") {
            if (message.contains("timeout", ignoreCase = true)) {
                xLogW("FlareSolverr timed out for ${session.url.host}: $message")
                return BrowserSessionResult(BrowserSessionResult.Outcome.TIMED_OUT, session.url, emptyList(), null)
            }
            val detail = message.ifEmpty { status.orEmpty() }
            if (detail.isEmpty()) throw FlareSolverrException(FlareSolverrException.Reason.UNREADABLE_ANSWER, httpCode = code)
            throw FlareSolverrException(FlareSolverrException.Reason.ERROR_ANSWER, httpCode = code, detail = detail)
        }

        val solution = answer.obj("solution")
            ?: throw FlareSolverrException(FlareSolverrException.Reason.NO_SOLUTION, httpCode = code)
        val finalUrl = solution.string("url")?.toHttpUrlOrNull() ?: session.url
        // A missing or null cookie list is an empty one.
        val cookies = solution.array("cookies").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.toCookie(finalUrl)
        }
        val userAgent = solution.string("userAgent")?.takeIf { it.isNotBlank() }
        val page = BrowserPage(url = finalUrl, statusCode = solution.primitive("status")?.intOrNull, cookies = cookies)
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
        val configured = currentEndpoint()
        if (configured.isEmpty()) return ConnectionTest.NotConfigured
        val base = baseUrl(configured) ?: return ConnectionTest.InvalidAddress
        val (code, text) = try {
            client.newCall(Request.Builder().url(base).withCredentialsOf(base).get().build()).execute()
                .use { it.code to it.body.string() }
        } catch (e: IOException) {
            return ConnectionTest.Unreachable("${base.safeName()}: ${e.message ?: e::class.simpleName}")
        }
        val answer = parseObject(text)
        if (code !in 200..299 || answer == null || ("msg" !in answer && "version" !in answer)) {
            return ConnectionTest.Failed("HTTP $code, ${base.safeName()}")
        }
        return ConnectionTest.Ok(answer.string("version"))
    }

    /**
     * A FlareSolverr cookie (Chrome's: `.example.com` = domain cookie; the expiry in seconds, as `expires` (FlareSolverr
     * v2 / Byparr) or `expiry` (FlareSolverr v3 through Selenium), an integer or a fraction; none or ≤ 0 = session).
     */
    private fun JsonObject.toCookie(pageUrl: HttpUrl): Cookie? = try {
        val name = string("name") ?: return null
        val value = string("value") ?: return null
        val domain = string("domain")?.takeIf { it.isNotBlank() } ?: pageUrl.host
        Cookie.Builder()
            .name(name)
            .value(value)
            .apply { if (domain.startsWith('.')) domain(domain.removePrefix(".")) else hostOnlyDomain(domain) }
            .path(string("path")?.takeIf { it.startsWith('/') } ?: "/")
            .apply {
                val expires = EXPIRY_KEYS.firstNotNullOfOrNull { key -> primitive(key)?.doubleOrNull?.takeIf { it > 0 } }
                if (expires != null) expiresAt((expires * 1000).toLong())
                if (primitive("secure")?.booleanOrNull == true) secure()
                if (primitive("httpOnly")?.booleanOrNull == true) httpOnly()
            }
            .build()
    } catch (_: IllegalArgumentException) {
        null
    }

    /** User info in the address (a solver behind a proxy with basic auth) as an `Authorization` header. */
    private fun Request.Builder.withCredentialsOf(url: HttpUrl): Request.Builder = apply {
        if (url.username.isNotEmpty()) header("Authorization", Credentials.basic(url.username, url.password))
    }

    /** Whether the call got a connection to the service: a timeout before it is "unreachable", after it a slow solve. */
    private class ConnectionListener : EventListener() {
        @Volatile
        var connected = false

        override fun connectionAcquired(call: Call, connection: Connection) {
            connected = true
        }
    }

    companion object {
        val MIN_TIMEOUT: Duration = 60.seconds
        val ANSWER_MARGIN: Duration = 15.seconds

        private const val CANCEL_POLL_MS = 200L
        private val EXPIRY_KEYS = listOf("expires", "expiry")

        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        /** Its own client: not the app's (whose Cloudflare interceptor would call this backend again). */
        private val DEFAULT_CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
        }

        /** Cancels the running solver calls whose session was cancelled (one daemon thread for the app). */
        private val cancelWatcher: ScheduledExecutorService by lazy {
            Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "FlareSolverr-cancel").apply { isDaemon = true }
            }
        }

        /**
         * The API address for a configured [endpoint]: `…/v1` as given, else the base address with `/v1` appended;
         * empty path segments (a trailing `/`, `//`) are dropped, also after `v1` (P8 review: `…/v1/`); no query or
         * fragment; `null` for anything but an `http(s)` address.
         */
        fun apiUrl(endpoint: String): HttpUrl? {
            val url = endpoint.trim().toHttpUrlOrNull() ?: return null
            if (url.scheme != "http" && url.scheme != "https") return null
            val segments = url.encodedPathSegments.filter { it.isNotEmpty() }
            val api = if (segments.lastOrNull() == "v1") segments else segments + "v1"
            return url.newBuilder()
                .query(null)
                .fragment(null)
                .encodedPath("/" + api.joinToString("/"))
                .build()
        }

        /**
         * The service's base address (its status page) for a configured [endpoint]: the API address without `v1`, as a
         * directory (`http://host:8191/`, `https://host/solver/`).
         */
        fun baseUrl(endpoint: String): HttpUrl? = apiUrl(endpoint)?.let { api ->
            val segments = api.encodedPathSegments.dropLast(1)
            api.newBuilder().encodedPath(segments.joinToString("/", prefix = "/", postfix = if (segments.isEmpty()) "" else "/")).build()
        }

        /** `scheme://host:port` only: no user info, path or query in logs and messages. */
        fun HttpUrl.safeName(): String = "$scheme://$host:$port"

        private fun parseObject(text: String): JsonObject? = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        }

        /** A field as a primitive, `null` for a missing one, `null` or another type (no exception, P8 review). */
        private fun JsonObject.primitive(key: String): JsonPrimitive? = get(key) as? JsonPrimitive

        private fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull

        private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

        private fun JsonObject.array(key: String): List<JsonElement>? = get(key) as? JsonArray
    }
}

/**
 * KMK: why the FlareSolverr-compatible service could not be used (P8 review), with the HTTP status and the service's own
 * message where there are ones; the message the user sees (quoted in `desktop_cloudflare_solver_failed`) comes from
 * `KMR` strings.
 */
class FlareSolverrException(
    val reason: Reason,
    val httpCode: Int? = null,
    /** The service's own message (an error answer) or the network error (unreachable), as given. */
    val detail: String? = null,
    cause: Throwable? = null,
) : BrowserUnavailableException(describe(reason, httpCode, detail), cause) {

    enum class Reason {
        NOT_CONFIGURED,
        INVALID_ADDRESS,

        /** No connection to the address (refused, unknown host, connect timeout). */
        UNREACHABLE,

        /** Something answered, but no JSON object (another server at the address). */
        UNREADABLE_ANSWER,

        /** The service answered with an error and its message. */
        ERROR_ANSWER,

        /** The service said "ok" without a solution. */
        NO_SOLUTION,
    }

    private companion object {
        fun describe(reason: Reason, httpCode: Int?, detail: String?): String {
            val code = httpCode ?: 0
            return when (reason) {
                Reason.NOT_CONFIGURED -> stringResource(KMR.strings.desktop_flaresolverr_not_configured)
                Reason.INVALID_ADDRESS -> stringResource(KMR.strings.desktop_flaresolverr_invalid)
                Reason.UNREACHABLE -> stringResource(KMR.strings.desktop_flaresolverr_unreachable, detail.orEmpty())
                Reason.UNREADABLE_ANSWER -> stringResource(KMR.strings.desktop_flaresolverr_unreadable_answer, code)
                Reason.ERROR_ANSWER -> stringResource(KMR.strings.desktop_flaresolverr_error_answer, code, detail.orEmpty())
                Reason.NO_SOLUTION -> stringResource(KMR.strings.desktop_flaresolverr_no_solution, code)
            }
        }
    }
}
