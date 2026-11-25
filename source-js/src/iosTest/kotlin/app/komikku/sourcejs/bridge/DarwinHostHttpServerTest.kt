package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.bridge.LoopbackHttpServer.Response
import app.komikku.sourcejs.timedTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DarwinHostHttp] on its real engine (NSURLSession) against [LoopbackHttpServer] on `127.0.0.1` inside the simulator
 * test process (App Transport Security does not apply to IP addresses): what the mock engine cannot show, i.e. the
 * session's own encoding negotiation and decompression, Foundation's joined response headers, no native redirects,
 * cookies, timeouts and cancellation of a real task, URLs NSURLSession must never see.
 */
class DarwinHostHttpServerTest {

    private val requestSeen = CompletableDeferred<Unit>()

    /** This test's cookie names (the shared cookie storage outlives the test). */
    private val cookie = "kmk${Random.nextInt(0, Int.MAX_VALUE)}"
    private val server = LoopbackHttpServer { request ->
        when (request.target) {
            "/echo" -> Response(
                status = 201,
                headers = listOf("X-A" to "1", "X-A" to "2"),
                body = listOf(
                    request.method,
                    request.headers["x-token"],
                    request.headers["accept-encoding"],
                    request.headers["content-type"],
                    request.body.decodeToString(),
                ).joinToString("|").encodeToByteArray(),
            )
            "/gz" -> Response(200, listOf("Content-Encoding" to "gzip"), GZIPPED)
            "/start" -> Response(302, listOf("Location" to "/target"))
            "/target" -> Response(200, body = "target".encodeToByteArray())
            "/slow" -> {
                requestSeen.complete(Unit)
                Response(200, body = "late".encodeToByteArray(), delaySeconds = 5.0)
            }
            "/big" -> Response(200, body = "x".repeat(2048).encodeToByteArray())
            "/set-cookie" -> Response(200, listOf("Set-Cookie" to "$cookie=one; Path=/"))
            "/redirect-setting-cookie" -> Response(302, listOf("Set-Cookie" to "${cookie}_hop=two; Path=/", "Location" to "/cookie"))
            "/cookie" -> Response(200, body = request.headers["cookie"].orEmpty().encodeToByteArray())
            else -> Response(404)
        }
    }
    private val base = server.baseUrl
    private val http = DarwinHostHttp()

    @AfterTest
    fun stop() {
        http.close()
        server.close()
        val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
        storage.cookiesForURL(NSURL(string = base)).orEmpty()
            .filterIsInstance<NSHTTPCookie>()
            .filter { it.name.startsWith(cookie) }
            .forEach { storage.deleteCookie(it) }
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
    fun cookiesAreStoredAndSentOnEveryHop() = timedTest {
        http.execute(HostHttpRequest("GET", "$base/set-cookie", emptyList(), null))
        val sent = http.execute(HostHttpRequest("GET", "$base/cookie", emptyList(), null)).body.decodeToString()
        assertTrue("$cookie=one" in sent, sent)
        // The redirect's cookie reaches the next hop, which is a request of its own.
        val afterRedirect = http.execute(HostHttpRequest("GET", "$base/redirect-setting-cookie", emptyList(), null))
        val sentAfterRedirect = afterRedirect.body.decodeToString()
        assertEquals(200, afterRedirect.statusCode)
        assertTrue("${cookie}_hop=two" in sentAfterRedirect && "$cookie=one" in sentAfterRedirect, sentAfterRedirect)
    }

    @Test
    fun urlsOtherThanHttpFailBeforeTheSession() = timedTest {
        // NSURLSession would answer them with a plain NSURLResponse, which Ktor's Darwin engine cannot take.
        listOf("data:,x" to "data", "file:///etc/hosts" to "file").forEach { (url, scheme) ->
            val error = assertFailsWith<IllegalArgumentException> { http.execute(HostHttpRequest("GET", url, emptyList(), null)) }
            assertEquals("Expected URL scheme 'http' or 'https' but was '$scheme'", error.message)
        }
    }

    @Test
    fun headHasNoBodyWhateverItsLength() = timedTest {
        val response = http.execute(HostHttpRequest("HEAD", "$base/big", emptyList(), null, maxBodyBytes = 1024))
        assertEquals(200, response.statusCode)
        assertEquals(0, response.body.size)
        assertEquals("2048", response.headers.firstOrNull { it.first.equals("Content-Length", ignoreCase = true) }?.second)
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
