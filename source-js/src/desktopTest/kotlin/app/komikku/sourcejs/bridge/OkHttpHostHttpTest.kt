package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.TestHost
import app.komikku.sourcejs.provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okio.Buffer
import okio.GzipSink
import okio.buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [OkHttpHostHttp] against a real HTTP server, and the `Client` bridge on top of it. */
class OkHttpHostHttpTest {
    private val server = MockWebServer()
    private val http = OkHttpHostHttp(OkHttpClient())

    @BeforeTest
    fun start() = server.start()

    @AfterTest
    fun stop() = server.close()

    @Test
    fun sendsMethodHeadersAndBody() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(201).addHeader("Content-Type", "text/plain").addHeader("X-A", "1").addHeader("X-A", "2")
                .body("created").build(),
        )
        val response = http.execute(
            HostHttpRequest(
                method = "POST",
                url = server.url("/items?x=1").toString(),
                headers = listOf("Content-Type" to "application/json", "X-Token" to "t"),
                body = """{"a":1}""".encodeToByteArray(),
            ),
        )
        assertEquals(201, response.statusCode)
        assertEquals("created", response.body.decodeToString())
        assertEquals(listOf("1", "2"), response.headers.filter { it.first.equals("X-A", true) }.map { it.second })

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/items?x=1", recorded.target)
        assertEquals("application/json", recorded.headers["Content-Type"])
        assertEquals("t", recorded.headers["X-Token"])
        assertEquals("""{"a":1}""", recorded.body?.utf8())
    }

    @Test
    fun postWithoutBodySendsAnEmptyBody() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("fail").build())
        val response = http.execute(HostHttpRequest("POST", server.url("/p").toString(), emptyList(), null))
        // Error statuses are returned, not thrown.
        assertEquals(500, response.statusCode)
        assertEquals(0L, server.takeRequest().bodySize)
    }

    @Test
    fun returnsRawBytes() = runBlocking {
        val bytes = byteArrayOf(0, 1, -1, 0x7F)
        server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
        assertContentEquals(bytes, http.execute(HostHttpRequest("GET", server.url("/b").toString(), emptyList(), null)).body)
    }

    @Test
    fun followsRedirectsUnlessAskedNotTo() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/target").build())
        server.enqueue(MockResponse.Builder().body("target").build())
        val followed = http.execute(HostHttpRequest("GET", server.url("/start").toString(), emptyList(), null))
        assertEquals(200 to "target", followed.statusCode to followed.body.decodeToString())

        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/target").build())
        val notFollowed = http.execute(HostHttpRequest("GET", server.url("/start").toString(), emptyList(), null, followRedirects = false))
        assertEquals(302, notFollowed.statusCode)
    }

    @Test
    fun honoursTheRequestTimeout() = runBlocking {
        server.enqueue(MockResponse.Builder().headersDelay(5, TimeUnit.SECONDS).body("late").build())
        assertFailsWith<IOException> {
            http.execute(HostHttpRequest("GET", server.url("/slow").toString(), emptyList(), null, timeoutMillis = 200))
        }
        Unit
    }

    @Test
    fun decompressesEvenWhenTheSourceAsksForAnEncoding() = runBlocking {
        val gzipped = Buffer().apply { GzipSink(this).buffer().use { it.writeUtf8("décompressé") } }
        server.enqueue(MockResponse.Builder().addHeader("Content-Encoding", "gzip").body(gzipped).build())
        val response = http.execute(
            HostHttpRequest("GET", server.url("/gz").toString(), listOf("Accept-Encoding" to "gzip, deflate, br", "X-A" to "1"), null),
        )
        assertEquals("décompressé", response.body.decodeToString())
        val recorded = server.takeRequest()
        // OkHttp's own negotiation, not the source's header.
        assertEquals("gzip", recorded.headers["Accept-Encoding"])
        assertEquals("1", recorded.headers["X-A"])
    }

    @Test
    fun stopsReadingBodiesOverTheLimit() = runBlocking {
        fun request() = HostHttpRequest("GET", server.url("/big").toString(), emptyList(), null, maxBodyBytes = 1024)
        server.enqueue(MockResponse.Builder().body("x".repeat(2048)).build())
        assertFailsWith<IOException> { http.execute(request()) }
        server.enqueue(MockResponse.Builder().chunkedBody("x".repeat(2048), 100).build())
        assertFailsWith<IOException> { http.execute(request()) }
        server.enqueue(MockResponse.Builder().body("x".repeat(1024)).build())
        assertEquals(1024, http.execute(request()).body.size)
    }

    /** A client whose [EventListener] completes [canceled] when OkHttp cancels a call. */
    private fun cancelTrackingClient(canceled: CompletableDeferred<Unit>) = OkHttpClient.Builder()
        .eventListener(
            object : EventListener() {
                override fun canceled(call: Call) {
                    canceled.complete(Unit)
                }
            },
        )
        .build()

    @Test
    fun cancellationCancelsTheCall() = runBlocking(Dispatchers.Default) {
        val canceled = CompletableDeferred<Unit>()
        val http = OkHttpHostHttp(cancelTrackingClient(canceled))
        server.enqueue(MockResponse.Builder().headersDelay(10, TimeUnit.SECONDS).body("late").build())
        val call = async { http.execute(HostHttpRequest("GET", server.url("/slow").toString(), emptyList(), null)) }
        // The request reached the server, so the OkHttp call is in flight.
        assertEquals("/slow", server.takeRequest(5, TimeUnit.SECONDS)?.target)
        call.cancel()
        withTimeout(2_000) { canceled.await() }
        withTimeout(2_000) { call.join() }
        assertTrue(call.isCancelled)
    }

    @Test
    fun cancellingAnExtensionCallCancelsItsRequest() = runBlocking(Dispatchers.Default) {
        val canceled = CompletableDeferred<Unit>()
        server.enqueue(MockResponse.Builder().headersDelay(10, TimeUnit.SECONDS).body("late").build())
        val extension = TestHost(http = OkHttpHostHttp(cancelTrackingClient(canceled))).extension(
            provider("""async getPopular(page) { await new Client().get(${'"'}${server.url("/slow")}${'"'}); }"""),
        )
        val call = async { extension.getPopular(1) }
        assertEquals("/slow", server.takeRequest(5, TimeUnit.SECONDS)?.target)
        call.cancel()
        withTimeout(5_000) { canceled.await() }
        withTimeout(5_000) { call.join() }
        assertTrue(call.isCancelled)
    }

    @Test
    fun extensionsUseItThroughClient() = runBlocking {
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "application/json; charset=utf-8")
                .body("""{"results":[{"title":"Ünïcode","id":"42"}],"more":true}""").build(),
        )
        val source = JsSourceDescriptor(id = 1, name = "Mock", baseUrl = server.url("").toString().trimEnd('/'))
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
        val recorded = server.takeRequest()
        assertEquals("/popular?page=3", recorded.target)
        assertEquals("application/json", recorded.headers["Accept"])
    }
}
