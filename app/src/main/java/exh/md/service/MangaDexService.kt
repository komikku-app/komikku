package exh.md.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import exh.md.dto.AggregateDto
import exh.md.dto.AtHomeDto
import exh.md.dto.AtHomeImageReportDto
import exh.md.dto.ChapterDto
import exh.md.dto.ChapterListDto
import exh.md.dto.CoverListDto
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
        return with(MdUtil.jsonParser) {
            client.newCall(
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
                        .build(),
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun viewManga(
        id: String,
    ): MangaDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.manga.toHttpUrl()
                        .newBuilder()
                        .apply {
                            addPathSegment(id)
                            addQueryParameter("includes[]", MdConstants.Types.coverArt)
                            addQueryParameter("includes[]", MdConstants.Types.author)
                            addQueryParameter("includes[]", MdConstants.Types.artist)
                        }
                        .build(),
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun mangasRating(
        vararg ids: String,
    ): StatisticsDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.statistics.toHttpUrl()
                        .newBuilder()
                        .apply {
                            ids.forEach { id ->
                                addQueryParameter("manga[]", id)
                            }
                        }
                        .build(),
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun aggregateChapters(
        id: String,
        translatedLanguage: String,
    ): AggregateDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.manga.toHttpUrl()
                        .newBuilder()
                        .apply {
                            addPathSegment(id)
                            addPathSegment("aggregate")
                            addQueryParameter("translatedLanguage[]", translatedLanguage)
                        }
                        .build(),
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
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

        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    url,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun viewChapter(id: String): ChapterDto {
        return with(MdUtil.jsonParser) {
            client.newCall(GET("${MdApi.chapter}/$id", cache = CacheControl.FORCE_NETWORK))
                .awaitSuccess()
                .parseAs()
        }
    }

    suspend fun randomManga(): MangaDto {
        return with(MdUtil.jsonParser) {
            client.newCall(GET("${MdApi.manga}/random", cache = CacheControl.FORCE_NETWORK))
                .awaitSuccess()
                .parseAs()
        }
    }

    suspend fun atHomeImageReport(atHomeImageReportDto: AtHomeImageReportDto): ResultDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                POST(
                    MdConstants.atHomeReportUrl,
                    body = MdUtil.encodeToBody(atHomeImageReportDto),
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun getAtHomeServer(
        atHomeRequestUrl: String,
        headers: Headers,
    ): AtHomeDto {
        return with(MdUtil.jsonParser) {
            client.newCall(GET(atHomeRequestUrl, headers, CacheControl.FORCE_NETWORK))
                .awaitSuccess()
                .parseAs()
        }
    }

    suspend fun relatedManga(id: String): RelationListDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.manga.toHttpUrl().newBuilder()
                        .apply {
                            addPathSegment(id)
                            addPathSegment("relation")
                        }
                        .build(),
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun fetchFirstVolumeCover(mangaDto: MangaDto): String? {
        val mangaData = mangaDto.data
        val result: CoverListDto = with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.cover.toHttpUrl().newBuilder()
                        .apply {
                            addQueryParameter("order[volume]", "asc")
                            addQueryParameter("manga[]", mangaData.id)
                            addQueryParameter("locales[]", mangaData.attributes.originalLanguage)
                            addQueryParameter("limit", "1")
                        }
                        .build(),
                ),
            ).awaitSuccess().parseAs()
        }
        return result.data.firstOrNull()?.attributes?.fileName
    }
}
