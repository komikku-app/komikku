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
import okhttp3.Request
import okhttp3.Response

class AzukiHandler(currentClient: OkHttpClient) {
    val baseUrl = "https://www.azuki.co"
    private val apiUrl = "https://production.api.azuki.co"
    val headers = Headers.Builder()
        .add("User-Agent", HttpSource.DEFAULT_USER_AGENT)
        .build()

    val client: OkHttpClient = currentClient

    suspend fun fetchPageList(externalUrl: String): List<Page> {
        val chapterId = externalUrl.substringAfterLast("/").substringBefore("?")
        val request = pageListRequest(chapterId)
        return pageListParse(client.newCall(request).await())
    }

    private fun pageListRequest(chapterId: String): Request {
        return GET("$apiUrl/chapter/$chapterId/pages/v0", headers)
    }

    fun pageListParse(response: Response): List<Page> {
        return Json.parseToJsonElement(response.body!!.string())
            .jsonObject["pages"]!!
            .jsonArray.mapIndexed { index, element ->
                val url = element.jsonObject["image_wm"]!!.jsonObject["webp"]!!.jsonArray[1].jsonObject["url"]!!.jsonPrimitive.content
                Page(index, url, url)
            }
    }
}
