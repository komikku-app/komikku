package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.lang.runAsObservable
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.handlers.serializers.ChapterListResponse
import exh.md.handlers.serializers.ChapterResponse
import exh.md.handlers.serializers.GroupListResponse
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.util.under
import kotlinx.coroutines.async
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaHandler(val client: OkHttpClient, val headers: Headers, private val lang: String, private val forceLatestCovers: Boolean = false) {

    suspend fun fetchMangaAndChapterDetails(manga: MangaInfo, sourceId: Long): Pair<MangaInfo, List<ChapterInfo>> {
        return withIOContext {
            val response = client.newCall(mangaRequest(manga)).await()
            val covers = getCovers(manga, forceLatestCovers)
            val parser = ApiMangaParser(client, lang)

            parser.parseToManga(manga, response, covers, sourceId) to getChapterList(manga)
        }
    }

    suspend fun getCovers(manga: MangaInfo, forceLatestCovers: Boolean): List<String> {
        /*  if (forceLatestCovers) {
              val covers = client.newCall(coverRequest(manga)).await().parseAs<ApiCovers>(MdUtil.jsonParser)
              return covers.data.map { it.url }
          } else {*/
        return emptyList<String>()
        //  }
    }

    suspend fun getMangaIdFromChapterId(urlChapterId: String): String {
        return withIOContext {
            val request = GET(MdUtil.chapterUrl + urlChapterId)
            val response = client.newCall(request).await()
            ApiMangaParser(client, lang).chapterParseForMangaId(response)
        }
    }

    suspend fun getMangaDetails(manga: MangaInfo, sourceId: Long): MangaInfo {
        return withIOContext {
            val response = client.newCall(mangaRequest(manga)).await()
            val covers = getCovers(manga, forceLatestCovers)
            ApiMangaParser(client, lang).parseToManga(manga, response, covers, sourceId)
        }
    }

    fun fetchMangaDetailsObservable(manga: SManga, sourceId: Long): Observable<SManga> {
        return client.newCall(mangaRequest(manga.toMangaInfo()))
            .asObservableSuccess()
            .flatMap { response ->
                runAsObservable({
                    ApiMangaParser(client, lang).parseToManga(manga.toMangaInfo(), response, emptyList(), sourceId).toSManga()
                })
            }
    }

    fun fetchChapterListObservable(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(mangaFeedRequest(manga.toMangaInfo(), 0, lang))
            .asObservableSuccess()
            .map { response ->
                val chapterListResponse = response.parseAs<ChapterListResponse>(MdUtil.jsonParser)
                val results = chapterListResponse.results.toMutableList()

                var hasMoreResults = chapterListResponse.limit + chapterListResponse.offset under chapterListResponse.total
                var lastOffset = chapterListResponse.offset

                while (hasMoreResults) {
                    val offset = lastOffset + chapterListResponse.limit
                    val newChapterListResponse = client.newCall(mangaFeedRequest(manga.toMangaInfo(), offset, lang)).execute()
                        .parseAs<ChapterListResponse>(MdUtil.jsonParser)
                    results.addAll(newChapterListResponse.results)
                    hasMoreResults = newChapterListResponse.limit + newChapterListResponse.offset under newChapterListResponse.total
                    lastOffset = newChapterListResponse.offset
                }
                val groupIds =
                    results.asSequence()
                        .map { chapter -> chapter.relationships }
                        .flatten()
                        .filter { it.type == "scanlation_group" }
                        .map { it.id }
                        .distinct()
                        .toList()

                val groupMap = runCatching {
                    groupIds.chunked(100).mapIndexed { index, ids ->
                        val groupList = client.newCall(groupIdRequest(ids, 100 * index)).execute()
                            .parseAs<GroupListResponse>(MdUtil.jsonParser)
                        groupList.results.map { group -> Pair(group.data.id, group.data.attributes.name) }
                    }.flatten().toMap()
                }.getOrNull() ?: emptyMap()

                ApiMangaParser(client, lang).chapterListParse(results, groupMap).map { it.toSChapter() }
            }
    }

    suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return withIOContext {
            val chapterListResponse = client.newCall(mangaFeedRequest(manga, 0, lang)).await().parseAs<ChapterListResponse>(MdUtil.jsonParser)
            val results = chapterListResponse.results

            var hasMoreResults = chapterListResponse.limit + chapterListResponse.offset under chapterListResponse.total
            var lastOffset = chapterListResponse.offset

            while (hasMoreResults) {
                val offset = lastOffset + chapterListResponse.limit
                val newChapterListResponse = client.newCall(mangaFeedRequest(manga, offset, lang)).await()
                    .parseAs<ChapterListResponse>(MdUtil.jsonParser)
                hasMoreResults = newChapterListResponse.limit + newChapterListResponse.offset under newChapterListResponse.total
                lastOffset = newChapterListResponse.offset
            }

            val groupMap = getGroupMap(results)

            ApiMangaParser(client, lang).chapterListParse(results, groupMap)
        }
    }

    private suspend fun getGroupMap(results: List<ChapterResponse>): Map<String, String> {
        val groupIds = results.map { chapter -> chapter.relationships }.flatten().filter { it.type == "scanlation_group" }.map { it.id }.distinct()
        val groupMap = runCatching {
            groupIds.chunked(100).mapIndexed { index, ids ->
                client.newCall(groupIdRequest(ids, 100 * index)).await()
                    .parseAs<GroupListResponse>(MdUtil.jsonParser)
                    .results.map { group -> Pair(group.data.id, group.data.attributes.name) }
            }.flatten().toMap()
        }.getOrNull() ?: emptyMap()

        return groupMap
    }

    suspend fun fetchRandomMangaId(): String {
        return withIOContext {
            val response = client.newCall(randomMangaRequest()).await()
            ApiMangaParser(client, lang).randomMangaIdParse(response)
        }
    }

    suspend fun getTrackingInfo(track: Track, useLowQualityCovers: Boolean, mdList: MdList): Pair<Track, MangaDexSearchMetadata?> {
        return withIOContext {
            val metadata = async {
                val mangaUrl = "/manga/" + MdUtil.getMangaId(track.tracking_url)
                val manga = MangaInfo(mangaUrl, track.title)
                val response = client.newCall(mangaRequest(manga)).await()
                val metadata = MangaDexSearchMetadata()
                ApiMangaParser(client, lang).parseIntoMetadata(metadata, response, emptyList())
                metadata
            }
            val remoteTrack = async {
                FollowsHandler(
                    client,
                    headers,
                    Injekt.get(),
                    lang,
                    useLowQualityCovers,
                    mdList
                ).fetchTrackingInfo(track.tracking_url)
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
