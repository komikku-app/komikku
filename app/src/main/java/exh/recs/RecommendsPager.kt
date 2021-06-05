package exh.recs

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.browse.source.browse.NoResultsException
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.log.maybeInjectEHLogger
import exh.util.MangaType
import exh.util.mangaType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

abstract class API(_endpoint: String) {
    var endpoint: String = _endpoint
    val client = OkHttpClient.Builder()
        .maybeInjectEHLogger()
        .build()

    abstract suspend fun getRecsBySearch(search: String): List<SManga>
}

class MyAnimeList : API("https://api.jikan.moe/v3/") {
    private suspend fun getRecsById(id: String): List<SManga> {
        val httpUrl = endpoint.toHttpUrlOrNull() ?: throw Exception("Could not convert endpoint url")
        val apiUrl = httpUrl.newBuilder()
            .addPathSegment("manga")
            .addPathSegment(id)
            .addPathSegment("recommendations")
            .build()
            .toString()

        val response = client.newCall(GET(apiUrl)).await()
        val body = withIOContext { response.body?.string() } ?: throw Exception("Null Response")
        val data = Json.decodeFromString<JsonObject>(body)
        val recommendations = data["recommendations"] as? JsonArray
        return recommendations?.filterIsInstance<JsonObject>()?.map { rec ->
            Timber.tag("RECOMMENDATIONS").d("MYANIMELIST > RECOMMENDATION: %s", rec["title"]?.jsonPrimitive?.content.orEmpty())
            SManga.create().apply {
                title = rec["title"]!!.jsonPrimitive.content
                thumbnail_url = rec["image_url"]!!.jsonPrimitive.content
                initialized = true
                url = rec["url"]!!.jsonPrimitive.content
            }
        }.orEmpty()
    }

    override suspend fun getRecsBySearch(search: String): List<SManga> {
        val httpUrl = endpoint.toHttpUrlOrNull() ?: throw Exception("Could not convert endpoint url")
        val url = httpUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment("manga")
            .addQueryParameter("q", search)
            .build()
            .toString()

        val response = client.newCall(GET(url)).await()
        val body = withIOContext { response.body?.string() } ?: throw Exception("Null Response")
        val data = Json.decodeFromString<JsonObject>(body)
        val results = data["results"] as? JsonArray
        if (results.isNullOrEmpty()) {
            throw Exception("'$search' not found")
        }
        val result = results.first().jsonObject
        val id = result["mal_id"]!!.jsonPrimitive.content
        return getRecsById(id)
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
            |""".trimMargin()
        val variables = buildJsonObject {
            put("search", search)
        }
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val payloadBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val response = client.newCall(POST(endpoint, body = payloadBody)).await()
        val body = withIOContext { response.body?.string() } ?: throw Exception("Null Response")
        val data = Json.decodeFromString<JsonObject>(body)["data"] as? JsonObject ?: throw Exception("Unexpected response")

        val media = data["Page"]?.jsonObject?.get("media")?.jsonArray
        if (media.isNullOrEmpty()) {
            throw Exception("'$search' not found")
        }
        val result = media.sortedWith(
            compareBy(
                { languageContains(it.jsonObject, "romaji", search) },
                { languageContains(it.jsonObject, "english", search) },
                { languageContains(it.jsonObject, "native", search) },
                { countOccurrence(it.jsonObject["synonyms"]!!.jsonArray, search) > 0 }
            )
        ).last().jsonObject

        return result["recommendations"]?.jsonObject?.get("edges")?.jsonArray?.map {
            val rec = it.jsonObject["node"]!!.jsonObject["mediaRecommendation"]!!.jsonObject
            val recTitle = getTitle(rec)
            Timber.tag("RECOMMENDATIONS").d("ANILIST > RECOMMENDATION: %s", recTitle)
            SManga.create().apply {
                title = recTitle
                thumbnail_url = rec["coverImage"]!!.jsonObject["large"]!!.jsonPrimitive.content
                initialized = true
                url = rec["siteUrl"]!!.jsonPrimitive.content
            }
        }.orEmpty()
    }
}

open class RecommendsPager(
    private val manga: Manga,
    private val smart: Boolean = true,
    private var preferredApi: API = API.MYANIMELIST
) : Pager() {
    override suspend fun requestNextPage() {
        if (smart) preferredApi = if (manga.mangaType() != MangaType.TYPE_MANGA) API.ANILIST else preferredApi

        val apiList = API_MAP.toList().sortedByDescending { it.first == preferredApi }

        val recs = apiList.firstNotNullOfOrNull { (key, api) ->
            try {
                val recs = api.getRecsBySearch(manga.originalTitle)
                Timber.tag("RECOMMENDATIONS").d("%s > Results: %s", key, recs.count())
                recs
            } catch (e: Exception) {
                Timber.tag("RECOMMENDATIONS").e("%s > Error: %s", key, e.message)
                null
            }
        }.orEmpty()

        val mangasPage = MangasPage(recs, false)

        if (mangasPage.mangas.isNotEmpty()) {
            onPageReceived(mangasPage)
        } else {
            throw NoResultsException()
        }
    }

    companion object {
        val API_MAP = mapOf(
            API.MYANIMELIST to MyAnimeList(),
            API.ANILIST to Anilist()
        )

        enum class API { MYANIMELIST, ANILIST }
    }
}
