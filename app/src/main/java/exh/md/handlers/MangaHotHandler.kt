package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.Page
import exh.md.dto.MangaHotPageList
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class MangaHotHandler(val client: OkHttpClient) {
    private val json by injectLazy<Json>()
    val baseUrl = "https://mangahot.jp"
    private val apiUrl = "https://api.mangahot.jp"

    suspend fun fetchPageList(externalUrl: String): List<Page> {
        val request =
            GET(
                externalUrl.substringBefore("?")
                    .replace(baseUrl, apiUrl)
                    .replace("viewer", "v1/works/storyDetail"),
            )
        return pageListParse(client.newCall(request).awaitSuccess())
    }

    fun pageListParse(response: Response): List<Page> {
        return with(json) { response.parseAs<MangaHotPageList>() }
            .content
            .contentUrls
            .mapIndexed { index, url -> Page(index, url, url) }
    }
}
