package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.log.xLogD
import exh.log.xLogE
import exh.md.handlers.serializers.FollowPage
import exh.md.handlers.serializers.FollowsIndividualSerializer
import exh.md.handlers.serializers.FollowsPageSerializer
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.util.awaitResponse
import exh.util.floor
import kotlinx.serialization.decodeFromString
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.EOFException

class FollowsHandler(val client: OkHttpClient, val headers: Headers, val preferences: PreferencesHelper, private val useLowQualityCovers: Boolean) {

    /**
     * fetch follows by page
     */
    suspend fun fetchFollows(): MangasPage {
        return client.newCall(followsListRequest())
            .await()
            .let { response ->
                followsParseMangaPage(response)
            }
    }

    /**
     * Parse follows api to manga page
     * used when multiple follows
     */
    private fun followsParseMangaPage(response: Response, forceHd: Boolean = false): MetadataMangasPage {
        val followsPageResult = try {
            MdUtil.jsonParser.decodeFromString(
                response.body?.string().orEmpty()
            )
        } catch (e: Exception) {
            xLogE("error parsing follows", e)
            FollowsPageSerializer(404, emptyList())
        }

        if (followsPageResult.data.isNullOrEmpty() || followsPageResult.code != 200) {
            return MetadataMangasPage(emptyList(), false, emptyList())
        }
        val lowQualityCovers = if (forceHd) false else useLowQualityCovers

        val follows = followsPageResult.data.map {
            followFromElement(it, lowQualityCovers)
        }

        val comparator = compareBy<Pair<SManga, MangaDexSearchMetadata>> { it.second.follow_status }.thenBy { it.first.title }

        val result = follows.sortedWith(comparator)

        return MetadataMangasPage(result.map { it.first }, false, result.map { it.second })
    }

    /**
     * fetch follow status used when fetching status for 1 manga
     */
    private fun followStatusParse(response: Response): Track {
        val followsPageResult = try {
            response.parseAs<FollowsIndividualSerializer>(MdUtil.jsonParser)
        } catch (e: Exception) {
            xLogE("error parsing follows", e)
            throw e
        }

        val track = Track.create(TrackManager.MDLIST)
        if (followsPageResult.code == 404) {
            track.status = FollowStatus.UNFOLLOWED.int
        } else {
            val follow = followsPageResult.data ?: throw Exception("Invalid response ${followsPageResult.code}")
            track.status = follow.followType
            if (follow.chapter.isNotBlank()) {
                track.last_chapter_read = follow.chapter.toFloat().floor()
            }
            track.tracking_url = MdUtil.baseUrl + follow.mangaId.toString()
            track.title = follow.mangaTitle
        }
        return track
    }

    /**
     * build Request for follows page
     */
    private fun followsListRequest(): Request {
        return GET("${MdUtil.apiUrl}${MdUtil.followsAllApi}", headers, CacheControl.FORCE_NETWORK)
    }

    /**
     * Parse result element  to manga
     */
    private fun followFromElement(result: FollowPage, lowQualityCovers: Boolean): Pair<SManga, MangaDexSearchMetadata> {
        val manga = SManga.create()
        manga.title = MdUtil.cleanString(result.mangaTitle)
        manga.url = "/manga/${result.mangaId}/"
        manga.thumbnail_url = MdUtil.formThumbUrl(manga.url, lowQualityCovers)
        return manga to MangaDexSearchMetadata().apply {
            title = manga.title
            mdUrl = manga.url
            thumbnail_url = manga.thumbnail_url
            follow_status = FollowStatus.fromInt(result.followType).int
        }
    }

    /**
     * Change the status of a manga
     */
    suspend fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Boolean {
        return withIOContext {
            val response: Response =
                if (followStatus == FollowStatus.UNFOLLOWED) {
                    client.newCall(
                        GET(
                            "${MdUtil.baseUrl}/ajax/actions.ajax.php?function=manga_unfollow&id=$mangaID&type=$mangaID",
                            headers,
                            CacheControl.FORCE_NETWORK
                        )
                    )
                        .await()
                } else {
                    val status = followStatus.int
                    client.newCall(
                        GET(
                            "${MdUtil.baseUrl}/ajax/actions.ajax.php?function=manga_follow&id=$mangaID&type=$status",
                            headers,
                            CacheControl.FORCE_NETWORK
                        )
                    )
                        .await()
                }

            response.succeeded()
        }
    }

    suspend fun updateReadingProgress(track: Track): Boolean {
        return withIOContext {
            val mangaID = MdUtil.getMangaId(track.tracking_url)
            val formBody = FormBody.Builder()
                .add("volume", "0")
                .add("chapter", track.last_chapter_read.toString())
            xLogD("chapter to update %s", track.last_chapter_read.toString())
            val response = client.newCall(
                POST(
                    "${MdUtil.baseUrl}/ajax/actions.ajax.php?function=edit_progress&id=$mangaID",
                    headers,
                    formBody.build()
                )
            ).await()

            response.succeeded()
        }
    }

    suspend fun updateRating(track: Track): Boolean {
        return withIOContext {
            val mangaID = MdUtil.getMangaId(track.tracking_url)
            val response = client.newCall(
                GET(
                    "${MdUtil.baseUrl}/ajax/actions.ajax.php?function=manga_rating&id=$mangaID&rating=${track.score.toInt()}",
                    headers
                )
            ).await()

            response.succeeded()
        }
    }

    private suspend fun Response.succeeded() = withIOContext {
        try {
            body?.string().let { body ->
                (body != null && body.isEmpty()).also {
                    if (!it) xLogD(body)
                }
            }
        } catch (e: EOFException) {
            true
        }
    }

    /**
     * fetch all manga from all possible pages
     */
    suspend fun fetchAllFollows(forceHd: Boolean): List<Pair<SManga, MangaDexSearchMetadata>> {
        return withIOContext {
            val response = client.newCall(followsListRequest()).await()
            val mangasPage = followsParseMangaPage(response, forceHd)
            mangasPage.mangas.mapIndexed { index, sManga ->
                sManga to mangasPage.mangasMetadata[index] as MangaDexSearchMetadata
            }
        }
    }

    suspend fun fetchTrackingInfo(url: String): Track {
        return withIOContext {
            val request = GET(
                MdUtil.apiUrl + MdUtil.followsMangaApi + MdUtil.getMangaId(url),
                headers,
                CacheControl.FORCE_NETWORK
            )
            val response = client.newCall(request).awaitResponse()
            followStatusParse(response)
        }
    }
}
