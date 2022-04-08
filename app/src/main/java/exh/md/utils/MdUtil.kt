package exh.md.utils

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.log.xLogD
import exh.md.dto.LoginBodyTokenDto
import exh.md.dto.MangaAttributesDto
import exh.md.dto.MangaDataDto
import exh.md.network.NoSessionException
import exh.source.getMainSource
import exh.util.dropBlank
import exh.util.floor
import exh.util.nullIfBlank
import exh.util.nullIfZero
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.parser.Parser
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MdUtil {

    companion object {
        const val cdnUrl = "https://uploads.mangadex.org"
        const val baseUrl = "https://mangadex.org"
        const val chapterSuffix = "/chapter/"

        const val similarCacheMapping = "https://api.similarmanga.com/mapping/mdex2search.csv"
        const val similarCacheMangas = "https://api.similarmanga.com/manga/"
        const val similarBaseApi = "https://api.similarmanga.com/similar/"

        const val groupSearchUrl = "$baseUrl/groups/0/1/"
        const val reportUrl = "https://api.mangadex.network/report"

        const val mdAtHomeTokenLifespan = 10 * 60 * 1000
        const val mangaLimit = 20

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

        const val contentRatingSafe = "safe"
        const val contentRatingSuggestive = "suggestive"
        const val contentRatingErotica = "erotica"
        const val contentRatingPornographic = "pornographic"

        val validOneShotFinalChapters = listOf("0", "1")

        val englishDescriptionTags = listOf(
            "[b][u]English:",
            "[b][u]English",
            "English:",
            "English :",
            "[English]:",
            "English Translaton:",
            "[B][ENG][/B]",
        )

        val bbCodeToRemove = listOf(
            "list", "*", "hr", "u", "b", "i", "s", "center", "spoiler=",
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

        fun buildMangaUrl(mangaUuid: String): String {
            return "/manga/$mangaUuid"
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

        fun getScanlators(scanlators: String?): Set<String> {
            return scanlators?.split(scanlatorSeparator)?.dropBlank()?.toSet().orEmpty()
        }

        fun getScanlatorString(scanlators: Set<String>): String {
            return scanlators.sorted().joinToString(scanlatorSeparator)
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

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun parseDate(dateAsString: String): Long =
            dateFormatter.parse(dateAsString)?.time ?: 0

        fun createMangaEntry(json: MangaDataDto, lang: String): MangaInfo {
            return MangaInfo(
                key = buildMangaUrl(json.id),
                title = cleanString(getTitleFromManga(json.attributes, lang)),
                cover = json.relationships
                    .firstOrNull { relationshipDto -> relationshipDto.type == MdConstants.Types.coverArt }
                    ?.attributes
                    ?.fileName
                    ?.let { coverFileName ->
                        cdnCoverUrl(json.id, coverFileName)
                    }.orEmpty(),
            )
        }

        fun getTitleFromManga(json: MangaAttributesDto, lang: String): String {
            return getFromLangMap(json.title.asMdMap(), lang, json.originalLanguage)
                ?: getAltTitle(json.altTitles, lang, json.originalLanguage)
                ?: json.title.asMdMap<String>()[json.originalLanguage]
                ?: json.altTitles.firstNotNullOfOrNull { it[json.originalLanguage] }
                    .orEmpty()
        }

        fun getFromLangMap(langMap: Map<String, String>, currentLang: String, originalLanguage: String): String? {
            return langMap[currentLang]
                ?: langMap["en"]
                ?: if (originalLanguage == "ja") {
                    langMap["ja-ro"]
                        ?: langMap["jp-ro"]
                } else null
        }

        fun getAltTitle(langMaps: List<Map<String, String>>, currentLang: String, originalLanguage: String): String? {
            return langMaps.firstNotNullOfOrNull { it[currentLang] }
                ?: langMaps.firstNotNullOfOrNull { it["en"] }
                ?: if (originalLanguage == "ja") {
                    langMaps.firstNotNullOfOrNull { it["ja-ro"] }
                        ?: langMaps.firstNotNullOfOrNull { it["jp-ro"] }
                } else null
        }

        fun cdnCoverUrl(dexId: String, fileName: String): String {
            return "$cdnUrl/covers/$dexId/$fileName"
        }

        fun getLoginBody(preferences: PreferencesHelper, mdList: MdList) = preferences.trackToken(mdList)
            .get()
            .nullIfBlank()
            ?.let {
                try {
                    jsonParser.decodeFromString<LoginBodyTokenDto>(it)
                } catch (e: SerializationException) {
                    xLogD("Unable to load login body")
                    null
                }
            }

        fun sessionToken(preferences: PreferencesHelper, mdList: MdList) = getLoginBody(preferences, mdList)?.session

        fun refreshToken(preferences: PreferencesHelper, mdList: MdList) = getLoginBody(preferences, mdList)?.refresh

        fun updateLoginToken(token: LoginBodyTokenDto, preferences: PreferencesHelper, mdList: MdList) {
            preferences.trackToken(mdList).set(jsonParser.encodeToString(token))
        }

        fun getAuthHeaders(headers: Headers, preferences: PreferencesHelper, mdList: MdList) =
            headers.newBuilder().add(
                "Authorization",
                "Bearer " + (sessionToken(preferences, mdList) ?: throw NoSessionException()),
            ).build()

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
                .asSequence()
                .mapNotNull { it.getMainSource<MangaDex>() }
                .filter { it.lang in languages }
                .filterNot { it.id.toString() in disabledSourceIds }
                .toList()
        }

        inline fun <reified T> encodeToBody(body: T): RequestBody {
            return jsonParser.encodeToString(body)
                .toRequestBody("application/json".toMediaType())
        }
    }
}
