package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.dto.MangaDataDto
import exh.md.dto.PersonalRatingDto
import exh.md.dto.ReadingStatusDto
import exh.md.service.MangaDexAuthService
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.md.utils.asMdMap
import exh.md.utils.mdListCall
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.util.under
import kotlinx.coroutines.async

class FollowsHandler(
    private val lang: String,
    private val service: MangaDexAuthService,
) {

    /**
     * fetch follows page
     */
    suspend fun fetchFollows(page: Int): MetadataMangasPage {
        return withIOContext {
            val follows = service.userFollowList(MdUtil.mangaLimit * page)

            if (follows.data.isEmpty()) {
                return@withIOContext MetadataMangasPage(emptyList(), false, emptyList())
            }

            val hasMoreResults = follows.limit + follows.offset under follows.total
            val statusListResponse = service.readingStatusAllManga()
            val results = followsParseMangaPage(follows.data, statusListResponse.statuses)

            MetadataMangasPage(results.map { it.first }, hasMoreResults, results.map { it.second })
        }
    }

    /**
     * Parse follows api to manga page
     * used when multiple follows
     */
    private fun followsParseMangaPage(
        response: List<MangaDataDto>,
        statuses: Map<String, String?>,
    ): List<Pair<SManga, MangaDexSearchMetadata>> {
        val comparator = compareBy<Pair<SManga, MangaDexSearchMetadata>> { it.second.followStatus }
            .thenBy { it.first.title }

        return response.map {
            MdUtil.createMangaEntry(
                it,
                lang,
            ).toSManga() to MangaDexSearchMetadata().apply {
                followStatus = FollowStatus.fromDex(statuses[it.id]).int
            }
        }.sortedWith(comparator)
    }

    /**
     * Change the status of a manga
     */
    suspend fun updateFollowStatus(mangaId: String, followStatus: FollowStatus): Boolean {
        return withIOContext {
            val status = when (followStatus == FollowStatus.UNFOLLOWED) {
                true -> null
                false -> followStatus.toDex()
            }
            val readingStatusDto = ReadingStatusDto(status)

            if (followStatus == FollowStatus.UNFOLLOWED) {
                service.unfollowManga(mangaId)
            } else {
                service.followManga(mangaId)
            }

            service.updateReadingStatusForManga(mangaId, readingStatusDto).result == "ok"
        }
    }

    /*suspend fun updateReadingProgress(track: Track): Boolean {
        return true
        return withIOContext {
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
        }
    }*/

    suspend fun updateRating(track: Track): Boolean {
        return withIOContext {
            val mangaId = MdUtil.getMangaId(track.tracking_url)
            val result = runCatching {
                if (track.score == 0f) {
                    service.deleteMangaRating(mangaId)
                } else {
                    service.updateMangaRating(mangaId, track.score.toInt())
                }.result == "ok"
            }
            result.getOrDefault(false)
        }
    }

    /**
     * fetch all manga from all possible pages
     */
    suspend fun fetchAllFollows(): List<Pair<SManga, MangaDexSearchMetadata>> {
        return withIOContext {
            val results = async {
                mdListCall {
                    service.userFollowList(it)
                }
            }

            val readingStatusResponse = async { service.readingStatusAllManga().statuses }

            followsParseMangaPage(results.await(), readingStatusResponse.await())
        }
    }

    suspend fun fetchTrackingInfo(url: String): Track {
        return withIOContext {
            val mangaId = MdUtil.getMangaId(url)
            val followStatusDef = async {
                FollowStatus.fromDex(service.readingStatusForManga(mangaId).status)
            }
            val ratingDef = async {
                service.mangasRating(mangaId).ratings.asMdMap<PersonalRatingDto>()[mangaId]
            }
            val (followStatus, rating) = followStatusDef.await() to ratingDef.await()
            Track.create(TrackManager.MDLIST).apply {
                title = ""
                status = followStatus.int
                tracking_url = url
                score = rating?.rating?.toFloat() ?: 0F
            }
        }
    }
}
