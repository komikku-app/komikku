package exh.md.utils

import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.source.getMainSource
import exh.util.floor
import exh.util.nullIfZero
import kotlinx.serialization.json.Json
import org.jsoup.parser.Parser
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URISyntaxException

@Suppress("unused")
class MdUtil {

    companion object {
        const val cdnUrl = "https://mangadex.org" // "https://s0.mangadex.org"
        const val baseUrl = "https://mangadex.org"
        const val randMangaPage = "/manga/"
        const val apiUrl = "https://api.mangadex.org"
        const val apiManga = "/v2/manga/"
        const val includeChapters = "?include=chapters"
        const val oldApiChapter = "/api/chapter/"
        const val newApiChapter = "/v2/chapter/"
        const val apiChapterSuffix = "?mark_read=0"
        const val groupSearchUrl = "$baseUrl/groups/0/1/"
        const val followsAllApi = "/v2/user/me/followed-manga"
        const val isLoggedInApi = "/v2/user/me"
        const val followsMangaApi = "/v2/user/me/manga/"
        const val apiCovers = "/covers"
        const val reportUrl = "https://api.mangadex.network/report"
        const val imageUrl = "$baseUrl/data"

        val jsonParser = Json {
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
        fun getMangaId(url: String): String {
            val lastSection = url.trimEnd('/').substringAfterLast("/")
            return if (lastSection.toIntOrNull() != null) {
                lastSection
            } else {
                // this occurs if person has manga from before that had the id/name/
                url.trimEnd('/').substringBeforeLast("/").substringAfterLast("/")
            }
        }

        fun getChapterId(url: String) = url.substringBeforeLast(apiChapterSuffix).substringAfterLast("/")

        // creates the manga url from the browse for the api
        fun modifyMangaUrl(url: String): String =
            url.replace("/title/", "/manga/").substringBeforeLast("/") + "/"

        // Removes the ?timestamp from image urls
        fun removeTimeParamUrl(url: String): String = url.substringBeforeLast("?")

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

        fun getScanlators(scanlators: String): List<String> {
            if (scanlators.isBlank()) return emptyList()
            return scanlators.split(scanlatorSeparator).distinct()
        }

        fun getScanlatorString(scanlators: Set<String>): String {
            return scanlators.toList().sorted().joinToString(scanlatorSeparator)
        }

        fun getMissingChapterCount(chapters: List<SChapter>, mangaStatus: Int): String? {
            if (mangaStatus == SManga.COMPLETED) return null

            // TODO
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

        fun getEnabledMangaDex(preferences: PreferencesHelper = Injekt.get(), sourceManager: SourceManager = Injekt.get()): MangaDex? {
            return getEnabledMangaDexs(preferences, sourceManager).let { mangadexs ->
                preferences.preferredMangaDexId().get().toLongOrNull()?.nullIfZero()?.let { preferredMangaDexId ->
                    mangadexs.firstOrNull { it.id == preferredMangaDexId }
                } ?: mangadexs.firstOrNull()
            }
        }

        fun getEnabledMangaDexs(preferences: PreferencesHelper = Injekt.get(), sourceManager: SourceManager = Injekt.get()): List<MangaDex> {
            val languages = preferences.enabledLanguages().get()
            val disabledSourceIds = preferences.disabledSources().get()

            return sourceManager.getVisibleOnlineSources()
                .map { it.getMainSource() }
                .filterIsInstance<MangaDex>()
                .filter { it.lang in languages }
                .filterNot { it.id.toString() in disabledSourceIds }
        }

        fun mapMdIdToMangaUrl(id: Int) = "/manga/$id/"
    }
}

/**
 * Assigns the url of the chapter without the scheme and domain. It saves some redundancy from
 * database and the urls could still work after a domain change.
 *
 * @param url the full url to the chapter.
 */
fun SChapter.setMDUrlWithoutDomain(url: String) {
    this.url = getMDUrlWithoutDomain(url)
}

/**
 * Assigns the url of the manga without the scheme and domain. It saves some redundancy from
 * database and the urls could still work after a domain change.
 *
 * @param url the full url to the manga.
 */
fun SManga.setMDUrlWithoutDomain(url: String) {
    this.url = getMDUrlWithoutDomain(url)
}

/**
 * Returns the url of the given string without the scheme and domain.
 *
 * @param orig the full url.
 */
private fun getMDUrlWithoutDomain(orig: String): String {
    return try {
        val uri = orig.toUri()
        var out = uri.path.orEmpty()
        if (uri.query != null) {
            out += "?" + uri.query
        }
        if (uri.fragment != null) {
            out += "#" + uri.fragment
        }
        out
    } catch (e: URISyntaxException) {
        orig
    }
}

fun Chapter.scanlatorList(): List<String> {
    return this.scanlator?.let {
        MdUtil.getScanlators(it)
    } ?: listOf("No scanlator")
}
