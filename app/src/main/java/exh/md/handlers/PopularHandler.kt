package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import exh.md.utils.MdUtil
import exh.md.utils.setMDUrlWithoutDomain
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

// Unused, kept for reference todo
/**
 * Returns the latest manga from the updates url since it actually respects the users settings
 */
class PopularHandler(val client: OkHttpClient, private val headers: Headers, private val useLowQualityCovers: Boolean) {

    fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

    private fun popularMangaRequest(page: Int): Request {
        return GET("${MdUtil.baseUrl}/updates/$page/", headers, CacheControl.FORCE_NETWORK)
    }

    private fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector).map { element ->
            popularMangaFromElement(element)
        }.distinctBy { it.url }

        val hasNextPage = popularMangaNextPageSelector.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.manga_title").first().let {
            val url = MdUtil.modifyMangaUrl(it.attr("href"))
            manga.setMDUrlWithoutDomain(url)
            manga.title = it.text().trim()
        }

        manga.thumbnail_url = MdUtil.formThumbUrl(manga.url, useLowQualityCovers)

        return manga
    }

    companion object {
        const val popularMangaSelector = "tr a.manga_title"
        const val popularMangaNextPageSelector = ".pagination li:not(.disabled) span[title*=last page]:not(disabled)"
    }
}
