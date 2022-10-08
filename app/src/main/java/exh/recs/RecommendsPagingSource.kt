package exh.recs

import eu.kanade.data.source.NoResultsException
import eu.kanade.data.source.SourcePagingSource
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.logcat
import exh.util.MangaType
import exh.util.mangaType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class API(val endpoint: String) {
    val client by lazy {
        Injekt.get<NetworkHelper>().client
    }

    abstract suspend fun getRecsBySearch(search: String): List<SManga>
}

class MyAnimeList : API("https://api.jikan.moe/v4/") {
    private suspend fun getRecsById(id: String): List<SManga> {
        val apiUrl = endpoint.toHttpUrl()
            .newBuilder()
            .addPathSegment("manga")
            .addPathSegment(id)
            .addPathSegment("recommendations")
            .build()

        val data = client.newCall(GET(apiUrl)).await().parseAs<JsonObject>()
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

        val data = client.newCall(GET(url)).await()
            .parseAs<JsonObject>()
        return getRecsById(data["data"]!!.jsonArray.first().jsonObject["mal_id"]!!.jsonPrimitive.content)
    }
}

class Anilist : API("https://graphql.anilist.co/") {
    private fun countOccurrence(arr: JsonArray, search: String): Int {
        return arr.count {
            val synonym = it.jsonPrimitive.content
            synonym.contains(search, true)
        }
    }

    private fun languageContains(obj: JsonObject, language: String, search: String): Boolean {
        return obj["title"]?.jsonObject?.get(language)?.jsonPrimitive?.content?.contains(search, true) == true
    }

    private fun getTitle(obj: JsonObject): String {
        val titleObj = obj["title"]!!.jsonObject

        val english = titleObj["english"]?.jsonPrimitive?.contentOrNull
        val romaji = titleObj["romaji"]?.jsonPrimitive?.contentOrNull
        val native = titleObj["native"]?.jsonPrimitive?.contentOrNull
        val synonym = obj["synonyms"]!!.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull

        val isJP = obj["countryOfOrigin"]!!.jsonPrimitive.content == "JP"

        return when {
            !english.isNullOrBlank() -> english
            isJP && !romaji.isNullOrBlank() -> romaji
            !synonym.isNullOrBlank() -> synonym
            !isJP && !romaji.isNullOrBlank() -> romaji
            else -> native ?: "NO NAME FOUND"
        }
    }

    override suspend fun getRecsBySearch(search: String): List<SManga> {
        val query =
            """
            |query Recommendations(${'$'}search: String!) {
                |Page {
                    |media(search: ${'$'}search, type: MANGA) {
                        |title {
                            |romaji
                            |english
                            |native
                        |}
                        |synonyms
                        |recommendations {
                            |edges {
                                |node {
                                    |mediaRecommendation {
                                        |countryOfOrigin
                                        |siteUrl
                                        |title {
                                            |romaji
                                            |english
                                            |native
                                        |}
                                        |synonyms
                                        |coverImage {
                                            |large
                                        |}
                                    |}
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
        val variables = buildJsonObject {
            put("search", search)
        }
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val payloadBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val data = client.newCall(POST(endpoint, body = payloadBody)).await()
            .parseAs<JsonObject>()

        val media = data["data"]!!
            .jsonObject["Page"]!!
            .jsonObject["media"]!!
            .jsonArray
            .ifEmpty { throw Exception("'$search' not found") }

        val result = media.sortedWith(
            compareBy(
                { languageContains(it.jsonObject, "romaji", search) },
                { languageContains(it.jsonObject, "english", search) },
                { languageContains(it.jsonObject, "native", search) },
                { countOccurrence(it.jsonObject["synonyms"]!!.jsonArray, search) > 0 },
            ),
        ).last().jsonObject

        return result["recommendations"]!!.jsonObject["edges"]!!.jsonArray.map {
            val rec = it.jsonObject["node"]!!.jsonObject["mediaRecommendation"]!!.jsonObject
            val recTitle = getTitle(rec)
            logcat { "ANILIST > RECOMMENDATION: $recTitle" }
            SManga(
                title = recTitle,
                thumbnail_url = rec["coverImage"]!!.jsonObject["large"]!!.jsonPrimitive.content,
                initialized = true,
                url = rec["siteUrl"]!!.jsonPrimitive.content,
            )
        }
    }
}

open class RecommendsPagingSource(
    source: CatalogueSource,
    private val manga: Manga,
    private val smart: Boolean = true,
    private var preferredApi: API = API.MYANIMELIST,
) : SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        if (smart) preferredApi = if (manga.mangaType() != MangaType.TYPE_MANGA) API.ANILIST else preferredApi

        val apiList = API_MAP.toList().sortedByDescending { it.first == preferredApi }

        val recs = apiList.firstNotNullOfOrNull { (key, api) ->
            try {
                val recs = api.getRecsBySearch(manga.ogTitle)
                logcat { key.toString() + " > Results: " + recs.size }
                recs.ifEmpty { null }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { key.toString() }
                null
            }
        } ?: throw NoResultsException()

        return MangasPage(recs, false)
    }

    companion object {
        val API_MAP = mapOf(
            API.MYANIMELIST to MyAnimeList(),
            API.ANILIST to Anilist(),
        )

        enum class API { MYANIMELIST, ANILIST }
    }
}
