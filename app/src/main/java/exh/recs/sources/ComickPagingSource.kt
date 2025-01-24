package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

fun CatalogueSource.isComickSource() = name == "Comick"

internal class ComickPagingSource(
    manga: Manga,
    // KMK -->
    private val sourceCatalogue: SourceCatalogue,
    // KMK <--
) : RecommendationPagingSource(
    // KMK -->
    sourceCatalogue.source,
    // KMK <--
    manga,
) {

    override val name: String
        get() = "Comick"

    override val category: StringResource
        get() = SYMR.strings.community_recommendations

    override val associatedSourceId: Long
        // KMK -->
        get() = sourceCatalogue.sourceId
    // KMK <--

    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json by injectLazy<Json>()
    private val thumbnailBaseUrl = "https://meo.comick.pictures/"

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val mangasPage = coroutineScope {
            val headers = Headers.Builder().apply {
                add("Referer", "api.comick.fun/")
                add("User-Agent", "Tachiyomi ${System.getProperty("http.agent")}")
            }

            // Comick extension populates the URL field with: '/comic/{hid}#'
            val url = "https://api.comick.fun/v1.0${manga.url}".toHttpUrl()
                .newBuilder()
                .addQueryParameter("tachiyomi", "true")
                .build()

            val request = GET(url, headers.build())

            val data = with(json) {
                client.newCall(request).awaitSuccess()
                    .parseAs<JsonObject>()
            }

            val recs = data["comic"]!!
                .jsonObject["recommendations"]!!
                .jsonArray
                .map { it.jsonObject["relates"]!! }
                .map { it.jsonObject }

            MangasPage(
                recs.map { rec ->
                    SManga(
                        title = rec["title"]!!.jsonPrimitive.content,
                        url = "/comic/${rec["hid"]!!.jsonPrimitive.content}#",
                        thumbnail_url = thumbnailBaseUrl + rec["md_covers"]!!
                            .jsonArray
                            .map { it.jsonObject["b2key"]!!.jsonPrimitive.content }
                            .first(),
                        // Mark as uninitialized to force fetching missing metadata
                        initialized = false,
                    )
                },
                false,
            )
        }

        return mangasPage.takeIf { it.mangas.isNotEmpty() } ?: throw NoResultsException()
    }
}
