package exh.md.utils

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.util.floor
import kotlinx.serialization.json.Json
import org.jsoup.parser.Parser
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI
import java.net.URISyntaxException

class MdUtil {

    companion object {
        const val cdnUrl = "https://mangadex.org" // "https://s0.mangadex.org"
        const val baseUrl = "https://mangadex.org"
        const val randMangaPage = "/manga/"
        const val apiManga = "/api/manga/"
        const val apiChapter = "/api/chapter/"
        const val apiChapterSuffix = "?mark_read=0"
        const val groupSearchUrl = "$baseUrl/groups/0/1/"
        const val followsAllApi = "/api/?type=manga_follows"
        const val followsMangaApi = "/api/?type=manga_follows&manga_id="
        const val coversApi = "/api/index.php?type=covers&id="
        const val reportUrl = "https://api.mangadex.network/report"
        const val imageUrl = "$baseUrl/data"

        val jsonParser = Json {
            isLenient = true
            ignoreUnknownKeys = true
            allowSpecialFloatingPointValues = true
            useArrayPolymorphism = true
            prettyPrint = true
        }

        private const
        val scanlatorSeparator = " & "

        val validOneShotFinalChapters = listOf("0", "1")

        val englishDescriptionTags = listOf(
            "[b][u]English:",
            "[b][u]English",
            "[English]:",
            "[B][ENG][/B]"
        )

        val descriptionLanguages = listOf(
            "Russian / Русский",
            "[u]Russian",
            "[b][u]Russian",
            "[RUS]",
            "Russian / Русский",
            "Russian/Русский:",
            "Russia/Русское",
            "Русский",
            "RUS:",
            "[b][u]German / Deutsch",
            "German/Deutsch:",
            "Espa&ntilde;ol / Spanish",
            "Spanish / Espa&ntilde;ol",
            "Spanish / Espa & ntilde; ol",
            "Spanish / Espa&ntilde;ol",
            "[b][u]Spanish",
            "[Espa&ntilde;ol]:",
            "[b] Spanish: [/ b]",
            "정보",
            "Spanish/Espa&ntilde;ol",
            "Espa&ntilde;ol / Spanish",
            "Italian / Italiano",
            "Italian/Italiano",
            "\r\n\r\nItalian\r\n",
            "Pasta-Pizza-Mandolino/Italiano",
            "Persian /فارسی",
            "Farsi/Persian/",
            "Polish / polski",
            "Polish / Polski",
            "Polish Summary / Polski Opis",
            "Polski",
            "Portuguese (BR) / Portugu&ecirc;s",
            "Portuguese / Portugu&ecirc;s",
            "Português / Portuguese",
            "Portuguese / Portugu",
            "Portuguese / Portugu&ecirc;s",
            "Portugu&ecirc;s",
            "Portuguese (BR) / Portugu & ecirc;",
            "Portuguese (BR) / Portugu&ecirc;",
            "[PTBR]",
            "R&eacute;sume Fran&ccedil;ais",
            "R&eacute;sum&eacute; Fran&ccedil;ais",
            "[b][u]French",
            "French / Fran&ccedil;ais",
            "Fran&ccedil;ais",
            "[hr]Fr:",
            "French - Français:",
            "Turkish / T&uuml;rk&ccedil;e",
            "Turkish/T&uuml;rk&ccedil;e",
            "T&uuml;rk&ccedil;e",
            "[b][u]Chinese",
            "Arabic / العربية",
            "العربية",
            "[hr]TH",
            "[b][u]Vietnamese",
            "[b]Links:",
            "[b]Link[/b]",
            "Links:",
            "[b]External Links"
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
            val bbRegex =
                """\[(\w+)[^]]*](.*?)\[/\1]""".toRegex()
            var intermediate = string
                .replace("[list]", "", true)
                .replace("[/list]", "", true)
                .replace("[*]", "")
                .replace("[hr]", "", true)
                .replace("[u]", "", true)
                .replace("[/u]", "", true)
                .replace("[b]", "", true)
                .replace("[/b]", "", true)

            // Recursively remove nested bbcode
            while (bbRegex.containsMatchIn(intermediate)) {
                intermediate = intermediate.replace(bbRegex, "$2")
            }
            return Parser.unescapeEntities(intermediate, false)
        }

        fun cleanDescription(string: String): String {
            var newDescription = string
            descriptionLanguages.forEach {
                newDescription = newDescription.substringBefore(it)
            }

            englishDescriptionTags.forEach {
                newDescription = newDescription.replace(it, "")
            }
            return cleanString(newDescription)
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

            remove0ChaptersFromCount.firstOrNull()?.let {
                val chpNumber = it.chapter_number.floor()
                val allChapters = (1..chpNumber).toMutableSet()

                remove0ChaptersFromCount.forEach {
                    allChapters.remove(it.chapter_number.floor())
                }

                if (allChapters.size <= 0) return null
                return allChapters.size.toString()
            }
            return null
        }

        fun getEnabledMangaDex(preferences: PreferencesHelper = Injekt.get(), sourceManager: SourceManager = Injekt.get()): MangaDex? {
            return getEnabledMangaDexs(preferences, sourceManager).let { mangadexs ->
                val preferredMangaDexId = preferences.preferredMangaDexId().get().toLongOrNull()
                mangadexs.firstOrNull { preferredMangaDexId != null && preferredMangaDexId != 0L && it.id == preferredMangaDexId } ?: mangadexs.firstOrNull()
            }
        }

        fun getEnabledMangaDexs(preferences: PreferencesHelper = Injekt.get(), sourceManager: SourceManager = Injekt.get()): List<MangaDex> {
            val languages = preferences.enabledLanguages().get()
            val disabledSourceIds = preferences.disabledSources().get()

            return sourceManager.getDelegatedCatalogueSources()
                .filter { it.lang in languages }
                .filterNot { it.id.toString() in disabledSourceIds }
                .filterIsInstance(MangaDex::class.java)
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
        val uri = URI(orig)
        var out = uri.path
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
