package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.util.lang.runAsObservable
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.handlers.serializers.ApiCovers
import exh.md.handlers.serializers.ApiMangaSerializer
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
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

class MangaHandler(val client: OkHttpClient, val headers: Headers, val lang: String, val forceLatestCovers: Boolean = false) {

    // TODO make use of this
    suspend fun fetchMangaAndChapterDetails(manga: MangaInfo, sourceId: Long): Pair<MangaInfo, List<ChapterInfo>> {
        return withIOContext {
            val apiNetworkManga = client.newCall(apiRequest(manga)).await().parseAs<ApiMangaSerializer>(MdUtil.jsonParser)
            val covers = getCovers(manga, forceLatestCovers)
            val parser = ApiMangaParser(lang)

            // TODO fix this
            /*val mangaInfo = parser.parseToManga(manga, response, covers, sourceId)
            val chapterList = parser.chapterListParse(apiNetworkManga)

            mangaInfo to chapterList*/
            manga to emptyList()
        }
    }

    suspend fun getCovers(manga: MangaInfo, forceLatestCovers: Boolean): List<String> {
        return if (forceLatestCovers) {
            val covers = client.newCall(coverRequest(manga)).await().parseAs<ApiCovers>(MdUtil.jsonParser)
            covers.data.map { it.url }
        } else {
            emptyList()
        }
    }

    suspend fun getMangaIdFromChapterId(urlChapterId: String): Int {
        return withIOContext {
            val request = GET(MdUtil.apiUrl + MdUtil.newApiChapter + urlChapterId + MdUtil.apiChapterSuffix, headers, CacheControl.FORCE_NETWORK)
            val response = client.newCall(request).await()
            ApiMangaParser(lang).chapterParseForMangaId(response)
        }
    }

    suspend fun getMangaDetails(manga: MangaInfo, sourceId: Long): MangaInfo {
        return withIOContext {
            val response = client.newCall(apiRequest(manga)).await()
            val covers = getCovers(manga, forceLatestCovers)
            ApiMangaParser(lang).parseToManga(manga, response, covers, sourceId)
        }
    }

    fun fetchMangaDetailsObservable(manga: SManga): Observable<SManga> {
        return client.newCall(apiRequest(manga.toMangaInfo()))
            .asObservableSuccess()
            .flatMap { response ->
                runAsObservable({
                    getCovers(manga.toMangaInfo(), forceLatestCovers)
                }).map {
                    response to it
                }
            }
            .flatMap {
                ApiMangaParser(lang).parseToManga(manga, it.first, it.second).andThen(
                    Observable.just(
                        manga.apply {
                            initialized = true
                        }
                    )
                )
            }
    }

    fun fetchChapterListObservable(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(apiRequest(manga.toMangaInfo()))
            .asObservableSuccess()
            .map { response ->
                ApiMangaParser(lang).chapterListParse(response).map { it.toSChapter() }
            }
    }

    suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return withIOContext {
            val response = client.newCall(apiRequest(manga)).await()
            ApiMangaParser(lang).chapterListParse(response)
        }
    }

    fun fetchRandomMangaIdObservable(): Observable<String> {
        return client.newCall(randomMangaRequest())
            .asObservableSuccess()
            .map { response ->
                ApiMangaParser(lang).randomMangaIdParse(response)
            }
    }

    suspend fun fetchRandomMangaId(): String {
        return withIOContext {
            val response = client.newCall(randomMangaRequest()).await()
            ApiMangaParser(lang).randomMangaIdParse(response)
        }
    }

    suspend fun getTrackingInfo(track: Track, useLowQualityCovers: Boolean): Pair<Track, MangaDexSearchMetadata> {
        return withIOContext {
            val metadata = async {
                val mangaUrl = MdUtil.mapMdIdToMangaUrl(MdUtil.getMangaId(track.tracking_url).toInt())
                val manga = MangaInfo(mangaUrl, track.title)
                val response = client.newCall(apiRequest(manga)).await()
                val metadata = MangaDexSearchMetadata()
                ApiMangaParser(lang).parseIntoMetadata(metadata, response, emptyList())
                metadata
            }
            val remoteTrack = async { FollowsHandler(client, headers, Injekt.get(), useLowQualityCovers).fetchTrackingInfo(track.tracking_url) }
            remoteTrack.await() to metadata.await()
        }
    }

    private fun randomMangaRequest(): Request {
        return GET(MdUtil.baseUrl + MdUtil.randMangaPage, cache = CacheControl.FORCE_NETWORK)
    }

    private fun apiRequest(manga: MangaInfo): Request {
        return GET(MdUtil.apiUrl + MdUtil.apiManga + MdUtil.getMangaId(manga.key) + MdUtil.includeChapters, headers, CacheControl.FORCE_NETWORK)
    }

    private fun coverRequest(manga: MangaInfo): Request {
        return GET(MdUtil.apiUrl + MdUtil.apiManga + MdUtil.getMangaId(manga.key) + MdUtil.apiCovers, headers, CacheControl.FORCE_NETWORK)
    }
}
