package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.LoopbackHttpServer
import app.komikku.sourcejs.bridge.LoopbackHttpServer.Response
import app.komikku.sourcejs.model.JsManga
import app.komikku.sourcejs.model.JsMangaPage
import kotlin.concurrent.AtomicReference
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Mangayomi JavaScript source calling `evaluateJavascriptViaWebview` through [IosJsSourceRuntime]'s default web view
 * (a `WKWebView`): the list is built by the page's own script, so only a web view sees it.
 */
class IosJsSourceRuntimeWebViewTest {

    private val tokens = AtomicReference(emptyList<String?>())
    private val server = LoopbackHttpServer { request ->
        when (request.target) {
            "/list?page=1" -> {
                while (true) {
                    val current = tokens.value
                    if (tokens.compareAndSet(current, current + request.headers["x-token"])) break
                }
                html(
                    """
                    <html><body><div id="list"></div><script>
                    document.getElementById('list').innerHTML =
                      '<a class="manga" href="/manga/1"> One Piece </a><a class="manga" href="/manga/2">Naruto</a>';
                    </script></body></html>
                    """,
                )
            }
            "/silent" -> html("<html><body>no report</body></html>")
            else -> Response(404)
        }
    }

    private val suitePrefix = "app.komikku.sourcejs.test.${Random.nextLong().toULong()}."
    private val runtime = IosJsSourceRuntime(suiteName = { suitePrefix + it })
    private val descriptor = JsSourceDescriptor(id = 434343, name = "WebFixture", lang = "en", baseUrl = server.baseUrl)

    private val code = """
const mangayomiSources = [{ "name": "WebFixture", "lang": "en", "id": 434343, "baseUrl": "${server.baseUrl}" }];
class DefaultExtension extends MProvider {
  async getPopular(page) {
    const json = await evaluateJavascriptViaWebview(this.source.baseUrl + "/list?page=" + page, { "X-Token": "e2e" }, [
      "window.flutter_inappwebview.callHandler('setResponse', JSON.stringify(Array.from(document.querySelectorAll('a.manga'))" +
        ".map(function (e) { return { name: e.textContent.trim(), link: e.getAttribute('href') }; })));"
    ], 20);
    const list = JSON.parse(json).map((m) => ({ name: m.name, link: m.link }));
    return { list: list, hasNextPage: false };
  }
  async getLatestUpdates(page) {
    const silent = await evaluateJavascriptViaWebview(this.source.baseUrl + "/silent", {}, [], 1);
    let refused = "";
    try {
      await evaluateJavascriptViaWebview("file:///etc/hosts", {}, [], 1);
    } catch (e) {
      refused = String(e && e.message);
    }
    return { list: [{ name: "timeout:" + JSON.stringify(silent), link: refused }], hasNextPage: false };
  }
}
"""

    @AfterTest
    fun cleanUp() {
        runtime.clearPreferences(descriptor)
        runtime.close()
        server.close()
    }

    @Test
    fun aSourceReadsAPageThroughTheWebView() = webKitTest {
        val page = runtime.extension(descriptor, code).getPopular(1)
        assertEquals(
            JsMangaPage(
                list = listOf(
                    JsManga(name = "One Piece", link = "/manga/1"),
                    JsManga(name = "Naruto", link = "/manga/2"),
                ),
                hasNextPage = false,
            ),
            page,
        )
        assertEquals(listOf<String?>("e2e"), tokens.value)
    }

    @Test
    fun aTimeoutIsAnEmptyValueAndANonHttpUrlACatchableError() = mainRunLoopTest {
        val result = runtime.extension(descriptor, code).getLatestUpdates(1).list.single()
        assertEquals("timeout:\"\"", result.name)
        assertTrue("http or https URL" in result.link, result.link)
    }

    private fun html(text: String) =
        Response(200, listOf("Content-Type" to "text/html; charset=utf-8"), text.trimIndent().encodeToByteArray())
}
