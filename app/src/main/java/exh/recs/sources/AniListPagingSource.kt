package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR

class AniListPagingSource(manga: Manga) : TrackerRecommendationPagingSource(
    "https://graphql.anilist.co/",
    manga,
) {
    override val name: String
        get() = "AniList"

    override val category: StringResource
        get() = SYMR.strings.community_recommendations

    override val associatedTrackerId: Long
        get() = trackerManager.aniList.id

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
            .ifEmpty { throw NoResultsException() }
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
