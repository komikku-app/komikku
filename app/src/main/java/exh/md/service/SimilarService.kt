package exh.md.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import exh.md.dto.SimilarMangaDto
import exh.md.utils.MdUtil
import okhttp3.OkHttpClient

class SimilarService(
    private val client: OkHttpClient,
) {
    suspend fun getSimilarManga(mangaId: String): SimilarMangaDto {
        return client.newCall(
            GET(
                "${MdUtil.similarBaseApi}$mangaId.json",
            ),
        ).await().parseAs()
    }
}
