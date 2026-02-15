@file:Suppress("PropertyName")

package exh.md.utils

import android.app.Application
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.util.PkceUtil
import exh.md.dto.MangaAttributesDto
import exh.md.dto.MangaDataDto
import exh.source.getMainSource
import exh.util.nullIfZero
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.parser.Parser
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
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

        const val similarBaseApi = "https://api.similarmanga.com/similar/"

        const val mangaLimit = 20

        val jsonParser =
            Json {
                isLenient = true
                ignoreUnknownKeys = true
                allowSpecialFloatingPointValues = true
                useArrayPolymorphism = true
                prettyPrint = true
            }

        private const val scanlatorSeparator = " & "

        val markdownLinksRegex = "\\[([^]]+)]\\(([^)]+)\\)".toRegex()
        val markdownItalicBoldRegex = "\\*+\\s*([^*]*)\\s*\\*+".toRegex()
        val markdownItalicRegex = "_+\\s*([^_]*)\\s*_+".toRegex()

        fun buildMangaUrl(mangaUuid: String): String {
            return "/manga/$mangaUuid"
        }

        // Get the ID from the manga url
        fun getMangaId(url: String): String = url.trimEnd('/').substringAfterLast("/")

        fun getChapterId(url: String) = url.substringAfterLast("/")

        fun cleanDescription(string: String): String {
            return Parser.unescapeEntities(string, false)
                .substringBefore("\n---")
                .replace(markdownLinksRegex, "$1")
                .replace(markdownItalicBoldRegex, "$1")
                .replace(markdownItalicRegex, "$1")
                .trim()
        }

        fun getScanlatorString(scanlators: Set<String>): String {
            return scanlators.sorted().joinToString(scanlatorSeparator)
        }

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun parseDate(dateAsString: String): Long =
            dateFormatter.parse(dateAsString)?.time ?: 0

        fun createMangaEntry(json: MangaDataDto, lang: String): SManga {
            return SManga(
                url = buildMangaUrl(json.id),
                title = getTitleFromManga(json.attributes, lang, true),
                thumbnail_url = json.relationships
                    .firstOrNull { relationshipDto -> relationshipDto.type == MdConstants.Types.coverArt }
                    ?.attributes
                    ?.fileName
                    ?.let { coverFileName ->
                        cdnCoverUrl(json.id, coverFileName)
                    }.orEmpty(),
            )
        }

        fun getTitleFromManga(json: MangaAttributesDto, lang: String, preferExtensionLangTitle: Boolean): String {
            val titleMap = json.title.asMdMap<String>()
            val altTitles = json.altTitles
            val originalLang = json.originalLanguage

            titleMap[lang]?.let { return it }

            val mainTitle = titleMap.values.firstOrNull()
            // KMK -->
            val langAltTitle = altTitles.firstNotNullOfOrNull { it[lang] }
            // KMK <--
            val enTitle = findTitleInMaps("en", titleMap, altTitles)
            val originalLangTitle = findTitleInMaps("$originalLang-ro", titleMap, altTitles) ?: findTitleInMaps(
                originalLang,
                titleMap,
                altTitles,
            )

            val ordered = if (preferExtensionLangTitle) {
                listOf(langAltTitle, mainTitle, enTitle, originalLangTitle)
            } else {
                listOf(mainTitle, langAltTitle, enTitle, originalLangTitle)
            }

            // KMM -->
            return ordered.firstNotNullOfOrNull { it } ?: ""
            // KMM <--
        }

        fun getFromLangMap(langMap: Map<String, String>, currentLang: String, originalLanguage: String): String? {
            return langMap[currentLang]
                ?: langMap["en"]
                ?: if (originalLanguage == "ja") {
                    langMap["ja-ro"]
                        ?: langMap["jp-ro"]
                } else {
                    null
                }
        }

        fun findTitleInMaps(
            lang: String,
            titleMap: Map<String, String>,
            altTitleMaps: List<Map<String, String>>,
        ): String? {
            return titleMap[lang] ?: altTitleMaps.firstNotNullOfOrNull { it[lang] }
        }

        fun cdnCoverUrl(dexId: String, fileName: String): String {
            return "$cdnUrl/covers/$dexId/$fileName"
        }

        fun saveOAuth(preferences: TrackPreferences, mdList: MdList, oAuth: MALOAuth?) {
            if (oAuth == null) {
                preferences.trackToken(mdList).delete()
            } else {
                preferences.trackToken(mdList).set(jsonParser.encodeToString(oAuth))
            }
        }

        fun loadOAuth(preferences: TrackPreferences, mdList: MdList): MALOAuth? {
            return try {
                jsonParser.decodeFromString<MALOAuth>(preferences.trackToken(mdList).get())
            } catch (_: Exception) {
                null
            }
        }

        private var codeVerifier: String? = null

        fun refreshTokenRequest(oauth: MALOAuth): Request {
            val formBody = FormBody.Builder()
                .add("client_id", MdConstants.Login.clientId)
                .add("grant_type", MdConstants.Login.refreshToken)
                .add("refresh_token", oauth.refreshToken)
                .add("code_verifier", getPkceChallengeCode())
                .add("redirect_uri", MdConstants.Login.redirectUri)
                .build()

            // Add the Authorization header manually as this particular
            // request is called by the interceptor itself so it doesn't reach
            // the part where the token is added automatically.
            val headers = Headers.Builder()
                .add("Authorization", "Bearer ${oauth.accessToken}")
                .build()

            return POST(MdApi.baseAuthUrl + MdApi.token, body = formBody, headers = headers)
        }

        fun getPkceChallengeCode(): String {
            return codeVerifier ?: PkceUtil.generateCodeVerifier().also { codeVerifier = it }
        }

        fun getEnabledMangaDex(
            sourcePreferences: SourcePreferences = Injekt.get(),
            sourceManager: SourceManager = Injekt.get(),
        ): MangaDex? {
            return getEnabledMangaDexs(sourcePreferences, sourceManager).let { mangadexs ->
                sourcePreferences.preferredMangaDexId().get().toLongOrNull()?.nullIfZero()
                    ?.let { preferredMangaDexId ->
                        mangadexs.firstOrNull { it.id == preferredMangaDexId }
                    }
                    ?: mangadexs.firstOrNull()
            }
        }

        fun getEnabledMangaDexs(
            preferences: SourcePreferences,
            sourceManager: SourceManager = Injekt.get(),
        ): List<MangaDex> {
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

        fun addAltTitleToDesc(description: String, altTitles: List<String>?): String {
            return if (altTitles.isNullOrEmpty()) {
                description
            } else {
                val altTitlesDesc = altTitles
                    .joinToString(
                        "\n",
                        "${Injekt.get<Application>().stringResource(SYMR.strings.alt_titles)}:\n",
                    ) { "• $it" }
                description + (if (description.isBlank()) "" else "\n\n") + Parser.unescapeEntities(
                    altTitlesDesc,
                    false,
                )
            }
        }

        fun addFinalChapterToDesc(description: String, lastVolume: String?, lastChapter: String?): String {
            val parts = listOfNotNull(
                lastVolume?.takeIf { it.isNotEmpty() }?.let { "Vol.$it" },
                lastChapter?.takeIf { it.isNotEmpty() }?.let { "Ch.$it" },
            )

            return if (parts.isEmpty()) {
                description
            } else {
                description + (if (description.isBlank()) "" else "\n\n") + parts.joinToString(
                    " ",
                    "${Injekt.get<Application>().stringResource(SYMR.strings.final_chapter)}:\n",
                )
            }
        }
    }
}
