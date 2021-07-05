package exh.md.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import exh.md.dto.AtHomeDto
import exh.md.dto.AtHomeImageReportDto
import exh.md.dto.ChapterDto
import exh.md.dto.ChapterListDto
import exh.md.dto.MangaDto
import exh.md.dto.MangaListDto
import exh.md.dto.ResultDto
import exh.md.utils.MdApi
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class MangaDexService(
    private val client: OkHttpClient
) {

    suspend fun viewMangas(
        ids: List<String>
    ): MangaListDto {
        return client.newCall(
            GET(
                MdApi.manga.toHttpUrl().newBuilder().apply {
                    addQueryParameter("includes[]", MdConstants.Types.coverArt)
                    addQueryParameter("limit", ids.size.toString())
                    ids.forEach {
                        addQueryParameter("ids[]", it)
                    }
                }.build().toString(),
                cache = CacheControl.FORCE_NETWORK
            )
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun viewManga(
        id: String
    ): MangaDto {
        return client.newCall(
            GET(
                "${MdApi.manga}/$id?includes[]=${MdConstants.Types.coverArt}&includes[]=${MdConstants.Types.author}&includes[]=${MdConstants.Types.artist}",
                cache = CacheControl.FORCE_NETWORK
            )
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun viewChapters(
        id: String,
        translatedLanguage: String,
        offset: Int,
    ): ChapterListDto {
        val url = "${MdApi.manga}/$id/feed?limit=500&includes[]=${MdConstants.Types.scanlator}&order[volume]=desc&order[chapter]=desc".toHttpUrl()
            .newBuilder()
            .apply {
                addQueryParameter("translatedLanguage[]", translatedLanguage)
                addQueryParameter("offset", offset.toString())
            }.build()
            .toString()

        return client.newCall(
            GET(
                url,
                cache = CacheControl.FORCE_NETWORK
            )
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun viewChapter(id: String): ChapterDto {
        return client.newCall(GET("${MdApi.chapter}/$id", cache = CacheControl.FORCE_NETWORK))
            .await()
            .parseAs(MdUtil.jsonParser)
    }

    suspend fun randomManga(): MangaDto {
        return client.newCall(GET("${MdApi.manga}/random", cache = CacheControl.FORCE_NETWORK))
            .await()
            .parseAs(MdUtil.jsonParser)
    }

    suspend fun atHomeImageReport(atHomeImageReportDto: AtHomeImageReportDto): ResultDto {
        return client.newCall(
            POST(
                MdConstants.atHomeReportUrl,
                body = MdUtil.encodeToBody(atHomeImageReportDto),
                cache = CacheControl.FORCE_NETWORK
            )
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun getAtHomeServer(
        headers: Headers,
        chapterId: String,
        forcePort443: Boolean,
    ): Pair<String, AtHomeDto> {
        val url = "${MdApi.atHomeServer}/$chapterId?forcePort443=$forcePort443"
        return client.newCall(GET(url, headers, CacheControl.FORCE_NETWORK))
            .await()
            .let { it.request.url.toUrl().toString() to it.parseAs(MdUtil.jsonParser) }
    }
}
