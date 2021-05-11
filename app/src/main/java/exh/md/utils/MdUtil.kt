package exh.md.utils

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.log.xLogD
import exh.md.handlers.serializers.AtHomeResponse
import exh.md.handlers.serializers.ListCallResponse
import exh.md.handlers.serializers.LoginBodyToken
import exh.md.handlers.serializers.MangaResponse
import exh.md.network.NoSessionException
import exh.source.getMainSource
import exh.util.floor
import exh.util.nullIfBlank
import exh.util.nullIfZero
import exh.util.under
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.parser.Parser
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MdUtil {

    companion object {
        const val cdnUrl = "https://mangadex.org" // "https://s0.mangadex.org"
        const val baseUrl = "https://mangadex.org"
        const val apiUrl = "https://api.mangadex.org"
        const val apiUrlCdnCache = "https://cdn.statically.io/gh/goldbattle/MangadexRecomendations/master/output/api/"
        const val apiUrlCache = "https://raw.githubusercontent.com/goldbattle/MangadexRecomendations/master/output/api/"
        const val imageUrlCacheNotFound = "https://cdn.statically.io/img/raw.githubusercontent.com/CarlosEsco/Neko/master/.github/manga_cover_not_found.png"
        const val atHomeUrl = "$apiUrl/at-home/server"
        const val chapterUrl = "$apiUrl/chapter/"
        const val chapterSuffix = "/chapter/"
        const val checkTokenUrl = "$apiUrl/auth/check"
        const val refreshTokenUrl = "$apiUrl/auth/refresh"
        const val loginUrl = "$apiUrl/auth/login"
        const val logoutUrl = "$apiUrl/auth/logout"
        const val groupUrl = "$apiUrl/group"
        const val authorUrl = "$apiUrl/author"
        const val randomMangaUrl = "$apiUrl/manga/random"
        const val mangaUrl = "$apiUrl/manga"
        const val mangaStatus = "$apiUrl/manga/status"
        const val userFollows = "$apiUrl/user/follows/manga"
        fun updateReadingStatusUrl(id: String) = "$apiUrl/manga/$id/status"

        fun mangaFeedUrl(id: String, offset: Int, language: String): String {
            return "$mangaUrl/$id/feed".toHttpUrl().newBuilder().apply {
                addQueryParameter("limit", "500")
                addQueryParameter("offset", offset.toString())
                addQueryParameter("locales[]", language)
                addQueryParameter("order[volume]", "desc")
                addQueryParameter("order[chapter]", "desc")
            }.build().toString()
        }

        const val groupSearchUrl = "$baseUrl/groups/0/1/"
        const val apiCovers = "/covers"
        const val reportUrl = "https://api.mangadex.network/report"

        const val mdAtHomeTokenLifespan = 10 * 60 * 1000
        const val mangaLimit = 25

        /**
         * Get the manga offset pages are 1 based, so subtract 1
         */
        fun getMangaListOffset(page: Int): String = (mangaLimit * (page - 1)).toString()

        val jsonParser =
            Json {
                isLenient = true
                ignoreUnknownKeys = true
                allowSpecialFloatingPointValues = true
                useArrayPolymorphism = true
                prettyPrint = true
            }

        private const val scanlatorSeparator = " & "

        val validOneShotFinalChapters = listOf("0", "1")

        val englishDescriptionTags = listOf(
            "[b][u]English:",
            "[b][u]English",
            "English:",
            "English :",
            "[English]:",
            "English Translaton:",
            "[B][ENG][/B]"
        )

        val bbCodeToRemove = listOf(
            "list", "*", "hr", "u", "b", "i", "s", "center", "spoiler="
        )
        val descriptionLanguages = listOf(
            "=FRANCAIS=",
            "[b] Spanish: [/ b]",
            "[b][u]Chinese",
            "[b][u]French",
            "[b][u]German / Deutsch",
            "[b][u]Russian",
            "[b][u]Spanish",
            "[b][u]Vietnamese",
            "[b]External Links",
            "[b]Link[/b]",
            "[b]Links:",
            "[Espa&ntilde;ol]:",
            "[hr]Fr:",
            "[hr]TH",
            "[INDO]",
            "[PTBR]",
            "[right][b][u]Persian",
            "[RUS]",
            "[u]Russian",
            "\r\n\r\nItalian\r\n",
            "Arabic /",
            "Descriptions in Other Languages",
            "Espanol",
            "[Espa&ntilde;",
            "Espa&ntilde;",
            "Farsi/",
            "Fran&ccedil;ais",
            "French - ",
            "Francois",
            "French:",
            "French/",
            "French /",
            "German/",
            "German /",
            "Hindi /",
            "Bahasa Indonesia",
            "Indonesia:",
            "Indonesian:",
            "Indonesian :",
            "Indo:",
            "[u]Indonesian",
            "Italian / ",
            "Italian Summary:",
            "Italian/",
            "Italiano",
            "Italian:",
            "Italian summary:",
            "Japanese /",
            "Original Japanese",
            "Official Japanese Translation",
            "Official Chinese Translation",
            "Official French Translation",
            "Official Indonesian Translation",
            "Links:",
            "Pasta-Pizza-Mandolino/Italiano",
            "Persian/فارسی",
            "Persian /فارسی",
            "Polish /",
            "Polish Summary /",
            "Polish/",
            "Polski",
            "Portugu&ecirc;s",
            "Portuguese (BR)",
            "PT/BR:",
            "Pt/Br:",
            "Pt-Br:",
            "Portuguese /",
            "[right]",
            "R&eacute;sum&eacute; Fran&ccedil;ais",
            "R&eacute;sume Fran&ccedil;ais",
            "R&Eacute;SUM&Eacute; FRANCAIS :",
            "RUS:",
            "Ru/Pyc",
            "\\r\\nRUS\\r\\n",
            "Russia/",
            "Russian /",
            "Spanish:",
            "Spanish /",
            "Spanish Summary:",
            "Spanish/",
            "T&uuml;rk&ccedil;e",
            "Thai:",
            "Turkish /",
            "Turkish/",
            "Turkish:",
            "Русский",
            "العربية",
            "정보",
            "(zh-Hant)",
        )

        // guess the thumbnail url is .jpg  this has a ~80% success rate
        fun formThumbUrl(mangaUrl: String, lowQuality: Boolean): String {
            var ext = ".jpg"
            if (lowQuality) {
                ext = ".thumb$ext"
            }
            return cdnUrl + "/images/manga/" + getMangaId(mangaUrl) + ext
        }

        // Get the ID from the manga url
        fun getMangaId(url: String): String = url.trimEnd('/').substringAfterLast("/")

        fun getChapterId(url: String) = url.substringAfterLast("/")

        fun cleanString(string: String): String {
            var cleanedString = string

            bbCodeToRemove.forEach {
                cleanedString = cleanedString.replace("[$it]", "", true)
                    .replace("[/$it]", "", true)
            }

            val bbRegex =
                """\[(\w+)[^]]*](.*?)\[/\1]""".toRegex()

            // Recursively remove nested bbcode
            while (bbRegex.containsMatchIn(cleanedString)) {
                cleanedString = cleanedString.replace(bbRegex, "$2")
            }

            return Parser.unescapeEntities(cleanedString, false)
        }

        fun cleanDescription(string: String): String {
            var newDescription = string
            descriptionLanguages.forEach {
                newDescription = newDescription.substringBefore(it)
            }

            englishDescriptionTags.forEach {
                newDescription = newDescription.replace(it, "")
            }
            return cleanString(newDescription).trim()
        }

        fun getImageUrl(attr: String): String {
            // Some images are hosted elsewhere
            if (attr.startsWith("http")) {
                return attr
            }
            return baseUrl + attr
        }

        fun getScanlators(scanlators: String?): List<String> {
            if (scanlators.isNullOrBlank()) return emptyList()
            return scanlators.split(scanlatorSeparator).distinct()
        }

        fun getScanlatorString(scanlators: Set<String>): String {
            return scanlators.toList().sorted().joinToString(scanlatorSeparator)
        }

        fun getMissingChapterCount(chapters: List<SChapter>, mangaStatus: Int): String? {
            if (mangaStatus == SManga.COMPLETED) return null

            val remove0ChaptersFromCount = chapters.distinctBy {
                /*if (it.chapter_txt.isNotEmpty()) {
                    it.vol + it.chapter_txt
                } else {*/
                it.name
                /*}*/
            }.sortedByDescending { it.chapter_number }

            remove0ChaptersFromCount.firstOrNull()?.let { chapter ->
                val chpNumber = chapter.chapter_number.floor()
                val allChapters = (1..chpNumber).toMutableSet()

                remove0ChaptersFromCount.forEach {
                    allChapters.remove(it.chapter_number.floor())
                }

                if (allChapters.isEmpty()) return null
                return allChapters.size.toString()
            }
            return null
        }

        fun atHomeUrlHostUrl(requestUrl: String, client: OkHttpClient): String {
            val atHomeRequest = GET(requestUrl)
            val atHomeResponse = client.newCall(atHomeRequest).execute()
            return atHomeResponse.parseAs<AtHomeResponse>(jsonParser).baseUrl
        }

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun parseDate(dateAsString: String): Long =
            dateFormatter.parse(dateAsString)?.time ?: 0

        fun createMangaEntry(json: MangaResponse, lang: String, lowQualityCovers: Boolean): MangaInfo {
            val key = "/manga/" + json.data.id
            return MangaInfo(
                key = key,
                title = cleanString(json.data.attributes.title[lang] ?: json.data.attributes.title["en"]!!),
                cover = formThumbUrl(key, lowQualityCovers)
            )
        }

        fun sessionToken(preferences: PreferencesHelper, mdList: MdList) = preferences.trackToken(mdList).get().nullIfBlank()?.let {
            try {
                jsonParser.decodeFromString<LoginBodyToken>(it)
            } catch (e: SerializationException) {
                xLogD("Unable to load session token")
                null
            }
        }?.session

        fun refreshToken(preferences: PreferencesHelper, mdList: MdList) = preferences.trackToken(mdList).get().nullIfBlank()?.let {
            try {
                jsonParser.decodeFromString<LoginBodyToken>(it)
            } catch (e: SerializationException) {
                xLogD("Unable to load session token")
                null
            }
        }?.refresh

        fun updateLoginToken(token: LoginBodyToken, preferences: PreferencesHelper, mdList: MdList) {
            preferences.trackToken(mdList).set(jsonParser.encodeToString(token))
        }

        fun getAuthHeaders(headers: Headers, preferences: PreferencesHelper, mdList: MdList) =
            headers.newBuilder().add("Authorization", "Bearer ${sessionToken(preferences, mdList) ?: throw NoSessionException()}").build()

        fun getEnabledMangaDex(preferences: PreferencesHelper, sourceManager: SourceManager = Injekt.get()): MangaDex? {
            return getEnabledMangaDexs(preferences, sourceManager).let { mangadexs ->
                preferences.preferredMangaDexId().get().toLongOrNull()?.nullIfZero()
                    ?.let { preferredMangaDexId ->
                        mangadexs.firstOrNull { it.id == preferredMangaDexId }
                    }
                    ?: mangadexs.firstOrNull()
            }
        }

        fun getEnabledMangaDexs(preferences: PreferencesHelper, sourceManager: SourceManager = Injekt.get()): List<MangaDex> {
            val languages = preferences.enabledLanguages().get()
            val disabledSourceIds = preferences.disabledSources().get()

            return sourceManager.getVisibleOnlineSources()
                .map { it.getMainSource() }
                .filterIsInstance<MangaDex>()
                .filter { it.lang in languages }
                .filterNot { it.id.toString() in disabledSourceIds }
        }
    }
}

suspend inline fun <reified T> OkHttpClient.mdListCall(request: (offset: Int) -> Request): List<T> {
    val results = mutableListOf<T>()
    var offset = 0

    do {
        val response = newCall(request(offset)).await()
        if (response.code == 204) {
            break
        }
        val mangaListResponse = response.parseAs<ListCallResponse<T>>(MdUtil.jsonParser)
        results += mangaListResponse.results
        offset += mangaListResponse.limit
    } while (offset under mangaListResponse.total)

    return results
}
