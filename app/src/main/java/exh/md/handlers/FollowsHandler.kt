package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.handlers.serializers.MangaListResponse
import exh.md.handlers.serializers.MangaResponse
import exh.md.handlers.serializers.MangaStatusListResponse
import exh.md.handlers.serializers.MangaStatusResponse
import exh.md.handlers.serializers.UpdateReadingStatus
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.md.utils.mdListCall
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.util.under
import kotlinx.serialization.encodeToString
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.Locale

class FollowsHandler(
    val client: OkHttpClient,
    val headers: Headers,
    val preferences: PreferencesHelper,
    private val lang: String,
    private val useLowQualityCovers: Boolean,
    private val mdList: MdList
) {

    /**
     * fetch follows page
     */
    suspend fun fetchFollows(page: Int): MetadataMangasPage {
        return withIOContext {
            val response = client.newCall(followsListRequest(MdUtil.mangaLimit * page - 1)).await()
            if (response.code == 204) {
                return@withIOContext MetadataMangasPage(emptyList(), false, emptyList())
            }

            val mangaListResponse = response.parseAs<MangaListResponse>(MdUtil.jsonParser)

            if (mangaListResponse.results.isEmpty()) {
                return@withIOContext MetadataMangasPage(emptyList(), false, emptyList())
            }

            val hasMoreResults = mangaListResponse.limit + mangaListResponse.offset under mangaListResponse.total
            val statusListResponse = client.newCall(mangaStatusListRequest()).await()
                .parseAs<MangaStatusListResponse>()
            val results = followsParseMangaPage(mangaListResponse.results, statusListResponse.statuses)

            MetadataMangasPage(results.map { it.first }, hasMoreResults, results.map { it.second })
        }
    }

    /**
     * Parse follows api to manga page
     * used when multiple follows
     */
    private fun followsParseMangaPage(response: List<MangaResponse>, statuses: Map<String, String?>): List<Pair<SManga, MangaDexSearchMetadata>> {
        val comparator = compareBy<Pair<SManga, MangaDexSearchMetadata>> { it.second.followStatus }
            .thenBy { it.first.title }

        return response.map {
            MdUtil.createMangaEntry(
                it,
                lang,
                useLowQualityCovers
            ).toSManga() to MangaDexSearchMetadata().apply {
                followStatus = FollowStatus.fromDex(statuses[it.data.id]).int
            }
        }.sortedWith(comparator)
    }

    /**
     * fetch follow status used when fetching status for 1 manga
     */
    private fun followStatusParse(response: Response, sResponse: Response): Track {
        val mangaResponse = response.parseAs<MangaResponse>(MdUtil.jsonParser)
        val statusResponse = sResponse.parseAs<MangaStatusResponse>()
        val track = Track.create(TrackManager.MDLIST)
        track.status = FollowStatus.fromDex(statusResponse.status).int
        track.tracking_url = MdUtil.baseUrl + "/manga/" + mangaResponse.data.id
        track.title = mangaResponse.data.attributes.title[lang] ?: mangaResponse.data.attributes.title["en"]!!

        /* if (follow.chapter.isNotBlank()) {
                track.last_chapter_read = follow.chapter.toFloat().floor()
        }*/
        return track
    }

    /**
     * build Request for follows page
     */
    private fun followsListRequest(offset: Int): Request {
        val tempUrl = MdUtil.userFollows.toHttpUrl().newBuilder()

        tempUrl.apply {
            addQueryParameter("limit", MdUtil.mangaLimit.toString())
            addQueryParameter("offset", offset.toString())
        }
        return GET(tempUrl.build().toString(), MdUtil.getAuthHeaders(headers, preferences, mdList), CacheControl.FORCE_NETWORK)
    }

    /**
     * Change the status of a manga
     */
    suspend fun updateFollowStatus(mangaId: String, followStatus: FollowStatus): Boolean {
        return withIOContext {
            val status = when (followStatus == FollowStatus.UNFOLLOWED) {
                true -> null
                false -> followStatus.name.toLowerCase(Locale.US)
            }

            val jsonString = MdUtil.jsonParser.encodeToString(UpdateReadingStatus(status))

            val postResult = client.newCall(
                POST(
                    MdUtil.updateReadingStatusUrl(mangaId),
                    MdUtil.getAuthHeaders(headers, preferences, mdList),
                    jsonString.toRequestBody("application/json".toMediaType())
                )
            ).await()
            postResult.isSuccessful
        }
    }

    suspend fun updateReadingProgress(track: Track): Boolean {
        return true
        /*return withIOContext {
            val mangaID = getMangaId(track.tracking_url)
            val formBody = FormBody.Builder()
                .add("volume", "0")
                .add("chapter", track.last_chapter_read.toString())
            XLog.d("chapter to update %s", track.last_chapter_read.toString())
            val result = runCatching {
                client.newCall(
                    POST(
                        "$baseUrl/ajax/actions.ajax.php?function=edit_progress&id=$mangaID",
                        headers,
                        formBody.build()
                    )
                ).execute()
            }
            result.exceptionOrNull()?.let {
                if (it is EOFException) {
                    return@withIOContext true
                } else {
                    XLog.e("error updating reading progress", it)
                    return@withIOContext false
                }
            }
            result.isSuccess
        }*/
    }

    suspend fun updateRating(track: Track): Boolean {
        return true
        /*return withIOContext {
            val mangaID = getMangaId(track.tracking_url)
            val result = runCatching {
                client.newCall(
                    GET(
                        "$baseUrl/ajax/actions.ajax.php?function=manga_rating&id=$mangaID&rating=${track.score.toInt()}",
                        headers
                    )
                )
                    .execute()
            }

            result.exceptionOrNull()?.let {
                if (it is EOFException) {
                    return@withIOContext true
                } else {
                    XLog.e("error updating rating", it)
                    return@withIOContext false
                }
            }
            result.isSuccess
        }*/
    }

    /**
     * fetch all manga from all possible pages
     */
    suspend fun fetchAllFollows(): List<Pair<SManga, MangaDexSearchMetadata>> {
        return withIOContext {
            val results = client.mdListCall<MangaResponse> {
                followsListRequest(it)
            }

            val statuses = client.newCall(mangaStatusListRequest()).await()
                .parseAs<MangaStatusListResponse>().statuses

            followsParseMangaPage(results, statuses)
        }
    }

    suspend fun fetchTrackingInfo(url: String): Track {
        return withIOContext {
            val mangaId = MdUtil.getMangaId(url)
            val request = GET(
                MdUtil.mangaUrl + "/" + mangaId,
                MdUtil.getAuthHeaders(headers, preferences, mdList),
                CacheControl.FORCE_NETWORK
            )
            val statusRequest = GET(
                MdUtil.mangaUrl + "/" + mangaId + "/status",
                MdUtil.getAuthHeaders(headers, preferences, mdList),
                CacheControl.FORCE_NETWORK
            )
            val response = client.newCall(request).await()
            val statusResponse = client.newCall(statusRequest).await()
            followStatusParse(response, statusResponse)
        }
    }

    private fun mangaStatusListRequest(status: FollowStatus? = null): Request {
        val mangaStatusUrl = MdUtil.mangaStatus.toHttpUrl().newBuilder()

        if (status != null) {
            mangaStatusUrl.addQueryParameter("status", status.name.toLowerCase(Locale.US))
        }

        return GET(mangaStatusUrl.build().toString(), MdUtil.getAuthHeaders(headers, preferences, mdList), CacheControl.FORCE_NETWORK)
    }
}
