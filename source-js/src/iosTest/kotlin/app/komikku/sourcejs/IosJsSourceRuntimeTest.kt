package app.komikku.sourcejs

import app.komikku.sourcejs.bridge.DarwinHostHttp
import app.komikku.sourcejs.bridge.JsPreferenceValue
import app.komikku.sourcejs.bridge.NSUserDefaultsHostPreferences
import app.komikku.sourcejs.mihon.JsSourceIds
import app.komikku.sourcejs.model.JsManga
import app.komikku.sourcejs.model.JsMangaPage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Mangayomi JavaScript source end to end through [IosJsSourceRuntime]: `getPopular` fetches HTML over
 * [DarwinHostHttp] (mock engine), reads its `SharedPreferences` (NSUserDefaults) and parses the page with the DOM
 * bridge (Ksoup) into a [JsMangaPage].
 */
class IosJsSourceRuntimeTest {

    private val suitePrefix = "app.komikku.sourcejs.test.${Random.nextLong().toULong()}."
    private val requests = mutableListOf<String>()

    private val engine = MockEngine { request ->
        requests += request.url.toString() + " " + request.headers[HttpHeaders.UserAgent]
        respond(
            """
            <html><body>
            <div class="manga"><a href="/manga/1"><img src="https://img.test/1.jpg"></a><h3> One Piece </h3></div>
            <div class="manga"><a href="/manga/2"><img src="https://img.test/2.jpg"></a><h3>Naruto</h3></div>
            <a class="next" href="?page=2">Next</a>
            </body></html>
            """.trimIndent(),
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
        )
    }
    private val http = DarwinHostHttp(engine)
    private val runtime = IosJsSourceRuntime(http = http, suiteName = { suitePrefix + it })

    private val descriptor = JsSourceDescriptor(id = 424242, name = "Fixture", lang = "en", baseUrl = "https://fixture.test")

    private val code = """
const mangayomiSources = [{ "name": "Fixture", "lang": "en", "id": 424242, "baseUrl": "https://fixture.test" }];
class DefaultExtension extends MProvider {
  getHeaders(url) {
    return { "User-Agent": new SharedPreferences().get("user_agent") };
  }
  async getPopular(page) {
    const prefs = new SharedPreferences();
    const res = await new Client().get(this.source.baseUrl + "/popular?page=" + page, this.getHeaders(this.source.baseUrl));
    const doc = new Document(res.body);
    const covers = prefs.get("covers");
    prefs.setString("last_page", String(page));
    const list = doc.select("div.manga").map((e) => ({
      name: e.selectFirst("h3").text.trim(),
      link: e.selectFirst("a").getHref,
      imageUrl: covers ? e.selectFirst("img").getSrc : "",
    }));
    return { list: list, hasNextPage: doc.selectFirst("a.next") != null && res.statusCode == 200 };
  }
  getSourcePreferences() {
    return [
      { key: "user_agent", editTextPreference: { title: "UA", summary: "", value: "FixtureUA/1", dialogTitle: "", dialogMessage: "", text: "" } },
      { key: "covers", switchPreferenceCompat: { title: "Covers", summary: "", value: true } },
    ];
  }
}
"""

    @AfterTest
    fun cleanUp() {
        runtime.clearPreferences(descriptor)
        runtime.close()
        http.close()
        engine.close()
    }

    @Test
    fun getPopularRunsEndToEnd() = runBlocking {
        val page = runtime.extension(descriptor, code).getPopular(1)
        assertEquals(
            JsMangaPage(
                list = listOf(
                    JsManga(name = "One Piece", link = "/manga/1", imageUrl = "https://img.test/1.jpg"),
                    JsManga(name = "Naruto", link = "/manga/2", imageUrl = "https://img.test/2.jpg"),
                ),
                hasNextPage = true,
            ),
            page,
        )
        assertEquals(listOf("https://fixture.test/popular?page=1 FixtureUA/1"), requests)

        // The declared defaults were stored in the source's own suite, with native types.
        val defaults = runtime.defaultsOf(descriptor)
        assertTrue(defaults.objectForKey("covers") is NSNumber)
        assertEquals("FixtureUA/1", defaults.stringForKey("user_agent"))
        assertEquals("1", runtime.preferences(descriptor).getString("last_page"))

        // The user's choice is what the next call sees.
        runtime.preferences(descriptor).setValue("covers", JsPreferenceValue.Bool(false))
        val withoutCovers = runtime.extension(descriptor, code).getPopular(2)
        assertEquals(listOf(null, null), withoutCovers.list.map { it.imageUrl })
        // One suite per source, named after its Komikku id.
        val suite = NSUserDefaults(suiteName = suitePrefix + JsSourceIds.komikkuId(descriptor.id))
        assertEquals("2", suite.stringForKey(NSUserDefaultsHostPreferences.STRING_KEY_PREFIX + "last_page"))
    }
}
