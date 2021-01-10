package exh.md.handlers

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.lang.runAsObservable
import exh.md.handlers.serializers.ApiCovers
import exh.md.utils.MdUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import rx.Observable
import tachiyomi.source.model.MangaInfo

class MangaHandler(val client: OkHttpClient, val headers: Headers, val lang: String, val forceLatestCovers: Boolean = false) {

    // TODO make use of this
    suspend fun fetchMangaAndChapterDetails(manga: MangaInfo, sourceId: Long): Pair<MangaInfo, List<SChapter>> {
        return withContext(Dispatchers.IO) {
            val response = client.newCall(apiRequest(manga.toSManga())).await()
            val covers = getCovers(manga, forceLatestCovers)
            val parser = ApiMangaParser(lang)

            val jsonData = withContext(Dispatchers.IO) { response.body!!.string() }
            if (response.code != 200) {
                XLog.tag("MangaHandler").enableStackTrace(2).e("error from MangaDex with response code ${response.code} \n body: \n$jsonData")
                throw Exception("Error from MangaDex Response code ${response.code} ")
            }

            parser.parseToManga(manga, response, covers, sourceId)
            val chapterList = parser.chapterListParse(jsonData)
            Pair(
                manga,
                chapterList
            )
        }
    }

    suspend fun getCovers(manga: MangaInfo, forceLatestCovers: Boolean): List<String> {
        return if (forceLatestCovers) {
            val covers = client.newCall(coverRequest(manga.toSManga())).await().parseAs<ApiCovers>()
            covers.data.map { it.url }
        } else {
            emptyList()
        }
    }

    suspend fun getMangaIdFromChapterId(urlChapterId: String): Int {
        return withContext(Dispatchers.IO) {
            val request = GET(MdUtil.apiUrl + MdUtil.newApiChapter + urlChapterId + MdUtil.apiChapterSuffix, headers, CacheControl.FORCE_NETWORK)
            val response = client.newCall(request).await()
            ApiMangaParser(lang).chapterParseForMangaId(response)
        }
    }

    suspend fun getMangaDetails(manga: MangaInfo, sourceId: Long): MangaInfo {
        return withContext(Dispatchers.IO) {
            val response = client.newCall(apiRequest(manga.toSManga())).await()
            val covers = getCovers(manga, forceLatestCovers)
            ApiMangaParser(lang).parseToManga(manga, response, covers, sourceId)
        }
    }

    fun fetchMangaDetailsObservable(manga: SManga): Observable<SManga> {
        return client.newCall(apiRequest(manga))
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
        return client.newCall(apiRequest(manga))
            .asObservableSuccess()
            .map { response ->
                ApiMangaParser(lang).chapterListParse(response)
            }
    }

    suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        return withContext(Dispatchers.IO) {
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
        return withContext(Dispatchers.IO) {
            val response = client.newCall(randomMangaRequest()).await()
            ApiMangaParser(lang).randomMangaIdParse(response)
        }
    }

    private fun randomMangaRequest(): Request {
        return GET(MdUtil.baseUrl + MdUtil.randMangaPage, cache = CacheControl.Builder().noCache().build())
    }

    private fun apiRequest(manga: SManga): Request {
        return GET(MdUtil.apiUrl + MdUtil.apiManga + MdUtil.getMangaId(manga.url) + MdUtil.includeChapters, headers, CacheControl.FORCE_NETWORK)
    }

    private fun coverRequest(manga: SManga): Request {
        return GET(MdUtil.apiUrl + MdUtil.apiManga + MdUtil.getMangaId(manga.url) + MdUtil.apiCovers, headers, CacheControl.FORCE_NETWORK)
    }
}
