package exh.md.handlers

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.handlers.serializers.SimilarMangaResponse
import exh.md.utils.MdUtil
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tachiyomi.source.model.MangaInfo

class SimilarHandler(val client: OkHttpClient, val lang: String, val preferences: PreferencesHelper, private val useLowQualityCovers: Boolean) {

    suspend fun getSimilar(manga: MangaInfo): MangasPage {
        val response = client.newCall(similarMangaRequest(manga)).await()
        return similarMangaParse(response)
    }

    private fun similarMangaRequest(manga: MangaInfo): Request {
        val tempUrl = MdUtil.similarBaseApi + MdUtil.getMangaId(manga.key) + ".json"
        return GET(tempUrl, Headers.Builder().build(), CacheControl.FORCE_NETWORK)
    }

    private fun similarMangaParse(response: Response): MangasPage {
        val mangaList = response.parseAs<SimilarMangaResponse>().matches.map {
            SManga.create().apply {
                url = "/manga/" + it.id
                title = MdUtil.cleanString(it.title[lang] ?: it.title["en"]!!)
                thumbnail_url = "https://coverapi.orell.dev/api/v1/mdaltimage/manga/${it.id}/cover"
            }
        }
        return MangasPage(mangaList, false)
    }
}
