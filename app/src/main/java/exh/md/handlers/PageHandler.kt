package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import exh.md.utils.MdUtil
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import rx.Observable

// Unused, kept for reference todo
class PageHandler(val client: OkHttpClient, val headers: Headers, private val imageServer: String, val dataSaver: String?) {

    @ExperimentalSerializationApi
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (chapter.scanlator.equals("MangaPlus")) {
            return client.newCall(pageListRequest(chapter))
                .asObservableSuccess()
                .map { response ->
                    val chapterId = ApiChapterParser().externalParse(response)
                    MangaPlusHandler(client).fetchPageList(chapterId)
                }
        }
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                ApiChapterParser().pageListParse(response)
            }
    }

    private fun pageListRequest(chapter: SChapter): Request {
        val chpUrl = chapter.url.substringBefore(MdUtil.apiChapterSuffix)
        return GET("${MdUtil.baseUrl}${chpUrl}${MdUtil.apiChapterSuffix}&server=$imageServer&saver=$dataSaver", headers, CacheControl.FORCE_NETWORK)
    }
}
