package exh.md.service

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import exh.md.dto.CheckTokenDto
import exh.md.dto.LoginRequestDto
import exh.md.dto.LoginResponseDto
import exh.md.dto.LogoutDto
import exh.md.dto.MangaListDto
import exh.md.dto.RatingDto
import exh.md.dto.RatingResponseDto
import exh.md.dto.ReadChapterDto
import exh.md.dto.ReadingStatusDto
import exh.md.dto.ReadingStatusMapDto
import exh.md.dto.RefreshTokenDto
import exh.md.dto.ResultDto
import exh.md.utils.MdApi
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import okhttp3.Authenticator
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class MangaDexAuthService(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val preferences: PreferencesHelper,
    private val mdList: MdList,
) {
    private val noAuthenticatorClient = client.newBuilder()
        .authenticator(Authenticator.NONE)
        .build()

    fun getHeaders() = MdUtil.getAuthHeaders(
        headers,
        preferences,
        mdList,
    )

    suspend fun login(request: LoginRequestDto): LoginResponseDto {
        return noAuthenticatorClient.newCall(
            POST(
                MdApi.login,
                body = MdUtil.encodeToBody(request),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun logout(): LogoutDto {
        return client.newCall(
            POST(
                MdApi.logout,
                getHeaders(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun checkToken(): CheckTokenDto {
        return noAuthenticatorClient.newCall(
            GET(
                MdApi.checkToken,
                getHeaders(),
                CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun refreshToken(request: RefreshTokenDto): LoginResponseDto {
        return noAuthenticatorClient.newCall(
            POST(
                MdApi.refreshToken,
                getHeaders(),
                body = MdUtil.encodeToBody(request),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun userFollowList(offset: Int): MangaListDto {
        return client.newCall(
            GET(
                "${MdApi.userFollows}?limit=100&offset=$offset&includes[]=${MdConstants.Types.coverArt}",
                getHeaders(),
                CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun readingStatusForManga(mangaId: String): ReadingStatusDto {
        return client.newCall(
            GET(
                "${MdApi.manga}/$mangaId/status",
                getHeaders(),
                CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun readChaptersForManga(mangaId: String): ReadChapterDto {
        return client.newCall(
            GET(
                "${MdApi.manga}/$mangaId/read",
                getHeaders(),
                CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun updateReadingStatusForManga(
        mangaId: String,
        readingStatusDto: ReadingStatusDto,
    ): ResultDto {
        return client.newCall(
            POST(
                "${MdApi.manga}/$mangaId/status",
                getHeaders(),
                body = MdUtil.encodeToBody(readingStatusDto),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun readingStatusAllManga(): ReadingStatusMapDto {
        return client.newCall(
            GET(
                MdApi.readingStatusForAllManga,
                getHeaders(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun readingStatusByType(status: String): ReadingStatusMapDto {
        return client.newCall(
            GET(
                "${MdApi.readingStatusForAllManga}?status=$status",
                getHeaders(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun markChapterRead(chapterId: String): ResultDto {
        return client.newCall(
            POST(
                "${MdApi.chapter}/$chapterId/read",
                getHeaders(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun markChapterUnRead(chapterId: String): ResultDto {
        return client.newCall(
            Request.Builder()
                .url("${MdApi.chapter}/$chapterId/read")
                .delete()
                .headers(getHeaders())
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build(),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun followManga(mangaId: String): ResultDto {
        return client.newCall(
            POST(
                "${MdApi.manga}/$mangaId/follow",
                getHeaders(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun unfollowManga(mangaId: String): ResultDto {
        return client.newCall(
            Request.Builder()
                .url("${MdApi.manga}/$mangaId/follow")
                .delete()
                .headers(getHeaders())
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build(),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun updateMangaRating(mangaId: String, rating: Int): ResultDto {
        return client.newCall(
            POST(
                "${MdApi.rating}/$mangaId",
                getHeaders(),
                body = MdUtil.encodeToBody(RatingDto(rating)),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun deleteMangaRating(mangaId: String): ResultDto {
        return client.newCall(
            Request.Builder()
                .delete()
                .url("${MdApi.rating}/$mangaId")
                .headers(getHeaders())
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build(),
        ).await().parseAs(MdUtil.jsonParser)
    }

    suspend fun mangasRating(vararg mangaIds: String): RatingResponseDto {
        return client.newCall(
            GET(
                MdApi.rating.toHttpUrl()
                    .newBuilder()
                    .apply {
                        mangaIds.forEach {
                            addQueryParameter("manga[]", it)
                        }
                    }
                    .build()
                    .toString(),
                getHeaders(),
                cache = CacheControl.FORCE_NETWORK,
            ),
        ).await().parseAs(MdUtil.jsonParser)
    }
}
