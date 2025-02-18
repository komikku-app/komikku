package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR

abstract class MangaUpdatesPagingSource(manga: Manga, source: CatalogueSource?) : TrackerRecommendationPagingSource(
    "https://api.mangaupdates.com/v1/",
    source,
    manga,
) {
    override val name: String
        get() = "MangaUpdates"

    override val associatedTrackerId: Long
        get() = trackerManager.mangaUpdates.id

    protected abstract val recommendationJsonObjectName: String

    override suspend fun getRecsById(id: String): List<SManga> {
        val apiUrl = endpoint.toHttpUrl()
            .newBuilder()
            .addPathSegment("series")
            .addPathSegment(id)
            .build()

        val data = with(json) { client.newCall(GET(apiUrl)).awaitSuccess().parseAs<JsonObject>() }
        return getRecommendations(data[recommendationJsonObjectName]!!.jsonArray)
    }

    private fun getRecommendations(recommendations: JsonArray): List<SManga> {
        return recommendations
            .map(JsonElement::jsonObject)
            .map { rec ->
                logcat { "MANGAUPDATES > RECOMMENDATION: " + rec["series_name"]!!.jsonPrimitive.content }
                SManga(
                    title = rec["series_name"]!!.jsonPrimitive.content,
                    url = rec["series_url"]!!.jsonPrimitive.content,
                    thumbnail_url = rec["series_image"]
                        ?.jsonObject
                        ?.get("url")
                        ?.jsonObject
                        ?.get("original")
                        ?.jsonPrimitive
                        ?.contentOrNull,
                    initialized = true,
                )
            }
    }

    override suspend fun getRecsBySearch(search: String): List<SManga> {
        val url = endpoint.toHttpUrl()
            .newBuilder()
            .addPathSegments("series/search")
            .build()
            .toString()

        val payload = buildJsonObject {
            put("search", search)
            put("stype", "title")
        }

        val body = payload
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val data = with(json) {
            client.newCall(POST(url, body = body))
                .awaitSuccess()
                .parseAs<JsonObject>()
        }
        return getRecsById(
            data["results"]!!
                .jsonArray
                .ifEmpty { throw Exception("'$search' not found") }
                .first()
                .jsonObject["record"]!!
                .jsonObject["series_id"]!!
                .jsonPrimitive.content,
        )
    }
}

class MangaUpdatesCommunityPagingSource(manga: Manga, source: CatalogueSource?) : MangaUpdatesPagingSource(manga, source) {
    override val category: StringResource
        get() = SYMR.strings.community_recommendations
    override val recommendationJsonObjectName: String
        get() = "recommendations"
}

class MangaUpdatesSimilarPagingSource(manga: Manga, source: CatalogueSource?) : MangaUpdatesPagingSource(manga, source) {
    override val category: StringResource
        get() = SYMR.strings.similar_titles
    override val recommendationJsonObjectName: String
        get() = "category_recommendations"
}
