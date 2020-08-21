package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.handlers.serializers.CoversResult
import exh.md.utils.MdUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient

// Unused, look into what its used for todo
class CoverHandler(val client: OkHttpClient, val headers: Headers) {

    suspend fun getCovers(manga: SManga): List<String> {
        return withContext(Dispatchers.IO) {
            val response = client.newCall(GET("${MdUtil.baseUrl}${MdUtil.coversApi}${MdUtil.getMangaId(manga.url)}", headers, CacheControl.FORCE_NETWORK)).execute()
            val result = MdUtil.jsonParser.decodeFromString(
                CoversResult.serializer(),
                response.body!!.string()
            )
            result.covers.map { "${MdUtil.baseUrl}$it" }
        }
    }
}
