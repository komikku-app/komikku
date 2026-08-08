package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.Page
import exh.md.dto.AzukiPageListDto
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class AzukiHandler(currentClient: OkHttpClient, userAgent: String) {
    val baseUrl = "https://www.omoi.com"
    private val apiUrl = "https://production.api.azuki.co"
    val headers = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("azuki-organization-key", "199e5a19-a236-49f5-81f4-43d4a541748a")
        .build()

    val client: OkHttpClient = currentClient

    suspend fun fetchPageList(externalUrl: String): List<Page> {
        val chapterId = externalUrl.substringAfterLast("/").substringBefore("?")
        val request = pageListRequest(chapterId)
        return pageListParse(client.newCall(request).awaitSuccess())
    }

    private fun pageListRequest(chapterId: String): Request {
        val token = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "idToken" }?.value
        return GET("$apiUrl/chapter/$chapterId/pages/v1", token?.let {
            headers.newBuilder().add("x-user-token", token).build()
        } ?: headers)
    }

    fun pageListParse(response: Response): List<Page> {
        return Json.decodeFromString<AzukiPageListDto>(response.body.string()).data.pages.mapIndexed { i, page ->
            val highRes = page.image.webp.maxByOrNull { it.width } ?: throw Exception("No image urls found for page $i")
            // This will give the highest possible resolution even if x2400 image doesn't exist.
            val highResUrl = highRes.url.replace("""/\d+_""".toRegex(), "/2400_")
            Page(i, imageUrl = "$highResUrl?drm=1")
        }
    }
}
