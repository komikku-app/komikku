package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response

class MangaHotHandler(currentClient: OkHttpClient) {
    val baseUrl = "https://mangahot.jp"
    private val apiUrl = "https://api.mangahot.jp"
    val headers = Headers.Builder()
        .add("User-Agent", HttpSource.DEFAULT_USER_AGENT)
        .build()

    val client: OkHttpClient = currentClient

    suspend fun fetchPageList(externalUrl: String): List<Page> {
        val request = GET(externalUrl.substringBefore("?").replace(baseUrl, apiUrl).replace("viewer", "v1/works/storyDetail"), headers)
        return pageListParse(client.newCall(request).await())
    }

    fun pageListParse(response: Response): List<Page> {
        return Json.parseToJsonElement(response.body!!.string())
            .jsonObject["content"]!!.jsonObject["contentUrls"]!!
            .jsonArray.mapIndexed { index, element ->
                val url = element.jsonPrimitive.content
                Page(index, url, url)
            }
    }
}
