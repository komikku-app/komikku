package app.komikku.sourcejs.conformance

import app.komikku.sourcejs.FakeHostHttp
import app.komikku.sourcejs.TestHost
import app.komikku.sourcejs.bridge.HostHttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Contract 4.2: `Client`, request encoding and the response object. */
class ClientConformanceTest {

    private val fixture = """
async function conformance(provider) {
  const client = new Client();
  const text = await client.get("https://h.test/text", { "User-Agent": "UA", "X-Num": 5 });
  const head = await client.head("https://h.test/text");
  const error = await client.get("https://h.test/error");
  const latin1 = await client.get("https://h.test/latin1");
  const noCharset = await client.get("https://h.test/nocharset");
  const raw = await client.get("https://h.test/raw");
  await client.post("https://h.test/post-text", {}, "a=b c");
  await client.post("https://h.test/post-form", {}, { q: "one piece", page: 2, "a&b": "é", "x*": "~y" });
  await client.post("https://h.test/post-json", { "Content-Type": "application/json" }, { q: "x", n: [1, 2] });
  await client.post("https://h.test/post-json-string", { "content-type": "application/json; charset=utf-8" }, '{"q":"x"}');
  await client.put("https://h.test/put", { "Content-Type": "text/xml" }, "<a/>");
  await client.patch("https://h.test/patch", undefined, [0, 1, 255]);
  await client.delete("https://h.test/delete");
  await client.post("https://h.test/post-empty");
  await new Client({ timeout: 2.5, followRedirects: false, verifyCertificates: false }).get("https://h.test/options");
  return {
    text: text,
    head: [head.statusCode, head.body],
    error: [error.statusCode, error.body],
    latin1: latin1.body,
    noCharset: noCharset.body,
    raw: [raw.body, raw.statusCode, typeof raw.headers],
  };
}
"""

    private val http = FakeHostHttp { request ->
        when (request.url.substringAfter("https://h.test")) {
            "/text" -> HostHttpResponse(
                statusCode = 200,
                headers = listOf(
                    "Content-Type" to "text/html; charset=utf-8",
                    "Set-Cookie" to "a=1",
                    "set-cookie" to "b=2",
                    "X-Custom" to "v",
                ),
                body = if (request.method == "HEAD") ByteArray(0) else "héllo ✓".encodeToByteArray(),
            )
            "/error" -> HostHttpResponse(503, emptyList(), "down".encodeToByteArray())
            "/latin1" -> HostHttpResponse(200, listOf("Content-Type" to "text/plain; charset=ISO-8859-1"), byteArrayOf(0xE9.toByte()))
            "/nocharset" -> HostHttpResponse(200, emptyList(), "ü".encodeToByteArray())
            "/raw" -> HostHttpResponse(200, emptyList(), "line 1\nline \"2\"\r\n{\"json\": \\u0041}\n".encodeToByteArray())
            else -> HostHttpResponse(204, emptyList(), ByteArray(0))
        }
    }

    @Test
    fun responsesHaveMangayomisShape() = runTest {
        val results = TestHost(http = http).runConformance(fixture)
        assertJson(
            """{
              "body": "héllo ✓", "statusCode": 200,
              "headers": { "content-type": "text/html; charset=utf-8", "set-cookie": "a=1, b=2", "x-custom": "v" },
              "isRedirect": false, "persistentConnection": true, "reasonPhrase": null,
              "request": {
                "contentLength": null, "finalized": true, "followRedirects": true,
                "headers": { "User-Agent": "UA", "X-Num": "5" }, "maxRedirects": 5, "method": "GET",
                "persistentConnection": true, "url": "https://h.test/text"
              }
            }""",
            results["text"],
        )
        assertJson("""[200, ""]""", results["head"])
        // HTTP error statuses resolve; only network failures reject.
        assertJson("""[503, "down"]""", results["error"])
        assertJson("\"é\"", results["latin1"])
        assertJson("\"ü\"", results["noCharset"])
        // Bodies travel raw next to the response object; newlines, quotes and backslashes are kept as sent.
        assertJson("""["line 1\nline \"2\"\r\n{\"json\": \\u0041}\n", 200, "object"]""", results["raw"])
    }

    @Test
    fun requestsAreEncodedLikeMangayomi() = runTest {
        TestHost(http = http).runConformance(fixture)
        val requests = http.requests.groupBy { it.url.substringAfter("https://h.test") }.mapValues { it.value.first() }

        fun body(path: String) = requests.getValue(path).body?.decodeToString()
        fun headers(path: String) = requests.getValue(path).headers

        assertEquals("GET", requests.getValue("/text").method)
        assertEquals(listOf("User-Agent" to "UA", "X-Num" to "5"), headers("/text"))
        assertNull(requests.getValue("/text").body)
        assertEquals("HEAD", http.requests[1].method)

        assertEquals("a=b c", body("/post-text"))
        assertEquals(listOf("Content-Type" to "text/plain; charset=utf-8"), headers("/post-text"))

        // Dart's Uri.encodeQueryComponent: `*` encoded, `~` kept.
        assertEquals("q=one+piece&page=2&a%26b=%C3%A9&x%2A=~y", body("/post-form"))
        assertEquals(listOf("Content-Type" to "application/x-www-form-urlencoded; charset=utf-8"), headers("/post-form"))

        assertEquals("""{"q":"x","n":[1,2]}""", body("/post-json"))
        // Mangayomi JSON-encodes a string body again when the content type is JSON.
        assertEquals("\"{\\\"q\\\":\\\"x\\\"}\"", body("/post-json-string"))

        assertEquals("PUT", requests.getValue("/put").method)
        assertEquals("<a/>", body("/put"))
        assertEquals(listOf("Content-Type" to "text/xml"), headers("/put"))

        assertEquals("PATCH", requests.getValue("/patch").method)
        assertEquals(listOf<Byte>(0, 1, -1), requests.getValue("/patch").body?.toList())
        assertEquals(emptyList(), headers("/patch"))

        assertEquals("DELETE", requests.getValue("/delete").method)
        assertNull(requests.getValue("/delete").body)
        assertEquals("POST", requests.getValue("/post-empty").method)
        assertNull(requests.getValue("/post-empty").body)

        val options = requests.getValue("/options")
        assertEquals(2500L, options.timeoutMillis)
        assertEquals(false, options.followRedirects)
        assertEquals(null, requests.getValue("/text").timeoutMillis)
        assertEquals(true, requests.getValue("/text").followRedirects)
    }
}
