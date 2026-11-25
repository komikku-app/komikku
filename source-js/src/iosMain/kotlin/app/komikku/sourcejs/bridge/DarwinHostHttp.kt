package app.komikku.sourcejs.bridge

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.takeFrom
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import platform.Foundation.NSHTTPCookieAcceptPolicy
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURLSessionConfiguration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * [HostHttp] on iOS: Ktor's Darwin engine, i.e. `NSURLSession`, with the semantics of the JVM's `OkHttpHostHttp`.
 *
 * - Only `http` / `https` URLs (`ws` / `wss` are sent as `http` / `https`, as OkHttp does); any other URL fails
 *   with an [IllegalArgumentException] in OkHttp's words before anything is sent.
 * - Any method; request headers as given, except a source's `Accept-Encoding`: NSURLSession negotiates the encoding
 *   itself and decompresses the body (Ktor then drops `Content-Encoding` / `Content-Length`, as OkHttp does).
 *   `Transfer-Encoding` / `Upgrade` are dropped too (Ktor refuses them; the session frames the body).
 *   Repeated request headers are sent as one comma-joined header (NSURLRequest keeps one value per name).
 * - A request body as given; `POST` / `PUT` / `PATCH` / `PROPPATCH` / `REPORT` without one send an empty body, `GET` /
 *   `HEAD` with one fail (OkHttp's rules).
 * - Redirects are followed here, not by NSURLSession or Ktor's `HttpRedirect`, with OkHttp's rules: up to 20
 *   follow-ups; the `Location` is resolved against the URL that was sent (as Ktor encoded it); 300 / 301 / 302 / 303
 *   turn a request other than `GET` / `HEAD` / `PROPFIND` into a body-less `GET`, 307 / 308 keep the method and body;
 *   `Authorization` is dropped when the scheme, host or port changes; HTTPS to HTTP is followed (OkHttp's default);
 *   a target other than `http` / `https` is not followed. With [HostHttpRequest.followRedirects] `false` the
 *   redirect is returned.
 * - [HostHttpRequest.timeoutMillis] bounds the whole call (all hops and the body), like OkHttp's call timeout, and
 *   fails with an [IOException] `timeout`. Without it the session's idle timeout applies (30 s for [DarwinHostHttp]'s
 *   own engine).
 * - The response: status, headers as received (repeated names joined by Foundation; the bridge joins Ktor's separate
 *   values the same way), the body capped at [HostHttpRequest.maxBodyBytes] with [HttpBridge.tooLargeMessage].
 *   A `HEAD` response and a 204 / 304 have an empty body whatever their `Content-Length` says (OkHttp's
 *   `promisesBody`).
 * - HTTP error statuses are returned; network failures throw. Cancelling the coroutine cancels the
 *   `NSURLSessionTask`. Certificates are verified by NSURLSession (no challenge handler is installed).
 *
 * Cookies: the own engine stores and sends them with `NSHTTPCookieStorage.sharedHTTPCookieStorage`, accepting every
 * cookie like the app's OkHttp cookie jar (Ktor's Darwin engine turns the session's cookie storage off;
 * [configureCookies] turns it back on). Each hop is its own `NSURLSessionTask` that starts after the previous
 * response, so a cookie set by a redirect is sent on the next hop. A caller-supplied engine needs [configureCookies]
 * in its own `configureSession` for the same.
 *
 * @param engine the Ktor engine; tests pass a `MockEngine`. It is not closed by [close] unless this class created it.
 */
