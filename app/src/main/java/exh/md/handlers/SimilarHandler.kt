package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.handlers.serializers.CoverListResponse
import exh.md.handlers.serializers.SimilarMangaResponse
import exh.md.utils.MdUtil
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.source.model.MangaInfo

class SimilarHandler(
    private val client: OkHttpClient,
    private val lang: String
) {

    suspend fun getSimilar(manga: MangaInfo): MangasPage {
        val response = client.newCall(similarMangaRequest(manga)).await()
            .parseAs<SimilarMangaResponse>()

        val ids = response.matches.map { it.id }

        val coverUrl = MdUtil.coverUrl.toHttpUrl().newBuilder().apply {
            ids.forEach { mangaId ->
                addQueryParameter("manga[]", mangaId)
            }
            addQueryParameter("limit", ids.size.toString())
        }.build().toString()
        val coverListResponse = client.newCall(GET(coverUrl)).await()
            .parseAs<CoverListResponse>()

        val unique = coverListResponse.results.distinctBy { it.relationships[0].id }

        val coverMap = unique.map { coverResponse ->
            val fileName = coverResponse.data.attributes.fileName
            val mangaId = coverResponse.relationships.first { it.type.equals("manga", true) }.id
            val thumbnailUrl = "${MdUtil.cdnUrl}/covers/$mangaId/$fileName"
            mangaId to thumbnailUrl
        }.toMap()

        return similarMangaParse(response, coverMap)
    }

    private fun similarMangaRequest(manga: MangaInfo): Request {
        val tempUrl = MdUtil.similarBaseApi + MdUtil.getMangaId(manga.key) + ".json"
        return GET(tempUrl, Headers.Builder().build(), CacheControl.FORCE_NETWORK)
    }

    private fun similarMangaParse(response: SimilarMangaResponse, coverMap: Map<String, String>): MangasPage {
        val mangaList = response.matches.map {
            SManga.create().apply {
                url = MdUtil.buildMangaUrl(it.id)
                title = MdUtil.cleanString(it.title[lang] ?: it.title["en"]!!)
                thumbnail_url = coverMap[it.id]
            }
        }
        return MangasPage(mangaList, false)
    }
}
