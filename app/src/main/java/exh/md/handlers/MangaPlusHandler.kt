package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import exh.md.dto.MangaPlusPage
import exh.md.dto.MangaPlusResponse
import exh.md.dto.toMangaPlusLanguage
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.UUID

class MangaPlusHandler(currentClient: OkHttpClient) {
    val headers = Headers.Builder()
        .add("Origin", WEB_URL)
        .add("Referer", WEB_URL)
        .add("User-Agent", USER_AGENT)
        .add("SESSION-TOKEN", UUID.randomUUID().toString()).build()

    val client: OkHttpClient = currentClient.newBuilder()
        .addInterceptor(::imageIntercept)
        .rateLimitHost(API_URL.toHttpUrl(), 1)
        .rateLimitHost(WEB_URL.toHttpUrl(), 2)
        .build()

    suspend fun fetchPageList(chapterId: String, dataSaver: Boolean, lang: String): List<Page> {
        val response = client.newCall(pageListRequest(chapterId.substringAfterLast("/"), dataSaver, lang)).awaitSuccess()
        return pageListParse(response, lang)
    }

    private fun pageListRequest(chapterId: String, dataSaver: Boolean, lang: String): Request {
        val newHeaders = headers.newBuilder()
            .set("Referer", "$WEB_URL/viewer/$chapterId")
            .build()

        val url = "$API_URL/manga_viewer_v3".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", chapterId)
            .addQueryParameter("split", "yes")
            .addQueryParameter(
                "img_quality",
                if (dataSaver) {
                    "low"
                } else {
                    "super_high"
                },
            )
            .addQueryParameter("clang", lang.toMangaPlusLanguage().clang)
            .toString()

        return GET(url, newHeaders)
    }

    private fun pageListParse(response: Response, lang: String): List<Page> {
        val result = ProtoBuf.decodeFromByteArray<MangaPlusResponse>(response.body.bytes())

        if (result.success == null) {
            throw Exception(result.error?.langPopup(lang.toMangaPlusLanguage())?.body ?: "error getting images")
        }

        val viewer = result.success.mangaViewer ?: throw Exception("mangaViewer missing from page list response")

        return viewer.pages
            .mapNotNull(MangaPlusPage::mangaPage)
            .mapIndexed { i, page ->
                val encryptionKey = if (page.encryptionKey == null) "" else "#${page.encryptionKey}"
                Page(i, viewer.viewToken.orEmpty(), page.imageUrl + encryptionKey)
            }
    }

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val encryptionKey = request.url.fragment

        if (encryptionKey.isNullOrEmpty()) {
            return response
        }

        val contentType = response.headers["Content-Type"] ?: "image/jpeg"
        val image = response.body.bytes().decodeXorCipher(encryptionKey)
        val body = image.toResponseBody(contentType.toMediaTypeOrNull())

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun ByteArray.decodeXorCipher(key: String): ByteArray {
        val keyStream = key.chunked(2)
            .map { it.toInt(16) }

        return mapIndexed { i, byte -> byte.toInt() xor keyStream[i % keyStream.size] }
            .map(Int::toByte)
            .toByteArray()
    }

    companion object {
        private const val WEB_URL = "https://mangaplus.shueisha.co.jp"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
        private const val API_URL = "https://jumpg-webapi.tokyo-cdn.com/api"
    }
}