class DarwinHostHttp private constructor(
    private val engine: HttpClientEngine,
    private val ownsEngine: Boolean,
) : HostHttp {

    /** Over [engine], which the caller keeps owning. */
    constructor(engine: HttpClientEngine) : this(engine, ownsEngine = false)

    /** Over its own Darwin engine (a dedicated `NSURLSession` with a [idleTimeoutSeconds] request idle timeout). */
    constructor(idleTimeoutSeconds: Double = DEFAULT_IDLE_TIMEOUT_SECONDS) : this(
        Darwin.create {
            configureSession {
                timeoutIntervalForRequest = idleTimeoutSeconds
                configureCookies()
            }
        },
        ownsEngine = true,
    )

    private val client = HttpClient(engine) {
        // Redirects are followed by [execute] with OkHttp's rules.
        followRedirects = false
        // Error statuses are results, not exceptions.
        expectSuccess = false
        // No `Accept` / `Accept-Charset` the source did not ask for; the body is read raw.
        useDefaultTransformers = false
    }

    override suspend fun execute(request: HostHttpRequest): HostHttpResponse {
        // Before Ktor sees it: Ktor's Darwin engine casts every response to `NSHTTPURLResponse` inside an NSURLSession
        // delegate callback, so a `data:` or `file:` URL would kill the process instead of failing the call.
        val url = httpUrl(request.url)
        require(request.body == null || request.method !in METHODS_WITHOUT_BODY) {
            "method ${request.method} must not have a request body."
        }
        val timeout = request.timeoutMillis?.takeIf { it > 0 } ?: return follow(request, url)
        val deadline = TimeSource.Monotonic.markNow() + timeout.milliseconds
        return try {
            withTimeout(timeout.milliseconds) { follow(request, url) }
        } catch (e: Throwable) {
            // The caller's own cancellation stays a cancellation.
            currentCoroutineContext().ensureActive()
            // A failure caused by the timeout cancelling the request (the engine may report it as its own error).
            if (e is TimeoutCancellationException || deadline.hasPassedNow()) throw IOException("timeout", e)
            throw e
        }
    }

    /** Closes the client, and the engine when this instance created it. */
    fun close() {
        client.close()
        if (ownsEngine) engine.close()
    }

    /** One hop of a call: what is sent. */
    private class Hop(
        val method: String,
        val url: String,
        val headers: List<Pair<String, String>>,
        val body: ByteArray?,
    )

    /** How one hop ended: a final response, or a redirect to follow. */
    private sealed interface HopResult {
        class Final(val response: HostHttpResponse) : HopResult
        class Redirect(val next: Hop) : HopResult
    }

    private suspend fun follow(request: HostHttpRequest, url: String): HostHttpResponse {
        var hop = Hop(
            method = request.method,
            url = url,
            // A source-supplied Accept-Encoding would hand back compressed bytes; the session negotiates it. The framing
            // headers are the engine's (Ktor refuses them).
            headers = request.headers.filterNot { (name, _) -> DROPPED_HEADERS.any { it.equals(name, ignoreCase = true) } },
            body = request.body,
        )
        var followUps = 0
        while (true) {
            when (val result = send(hop, request.followRedirects, request.maxBodyBytes)) {
                is HopResult.Final -> return result.response
                is HopResult.Redirect -> {
                    if (++followUps > MAX_FOLLOW_UPS) throw IOException("Too many follow-up requests: $followUps")
                    hop = result.next
                }
            }
        }
    }

    private suspend fun send(hop: Hop, followRedirects: Boolean, maxBodyBytes: Long?): HopResult {
        val statement = client.prepareRequest {
            method = HttpMethod(hop.method)
            url(hop.url)
            headers {
                hop.headers.forEach { (name, value) -> append(name, value) }
            }
            val bytes = hop.body ?: ByteArray(0).takeIf { hop.method in METHODS_WITH_BODY }
            if (bytes != null) setBody(RawBytes(bytes))
        }
        return statement.execute { response ->
            val redirect = if (followRedirects) redirectOf(hop, response) else null
            redirect?.let { HopResult.Redirect(it) } ?: HopResult.Final(
                HostHttpResponse(
                    statusCode = response.status.value,
                    headers = buildList {
                        response.headers.forEach { name, values -> values.forEach { add(name to it) } }
                    },
                    body = if (promisesBody(hop.method, response.status.value)) {
                        response.readBody(maxBodyBytes)
                    } else {
                        ByteArray(0)
                    },
                ),
            )
        }
    }

    /**
     * Whether a response has a body (OkHttp's `promisesBody`): not for `HEAD`, whose `Content-Length` is the `GET`'s,
     * nor for 204 / 304.
     */
    private fun promisesBody(method: String, code: Int): Boolean =
        method != "HEAD" && code != 204 && code != 304

    /** The next hop when [response] is a redirect OkHttp would follow, `null` otherwise (it is the result then). */
    private fun redirectOf(hop: Hop, response: HttpResponse): Hop? {
        val code = response.status.value
        if (code !in REDIRECT_CODES) return null
        val location = response.headers[HttpHeaders.Location] ?: return null
        // The URL that was sent, as Ktor encoded it: the script's own text may have spaces or non-ASCII characters that
        // `NSURL` rejects on iOS 16 and older.
        val from = response.call.request.url
        val target = resolve(from, location) ?: return null
        if (target.protocol.name !in HTTP_SCHEMES) return null

        var method = hop.method
        var body = hop.body
        var headers = hop.headers
        // OkHttp's `buildRedirectRequest`: only PROPFIND and 307 / 308 keep the method and body.
        if (method !in METHODS_WITHOUT_BODY && method != "PROPFIND" && code != 307 && code != 308) {
            method = "GET"
            body = null
            headers = headers.filterNot { (name, _) -> BODY_HEADERS.any { it.equals(name, ignoreCase = true) } }
        }
        if (!sameOrigin(from, target)) {
            headers = headers.filterNot { (name, _) -> name.equals(HttpHeaders.Authorization, ignoreCase = true) }
        }
        return Hop(method, target.toString(), headers, body)
    }

    /**
     * [location] resolved against [base] (RFC 3986, section 5.2, like OkHttp's `HttpUrl.resolve`): a reference with a
     * scheme or an authority stands alone (no user info or port of [base]); a reference with a path or a query
     * replaces the base's query; the fragment is the reference's; dot segments are removed (as Ktor's `HttpRedirect`
     * does). `null` when it does not parse: OkHttp's `resolve` returns `null` then, and the redirect is the result.
     */
    private fun resolve(base: Url, location: String): Url? = try {
        val reference = location.trim()
        val builder = when {
            schemeOf(reference) != null -> URLBuilder().takeFrom(reference)
            reference.startsWith("//") -> URLBuilder().apply { protocol = base.protocol }.takeFrom(reference)
            else -> URLBuilder(base).apply {
                if (reference.substringBefore('#').isNotEmpty()) {
                    parameters.clear()
                    trailingQuery = false
                }
                encodedFragment = ""
                takeFrom(reference)
            }
        }
        if (reference.substringBefore('#').substringBefore('?').isNotEmpty()) {
            builder.encodedPathSegments = removeDotSegments(builder.encodedPathSegments)
        }
        builder.build()
    } catch (e: Exception) {
        null
    }

    private fun sameOrigin(a: Url, b: Url): Boolean =
        a.protocol.name == b.protocol.name && a.host.equals(b.host, ignoreCase = true) && a.port == b.port

    /** The body, failing as soon as it is known to exceed [maxBytes] (declared length or bytes read). */
    private suspend fun HttpResponse.readBody(maxBytes: Long?): ByteArray {
        val channel = bodyAsChannel()
        if (maxBytes == null || maxBytes == Long.MAX_VALUE) return channel.readBuffer().readByteArray()
        // Ktor removes Content-Length when the session decompressed the body, so this is the body's length, except
        // where Ktor keeps the encoding headers (`OPTIONS`): then it is the compressed length.
        val declared = headers[HttpHeaders.ContentLength]?.toLongOrNull()
            ?.takeIf { headers[HttpHeaders.ContentEncoding] == null }
        if (declared != null && declared > maxBytes) throw IOException(HttpBridge.tooLargeMessage(maxBytes))
        val bytes = channel.readBuffer(maxBytes + 1).readByteArray()
        if (bytes.size > maxBytes) throw IOException(HttpBridge.tooLargeMessage(maxBytes))
        // `readBuffer(max)` ends quietly at a failed channel: a body cut off by a network error is that error.
        channel.closedCause?.let { throw it }
        return bytes
    }

    /** A body sent as is; its `Content-Type` is the request header (the source's), so it is not parsed or changed. */
    private class RawBytes(private val bytes: ByteArray) : OutgoingContent.ByteArrayContent() {
        override val contentLength: Long get() = bytes.size.toLong()

        override fun bytes(): ByteArray = bytes
    }

    companion object {
        /**
         * Stores and sends cookies with `NSHTTPCookieStorage.sharedHTTPCookieStorage`, accepting all of them like the
         * app's OkHttp cookie jar. Ktor's Darwin engine sets the storage to `nil` before running `configureSession`,
         * so an engine without this has no cookies at all.
         */
        fun NSURLSessionConfiguration.configureCookies() {
            HTTPCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage
            HTTPCookieAcceptPolicy = NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyAlways
            HTTPShouldSetCookies = true
        }

        /**
         * [url] when it is an `http` / `https` URL, with `ws` / `wss` turned into those like OkHttp's
         * `Request.Builder`; otherwise an [IllegalArgumentException] with OkHttp's `HttpUrl` message.
         */
        internal fun httpUrl(url: String): String {
            val canonical = when {
                url.startsWith("ws:", ignoreCase = true) -> "http:" + url.substring(3)
                url.startsWith("wss:", ignoreCase = true) -> "https:" + url.substring(4)
                else -> url
            }
            val input = canonical.trim { it in ASCII_WHITESPACE }
            val scheme = schemeOf(input)
            if (scheme == null) {
                val truncated = if (input.length > 6) input.take(6) + "..." else input
                throw IllegalArgumentException(
                    "Expected URL scheme 'http' or 'https' but no scheme was found for $truncated",
                )
            }
            require(scheme.lowercase() in HTTP_SCHEMES) { "Expected URL scheme 'http' or 'https' but was '$scheme'" }
            return canonical
        }

        /** The scheme of [input] (OkHttp's `schemeDelimiterOffset`): a letter, letters, digits, `+ - .`, then `:`. */
        private fun schemeOf(input: String): String? {
            if (input.isEmpty() || !input[0].isAsciiLetter()) return null
            for (i in 1 until input.length) {
                val c = input[i]
                when {
                    c.isAsciiLetter() || c in '0'..'9' || c == '+' || c == '-' || c == '.' -> continue
                    c == ':' -> return input.substring(0, i)
                    else -> return null
                }
            }
            return null
        }

        private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

        private val ASCII_WHITESPACE = charArrayOf('\t', '\n', '\u000C', '\r', ' ')

        /** Ktor `HttpRedirect`'s dot-segment removal; the root is kept, so `..` cannot go above it. */
        private fun removeDotSegments(path: List<String>): List<String> {
            if (path.none { it == "." || it == ".." }) return path
            val segments = if (path.firstOrNull() == "") path else listOf("") + path
            val output = ArrayList<String>(segments.size)
            segments.forEachIndexed { index, segment ->
                val isLast = index == segments.lastIndex
                when (segment) {
                    "." -> if (isLast) output.add("")
                    ".." -> {
                        if (output.size > 1) output.removeAt(output.lastIndex)
                        if (isLast) output.add("")
                    }
                    else -> output.add(segment)
                }
            }
            return output
        }

        /** NSURLSession's request idle timeout of the default engine, like the app's OkHttp read timeout. */
        const val DEFAULT_IDLE_TIMEOUT_SECONDS = 30.0

        /** OkHttp's `RetryAndFollowUpInterceptor.MAX_FOLLOW_UPS`. */
        private const val MAX_FOLLOW_UPS = 20

        /** The methods OkHttp refuses without a body (`HttpMethod.requiresRequestBody`). */
        private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")

        /** The methods OkHttp refuses with a body (`HttpMethod.permitsRequestBody`). */
        private val METHODS_WITHOUT_BODY = setOf("GET", "HEAD")

        /** Request headers never sent as given: the session negotiates the encoding and frames the body. */
        private val DROPPED_HEADERS = listOf(HttpHeaders.AcceptEncoding, HttpHeaders.TransferEncoding, HttpHeaders.Upgrade)

        private val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)

        private val HTTP_SCHEMES = setOf("http", "https")

        /** Dropped with the body when a redirect turns the request into a `GET`. */
        private val BODY_HEADERS = listOf(HttpHeaders.TransferEncoding, HttpHeaders.ContentLength, HttpHeaders.ContentType)
    }
}
