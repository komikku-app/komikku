package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.JsSourceException
import app.komikku.sourcejs.TestHost
import app.komikku.sourcejs.provider
import app.komikku.sourcejs.timedTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DarwinHostHttp]'s own logic (headers, bodies, redirects, timeout, body cap, cancellation) against Ktor's
 * `MockEngine`, the same cases as the JVM's `OkHttpHostHttpTest`. The NSURLSession specifics are in
 * [DarwinHostHttpServerTest].
 */
class DarwinHostHttpTest {

    /** What the mock engine received, in order. */
    private class Recorded(val method: String, val url: String, val headers: Map<String, List<String>>, val body: ByteArray)

    private val recorded = mutableListOf<Recorded>()
    private var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.NotFound) }

    private val engine = MockEngine { request ->
        recorded += Recorded(
            method = request.method.value,
            url = request.url.toString(),
            headers = request.headers.entries().associate { it.key to it.value },
            body = request.body.toByteArray(),
        )
        handler(request)
    }
    private val http = DarwinHostHttp(engine)

    @AfterTest
    fun close() {
        http.close()
        engine.close()
    }

    private fun get(url: String, headers: List<Pair<String, String>> = emptyList(), maxBodyBytes: Long? = null) =
        HostHttpRequest("GET", url, headers, null, maxBodyBytes = maxBodyBytes)

    @Test
    fun sendsMethodHeadersAndBody() = timedTest {
        handler = {
            respond(
                "created",
                HttpStatusCode.Created,
                headersOf("Content-Type" to listOf("text/plain"), "X-A" to listOf("1", "2")),
            )
        }
        val response = http.execute(
            HostHttpRequest(
                method = "POST",
                url = "https://h.test/items?x=1",
                headers = listOf("Content-Type" to "application/json", "X-Token" to "t"),
                body = """{"a":1}""".encodeToByteArray(),
            ),
        )
        assertEquals(201, response.statusCode)
        assertEquals("created", response.body.decodeToString())
        assertEquals(listOf("1", "2"), response.headers.filter { it.first.equals("X-A", true) }.map { it.second })

        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://h.test/items?x=1", request.url)
        assertEquals(listOf("application/json"), request.headers["Content-Type"])
        assertEquals(listOf("t"), request.headers["X-Token"])
        assertEquals("""{"a":1}""", request.body.decodeToString())
    }

    @Test
    fun sendsEveryMethod() = timedTest {
        handler = { respond("", HttpStatusCode.NoContent) }
        val methods = listOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "PROPFIND")
        methods.forEach { method -> http.execute(HostHttpRequest(method, "https://h.test/$method", emptyList(), null)) }
        assertEquals(methods, recorded.map { it.method })
    }

    @Test
    fun dropsTheSourcesAcceptEncodingAndKeepsTheRest() = timedTest {
        handler = { respond("ok") }
        http.execute(get("https://h.test/gz", listOf("Accept-Encoding" to "gzip, deflate, br", "X-A" to "1", "User-Agent" to "UA")))
        val headers = recorded.single().headers
        assertNull(headers.keys.firstOrNull { it.equals("Accept-Encoding", ignoreCase = true) })
        assertEquals(listOf("1"), headers["X-A"])
        assertEquals(listOf("UA"), headers["User-Agent"])
        // No Accept / Accept-Charset the source did not send.
        assertNull(headers.keys.firstOrNull { it.equals("Accept-Charset", ignoreCase = true) || it.equals("Accept", ignoreCase = true) })
    }

    @Test
    fun bodiesFollowOkHttpsRules() = timedTest {
        handler = { respond("fail", HttpStatusCode.InternalServerError) }
        // Error statuses are returned, not thrown; a POST without body sends an empty one.
        val response = http.execute(HostHttpRequest("POST", "https://h.test/p", emptyList(), null))
        assertEquals(500 to "fail", response.statusCode to response.body.decodeToString())
        assertEquals(0, recorded.single().body.size)
        // A GET cannot carry a body (OkHttp refuses it too).
        assertFailsWith<IllegalArgumentException> {
            http.execute(HostHttpRequest("GET", "https://h.test/g", emptyList(), byteArrayOf(1)))
        }
    }

    @Test
    fun returnsRawBytes() = timedTest {
        val bytes = byteArrayOf(0, 1, -1, 0x7F)
        handler = { respond(bytes) }
        assertContentEquals(bytes, http.execute(get("https://h.test/b")).body)
    }

    @Test
    fun followsRedirectsUnlessAskedNotTo() = timedTest {
        handler = { request ->
            when (request.url.encodedPath) {
                "/start" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "target?q=1"))
                "/target" -> respond("target")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val followed = http.execute(get("https://h.test/start"))
        assertEquals(200 to "target", followed.statusCode to followed.body.decodeToString())
        // The relative location is resolved against the request URL.
        assertEquals(listOf("https://h.test/start", "https://h.test/target?q=1"), recorded.map { it.url })
        recorded.clear()

        val notFollowed = http.execute(HostHttpRequest("GET", "https://h.test/start", emptyList(), null, followRedirects = false))
        assertEquals(302, notFollowed.statusCode)
        assertEquals(listOf("target?q=1"), notFollowed.headers.filter { it.first.equals("Location", true) }.map { it.second })
        assertEquals(1, recorded.size)
    }

    @Test
    fun redirectsChangeMethodAndHeadersLikeOkHttp() = timedTest {
        handler = { request ->
            when (request.url.encodedPath) {
                "/found" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/landing"))
                "/temporary" -> respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/landing"))
                "/elsewhere" -> respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "https://other.test/landing"))
                else -> respond("landed")
            }
        }
        val headers = listOf("Content-Type" to "text/plain", "Authorization" to "Bearer x")
        fun post(path: String) = HostHttpRequest("POST", "https://h.test$path", headers, "data".encodeToByteArray())

        // 302 after a POST: a GET without body and body headers.
        http.execute(post("/found"))
        val afterFound = recorded.last()
        assertEquals("GET" to "https://h.test/landing", afterFound.method to afterFound.url)
        assertEquals(0, afterFound.body.size)
        assertNull(afterFound.headers["Content-Type"])
        assertEquals(listOf("Bearer x"), afterFound.headers["Authorization"])

        // 307 keeps the method and the body.
        http.execute(post("/temporary"))
        val afterTemporary = recorded.last()
        assertEquals("POST" to "data", afterTemporary.method to afterTemporary.body.decodeToString())
        assertEquals(listOf("text/plain"), afterTemporary.headers["Content-Type"])

        // Another host does not get the credentials.
        http.execute(get("https://h.test/elsewhere", listOf("Authorization" to "Bearer x")))
        val afterElsewhere = recorded.last()
        assertEquals("https://other.test/landing", afterElsewhere.url)
        assertNull(afterElsewhere.headers["Authorization"])
    }

    @Test
    fun redirectsResolveAgainstTheUrlThatWasSent() = timedTest {
        handler = { request ->
            when (request.url.encodedPath) {
                "/a%20b/%C3%A9/start" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "next?q=1"))
                "/a%20b/%C3%A9/next" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "../up dir/ü"))
                "/a%20b/up%20dir/%C3%BC" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/final"))
                else -> respond(request.url.toString())
            }
        }
        val response = http.execute(get("https://h.test/a b/é/start?x=1"))
        assertEquals("https://h.test/final", response.body.decodeToString())
        // Spaces and non-ASCII encoded, the base's query not carried over, dot segments removed.
        assertEquals(
            listOf(
                "https://h.test/a%20b/%C3%A9/start?x=1",
                "https://h.test/a%20b/%C3%A9/next?q=1",
                "https://h.test/a%20b/up%20dir/%C3%BC",
                "https://h.test/final",
            ),
            recorded.map { it.url },
        )
    }

    @Test
    fun redirectsToAnotherAuthorityDoNotKeepTheBasesUserOrPort() = timedTest {
        handler = { request ->
            when (request.url.host) {
                "h.test" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "//other.test/x"))
                else -> respond("other")
            }
        }
        http.execute(get("https://user:pw@h.test:8443/start", listOf("Authorization" to "Bearer x")))
        val second = recorded.last()
        assertEquals("https://other.test/x", second.url)
        assertNull(second.headers["Authorization"])
    }

    @Test
    fun redirectsToOtherSchemesAreTheResult() = timedTest {
        handler = { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "intent://x#Intent;end")) }
        assertEquals(302, http.execute(get("https://h.test/app")).statusCode)
        assertEquals(1, recorded.size)
    }

    @Test
    fun stopsAfterTwentyFollowUps() = timedTest {
        handler = { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/loop")) }
        val error = assertFailsWith<IOException> { http.execute(get("https://h.test/loop")) }
        assertEquals("Too many follow-up requests: 21", error.message)
        assertEquals(21, recorded.size)
    }

    @Test
    fun honoursTheRequestTimeout() = timedTest {
        handler = {
            delay(5_000)
            respond("late")
        }
        val error = assertFailsWith<IOException> {
            http.execute(HostHttpRequest("GET", "https://h.test/slow", emptyList(), null, timeoutMillis = 200))
        }
        assertEquals("timeout", error.message)
    }

    @Test
    fun stopsReadingBodiesOverTheLimit() = timedTest {
        val big = "x".repeat(2048).encodeToByteArray()
        // Declared too large.
        handler = { respond(ByteReadChannel(big), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "2048")) }
        val declared = assertFailsWith<IOException> { http.execute(get("https://h.test/big", maxBodyBytes = 1024)) }
        assertEquals(HttpBridge.tooLargeMessage(1024), declared.message)
        // Streamed without a length.
        handler = { respond(ByteReadChannel(big)) }
        val streamed = assertFailsWith<IOException> { http.execute(get("https://h.test/big", maxBodyBytes = 1024)) }
        assertEquals(HttpBridge.tooLargeMessage(1024), streamed.message)
        // Exactly the limit, and no limit at all.
        handler = { respond(big.copyOf(1024)) }
        assertEquals(1024, http.execute(get("https://h.test/big", maxBodyBytes = 1024)).body.size)
        handler = { respond(big) }
        assertEquals(2048, http.execute(get("https://h.test/big", maxBodyBytes = Long.MAX_VALUE)).body.size)
    }

    @Test
    fun headAnd204And304HaveNoBody() = timedTest {
        // A HEAD declares the length of the GET's body; Ktor keeps that Content-Length for HEAD.
        handler = { respond(ByteReadChannel.Empty, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "4096")) }
        val head = http.execute(HostHttpRequest("HEAD", "https://h.test/big", emptyList(), null, maxBodyBytes = 1024))
        assertEquals(200 to 0, head.statusCode to head.body.size)
        assertEquals(listOf("4096"), head.headers.filter { it.first.equals("Content-Length", true) }.map { it.second })
        listOf(HttpStatusCode.NoContent, HttpStatusCode.NotModified).forEach { status ->
            handler = { respond(ByteReadChannel.Empty, status, headersOf(HttpHeaders.ContentLength, "4096")) }
            val response = http.execute(get("https://h.test/${status.value}", maxBodyBytes = 1024))
            assertEquals(status.value to 0, response.statusCode to response.body.size)
        }
    }

    @Test
    fun refusesUrlsOtherThanHttpLikeOkHttp() = timedTest {
        handler = { respond("ok") }
        val data = assertFailsWith<IllegalArgumentException> { http.execute(get("data:,x")) }
        assertEquals("Expected URL scheme 'http' or 'https' but was 'data'", data.message)
        val file = assertFailsWith<IllegalArgumentException> { http.execute(get("file:///etc/hosts")) }
        assertEquals("Expected URL scheme 'http' or 'https' but was 'file'", file.message)
        val relative = assertFailsWith<IllegalArgumentException> { http.execute(get("/relative/path")) }
        assertEquals("Expected URL scheme 'http' or 'https' but no scheme was found for /relat...", relative.message)
        assertTrue(recorded.isEmpty())
        // OkHttp's Request.Builder sends ws / wss as http / https.
        http.execute(get("wss://h.test/socket"))
        assertEquals("https://h.test/socket", recorded.single().url)
    }

    @Test
    fun urlsOtherThanHttpAreCatchableBridgeErrors() = timedTest {
        val source = JsSourceDescriptor(id = 1, name = "Mock", baseUrl = "https://h.test")
        val code = provider(
            """
            async getPopular(page) { await new Client().get("data:,x"); }
            async getLatestUpdates(page) {
              try { await new Client().get("file:///etc/hosts"); } catch (e) { return { list: [{ name: e.message, link: "x" }], hasNextPage: false }; }
            }
            """,
        )
        val extension = TestHost(http = http).extension(code, source)
        val error = assertFailsWith<JsSourceException.BridgeError> { extension.getPopular(1) }
        assertEquals("http", error.channel)
        assertEquals("HTTP GET data:,x failed: Expected URL scheme 'http' or 'https' but was 'data'", error.detail)
        assertEquals(
            "HTTP GET file:///etc/hosts failed: Expected URL scheme 'http' or 'https' but was 'file'",
            extension.getLatestUpdates(1).list.single().name,
        )
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun cancellationCancelsTheRequest() = timedTest(Dispatchers.Default) {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        handler = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        val call = async { http.execute(get("https://h.test/slow")) }
        withTimeout(5_000) { started.await() }
        call.cancel()
        withTimeout(2_000) { cancelled.await() }
        withTimeout(2_000) { call.join() }
        assertTrue(call.isCancelled)
    }

    @Test
    fun extensionsUseItThroughClient() = timedTest {
        handler = {
            respond(
                """{"results":[{"title":"Ünïcode","id":"42"}],"more":true}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"),
            )
        }
        val source = JsSourceDescriptor(id = 1, name = "Mock", baseUrl = "https://h.test")
        val code = provider(
            """
            async getPopular(page) {
              const res = await new Client().get(this.source.baseUrl + "/popular?page=" + page, { "Accept": "application/json" });
              const data = JSON.parse(res.body);
              return { list: data.results.map((m) => ({ name: m.title, link: "/m/" + m.id })), hasNextPage: data.more && res.statusCode == 200 };
            }
            """,
        )
        val page = TestHost(http = http).extension(code, source).getPopular(3)
        assertEquals(listOf("Ünïcode" to "/m/42"), page.list.map { it.name to it.link })
        assertTrue(page.hasNextPage)
        val request = recorded.single()
        assertEquals("https://h.test/popular?page=3", request.url)
        assertEquals(listOf("application/json"), request.headers["Accept"])
    }
}
