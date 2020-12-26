package exh.md.handlers

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.handlers.serializers.FollowsPageResult
import exh.md.handlers.serializers.Result
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.util.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
            XLog.tag("FollowsHandler").enableStackTrace(2).e("error parsing follows", e)
            FollowsPageResult()
        }

        if (followsPageResult.result.isEmpty()) {
            return MetadataMangasPage(emptyList(), false, emptyList())
        }
        val lowQualityCovers = if (forceHd) false else useLowQualityCovers

        val follows = followsPageResult.result.map {
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
            MdUtil.jsonParser.decodeFromString(
                response.body?.string().orEmpty()
            )
        } catch (e: Exception) {
            XLog.tag("FollowsHandler").enableStackTrace(2).e("error parsing follows", e)
            FollowsPageResult()
        }
        val track = Track.create(TrackManager.MDLIST)
        if (followsPageResult.result.isEmpty()) {
            track.status = FollowStatus.UNFOLLOWED.int
        } else {
            val follow = followsPageResult.result.first()
            track.status = follow.follow_type
            if (followsPageResult.result[0].chapter.isNotBlank()) {
                track.last_chapter_read = follow.chapter.toFloat().floor()
            }
            track.tracking_url = MdUtil.baseUrl + follow.manga_id.toString()
            track.title = follow.title
        }
        return track
    }

    /**
     * build Request for follows page
     */
    private fun followsListRequest(): Request {
        return GET("${MdUtil.baseUrl}${MdUtil.followsAllApi}", headers, CacheControl.FORCE_NETWORK)
    }

    /**
     * Parse result element  to manga
     */
    private fun followFromElement(result: Result, lowQualityCovers: Boolean): Pair<SManga, MangaDexSearchMetadata> {
        val manga = SManga.create()
        manga.title = MdUtil.cleanString(result.title)
        manga.url = "/manga/${result.manga_id}/"
        manga.thumbnail_url = MdUtil.formThumbUrl(manga.url, lowQualityCovers)
        return manga to MangaDexSearchMetadata().apply {
            title = manga.title
            mdUrl = manga.url
            thumbnail_url = manga.thumbnail_url
            follow_status = FollowStatus.fromInt(result.follow_type)?.int
        }
    }

    /**
     * Change the status of a manga
     */
    suspend fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Boolean {
        return withContext(Dispatchers.IO) {
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

            withContext(Dispatchers.IO) { response.body?.string().isNullOrEmpty() }
        }
    }

    suspend fun updateReadingProgress(track: Track): Boolean {
        return withContext(Dispatchers.IO) {
            val mangaID = MdUtil.getMangaId(track.tracking_url)
            val formBody = FormBody.Builder()
                .add("chapter", track.last_chapter_read.toString())
            XLog.tag("FollowsHandler").d("chapter to update %s", track.last_chapter_read.toString())
            val response = client.newCall(
                POST(
                    "${MdUtil.baseUrl}/ajax/actions.ajax.php?function=edit_progress&id=$mangaID",
                    headers,
                    formBody.build()
                )
            ).await()

            client.newCall(
                GET(
                    "${MdUtil.baseUrl}/ajax/actions.ajax.php?function=manga_rating&id=$mangaID&rating=${track.score.toInt()}",
                    headers
                )
            ).await()

            withContext(Dispatchers.IO) { response.body?.string().isNullOrEmpty() }
        }
    }

    suspend fun updateRating(track: Track): Boolean {
        return withContext(Dispatchers.IO) {
            val mangaID = MdUtil.getMangaId(track.tracking_url)
            val response = client.newCall(
                GET(
                    "${MdUtil.baseUrl}/ajax/actions.ajax.php?function=manga_rating&id=$mangaID&rating=${track.score.toInt()}",
                    headers
                )
            )
                .await()

            withContext(Dispatchers.IO) { response.body?.string().isNullOrEmpty() }
        }
    }

    /**
     * fetch all manga from all possible pages
     */
    suspend fun fetchAllFollows(forceHd: Boolean): List<Pair<SManga, MangaDexSearchMetadata>> {
        return withContext(Dispatchers.IO) {
            val listManga = mutableListOf<Pair<SManga, MangaDexSearchMetadata>>()
            val response = client.newCall(followsListRequest()).await()
            val mangasPage = followsParseMangaPage(response, forceHd)
            listManga.addAll(
                mangasPage.mangas.mapIndexed { index, sManga ->
                    sManga to mangasPage.mangasMetadata[index] as MangaDexSearchMetadata
                }
            )
            listManga
        }
    }

    suspend fun fetchTrackingInfo(url: String): Track {
        return withContext(Dispatchers.IO) {
            val request = GET(
                "${MdUtil.baseUrl}${MdUtil.followsMangaApi}" + MdUtil.getMangaId(url),
                headers,
                CacheControl.FORCE_NETWORK
            )
            val response = client.newCall(request).await()
            val track = followStatusParse(response)

            track
        }
    }
}
