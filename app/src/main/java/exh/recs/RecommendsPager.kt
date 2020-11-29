package exh.recs

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.ui.browse.source.browse.NoResultsException
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager
import eu.kanade.tachiyomi.util.lang.asObservable
import exh.log.maybeInjectEHLogger
import exh.util.MangaType
import exh.util.mangaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber

abstract class API(_endpoint: String) {
    var endpoint: String = _endpoint
    val client = OkHttpClient.Builder()
        .maybeInjectEHLogger()
        .build()
    val scope = CoroutineScope(Job() + Dispatchers.Default)

    abstract suspend fun getRecsBySearch(search: String): List<SMangaImpl>
}

class MyAnimeList : API("https://api.jikan.moe/v3/") {
    private suspend fun getRecsById(id: String): List<SMangaImpl> {
        val httpUrl = endpoint.toHttpUrlOrNull()
        if (httpUrl == null) {
            throw Exception("Could not convert endpoint url")
        }
        val urlBuilder = httpUrl.newBuilder()
        urlBuilder.addPathSegment("manga")
        urlBuilder.addPathSegment(id)
        urlBuilder.addPathSegment("recommendations")
        val url = urlBuilder.build().toUrl()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).await()
        val body = response.body?.string().orEmpty()
        if (body.isEmpty()) {
            throw Exception("Null Response")
        }
        val data = Json.decodeFromString<JsonObject>(body)
        val recommendations = data["recommendations"] as? JsonArray
            ?: throw Exception("Unexpected response")
        val recs = recommendations.map { rec ->
            rec as? JsonObject ?: throw Exception("Invalid json")
            Timber.tag("RECOMMENDATIONS").d("MYANIMELIST > RECOMMENDATION: %s", rec["title"]!!.jsonPrimitive.content)
            SMangaImpl().apply {
                this.title = rec["title"]!!.jsonPrimitive.content
                this.thumbnail_url = rec["image_url"]!!.jsonPrimitive.content
                this.initialized = true
                this.url = rec["url"]!!.jsonPrimitive.content
            }
        }
        return recs
    }

    override suspend fun getRecsBySearch(search: String): List<SMangaImpl> {
        val httpUrl =
            endpoint.toHttpUrlOrNull()
        if (httpUrl == null) {
            throw Exception("Could not convert endpoint url")
        }
        val urlBuilder = httpUrl.newBuilder()
        urlBuilder.addPathSegment("search")
        urlBuilder.addPathSegment("manga")
        urlBuilder.addQueryParameter("q", search)
        val url = urlBuilder.build().toUrl()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).await()
        val body = response.body?.string().orEmpty()
        if (body.isEmpty()) {
            throw Exception("Null Response")
        }
        val data = Json.decodeFromString<JsonObject>(body)
        val results = data["results"] as? JsonArray ?: throw Exception("Unexpected response")
        if (results.size <= 0) {
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
        return obj["title"]!!.jsonObject.let {
            it["romaji"]?.jsonPrimitive?.content
                ?: it["english"]?.jsonPrimitive?.content
                ?: it["native"]!!.jsonPrimitive.content
        }
    }

    override suspend fun getRecsBySearch(search: String): List<SMangaImpl> {
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
                                        |siteUrl
                                        |title {
                                            |romaji
                                            |english
                                            |native
                                        |}
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
        val payloadBody =
            payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(endpoint)
            .post(payloadBody)
            .build()

        val response = client.newCall(request).await()
        val body = response.body?.string().orEmpty()
        if (body.isEmpty()) {
            throw Exception("Null Response")
        }
        val data = Json.decodeFromString<JsonObject>(body)["data"] as? JsonObject
            ?: throw Exception("Unexpected response")
        val page = data["Page"]!!.jsonObject
        val media = page["media"]!!.jsonArray
        if (media.size <= 0) {
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
        val recommendations = result["recommendations"]!!.jsonObject["edges"]!!.jsonArray
        val recs = recommendations.map {
            val rec = it.jsonObject["node"]!!.jsonObject["mediaRecommendation"]!!.jsonObject
            Timber.tag("RECOMMENDATIONS").d("ANILIST > RECOMMENDATION: %s", getTitle(rec))
            SMangaImpl().apply {
                this.title = getTitle(rec)
                this.thumbnail_url = rec["coverImage"]!!.jsonObject["large"]!!.jsonPrimitive.content
                this.initialized = true
                this.url = rec["siteUrl"]!!.jsonPrimitive.content
            }
        }
        return recs
    }
}

open class RecommendsPager(
    private val manga: Manga,
    private val smart: Boolean = true,
    private var preferredApi: API = API.MYANIMELIST
) : Pager() {
    override fun requestNext(): Observable<MangasPage> {
        return flow {
            if (smart) preferredApi = if (manga.mangaType() != MangaType.TYPE_MANGA) API.ANILIST else preferredApi

            val apiList = mapOf(preferredApi to API_MAP[preferredApi]!!) + API_MAP.filter { it.key != preferredApi }.toList()

            val recs = supervisorScope {
                apiList
                    .asSequence()
                    .map { (key, api) ->
                        async(Dispatchers.Default) {
                            try {
                                val recs = api.getRecsBySearch(manga.originalTitle).orEmpty()
                                Timber.tag("RECOMMENDATIONS").d("%s > Results: %s", key, recs.count())
                                recs
                            } catch (e: Exception) {
                                Timber.tag("RECOMMENDATIONS").e("%s > Error: %s", key, e.message)
                                listOf()
                            }
                        }
                    }
                    .firstOrNull { it.await().isNotEmpty() }
                    ?.await().orEmpty()
            }

            val page = MangasPage(recs, false)
            emit(page)
        }
            .asObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                if (it.mangas.isNotEmpty()) {
                    onPageReceived(it)
                } else {
                    throw NoResultsException()
                }
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
