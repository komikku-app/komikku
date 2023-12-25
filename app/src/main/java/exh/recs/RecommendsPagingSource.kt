package exh.recs

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.util.MangaType
import exh.util.mangaType
import kotlinx.serialization.json.Json
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
import tachiyomi.core.util.system.logcat
import tachiyomi.data.source.NoResultsException
import tachiyomi.data.source.SourcePagingSource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

abstract class API(val endpoint: String) {
    val client by lazy {
        Injekt.get<NetworkHelper>().client
    }
    val json by injectLazy<Json>()

    abstract suspend fun getRecsBySearch(search: String): List<SManga>

    abstract suspend fun getRecsById(id: String): List<SManga>
}

class MyAnimeList : API("https://api.jikan.moe/v4/") {
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

class Anilist : API("https://graphql.anilist.co/") {
    private fun countOccurrence(arr: JsonArray, search: String): Int {
        return arr.count {
            val synonym = it.jsonPrimitive.content
            synonym.contains(search, true)
        }
    }

    private fun languageContains(obj: JsonObject, language: String, search: String): Boolean {
        return obj["title"]?.jsonObject?.get(language)?.jsonPrimitive?.contentOrNull?.contains(search, true) == true
    }

    private fun getTitle(obj: JsonObject): String {
        val titleObj = obj["title"]!!.jsonObject

        val english = titleObj["english"]?.jsonPrimitive?.contentOrNull
        val romaji = titleObj["romaji"]?.jsonPrimitive?.contentOrNull
        val native = titleObj["native"]?.jsonPrimitive?.contentOrNull
        val synonym = obj["synonyms"]!!.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull

        val isJP = obj["countryOfOrigin"]?.jsonPrimitive?.contentOrNull == "JP"

        return when {
            !english.isNullOrBlank() -> english
            isJP && !romaji.isNullOrBlank() -> romaji
            !synonym.isNullOrBlank() -> synonym
            !isJP && !romaji.isNullOrBlank() -> romaji
            else -> native ?: "NO NAME FOUND"
        }
    }

    private suspend fun getRecs(
        query: String,
        variables: JsonObject,
        queryParam: String? = null,
        filter: List<JsonElement>.() -> List<JsonElement> = { this },
    ): List<SManga> {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val payloadBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val data = with(json) {
            client.newCall(POST(endpoint, body = payloadBody)).awaitSuccess()
                .parseAs<JsonObject>()
        }

        val media = data["data"]!!
            .jsonObject["Page"]!!
            .jsonObject["media"]!!
            .jsonArray
            .ifEmpty { throw Exception("'$queryParam' not found") }
            .filter()

        return media.flatMap { it.jsonObject["recommendations"]!!.jsonObject["edges"]!!.jsonArray }.map {
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

    override suspend fun getRecsById(id: String): List<SManga> {
        val query =
            """
            |query Recommendations(${'$'}id: Int!) {
                |Page {
                    |media(id: ${'$'}id, type: MANGA) {
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
            put("id", id)
        }

        return getRecs(
            query = query,
            variables = variables,
        )
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
        return getRecs(
            queryParam = search,
            query = query,
            variables = variables,
            filter = {
                filter {
                    val jsonObject = it.jsonObject
                    languageContains(jsonObject, "romaji", search) ||
                        languageContains(jsonObject, "english", search) ||
                        languageContains(jsonObject, "native", search) ||
                        countOccurrence(jsonObject["synonyms"]!!.jsonArray, search) > 0
                }
            },
        )
    }
}

open class RecommendsPagingSource(
    source: CatalogueSource,
    private val manga: Manga,
    private val smart: Boolean = true,
    private var preferredApi: API = API.MYANIMELIST,
) : SourcePagingSource(source) {
    val trackerManager: TrackerManager by injectLazy()
    val getTracks: GetTracks by injectLazy()

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        if (smart) preferredApi = if (manga.mangaType() != MangaType.TYPE_MANGA) API.ANILIST else preferredApi

        val apiList = API_MAP.toList().sortedByDescending { it.first == preferredApi }

        val tracks = getTracks.await(manga.id)

        val recs = apiList.firstNotNullOfOrNull { (key, api) ->
            try {
                val id = when (key) {
                    API.MYANIMELIST -> tracks.find { it.trackerId == trackerManager.myAnimeList.id }?.remoteId
                    API.ANILIST -> tracks.find { it.trackerId == trackerManager.aniList.id }?.remoteId
                }

                val recs = if (id != null) {
                    api.getRecsById(id.toString())
                } else {
                    api.getRecsBySearch(manga.ogTitle)
                }
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
