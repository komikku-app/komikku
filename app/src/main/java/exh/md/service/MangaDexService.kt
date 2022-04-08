package exh.md.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import exh.md.dto.AggregateDto
import exh.md.dto.AtHomeDto
import exh.md.dto.AtHomeImageReportDto
import exh.md.dto.ChapterDto
import exh.md.dto.ChapterListDto
import exh.md.dto.MangaDto
import exh.md.dto.MangaListDto
import exh.md.dto.RelationListDto
import exh.md.dto.ResultDto
import exh.md.dto.StatisticsDto
import exh.md.utils.MdApi
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import exh.util.dropEmpty
import exh.util.trimAll
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class MangaDexService(
    private val client: OkHttpClient,
) {

    suspend fun viewMangas(
        ids: List<String>,
    ): MangaListDto {
        return client.newCall(
            GET(
                MdApi.manga.toHttpUrl()
                    .newBuilder()
                    .apply {
                        addQueryParameter("includes[]", MdConstants.Types.coverArt)
                        addQueryParameter("limit", ids.size.toString())
                        ids.forEach {
                            addQueryParameter("ids[]", it)
                        }
                    }
                    .build()
                    .toString(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun viewManga(
        id: String,
    ): MangaDto {
        return client.newCall(
            GET(
                MdApi.manga.toHttpUrl()
                    .newBuilder()
                    .apply {
                        addPathSegment(id)
                        addQueryParameter("includes[]", MdConstants.Types.coverArt)
                        addQueryParameter("includes[]", MdConstants.Types.author)
                        addQueryParameter("includes[]", MdConstants.Types.artist)
                    }
                    .build()
                    .toString(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun mangasRating(
        vararg ids: String,
    ): StatisticsDto {
        return client.newCall(
            GET(
                MdApi.statistics.toHttpUrl()
                    .newBuilder()
                    .apply {
                        ids.forEach { id ->
                            addQueryParameter("manga[]", id)
                        }
                    }
                    .build()
                    .toString(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun aggregateChapters(
        id: String,
        translatedLanguage: String,
    ): AggregateDto {
        return client.newCall(
            GET(
                MdApi.manga.toHttpUrl()
                    .newBuilder()
                    .apply {
                        addPathSegment(id)
                        addPathSegment("aggregate")
                        addQueryParameter("translatedLanguage[]", translatedLanguage)
                    }
                    .build()
                    .toString(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    private fun String.splitString() = replace("\n", "").split(',').trimAll().dropEmpty()

    suspend fun viewChapters(
        id: String,
        translatedLanguage: String,
        offset: Int,
        blockedGroups: String,
        blockedUploaders: String,
    ): ChapterListDto {
        val url = MdApi.manga.toHttpUrl()
            .newBuilder()
            .apply {
                addPathSegment(id)
                addPathSegment("feed")
                addQueryParameter("limit", "500")
                addQueryParameter("includes[]", MdConstants.Types.scanlator)
                addQueryParameter("order[volume]", "desc")
                addQueryParameter("order[chapter]", "desc")
                addQueryParameter("contentRating[]", "safe")
                addQueryParameter("contentRating[]", "suggestive")
                addQueryParameter("contentRating[]", "erotica")
                addQueryParameter("contentRating[]", "pornographic")
                addQueryParameter("translatedLanguage[]", translatedLanguage)
                addQueryParameter("offset", offset.toString())
                blockedGroups.splitString().forEach {
                    addQueryParameter("excludedGroups[]", it)
                }
                blockedUploaders.splitString().forEach {
                    addQueryParameter("excludedUploaders[]", it)
                }
            }
            .build()
            .toString()

        return client.newCall(
            GET(
                url,
                cache = CacheControl.FORCE_NETWORK,
            ),
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
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun getAtHomeServer(
        atHomeRequestUrl: String,
        headers: Headers,
    ): AtHomeDto {
        return client.newCall(GET(atHomeRequestUrl, headers, CacheControl.FORCE_NETWORK))
            .await()
            .parseAs(MdUtil.jsonParser)
    }

    suspend fun relatedManga(id: String): RelationListDto {
        return client.newCall(
            GET(
                MdApi.manga.toHttpUrl().newBuilder()
                    .apply {
                        addPathSegment(id)
                        addPathSegment("relation")
                    }
                    .build()
                    .toString(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }
}
