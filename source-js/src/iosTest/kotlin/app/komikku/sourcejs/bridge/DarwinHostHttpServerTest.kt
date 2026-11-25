package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.timedTest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DarwinHostHttp] on its real engine (NSURLSession) against a Ktor CIO server in the simulator test process, on
 * `127.0.0.1` (App Transport Security does not apply to IP addresses): what the mock engine cannot show, i.e. the
 * session's own encoding negotiation and decompression, Foundation's joined response headers, no native redirects,
 * timeouts and cancellation of a real task.
 */
class DarwinHostHttpServerTest {

    private val requestSeen = CompletableDeferred<Unit>()
    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var base: String
    private val http = DarwinHostHttp()

    private fun Application.routes() = routing {
        route("/echo") {
            handle {
                val body = call.receiveText()
                val headers = call.request.headers
                call.response.header("X-A", "1")
                call.response.header("X-A", "2")
                call.respondText(
                    listOf(call.request.httpMethod.value, headers["X-Token"], headers["Accept-Encoding"], headers["Content-Type"], body)
                        .joinToString("|"),
                    status = HttpStatusCode.Created,
                )
            }
        }
        get("/gz") {
            call.response.header(HttpHeaders.ContentEncoding, "gzip")
            call.respondBytes(GZIPPED)
        }
        get("/start") { call.respondRedirect("/target") }
        get("/target") { call.respondText("target") }
        get("/slow") {
            requestSeen.complete(Unit)
            delay(10_000)
            call.respondText("late")
        }
        get("/big") { call.respondText("x".repeat(2048)) }
    }

    @BeforeTest
    fun start() = timedTest {
        withTimeout(10_000) {
            server = embeddedServer(CIO, port = 0, host = "127.0.0.1") { routes() }
            server.startSuspend(wait = false)
            base = "http://127.0.0.1:${server.engine.resolvedConnectors().first().port}"
        }
    }

    @AfterTest
    fun stop() = timedTest {
        http.close()
        withTimeout(10_000) { server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 1_000) }
    }

    @Test
    fun sendsTheRequestAndJoinsRepeatedResponseHeaders() = timedTest {
        val response = http.execute(
            HostHttpRequest(
                method = "POST",
                url = "$base/echo",
                headers = listOf("Content-Type" to "application/json", "X-Token" to "t", "Accept-Encoding" to "identity-from-source"),
                body = """{"a":1}""".encodeToByteArray(),
            ),
        )
        assertEquals(201, response.statusCode)
        val (method, token, acceptEncoding, contentType, body) = response.body.decodeToString().split("|")
        assertEquals(listOf("POST", "t", "application/json", """{"a":1}"""), listOf(method, token, contentType, body))
        // The session's own negotiation replaces the source's header.
        assertNotEquals("identity-from-source", acceptEncoding)
        assertTrue("gzip" in acceptEncoding, acceptEncoding)
        val xa = response.headers.filter { it.first.equals("X-A", ignoreCase = true) }.map { it.second }
        assertEquals("1, 2", xa.joinToString(", "))
    }

    @Test
    fun theSessionDecompressesAndTheEncodingHeadersAreGone() = timedTest {
        val response = http.execute(HostHttpRequest("GET", "$base/gz", listOf("Accept-Encoding" to "br"), null))
        assertEquals("décompressé", response.body.decodeToString())
        assertNull(response.headers.firstOrNull { it.first.equals("Content-Encoding", ignoreCase = true) })
    }

    @Test
    fun redirectsAreFollowedOnlyWhenAsked() = timedTest {
        val followed = http.execute(HostHttpRequest("GET", "$base/start", emptyList(), null))
        assertEquals(200 to "target", followed.statusCode to followed.body.decodeToString())
        // NSURLSession does not follow it by itself.
        val notFollowed = http.execute(HostHttpRequest("GET", "$base/start", emptyList(), null, followRedirects = false))
        assertEquals(302, notFollowed.statusCode)
    }

    @Test
    fun timeoutAndBodyCap() = timedTest {
        val late = assertFailsWith<IOException> {
            http.execute(HostHttpRequest("GET", "$base/slow", emptyList(), null, timeoutMillis = 300))
        }
        assertEquals("timeout", late.message)
        val big = assertFailsWith<IOException> {
            http.execute(HostHttpRequest("GET", "$base/big", emptyList(), null, maxBodyBytes = 1024))
        }
        assertEquals(HttpBridge.tooLargeMessage(1024), big.message)
    }

    @Test
    fun cancellationEndsTheCall() = timedTest(Dispatchers.Default) {
        val call = async { http.execute(HostHttpRequest("GET", "$base/slow", emptyList(), null)) }
        withTimeout(5_000) { requestSeen.await() }
        call.cancel()
        withTimeout(2_000) { call.join() }
        assertTrue(call.isCancelled)
    }

    private companion object {
        /** `gzip.compress("décompressé".encode(), mtime=0)`. */
        val GZIPPED = intArrayOf(
            31, 139, 8, 0, 0, 0, 0, 0, 2, 255, 75, 57, 188, 50, 57, 63, 183, 160, 40, 181, 184, 248, 240, 74, 0, 109,
            254, 96, 5, 13, 0, 0, 0,
        ).let { ints -> ByteArray(ints.size) { ints[it].toByte() } }
    }
}
