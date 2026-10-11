package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response

class NamicomiHandler(val client: OkHttpClient) {
    private val apiUrl = "https://api.namicomi.com"

    suspend fun fetchPageList(externalUrl: String, dataSaver: Boolean): List<Page> {
        val chapterId = externalUrl.substringBefore("?").substringAfterLast("/")
        val request =
            GET(
                "$apiUrl/images/chapter/$chapterId?newQualities=true",
            )
        return pageListParse(client.newCall(request).awaitSuccess(), chapterId, dataSaver)
    }

    private fun pageListParse(response: Response, chapterId: String, dataSaver: Boolean): List<Page> {
        val data = Json.parseToJsonElement(response.body.string()).jsonObject["data"]!!
        val baseUrl = data.jsonObject["baseUrl"]!!.jsonPrimitive.content
        val hash = data.jsonObject["hash"]!!.jsonPrimitive.content

        /* Available quality levels: source, high, medium, low */
        val quality = if (dataSaver) "low" else "high"

        return data
            .jsonObject[quality]!!
            .jsonArray.mapIndexed { index, element ->
                val fileName = element.jsonObject["filename"]!!.jsonPrimitive.content
                val url = "$baseUrl/chapter/$chapterId/$hash/$quality/$fileName"
                Page(index, url, url)
            }
    }
}
