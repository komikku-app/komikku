package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.lang.runAsObservable
import exh.md.handlers.serializers.CoverListResponse
import exh.md.handlers.serializers.MangaListResponse
import exh.md.handlers.serializers.MangaResponse
import exh.md.utils.MdUtil
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class SearchHandler(val client: OkHttpClient, private val headers: Headers, val lang: String, val filterHandler: FilterHandler, private val apiMangaParser: ApiMangaParser) {

    fun fetchSearchManga(page: Int, query: String, filters: FilterList, sourceId: Long): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .flatMap { response ->
                    runAsObservable({
                        val mangaResponse = response.parseAs<MangaResponse>(MdUtil.jsonParser)

                        val coverUrl = MdUtil.formThumbUrl(mangaResponse.data.id)
                        /*val coverUrlId = mangaResponse.relationships.firstOrNull { it.type == "cover_art" }?.id
                        if (coverUrlId != null) {
                            runCatching {
                                val covers = client.newCall(GET(MdUtil.coverUrl(mangaResponse.data.id, coverUrlId))).await()
                                    .parseAs<CoverListResponse>()
                                covers.results.firstOrNull()?.data?.attributes?.fileName?.let { fileName ->
                                    coverUrl = "${MdUtil.cdnUrl}/covers/${mangaResponse.data.id}/$fileName"
                                }
                            }
                        }*/

                        val details = apiMangaParser
                            .parseToManga(MdUtil.createMangaEntry(mangaResponse, lang, coverUrl), response, sourceId).toSManga()
                        MangasPage(listOf(details), false)
                    })
                }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .flatMap { response ->
                    runAsObservable({
                        searchMangaParse(response)
                    })
                }
        }
    }

    private suspend fun searchMangaParse(response: Response): MangasPage {
        val mlResponse = response.parseAs<MangaListResponse>(MdUtil.jsonParser)
        val hasMoreResults = mlResponse.limit + mlResponse.offset < mlResponse.total
        val mangaList = mlResponse.results.map {
            var coverUrl = MdUtil.formThumbUrl(it.data.id)
            val coverUrlId = it.relationships.firstOrNull { it.type == "cover_art" }?.id
            if (coverUrlId != null) {
                runCatching {
                    val covers = client.newCall(GET(MdUtil.coverUrl(it.data.id, coverUrlId))).await()
                        .parseAs<CoverListResponse>()
                    covers.results.firstOrNull()?.data?.attributes?.fileName?.let { fileName ->
                        coverUrl = "${MdUtil.cdnUrl}/covers/${it.data.id}/$fileName"
                    }
                }
            }
            MdUtil.createMangaEntry(it, lang, coverUrl).toSManga()
        }
        return MangasPage(mangaList, hasMoreResults)
    }

    private fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tempUrl = MdUtil.mangaUrl.toHttpUrl().newBuilder()

        tempUrl.apply {
            addQueryParameter("limit", MdUtil.mangaLimit.toString())
            addQueryParameter("offset", (MdUtil.getMangaListOffset(page)))
            val actualQuery = query.replace(WHITESPACE_REGEX, " ")
            if (actualQuery.isNotBlank()) {
                addQueryParameter("title", actualQuery)
            }
        }

        val finalUrl = filterHandler.addFiltersToUrl(tempUrl, filters)

        return GET(finalUrl, headers, CacheControl.FORCE_NETWORK)
    }

    private fun searchMangaByIdRequest(id: String): Request {
        return GET(MdUtil.mangaUrl + "/" + id, headers, CacheControl.FORCE_NETWORK)
    }

    private fun searchMangaByGroupRequest(group: String): Request {
        return GET(MdUtil.groupSearchUrl + group, headers, CacheControl.FORCE_NETWORK)
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        const val PREFIX_GROUP_SEARCH = "group:"
        val WHITESPACE_REGEX = "\\s".toRegex()
    }
}
