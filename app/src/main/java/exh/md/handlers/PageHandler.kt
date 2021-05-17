package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import exh.md.utils.MdUtil
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import rx.Observable

class PageHandler(
    val client: OkHttpClient,
    val headers: Headers,
    private val dataSaver: Boolean,
    private val apiChapterParser: ApiChapterParser,
    private val mangaPlusHandler: MangaPlusHandler
) {

    fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (chapter.scanlator.equals("MangaPlus")) {
            return client.newCall(pageListRequest(chapter))
                .asObservableSuccess()
                .map { response ->
                    val chapterId = apiChapterParser.externalParse(response)
                    mangaPlusHandler.fetchPageList(chapterId)
                }
        }
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                val host = MdUtil.atHomeUrlHostUrl("${MdUtil.atHomeUrl}/${MdUtil.getChapterId(chapter.url)}", client)
                apiChapterParser.pageListParse(response, host, dataSaver)
            }
    }

    private fun pageListRequest(chapter: SChapter): Request {
        return GET("${MdUtil.chapterUrl}${MdUtil.getChapterId(chapter.url)}", headers, CacheControl.FORCE_NETWORK)
    }
}
