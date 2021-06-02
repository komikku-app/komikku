package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.lang.runAsObservable
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.handlers.serializers.ChapterResponse
import exh.md.handlers.serializers.GroupListResponse
import exh.md.utils.MdUtil
import exh.md.utils.mdListCall
import exh.metadata.metadata.MangaDexSearchMetadata
import kotlinx.coroutines.async
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.api.get

class MangaHandler(
    val client: OkHttpClient,
    val headers: Headers,
    private val lang: String,
    private val apiMangaParser: ApiMangaParser,
    private val followsHandler: FollowsHandler
) {

    suspend fun fetchMangaAndChapterDetails(manga: MangaInfo, sourceId: Long, forceLatestCovers: Boolean): Pair<MangaInfo, List<ChapterInfo>> {
        return withIOContext {
            val response = client.newCall(mangaRequest(manga)).await()
            val covers = getCovers(manga, forceLatestCovers)

            apiMangaParser.parseToManga(manga, response, covers, sourceId) to getChapterList(manga)
        }
    }

    suspend fun getCovers(manga: MangaInfo, forceLatestCovers: Boolean): List<String> {
        /*  if (forceLatestCovers) {
              val covers = client.newCall(coverRequest(manga)).await().parseAs<ApiCovers>(MdUtil.jsonParser)
              return covers.data.map { it.url }
          } else {*/
        return emptyList()
        //  }
    }

    suspend fun getMangaIdFromChapterId(urlChapterId: String): String {
        return withIOContext {
            val request = GET(MdUtil.chapterUrl + urlChapterId)
            val response = client.newCall(request).await()
            apiMangaParser.chapterParseForMangaId(response)
        }
    }

    suspend fun getMangaDetails(manga: MangaInfo, sourceId: Long, forceLatestCovers: Boolean): MangaInfo {
        val response = withIOContext { client.newCall(mangaRequest(manga)).await() }
        val covers = withIOContext { getCovers(manga, forceLatestCovers) }
        return apiMangaParser.parseToManga(manga, response, covers, sourceId)
    }

    fun fetchMangaDetailsObservable(manga: SManga, sourceId: Long, forceLatestCovers: Boolean): Observable<SManga> {
        return runAsObservable({
            getMangaDetails(manga.toMangaInfo(), sourceId, forceLatestCovers).toSManga()
        })
    }

    fun fetchChapterListObservable(manga: SManga): Observable<List<SChapter>> = runAsObservable({
        getChapterList(manga.toMangaInfo()).map { it.toSChapter() }
    })

    suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return withIOContext {
            val results = client.mdListCall<ChapterResponse> {
                mangaFeedRequest(manga, it, lang)
            }

            val groupMap = getGroupMap(results)

            apiMangaParser.chapterListParse(results, groupMap)
        }
    }

    private suspend fun getGroupMap(results: List<ChapterResponse>): Map<String, String> {
        val groupIds = results.asSequence()
            .flatMap { chapter -> chapter.relationships }
            .filter { it.type == "scanlation_group" }
            .map { it.id }
            .toSet()

        return runCatching {
            groupIds.chunked(100).flatMapIndexed { index, ids ->
                val response = client.newCall(groupIdRequest(ids, 100 * index)).await()
                if (response.code != 204) {
                    response
                        .parseAs<GroupListResponse>(MdUtil.jsonParser)
                        .results.map { group -> group.data.id to group.data.attributes.name }
                } else {
                    emptyList()
                }
            }.toMap()
        }.getOrNull().orEmpty()
    }

    suspend fun fetchRandomMangaId(): String {
        return withIOContext {
            val response = client.newCall(randomMangaRequest()).await()
            apiMangaParser.randomMangaIdParse(response)
        }
    }

    suspend fun getTrackingInfo(track: Track): Pair<Track, MangaDexSearchMetadata?> {
        return withIOContext {
            /*val metadata = async {
                val mangaUrl = MdUtil.buildMangaUrl(MdUtil.getMangaId(track.tracking_url))
                val manga = MangaInfo(mangaUrl, track.title)
                val response = client.newCall(mangaRequest(manga)).await()
                val metadata = MangaDexSearchMetadata()
                apiMangaParser.parseIntoMetadata(metadata, response, emptyList())
                metadata
            }*/
            val remoteTrack = async {
                followsHandler.fetchTrackingInfo(track.tracking_url)
            }
            remoteTrack.await() to null
        }
    }

    private fun randomMangaRequest(): Request {
        return GET(MdUtil.randomMangaUrl, cache = CacheControl.FORCE_NETWORK)
    }

    private fun mangaRequest(manga: MangaInfo): Request {
        return GET(MdUtil.mangaUrl + "/" + MdUtil.getMangaId(manga.key), headers, CacheControl.FORCE_NETWORK)
    }

    private fun mangaFeedRequest(manga: MangaInfo, offset: Int, lang: String): Request {
        return GET(MdUtil.mangaFeedUrl(MdUtil.getMangaId(manga.key), offset, lang), headers, CacheControl.FORCE_NETWORK)
    }

    private fun groupIdRequest(id: List<String>, offset: Int): Request {
        val urlSuffix = id.joinToString("&ids[]=", "?limit=100&offset=$offset&ids[]=")
        return GET(MdUtil.groupUrl + urlSuffix, headers)
    }

    /*  private fun coverRequest(manga: SManga): Request {
          return GET(MdUtil.apiUrl + MdUtil.apiManga + MdUtil.getMangaId(manga.url) + MdUtil.apiCovers, headers, CacheControl.FORCE_NETWORK)
      }*/

    companion object {
    }
}
