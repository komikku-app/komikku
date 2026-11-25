package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.TestHost
import app.komikku.sourcejs.provider
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
import kotlinx.coroutines.runBlocking
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
    fun sendsMethodHeadersAndBody() = runBlocking {
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
    fun sendsEveryMethod() = runBlocking {
        handler = { respond("", HttpStatusCode.NoContent) }
        val methods = listOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "PROPFIND")
        methods.forEach { method -> http.execute(HostHttpRequest(method, "https://h.test/$method", emptyList(), null)) }
        assertEquals(methods, recorded.map { it.method })
    }

    @Test
    fun dropsTheSourcesAcceptEncodingAndKeepsTheRest() = runBlocking {
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
    fun bodiesFollowOkHttpsRules() = runBlocking {
        handler = { respond("fail", HttpStatusCode.InternalServerError) }
        // Error statuses are returned, not thrown; a POST without body sends an empty one.
        val response = http.execute(HostHttpRequest("POST", "https://h.test/p", emptyList(), null))
        assertEquals(500 to "fail", response.statusCode to response.body.decodeToString())
        assertEquals(0, recorded.single().body.size)
        // A GET cannot carry a body (OkHttp refuses it too).
        assertFailsWith<IllegalArgumentException> {
            http.execute(HostHttpRequest("GET", "https://h.test/g", emptyList(), byteArrayOf(1)))
        }
        Unit
    }

    @Test
    fun returnsRawBytes() = runBlocking {
        val bytes = byteArrayOf(0, 1, -1, 0x7F)
        handler = { respond(bytes) }
        assertContentEquals(bytes, http.execute(get("https://h.test/b")).body)
    }

    @Test
    fun followsRedirectsUnlessAskedNotTo() = runBlocking {
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
    fun redirectsChangeMethodAndHeadersLikeOkHttp() = runBlocking {
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
    fun stopsAfterTwentyFollowUps() = runBlocking {
        handler = { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/loop")) }
        val error = assertFailsWith<IOException> { http.execute(get("https://h.test/loop")) }
        assertEquals("Too many follow-up requests: 21", error.message)
        assertEquals(21, recorded.size)
    }

    @Test
    fun honoursTheRequestTimeout() = runBlocking {
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
    fun stopsReadingBodiesOverTheLimit() = runBlocking {
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
    fun cancellationCancelsTheRequest() = runBlocking(Dispatchers.Default) {
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
    fun extensionsUseItThroughClient() = runBlocking {
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
