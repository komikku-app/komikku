package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR

class MyAnimeListPagingSource(manga: Manga, source: CatalogueSource?) : TrackerRecommendationPagingSource(
    "https://api.jikan.moe/v4/",
    source,
    manga,
) {
    override val name: String
        get() = "MyAnimeList"

    override val category: StringResource
        get() = SYMR.strings.community_recommendations

    override val associatedTrackerId: Long
        get() = trackerManager.myAnimeList.id

    override suspend fun getRecsById(id: String): List<SManga> {
        val apiUrl = endpoint.toHttpUrl()
            .newBuilder()
            .addPathSegment("manga")
            .addPathSegment(id)
            .addPathSegment("recommendations")
            .build()

        val data = with(json) { client.newCall(GET(apiUrl)).awaitSuccess().parseAs<JsonObject>() }
        return data["data"]!!.jsonArray
            .map { it.jsonObject["entry"]!!.jsonObject }
            .map { rec ->
                logcat { "MYANIMELIST > RECOMMENDATION: " + rec["title"]!!.jsonPrimitive.content }
                SManga(
                    title = rec["title"]!!.jsonPrimitive.content,
                    url = rec["url"]!!.jsonPrimitive.content,
                    thumbnail_url = rec["images"]
                        ?.let(JsonElement::jsonObject)
                        ?.let(::getImage),
                    initialized = true,
                )
            }
    }

    fun getImage(imageObject: JsonObject): String? {
        return imageObject["webp"]
            ?.jsonObject
            ?.get("image_url")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: imageObject["jpg"]
                ?.jsonObject
                ?.get("image_url")
                ?.jsonPrimitive
                ?.contentOrNull
    }

    override suspend fun getRecsBySearch(search: String): List<SManga> {
        val url = endpoint.toHttpUrl()
            .newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("q", search)
            .build()

        val data = with(json) {
            client.newCall(GET(url)).awaitSuccess()
                .parseAs<JsonObject>()
        }
        return getRecsById(data["data"]!!.jsonArray.first().jsonObject["mal_id"]!!.jsonPrimitive.content)
    }
}
