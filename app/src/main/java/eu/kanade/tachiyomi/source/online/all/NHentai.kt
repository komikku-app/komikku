package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullArray
import com.github.salomonbrys.kotson.nullLong
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.nullString
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.asJsoup
import exh.NHENTAI_SOURCE_ID
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.metadata.metadata.NHentaiSearchMetadata.Companion.TAG_TYPE_DEFAULT
import exh.metadata.metadata.base.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.metadata.metadata.base.RaisedTag
import exh.ui.metadata.adapters.NHentaiDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable

/**
 * NHentai source
 */

class NHentai(val context: Context) : HttpSource(), LewdSource<NHentaiSearchMetadata, Response>, UrlImportableSource {
    override val metaClass = NHentaiSearchMetadata::class

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        // TODO There is currently no way to get the most popular mangas
        // TODO Instead, we delegate this to the latest updates thing to avoid confusing users with an empty screen
        return fetchLatestUpdates(page)
    }

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    // Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val trimmedIdQuery = query.trim().removePrefix("id:")
        val newQuery = if (trimmedIdQuery.toIntOrNull() ?: -1 >= 0) {
            "$baseUrl/g/$trimmedIdQuery/"
        } else query

        return urlImportFetchSearchManga(context, newQuery) {
            searchMangaRequestObservable(page, query, filters).flatMap {
                client.newCall(it).asObservableSuccess()
            }.map { response ->
                searchMangaParse(response)
            }
        }
    }

    private fun searchMangaRequestObservable(page: Int, query: String, filters: FilterList): Observable<Request> {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val advQuery = combineQuery(filterList)
        val favoriteFilter = filterList.findInstance<FavoriteFilter>()
        val isOkayToSort = filterList.findInstance<UploadedFilter>()?.state?.isBlank() ?: true

        val url: HttpUrl.Builder

        if (favoriteFilter?.state == true) {
            url = "$baseUrl/favorites".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("q", "$query $advQuery")
                .addQueryParameter("page", page.toString())
        } else {
            url = "$baseUrl/search".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("q", "$query $advQuery")
                .addQueryParameter("page", page.toString())

            if (isOkayToSort) {
                filterList.findInstance<SortFilter>()?.let { f ->
                    url.addQueryParameter("sort", f.toUriPart())
                }
            }
        }

        return client.newCall(nhGet(url.toString()))
            .asObservableSuccess()
            .map { nhGet(url.toString(), page) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response) = parseResultPage(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
        uri.appendQueryParameter("page", page.toString())
        return nhGet(uri.toString(), page)
    }

    override fun latestUpdatesParse(response: Response) = parseResultPage(response)

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .flatMap {
                parseToManga(manga, it).andThen(
                    Observable.just(
                        manga.apply {
                            initialized = true
                        }
                    )
                )
            }
    }

    override fun mangaDetailsRequest(manga: SManga) = nhGet(baseUrl + manga.url)

    private fun parseResultPage(response: Response): MangasPage {
        val doc = response.asJsoup()

        // TODO Parse lang + tags

        val mangas = doc.select(".gallery > a").map {
            SManga.create().apply {
                url = it.attr("href")

                title = it.selectFirst(".caption").text()

                // last() is a hack to ignore the lazy-loader placeholder image on the front page
                thumbnail_url = it.select("img").last().attr("src")
                // In some pages, the thumbnail url does not include the protocol
                if (!thumbnail_url!!.startsWith("https:")) thumbnail_url = "https:$thumbnail_url"
            }
        }

        val hasNextPage = if (!response.request.url.queryParameterNames.contains(REVERSE_PARAM)) {
            doc.selectFirst(".next") != null
        } else {
            response.request.url.queryParameter(REVERSE_PARAM)!!.toBoolean()
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun parseIntoMetadata(metadata: NHentaiSearchMetadata, input: Response) {
        val json = GALLERY_JSON_REGEX.find(input.body!!.string())!!.groupValues[1].replace(UNICODE_ESCAPE_REGEX) { it.groupValues[1].toInt(radix = 16).toChar().toString() }
        val obj = JsonParser.parseString(json).asJsonObject

        with(metadata) {
            nhId = obj["id"].asLong

            uploadDate = obj["upload_date"].nullLong

            favoritesCount = obj["num_favorites"].nullLong

            mediaId = obj["media_id"].nullString

            obj["title"].nullObj?.let { title ->
                japaneseTitle = title["japanese"].nullString
                shortTitle = title["pretty"].nullString
                englishTitle = title["english"].nullString
            }

            obj["images"].nullObj?.let { images ->
                coverImageType = images["cover"]?.get("t").nullString
                images["pages"].nullArray?.mapNotNull {
                    it?.asJsonObject?.get("t").nullString
                }?.let {
                    pageImageTypes = it
                }
                thumbnailImageType = images["thumbnail"]?.get("t").nullString
            }

            scanlator = obj["scanlator"].nullString

            obj["tags"]?.asJsonArray?.map {
                val asObj = it.asJsonObject
                Pair(asObj["type"].nullString, asObj["name"].nullString)
            }?.apply {
                tags.clear()
            }?.forEach {
                if (it.first != null && it.second != null) {
                    tags.add(RaisedTag(it.first!!, it.second!!, if (it.first == "category") TAG_TYPE_VIRTUAL else TAG_TYPE_DEFAULT))
                }
            }
        }
    }

    private fun getOrLoadMetadata(mangaId: Long?, nhId: Long) = getOrLoadMetadata(mangaId) {
        client.newCall(nhGet(baseUrl + NHentaiSearchMetadata.nhIdToPath(nhId)))
            .asObservableSuccess()
            .toSingle()
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                url = manga.url
                name = "Chapter"
                chapter_number = 1f
            }
        )
    )

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = getOrLoadMetadata(chapter.mangaId, NHentaiSearchMetadata.nhUrlToId(chapter.url)).map { metadata ->
        if (metadata.mediaId == null) {
            emptyList()
        } else {
            metadata.pageImageTypes.mapIndexed { index, s ->
                val imageUrl = imageUrlFromType(metadata.mediaId!!, index + 1, s)
                Page(index, imageUrl!!, imageUrl)
            }
        }
    }.toObservable()

    override fun fetchImageUrl(page: Page) = Observable.just(page.imageUrl!!)!!

    private fun imageUrlFromType(mediaId: String, page: Int, t: String) = NHentaiSearchMetadata.typeToExtension(t)?.let {
        "https://i.nhentai.net/galleries/$mediaId/$page.$it"
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw NotImplementedError("Unused method called!")
    }

    override fun pageListParse(response: Response): List<Page> {
        throw NotImplementedError("Unused method called!")
    }

    override fun imageUrlParse(response: Response): String {
        throw NotImplementedError("Unused method called!")
    }

    private fun combineQuery(filters: FilterList): String {
        val stringBuilder = StringBuilder()
        val advSearch = filters.filterIsInstance<AdvSearchEntryFilter>().flatMap { filter ->
            val splitState = filter.state.split(",").map(String::trim).filterNot(String::isBlank)
            splitState.map {
                AdvSearchEntry(filter.name, it.removePrefix("-"), it.startsWith("-"))
            }
        }

        advSearch.forEach { entry ->
            if (entry.exclude) stringBuilder.append("-")
            stringBuilder.append("${entry.name}:")
            stringBuilder.append(entry.text)
            stringBuilder.append(" ")
        }

        val langFilter = filters.filterIsInstance<FilterLang>().firstOrNull()
        if (langFilter != null) {
            val language = SOURCE_LANG_LIST.first { it.first == langFilter.values[langFilter.state] }.second
            if (!language.isBlank()) {
                stringBuilder.append("language:$language")
            }
        }

        return stringBuilder.toString()
    }

    data class AdvSearchEntry(val name: String, val text: String, val exclude: Boolean)

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TagFilter(),
        CategoryFilter(),
        GroupFilter(),
        ArtistFilter(),
        ParodyFilter(),
        CharactersFilter(),
        Filter.Header("Uploaded valid units are h, d, w, m, y."),
        Filter.Header("example: (>20d)"),
        UploadedFilter(),

        Filter.Separator(),
        SortFilter(),
        Filter.Header("Sort is ignored if favorites only"),
        FavoriteFilter(),
        FilterLang()
    )

    class TagFilter : AdvSearchEntryFilter("Tags")
    class CategoryFilter : AdvSearchEntryFilter("Categories")
    class GroupFilter : AdvSearchEntryFilter("Groups")
    class ArtistFilter : AdvSearchEntryFilter("Artists")
    class ParodyFilter : AdvSearchEntryFilter("Parodies")
    class CharactersFilter : AdvSearchEntryFilter("Characters")
    class UploadedFilter : AdvSearchEntryFilter("Uploaded")
    open class AdvSearchEntryFilter(name: String) : Filter.Text(name)

    private class FavoriteFilter : Filter.CheckBox("Show favorites only", false)

    // language filtering
    private class FilterLang : Filter.Select<String>("Language", SOURCE_LANG_LIST.map { it.first }.toTypedArray())

    private class SortFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Popular: All Time", "popular"),
            Pair("Popular: Week", "popular-week"),
            Pair("Popular: Today", "popular-today"),
            Pair("Recent", "date")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private val appName by lazy {
        context.getString(R.string.app_name)
    }

    private fun nhGet(url: String, tag: Any? = null) = GET(url)
        .newBuilder()
        .header(
            "User-Agent",
            "Mozilla/5.0 (X11; Linux x86_64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/56.0.2924.87 " +
                "Safari/537.36 " +
                "$appName/${BuildConfig.VERSION_CODE}"
        )
        .tag(tag).build()

    override val id = NHENTAI_SOURCE_ID

    override val lang = "all"

    override val name = "nhentai"

    override val baseUrl = NHentaiSearchMetadata.BASE_URL

    override val supportsLatest = true

    // === URL IMPORT STUFF

    override val matchingHosts = listOf(
        "nhentai.net"
    )

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        if (uri.pathSegments.firstOrNull()?.toLowerCase() != "g") {
            return null
        }

        return "$baseUrl/g/${uri.pathSegments[1]}/"
    }

    override fun getDescriptionAdapter(controller: MangaController): NHentaiDescriptionAdapter {
        return NHentaiDescriptionAdapter(controller)
    }

    companion object {
        private val GALLERY_JSON_REGEX = Regex(".parse\\(\"(.*)\"\\);")
        private val UNICODE_ESCAPE_REGEX = Regex("\\\\u([0-9a-fA-F]{4})")
        private const val REVERSE_PARAM = "TEH_REVERSE"

        private val SOURCE_LANG_LIST = listOf(
            Pair("All", ""),
            Pair("English", "english"),
            Pair("Japanese", "japanese"),
            Pair("Chinese", "chinese")
        )
    }
}
