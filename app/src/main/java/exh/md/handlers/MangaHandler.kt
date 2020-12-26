package exh.md.handlers

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.utils.MdUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import rx.Observable

class MangaHandler(val client: OkHttpClient, val headers: Headers, val langs: List<String>, val forceLatestCovers: Boolean = false) {

    // TODO make use of this
    suspend fun fetchMangaAndChapterDetails(manga: SManga): Pair<SManga, List<SChapter>> {
        return withContext(Dispatchers.IO) {
            val response = client.newCall(apiRequest(manga)).await()
            val parser = ApiMangaParser(langs)

            val jsonData = withContext(Dispatchers.IO) { response.body!!.string() }
            if (response.code != 200) {
                XLog.tag("MangaHandler").enableStackTrace(2).e("error from MangaDex with response code ${response.code} \n body: \n$jsonData")
                throw Exception("Error from MangaDex Response code ${response.code} ")
            }

            parser.parseToManga(manga, response, forceLatestCovers).await()
            val chapterList = parser.chapterListParse(jsonData)
            Pair(
                manga,
                chapterList
            )
        }
    }

    suspend fun getMangaIdFromChapterId(urlChapterId: String): Int {
        return withContext(Dispatchers.IO) {
            val request = GET(MdUtil.baseUrl + MdUtil.apiChapter + urlChapterId + MdUtil.apiChapterSuffix, headers, CacheControl.FORCE_NETWORK)
            val response = client.newCall(request).await()
            ApiMangaParser(langs).chapterParseForMangaId(response)
        }
    }

    suspend fun fetchMangaDetails(manga: SManga): SManga {
        return withContext(Dispatchers.IO) {
            val response = client.newCall(apiRequest(manga)).await()
            ApiMangaParser(langs).parseToManga(manga, response, forceLatestCovers).await()
            manga.apply {
                initialized = true
            }
        }
    }

    fun fetchMangaDetailsObservable(manga: SManga): Observable<SManga> {
        return client.newCall(apiRequest(manga))
            .asObservableSuccess()
            .flatMap {
                ApiMangaParser(langs).parseToManga(manga, it, forceLatestCovers).andThen(
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
                ApiMangaParser(langs).chapterListParse(response)
            }
    }

    suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        return withContext(Dispatchers.IO) {
            val response = client.newCall(apiRequest(manga)).await()
            ApiMangaParser(langs).chapterListParse(response)
        }
    }

    fun fetchRandomMangaIdObservable(): Observable<String> {
        return client.newCall(randomMangaRequest())
            .asObservableSuccess()
            .map { response ->
                ApiMangaParser(langs).randomMangaIdParse(response)
            }
    }

    suspend fun fetchRandomMangaId(): String {
        return withContext(Dispatchers.IO) {
            val response = client.newCall(randomMangaRequest()).await()
            ApiMangaParser(langs).randomMangaIdParse(response)
        }
    }

    private fun randomMangaRequest(): Request {
        return GET(MdUtil.baseUrl + MdUtil.randMangaPage, cache = CacheControl.Builder().noCache().build())
    }

    private fun apiRequest(manga: SManga): Request {
        return GET(MdUtil.baseUrl + MdUtil.apiManga + MdUtil.getMangaId(manga.url), headers, CacheControl.FORCE_NETWORK)
    }
}
