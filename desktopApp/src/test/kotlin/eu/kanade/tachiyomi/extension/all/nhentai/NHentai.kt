package eu.kanade.tachiyomi.extension.all.nhentai

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * P4 test double of the NHentai extension: the class name the SY delegate is chosen by, an offline client that answers
 * the gallery API with [GALLERY_JSON] (and 404 for anything else), a fixed search result for plain queries.
 */
class NHentai : HttpSource() {
    override val name = "NHentai"
    override val lang = "all"
    override val baseUrl = "https://nhentai.net"
    override val supportsLatest = false

    val requests = mutableListOf<String>()

    override val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val url = chain.request().url
                synchronized(requests) { requests += url.encodedPath }
                val found = url.encodedPath.startsWith("/g/")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (found) 200 else 404)
                    .message(if (found) "OK" else "Not Found")
                    .body((if (found) GALLERY_JSON else "").toResponseBody("application/json".toMediaType()))
                    .build()
            },
        )
        .build()

    override fun chapterListParse(response: Response): List<SChapter> =
        listOf(
            SChapter.create().apply {
                url = response.request.url.encodedPath
                name = "Chapter"
            },
        )

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        MangasPage(
            listOf(
                SManga.create().apply {
                    url = "/g/9/"
                    title = "Plain result for $query"
                },
            ),
            false,
        )

    companion object {
        val GALLERY_JSON = """
            {"id": 1, "media_id": "777", "title": {"english": "Delegated English", "japanese": "Delegated Japanese", "pretty": "Delegated"},
             "cover": {"path": "galleries/777/cover.jpg"}, "upload_date": 1700000000, "num_pages": 2, "num_favorites": 5,
             "tags": [{"id": 1, "type": "tag", "name": "full color"}, {"id": 2, "type": "artist", "name": "someone"}],
             "pages": [{"path": "galleries/777/1.jpg", "thumbnail": "galleries/777/1t.jpg"}]}
        """.trimIndent()
    }
}
