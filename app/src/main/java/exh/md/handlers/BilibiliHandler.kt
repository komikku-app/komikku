package exh.md.handlers

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import exh.log.xLogD
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import tachiyomi.core.common.util.lang.runAsObservable
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.seconds

class BilibiliHandler(currentClient: OkHttpClient) {
    val baseUrl = "https://www.bilibilicomics.com"
    val headers = Headers.Builder()
        .add("Accept", ACCEPT_JSON)
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")
        .build()

    val client: OkHttpClient = currentClient.newBuilder()
        .rateLimit(1, 1.seconds)
        .build()

    val json by injectLazy<Json>()

    suspend fun fetchPageList(externalUrl: String, chapterNumber: String): List<Page> {
        // Sometimes the urls direct it to the manga page instead, so we try to find the correct chapter
        // Though these seem to be older chapters, so maybe remove this later
        val chapterUrl = if (externalUrl.contains("mc\\d*/\\d*".toRegex())) {
            getChapterUrl(externalUrl)
        } else {
            val mangaUrl = getMangaUrl(externalUrl)
            val chapters = getChapterList(mangaUrl)
            val chapter = chapters
                .find { it.chapter_number == chapterNumber.toFloatOrNull() }
                ?: throw Exception("Unknown chapter $chapterNumber")
            chapter.url
        }

        return fetchPageList(chapterUrl)
    }

    private fun getMangaUrl(externalUrl: String): String {
        xLogD(externalUrl)
        val comicId = externalUrl
            .substringAfter("/mc")
            .substringBefore('?')
            .toInt()

        return "/detail/mc$comicId"
    }

    private fun getChapterUrl(externalUrl: String): String {
        val comicId = externalUrl.substringAfterLast("/mc")
            .substringBefore('/')
            .toInt()
        val episodeId = externalUrl.substringAfterLast('/')
            .substringBefore('?')
            .toInt()
        return "/mc$comicId/$episodeId"
    }

    private fun mangaDetailsApiRequest(mangaUrl: String): Request {
        val comicId = mangaUrl.substringAfterLast("/mc").toInt()

        val jsonPayload = buildJsonObject { put("comic_id", comicId) }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headers.newBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .set("Referer", baseUrl + mangaUrl)
            .build()

        return POST(
            "$baseUrl/$BASE_API_ENDPOINT/ComicDetail?device=pc&platform=web",
            headers = newHeaders,
            body = requestBody,
        )
    }

    suspend fun getChapterList(mangaUrl: String): List<SChapter> {
        val response = client.newCall(mangaDetailsApiRequest(mangaUrl)).awaitSuccess()
        return response.use { chapterListParse(it) }
    }

    fun chapterListParse(response: Response): List<SChapter> {
        val result = with(json) { response.parseAs<BilibiliResultDto<BilibiliComicDto>>() }

        if (result.code != 0) {
            return emptyList()
        }

        return result.data!!.episodeList
            .filter { episode -> episode.isLocked.not() }
            .map { ep -> chapterFromObject(ep, result.data.id) }
    }

    private fun chapterFromObject(episode: BilibiliEpisodeDto, comicId: Int): SChapter = SChapter(
        url = "/mc$comicId/${episode.id}",
        name = "Ep. " + episode.order.toString().removeSuffix(".0") + " - " + episode.title,
        chapter_number = episode.order,
    )

    private suspend fun fetchPageList(chapterUrl: String): List<Page> {
        val response = client.newCall(pageListRequest(chapterUrl)).awaitSuccess()
        return response.use { pageListParse(it) }
    }

    private fun pageListRequest(chapterUrl: String): Request {
        val chapterId = chapterUrl.substringAfterLast("/").toInt()

        val jsonPayload = buildJsonObject { put("ep_id", chapterId) }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headers
            .newBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .set("Referer", baseUrl + chapterUrl)
            .build()

        return POST(
            "$baseUrl/$BASE_API_ENDPOINT/GetImageIndex?device=pc&platform=web",
            headers = newHeaders,
            body = requestBody,
        )
    }

    private fun pageListParse(response: Response): List<Page> {
        val result = with(json) { response.parseAs<BilibiliResultDto<BilibiliReader>>() }

        if (result.code != 0) {
            return emptyList()
        }

        return result.data!!.images
            .mapIndexed { i, page -> Page(i, page.path, "") }
    }

    suspend fun getImageUrl(page: Page): String {
        val response = client.newCall(imageUrlRequest(page)).awaitSuccess()
        return response.use { imageUrlParse(it) }
    }

    fun fetchImageUrl(page: Page): Observable<String> {
        return runAsObservable { getImageUrl(page) }
    }

    private fun imageUrlRequest(page: Page): Request {
        val jsonPayload = buildJsonObject {
            put("urls", buildJsonArray { add(page.url) }.toString())
        }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headers.newBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .build()

        return POST(
            "$baseUrl/$BASE_API_ENDPOINT/ImageToken?device=pc&platform=web",
            headers = newHeaders,
            body = requestBody,
        )
    }

    private fun imageUrlParse(response: Response): String {
        val result = with(json) {
            response.parseAs<BilibiliResultDto<List<BilibiliPageDto>>>()
        }
        val page = result.data!![0]

        return "${page.url}?token=${page.token}"
    }

    @Serializable
    data class BilibiliPageDto(
        val token: String,
        val url: String,
    )

    @Serializable
    data class BilibiliResultDto<T>(
        val code: Int = 0,
        val data: T? = null,
        @SerialName("msg") val message: String = "",
    )

    @Serializable
    data class BilibiliReader(
        val images: List<BilibiliImageDto> = emptyList(),
    )

    @Serializable
    data class BilibiliImageDto(
        val path: String,
    )

    @Serializable
    data class BilibiliComicDto(
        @SerialName("author_name") val authorName: List<String> = emptyList(),
        @SerialName("classic_lines") val classicLines: String = "",
        @SerialName("comic_id") val comicId: Int = 0,
        @SerialName("ep_list") val episodeList: List<BilibiliEpisodeDto> = emptyList(),
        val id: Int = 0,
        @SerialName("is_finish") val isFinish: Int = 0,
        @SerialName("season_id") val seasonId: Int = 0,
        val styles: List<String> = emptyList(),
        val title: String,
        @SerialName("vertical_cover") val verticalCover: String = "",
    )

    @Serializable
    data class BilibiliEpisodeDto(
        val id: Int,
        @SerialName("is_locked") val isLocked: Boolean,
        @SerialName("ord") val order: Float,
        @SerialName("pub_time") val publicationTime: String,
        val title: String,
    )

    companion object {
        private const val BASE_API_ENDPOINT = "twirp/comic.v1.Comic"
        private const val ACCEPT_JSON = "application/json, text/plain, */*"
        private val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()
    }
}
