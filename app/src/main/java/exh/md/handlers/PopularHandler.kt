package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.lang.runAsObservable
import exh.md.handlers.serializers.MangaListResponse
import exh.md.utils.MdUtil
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

/**
 * Returns the latest manga from the updates url since it actually respects the users settings
 */
class PopularHandler(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val lang: String
) {

    fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .flatMap { response ->
                runAsObservable({
                    popularMangaParse(response)
                })
            }
    }

    private fun popularMangaRequest(page: Int): Request {
        val tempUrl = MdUtil.mangaUrl.toHttpUrl().newBuilder()

        tempUrl.apply {
            addQueryParameter("limit", MdUtil.mangaLimit.toString())
            addQueryParameter("offset", (MdUtil.getMangaListOffset(page)))
        }

        return GET(tempUrl.build().toString(), headers, CacheControl.FORCE_NETWORK)
    }

    private suspend fun popularMangaParse(response: Response): MangasPage {
        val mlResponse = response.parseAs<MangaListResponse>(MdUtil.jsonParser)
        val hasMoreResults = mlResponse.limit + mlResponse.offset < mlResponse.total

        val coverMap = MdUtil.getCoversFromMangaList(mlResponse.results, client)

        val mangaList = mlResponse.results.map {
            MdUtil.createMangaEntry(it, lang, coverMap[it.data.id]).toSManga()
        }
        return MangasPage(mangaList, hasMoreResults)
    }
}
