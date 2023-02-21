package exh.md.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import exh.md.dto.MangaListDto
import exh.md.dto.RatingDto
import exh.md.dto.RatingResponseDto
import exh.md.dto.ReadChapterDto
import exh.md.dto.ReadingStatusDto
import exh.md.dto.ReadingStatusMapDto
import exh.md.dto.ResultDto
import exh.md.utils.MdApi
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class MangaDexAuthService(
    private val client: OkHttpClient,
    private val headers: Headers,
) {

    suspend fun userFollowList(offset: Int): MangaListDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    "${MdApi.userFollows}?limit=100&offset=$offset&includes[]=${MdConstants.Types.coverArt}",
                    headers,
                    CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun readingStatusForManga(mangaId: String): ReadingStatusDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    "${MdApi.manga}/$mangaId/status",
                    headers,
                    CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun readChaptersForManga(mangaId: String): ReadChapterDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    "${MdApi.manga}/$mangaId/read",
                    headers,
                    CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun updateReadingStatusForManga(
        mangaId: String,
        readingStatusDto: ReadingStatusDto,
    ): ResultDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                POST(
                    "${MdApi.manga}/$mangaId/status",
                    headers,
                    body = MdUtil.encodeToBody(readingStatusDto),
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun readingStatusAllManga(): ReadingStatusMapDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.readingStatusForAllManga,
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun readingStatusByType(status: String): ReadingStatusMapDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    "${MdApi.readingStatusForAllManga}?status=$status",
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun markChapterRead(chapterId: String): ResultDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                POST(
                    "${MdApi.chapter}/$chapterId/read",
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun markChapterUnRead(chapterId: String): ResultDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                Request.Builder()
                    .url("${MdApi.chapter}/$chapterId/read")
                    .delete()
                    .headers(headers)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build(),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun followManga(mangaId: String): ResultDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                POST(
                    "${MdApi.manga}/$mangaId/follow",
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun unfollowManga(mangaId: String): ResultDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                Request.Builder()
                    .url("${MdApi.manga}/$mangaId/follow")
                    .delete()
                    .headers(headers)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build(),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun updateMangaRating(mangaId: String, rating: Int): ResultDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                POST(
                    "${MdApi.rating}/$mangaId",
                    headers,
                    body = MdUtil.encodeToBody(RatingDto(rating)),
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun deleteMangaRating(mangaId: String): ResultDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                Request.Builder()
                    .delete()
                    .url("${MdApi.rating}/$mangaId")
                    .headers(headers)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build(),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun mangasRating(vararg mangaIds: String): RatingResponseDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.rating.toHttpUrl()
                        .newBuilder()
                        .apply {
                            mangaIds.forEach {
                                addQueryParameter("manga[]", it)
                            }
                        }
                        .build(),
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }
}
