package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.PagePreviewInfo
import eu.kanade.tachiyomi.source.PagePreviewPage
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.copy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.debug.DebugToggles
import exh.eh.EHTags
import exh.eh.EHentaiUpdateHelper
import exh.eh.EHentaiUpdateWorkerConstants
import exh.eh.GalleryEntry
import exh.log.xLogD
import exh.metadata.MetadataUtil
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.EH_GENRE_NAMESPACE
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.EH_META_NAMESPACE
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.EH_UPLOADER_NAMESPACE
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.EH_VISIBILITY_NAMESPACE
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.TAG_TYPE_LIGHT
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.TAG_TYPE_NORMAL
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.TAG_TYPE_WEAK
import exh.metadata.metadata.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.metadata.metadata.RaisedSearchMetadata.Companion.toGenreString
import exh.metadata.metadata.base.RaisedTag
import exh.source.ExhPreferences
import exh.ui.login.EhLoginActivity
import exh.util.UriFilter
import exh.util.UriGroup
import exh.util.asObservableWithAsyncStacktrace
import exh.util.dropBlank
import exh.util.ignore
import exh.util.nullIfBlank
import exh.util.trimAll
import exh.util.trimOrNull
import exh.util.urlImportFetchSearchManga
import exh.util.urlImportFetchSearchMangaSuspend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import rx.Observable
import tachiyomi.core.common.util.lang.runAsObservable
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.time.ZoneOffset
import java.time.ZonedDateTime

// TODO Consider gallery updating when doing tabbed browsing
class EHentai(
    override val id: Long,
    val exh: Boolean,
    val context: Context,
    // KMK -->
    override val lang: String = "all",
    // KMK <--
) : HttpSource(),
    // KMK -->
    EhBasedSource,
    // KMK <--
    MetadataSource<EHentaiSearchMetadata, Document>,
    UrlImportableSource,
    NamespaceSource,
    PagePreviewSource {
    override val metaClass = EHentaiSearchMetadata::class

    private val domain: String
        get() = if (exh) {
            "exhentai.org"
        } else {
            "e-hentai.org"
        }

    override val baseUrl: String
        get() = "https://$domain"

    override val supportsLatest = true

    // KMK -->
    private val ehLang = languageMapping[lang]

    // true if lang is a "natural human language"
    private fun isLangNatural(): Boolean = lang !in listOf("none", "other", "all")

    private fun languageTag(): String {
        return "language:$ehLang"
    }
    // KMK <--

    private val exhPreferences: ExhPreferences by injectLazy()
    private val updateHelper: EHentaiUpdateHelper by injectLazy()

    /**
     * Gallery list entry
     */
    data class ParsedManga(val fav: Int, val manga: SManga, val metadata: EHentaiSearchMetadata)

    private fun extendedGenericMangaParse(doc: Document) = with(doc) {
        // Parse mangas (supports compact + extended layout)
        val parsedMangas = select(".itg > tbody > tr").filter { element ->
            // Do not parse header and ads
            element.selectFirst("th") == null && element.selectFirst(".itd") == null
        }.map { body ->
            val thumbnailElement = body.selectFirst(".gl1e img, .gl2c .glthumb img")!!
            val column2 = body.selectFirst(".gl3e, .gl2c")!!
            val linkElement = body.selectFirst(".gl3c > a, .gl2e > div > a")!!
            val infoElement = body.selectFirst(".gl3e")

            // why is column2 null
            val favElement = column2.children().find { it.attr("style").startsWith("border-color") }
            val infoElements = infoElement?.select("div")
            val parsedTags = mutableListOf<RaisedTag>()

            ParsedManga(
                fav = FAVORITES_BORDER_HEX_COLORS.indexOf(
                    favElement?.attr("style")?.substring(14, 17),
                ),
                manga = SManga.create().apply {
                    // Get title
                    title = thumbnailElement.attr("title")
                    url = EHentaiSearchMetadata.normalizeUrl(linkElement.attr("href"))
                    // Get image
                    thumbnail_url = thumbnailElement.attr("src")

                    if (infoElements != null) {
                        linkElement.select("div div").getOrNull(1)?.select("tr")?.forEach { row ->
                            val namespace = row.select(".tc").text().removeSuffix(":")
                            parsedTags.addAll(
                                row.select("div").map { element ->
                                    RaisedTag(
                                        namespace,
                                        element.text().trim(),
                                        when {
                                            element.hasClass("gtl") -> TAG_TYPE_LIGHT
                                            element.hasClass("gtw") -> TAG_TYPE_WEAK
                                            else -> TAG_TYPE_NORMAL
                                        },
                                    )
                                },
                            )
                        }
                    } else {
                        val tagElement = body.selectFirst(".gl3c > a")!!
                        val tagElements = tagElement.select("div")
                        tagElements.forEach { element ->
                            if (element.className() == "gt") {
                                val namespace = element.attr("title").substringBefore(":").trimOrNull() ?: "misc"
                                parsedTags += RaisedTag(
                                    namespace,
                                    element.attr("title").substringAfter(":").trim(),
                                    TAG_TYPE_NORMAL,
                                )
                            }
                        }
                    }

                    genre = parsedTags.toGenreString()
                },
                metadata = EHentaiSearchMetadata().apply {
                    tags += parsedTags

                    if (infoElements != null) {
                        genre = getGenre(infoElements.getOrNull(1))

                        datePosted = getDateTag(infoElements.getOrNull(2))

                        averageRating = getRating(infoElements.getOrNull(3))

                        uploader = getUploader(infoElements.getOrNull(4))

                        length = getPageCount(infoElements.getOrNull(5))
                    } else {
                        genre = getGenre(body.selectFirst(".gl1c div"))

                        val info = body.selectFirst(".gl2c")!!
                        val extraInfo = body.selectFirst(".gl4c")!!

                        val infoList = info.select("div div")

                        datePosted = getDateTag(infoList.getOrNull(8))

                        averageRating = getRating(infoList.getOrNull(9))

                        val extraInfoList = extraInfo.select("div")

                        if (extraInfoList.getOrNull(2) == null) {
                            uploader = getUploader(extraInfoList.getOrNull(0))

                            length = getPageCount(extraInfoList.getOrNull(1))
                        } else {
                            uploader = getUploader(extraInfoList.getOrNull(1))

                            length = getPageCount(extraInfoList.getOrNull(2))
                        }
                    }
                },
            )
        }.ifEmpty {
            selectFirst(".searchwarn")?.let { throw Exception(it.text()) }
            emptyList()
        }

        val parsedLocation = doc.location().toHttpUrlOrNull()
        val isReversed = parsedLocation != null && parsedLocation.queryParameterNames.contains(REVERSE_PARAM)

        // Add to page if required
        val hasNextPage = if (isReversed) {
            select(".searchnav >div > a")
                .any { "prev" in it.attr("href") }
        } else {
            select(".searchnav >div > a")
                .any { "next" in it.attr("href") }
        }
        val nextPage = if (parsedLocation?.pathSegments?.contains("toplist.php") == true) {
            ((parsedLocation.queryParameter("p")?.toLong() ?: 0) + 2).takeIf { it <= 200 }
        } else if (hasNextPage) {
            parsedMangas.let { if (isReversed) it.first() else it.last() }
                .manga
                .url
                .let { EHentaiSearchMetadata.galleryId(it).toLong() }
        } else {
            null
        }

        parsedMangas.let { if (isReversed) it.reversed() else it } to nextPage
    }

    private fun getGenre(element: Element?): String? {
        return element?.attr("onclick")
            ?.nullIfBlank()
            ?.substringAfterLast('/')
            ?.removeSuffix("'")
            ?.trim()
            ?.substringAfterLast('/')
            ?.removeSuffix("'")
            ?: element?.text()
                ?.nullIfBlank()
                ?.lowercase()
                ?.replace(" ", "")
                ?.trim()
    }

    private fun getDateTag(element: Element?): Long? {
        val text = element?.text()?.nullIfBlank()
        return if (text != null) {
            val date = ZonedDateTime.parse(text, MetadataUtil.EX_DATE_FORMAT.withZone(ZoneOffset.UTC))
            date?.toInstant()?.toEpochMilli()
        } else {
            null
        }
    }

    private fun getRating(element: Element?): Double? {
        val ratingStyle = element?.attr("style")?.nullIfBlank()
        return if (ratingStyle != null) {
            val matches = RATING_REGEX.findAll(ratingStyle)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .toList()
            if (matches.size == 2) {
                var rate = 5 - matches[0] / 16
                if (matches[1] == 21) {
                    rate--
                    rate + 0.5
                } else {
                    rate.toDouble()
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun getUploader(element: Element?): String? {
        return element?.select("a")?.text()?.trimOrNull()
    }

    private fun getPageCount(element: Element?): Int? {
        val pageCount = element?.text()?.trimOrNull()
        return if (pageCount != null) {
            PAGE_COUNT_REGEX.find(pageCount)?.value?.toIntOrNull()
        } else {
            null
        }
    }

    /**
     * Parse a list of galleries
     */
    private fun genericMangaParse(
        response: Response,
    ) = extendedGenericMangaParse(response.asJsoup()).let { (parsedManga, nextPage) ->
        MetadataMangasPage(
            parsedManga.map { it.manga },
            nextPage != null,
            parsedManga.map { it.metadata },
            nextPage,
        )
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> = getChapterList(manga) {}

    suspend fun getChapterList(manga: SManga, throttleFunc: suspend () -> Unit): List<SChapter> {
        // Pull all the way to the root gallery
        // We can't do this with RxJava or we run into stack overflows on shit like this:
        //   https://exhentai.org/g/1073061/f9345f1c12/
        var url = manga.url
        var doc: Document

        while (true) {
            val gid = EHentaiSearchMetadata.galleryId(url).toInt()
            val cachedParent = updateHelper.parentLookupTable.get(
                gid,
            )
            if (cachedParent == null) {
                throttleFunc()
                doc = client.newCall(exGet(baseUrl + url)).awaitSuccess().asJsoup()

                val parentLink = doc.select("#gdd .gdt1").find { el ->
                    el.text().lowercase() == "parent:"
                }!!.nextElementSibling()!!.selectFirst("a")?.attr("href")

                if (parentLink != null) {
                    updateHelper.parentLookupTable.put(
                        gid,
                        GalleryEntry(
                            EHentaiSearchMetadata.galleryId(parentLink),
                            EHentaiSearchMetadata.galleryToken(parentLink),
                        ),
                    )
                    url = EHentaiSearchMetadata.normalizeUrl(parentLink)
                } else {
                    break
                }
            } else {
                this@EHentai.xLogD("Parent cache hit: %s!", gid)
                url = EHentaiSearchMetadata.idAndTokenToUrl(
                    cachedParent.gId,
                    cachedParent.gToken,
                )
            }
        }
        val newDisplay = doc.select("#gnd a")
        // Build chapter for root gallery
        val location = doc.location()
        val self = SChapter(
            url = EHentaiSearchMetadata.normalizeUrl(location),
            name = "v1: " + doc.selectFirst("#gn")!!.text(),
            chapter_number = 1f,
            date_upload = ZonedDateTime.parse(
                doc.select("#gdd .gdt1").find { el ->
                    el.text().lowercase() == "posted:"
                }!!.nextElementSibling()!!.text(),
                MetadataUtil.EX_DATE_FORMAT.withZone(ZoneOffset.UTC),
            )!!.toInstant().toEpochMilli(),
            scanlator = EHentaiSearchMetadata.galleryId(location),
        )
        // Build and append the rest of the galleries
        return if (DebugToggles.INCLUDE_ONLY_ROOT_WHEN_LOADING_EXH_VERSIONS.enabled) {
            listOf(self)
        } else {
            newDisplay.mapIndexed { index, newGallery ->
                val link = newGallery.attr("href")
                val name = newGallery.text()
                val posted = (newGallery.nextSibling() as TextNode).text().removePrefix(", added ")
                SChapter(
                    url = EHentaiSearchMetadata.normalizeUrl(link),
                    name = "v${index + 2}: $name",
                    chapter_number = index + 2f,
                    date_upload = ZonedDateTime.parse(
                        posted,
                        MetadataUtil.EX_DATE_FORMAT.withZone(ZoneOffset.UTC),
                    ).toInstant().toEpochMilli(),
                    scanlator = EHentaiSearchMetadata.galleryId(link),
                )
            }.reversed() + self
        }
    }

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getChapterList"))
    @Suppress("DEPRECATION")
    override fun fetchChapterList(manga: SManga) = fetchChapterList(manga) {}

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getChapterList"))
    fun fetchChapterList(manga: SManga, throttleFunc: suspend () -> Unit) = runAsObservable {
        getChapterList(manga, throttleFunc)
    }

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getPageList"))
    override fun fetchPageList(
        chapter: SChapter,
    ): Observable<List<Page>> = fetchChapterPage(chapter, baseUrl + chapter.url)
        .map {
            it.mapIndexed { i, s ->
                Page(i, s)
            }
        }!!

    private fun fetchChapterPage(
        chapter: SChapter,
        np: String,
        pastUrls: List<String> = emptyList(),
    ): Observable<List<String>> {
        val urls = ArrayList(pastUrls)
        return chapterPageCall(np).flatMap {
            val jsoup = it.asJsoup()
            urls += parseChapterPage(jsoup)
            val nextUrl = nextPageUrl(jsoup)
            if (nextUrl != null) {
                fetchChapterPage(chapter, nextUrl, urls)
            } else {
                Observable.just(urls)
            }
        }
    }

    private fun parseChapterPage(response: Element) = with(response) {
        select(".gdtm a").map {
            Pair(it.child(0).attr("alt").toInt(), it.attr("href"))
        }.plus(
            select("#gdt a").map {
                Pair(it.child(0).attr("title").removePrefix("Page ").substringBefore(":").toInt(), it.attr("href"))
            },
        ).sortedBy(Pair<Int, String>::first).map { it.second }
    }

    private fun chapterPageCall(np: String): Observable<Response> {
        return client.newCall(chapterPageRequest(np)).asObservableSuccess()
    }
    private fun chapterPageRequest(np: String): Request {
        return exGet(url = np, additionalHeaders = headers)
    }

    private fun nextPageUrl(element: Element): String? = element.select("a[onclick=return false]").last()?.let {
        return if (it.text() == ">") it.attr("href") else null
    }

    override fun popularMangaRequest(page: Int) =
        // KMK -->
        if (isLangNatural()) {
            exGet("$baseUrl/?f_search=${languageTag()}&f_srdd=5&f_sr=on", page)
        } else {
            if (page > 1) {
                exGet("$baseUrl/?f_srdd=5&f_sr=on", page - 1)
            } else {
                // KMK <--
                exGet("$baseUrl/popular")
            }
        }

    private fun <T : MangasPage> Observable<T>.checkValid(): Observable<MangasPage> = map {
        it.checkValid()
    }

    private fun <T : MangasPage> T.checkValid(): MangasPage =
        if (exh && mangas.isEmpty() && exhPreferences.igneousVal().get().equals("mystery", true)) {
            throw Exception(
                "Invalid igneous cookie, try re-logging or finding a correct one to input in the login menu",
            )
        } else {
            this
        }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        @Suppress("DEPRECATION")
        return super<HttpSource>.fetchLatestUpdates(page).checkValid()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        return super<HttpSource>.getLatestUpdates(page).checkValid()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularManga"))
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        @Suppress("DEPRECATION")
        return super<HttpSource>.fetchPopularManga(page).checkValid()
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        return super<HttpSource>.getPopularManga(page).checkValid()
    }

    // Support direct URL importing
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchManga"))
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            @Suppress("DEPRECATION")
            super<HttpSource>.fetchSearchManga(page, query, filters).checkValid()
        }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        return urlImportFetchSearchMangaSuspend(context, query) {
            super<HttpSource>.getSearchManga(page, query, filters).checkValid()
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val toplist = ToplistOption.entries[filters.firstNotNullOfOrNull { (it as? ToplistOptions)?.state } ?: 0]
        if (toplist != ToplistOption.NONE) {
            val uri = "https://e-hentai.org".toUri().buildUpon()
            uri.appendPath("toplist.php")
            uri.appendQueryParameter("tl", toplist.index.toString())
            uri.appendQueryParameter("p", (page - 1).toString())

            return exGet(url = uri.toString())
        }

        val uri = baseUrl.toUri().buildUpon()
        val isReverseFilterEnabled = filters.any { it is ReverseFilter && it.state }
        val jumpSeekValue = filters.firstNotNullOfOrNull { (it as? JumpSeekFilter)?.state?.nullIfBlank() }

        uri.appendQueryParameter("f_apply", "Apply+Filter")
        uri.appendQueryParameter("f_search", (query + " " + combineQuery(filters)).trim())
        filters.forEach {
            if (it is UriFilter) it.addToUri(uri)
        }
        // Reverse search results on filter
        if (isReverseFilterEnabled) {
            uri.appendQueryParameter(REVERSE_PARAM, "on")
        }
        if (jumpSeekValue != null && page == 1) {
            if (
                MATCH_SEEK_REGEX.matches(jumpSeekValue) ||
                (
                    MATCH_YEAR_REGEX.matches(jumpSeekValue) &&
                        jumpSeekValue.toIntOrNull()?.let {
                            it in 2007..2099
                        } == true
                    )
            ) {
                uri.appendQueryParameter("seek", jumpSeekValue)
            } else if (MATCH_JUMP_REGEX.matches(jumpSeekValue)) {
                uri.appendQueryParameter("jump", jumpSeekValue)
            }
        }

        return exGet(
            url = uri.toString(),
            next = if (!isReverseFilterEnabled) page else null,
            prev = if (isReverseFilterEnabled) page else null,
        )
    }

    override fun latestUpdatesRequest(page: Int) =
        // KMK -->
        if (isLangNatural()) {
            exGet("$baseUrl/?f_search=${languageTag()}", page)
        } else {
            // KMK <--
            exGet(baseUrl, page)
        }

    override fun popularMangaParse(response: Response) = genericMangaParse(response)
    override fun searchMangaParse(response: Response) = genericMangaParse(response)
    override fun latestUpdatesParse(response: Response) = genericMangaParse(response)

    private fun exGet(
        url: String,
        next: Int? = null,
        prev: Int? = null,
        additionalHeaders: Headers? = null,
        cacheControl: CacheControl? = null,
    ): Request {
        return GET(
            when {
                next != null && next > 1 -> addParam(url, "next", next.toString())
                prev != null && prev > 0 -> addParam(url, "prev", prev.toString())
                else -> url
            },
            if (additionalHeaders != null) {
                val headers = headers.newBuilder()
                additionalHeaders.toMultimap().forEach { (t, u) ->
                    u.forEach {
                        headers.add(t, it)
                    }
                }
                headers.build()
            } else {
                headers
            },
        ).let {
            if (cacheControl == null) {
                it
            } else {
                it.newBuilder().cacheControl(cacheControl).build()
            }
        }
    }

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getMangaDetails"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableWithAsyncStacktrace()
            .flatMap { (stacktrace, response) ->
                if (response.isSuccessful) {
                    // Pull to most recent
                    val doc = response.asJsoup()
                    val newerGallery = doc.select("#gnd a").lastOrNull()
                    val pre = if (
                        newerGallery != null && DebugToggles.PULL_TO_ROOT_WHEN_LOADING_EXH_MANGA_DETAILS.enabled
                    ) {
                        manga.url = EHentaiSearchMetadata.normalizeUrl(newerGallery.attr("href"))
                        client.newCall(mangaDetailsRequest(manga))
                            .asObservableSuccess().map { it.asJsoup() }
                    } else {
                        Observable.just(doc)
                    }

                    pre.flatMap {
                        @Suppress("DEPRECATION")
                        parseToMangaCompletable(manga, it).andThen(
                            Observable.just(
                                manga.apply {
                                    initialized = true
                                },
                            ),
                        )
                    }
                } else {
                    response.close()

                    if (response.code == 404) {
                        throw GalleryNotFoundException(stacktrace)
                    } else {
                        throw Exception("HTTP error ${response.code}", stacktrace)
                    }
                }
            }
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val exception = Exception("Async stacktrace")
        val response = client.newCall(mangaDetailsRequest(manga)).await()
        if (response.isSuccessful) {
            // Pull to most recent
            val doc = response.asJsoup()
            val newerGallery = doc.select("#gnd a").lastOrNull()
            val pre = if (
                newerGallery != null && DebugToggles.PULL_TO_ROOT_WHEN_LOADING_EXH_MANGA_DETAILS.enabled
            ) {
                val sManga = manga.copy(
                    url = EHentaiSearchMetadata.normalizeUrl(newerGallery.attr("href")),
                )
                client.newCall(mangaDetailsRequest(sManga)).awaitSuccess().asJsoup()
            } else {
                doc
            }
            return parseToManga(manga, pre)
        } else {
            response.close()

            if (response.code == 404) {
                throw GalleryNotFoundException(exception)
            } else {
                throw Exception("HTTP error ${response.code}", exception)
            }
        }
    }

    /**
     * Parse gallery page to metadata model
     */
    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun newMetaInstance() = EHentaiSearchMetadata()

    override suspend fun parseIntoMetadata(metadata: EHentaiSearchMetadata, input: Document) {
        with(metadata) {
            with(input) {
                val url = location()
                gId = EHentaiSearchMetadata.galleryId(url)
                gToken = EHentaiSearchMetadata.galleryToken(url)

                exh = this@EHentai.exh
                title = select("#gn").text().trimOrNull()

                altTitle = select("#gj").text().trimOrNull()

                thumbnailUrl = select("#gd1 div").attr("style").nullIfBlank()?.let {
                    it.substring(it.indexOf('(') + 1 until it.lastIndexOf(')'))
                }
                genre = select(".cs")
                    .attr("onclick")
                    .trimOrNull()
                    ?.substringAfterLast('/')
                    ?.removeSuffix("'")

                uploader = select("#gdn").text().trimOrNull()

                // Parse the table
                select("#gdd tr").forEach {
                    val left = it.select(".gdt1").text().trimOrNull()
                    val rightElement = it.selectFirst(".gdt2")!!
                    val right = rightElement.text().trimOrNull()
                    if (left != null && right != null) {
                        ignore {
                            when (left.removeSuffix(":").lowercase()) {
                                "posted" -> datePosted = ZonedDateTime.parse(
                                    right,
                                    MetadataUtil.EX_DATE_FORMAT.withZone(ZoneOffset.UTC),
                                ).toInstant().toEpochMilli()
                                // Example gallery with parent: https://e-hentai.org/g/1390451/7f181c2426/
                                // Example JP gallery: https://exhentai.org/g/1375385/03519d541b/
                                // Parent is older variation of the gallery
                                "parent" -> parent = if (!right.equals("None", true)) {
                                    rightElement.child(0).attr("href")
                                } else {
                                    null
                                }
                                "visible" -> visible = right.nullIfBlank()
                                "language" -> {
                                    language = right.removeSuffix(TR_SUFFIX).trimOrNull()
                                    translated = right.endsWith(TR_SUFFIX, true)
                                }
                                "file size" -> size = MetadataUtil.parseHumanReadableByteCount(right)?.toLong()
                                "length" -> length = right.removeSuffix("pages").trimOrNull()?.toInt()
                                "favorited" -> favorites = right.removeSuffix("times").trimOrNull()?.toInt()
                            }
                        }
                    }
                }

                lastUpdateCheck = System.currentTimeMillis()
                if (datePosted != null &&
                    lastUpdateCheck - datePosted!! > EHentaiUpdateWorkerConstants.GALLERY_AGE_TIME
                ) {
                    aged = true
                    this@EHentai.xLogD("aged %s - too old", title)
                }

                // Parse ratings
                ignore {
                    averageRating = select("#rating_label")
                        .text()
                        .removePrefix("Average:")
                        .trimOrNull()
                        ?.toDouble()
                    ratingCount = select("#rating_count")
                        .text()
                        .trimOrNull()
                        ?.toInt()
                }

                // Parse tags
                tags.clear()
                select("#taglist tr").forEach {
                    val namespace = it.select(".tc").text().removeSuffix(":")
                    tags += it.select("div").map { element ->
                        RaisedTag(
                            namespace,
                            element.text().trim(),
                            when {
                                element.hasClass("gtl") -> TAG_TYPE_LIGHT
                                element.hasClass("gtw") -> TAG_TYPE_WEAK
                                else -> TAG_TYPE_NORMAL
                            },
                        )
                    }
                }

                // Add genre as virtual tag
                genre?.let {
                    tags += RaisedTag(EH_GENRE_NAMESPACE, it, TAG_TYPE_VIRTUAL)
                }
                if (aged) {
                    tags += RaisedTag(EH_META_NAMESPACE, "aged", TAG_TYPE_VIRTUAL)
                }
                uploader?.let {
                    tags += RaisedTag(EH_UPLOADER_NAMESPACE, it, TAG_TYPE_VIRTUAL)
                }
                visible?.let {
                    tags += RaisedTag(
                        EH_VISIBILITY_NAMESPACE,
                        it.substringAfter('(').substringBeforeLast(')'),
                        TAG_TYPE_VIRTUAL,
                    )
                }
            }
        }
    }

    override fun chapterListParse(response: Response) =
        throw UnsupportedOperationException("Unused method was called somehow!")
    override fun chapterPageParse(
        response: Response,
    ) = throw UnsupportedOperationException("Unused method was called somehow!")

    override fun pageListParse(response: Response) =
        throw UnsupportedOperationException("Unused method was called somehow!")

    override suspend fun getImageUrl(page: Page): String {
        val imageUrlResponse = client.newCall(imageUrlRequest(page)).awaitSuccess()
        return realImageUrlParse(imageUrlResponse, page)
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getImageUrl"))
    override fun fetchImageUrl(page: Page): Observable<String> {
        return client.newCall(imageUrlRequest(page))
            .asObservableSuccess()
            .map { realImageUrlParse(it, page) }
    }

    private fun realImageUrlParse(response: Response, page: Page): String {
        with(response.asJsoup()) {
            val currentImage = getElementById("img")!!.attr("src")
            // Each press of the retry button will choose another server
            select("#loadfail").attr("onclick").nullIfBlank()?.let {
                page.url = addParam(page.url, "nl", it.substring(it.indexOf('\'') + 1 until it.lastIndexOf('\'')))
            }
            if (currentImage == "https://ehgt.org/g/509.gif") {
                throw Exception("Exceeded page quota")
            }
            return currentImage
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Unused method was called somehow!")
    }

    suspend fun fetchFavorites(): Pair<List<ParsedManga>, List<String>> {
        val favoriteUrl = "$baseUrl/favorites.php"
        val result = mutableListOf<ParsedManga>()
        var page = 1

        var favNames: List<String>? = null

        do {
            val response2 = withIOContext {
                client.newCall(
                    exGet(
                        favoriteUrl,
                        next = page,
                        cacheControl = CacheControl.FORCE_NETWORK,
                    ),
                ).await()
            }
            val doc = response2.asJsoup()

            // Parse favorites
            val parsed = extendedGenericMangaParse(doc)
            result += parsed.first

            // Parse fav names
            if (favNames == null) {
                favNames = doc.select(".fp:not(.fps)").mapNotNull {
                    it.child(2).text()
                }
            }
            // Next page

            page = parsed.first.lastOrNull()?.manga?.url?.let { EHentaiSearchMetadata.galleryId(it) }?.toInt() ?: 0
        } while (parsed.second != null)

        return Pair(result.toList(), favNames.orEmpty())
    }

    fun spPref() = if (exh) {
        exhPreferences.exhSettingsProfile()
    } else {
        exhPreferences.ehSettingsProfile()
    }

    private fun rawCookies(sp: Int): Map<String, String> {
        val cookies: MutableMap<String, String> = mutableMapOf()
        if (exhPreferences.enableExhentai().get()) {
            cookies[EhLoginActivity.MEMBER_ID_COOKIE] = exhPreferences.memberIdVal().get()
            cookies[EhLoginActivity.PASS_HASH_COOKIE] = exhPreferences.passHashVal().get()
            cookies[EhLoginActivity.IGNEOUS_COOKIE] = exhPreferences.igneousVal().get()
            cookies["sp"] = sp.toString()

            val sessionKey = exhPreferences.exhSettingsKey().get()
            if (sessionKey.isNotBlank()) {
                cookies["sk"] = sessionKey
            }

            val sessionCookie = exhPreferences.exhSessionCookie().get()
            if (sessionCookie.isNotBlank()) {
                cookies["s"] = sessionCookie
            }

            val hathPerksCookie = exhPreferences.exhHathPerksCookies().get()
            if (hathPerksCookie.isNotBlank()) {
                cookies["hath_perks"] = hathPerksCookie
            }
        }

        // Session-less extended display mode (for users without ExHentai)
        cookies["sl"] = "dm_2"

        // Ignore all content warnings ("Offensive For Everyone")
        cookies["nw"] = "1"

        return cookies
    }

    fun cookiesHeader(sp: Int = spPref().get()) = buildCookies(rawCookies(sp))

    // Headers
    override fun headersBuilder() = super.headersBuilder().add("Cookie", cookiesHeader())

    private fun addParam(url: String, param: String, value: String) = url.toUri()
        .buildUpon()
        .appendQueryParameter(param, value)
        .toString()

    override val client = network.client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .addInterceptor { chain ->
            val newReq = chain
                .request()
                .newBuilder()
                .removeHeader("Cookie")
                .addHeader("Cookie", cookiesHeader())
                .build()

            chain.proceed(newReq)
        }
        .addInterceptor(ThumbnailPreviewInterceptor())
        .build()

    // Filters
    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Note: Will ignore other parameters!"),
            ToplistOptions(),
            Filter.Separator(),
            AutoCompleteTags(),
            Watched(isEnabled = exhPreferences.exhWatchedListDefaultState().get()),
            GenreGroup(),
            AdvancedGroup(),
            ReverseFilter(),
            JumpSeekFilter(),
            Filter.Header("Seek to specific date: YYYY, (YY)YY-MM, (YY)YY-MM-DD"),
            Filter.Header("or Jump by number of days/weeks/months/years: 7d, 4w, 12m, 10y"),
        )
    }

    class Watched(val isEnabled: Boolean) : Filter.CheckBox("Watched List", isEnabled), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state) {
                builder.appendPath("watched")
            }
        }
    }

    enum class ToplistOption(val humanName: String, val index: Int) {
        NONE("None", 0),
        ALL_TIME("All time", 11),
        PAST_YEAR("Past year", 12),
        PAST_MONTH("Past month", 13),
        YESTERDAY("Yesterday", 15),
        ;

        override fun toString(): String {
            return humanName
        }
    }

    class ToplistOptions : Filter.Select<ToplistOption>(
        "Toplists",
        ToplistOption.entries.toTypedArray(),
    )

    class GenreOption(name: String, val genreId: Int) : Filter.CheckBox(name, false)
    class GenreGroup :
        Filter.Group<GenreOption>(
            "Genres",
            listOf(
                GenreOption("Dōjinshi", 2),
                GenreOption("Manga", 4),
                GenreOption("Artist CG", 8),
                GenreOption("Game CG", 16),
                GenreOption("Western", 512),
                GenreOption("Non-H", 256),
                GenreOption("Image Set", 32),
                GenreOption("Cosplay", 64),
                GenreOption("Asian Porn", 128),
                GenreOption("Misc", 1),
            ),
        ),
        UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            val bits = state.fold(0) { acc, genre ->
                if (!genre.state) acc + genre.genreId else acc
            }
            builder.appendQueryParameter("f_cats", bits.toString())
        }
    }

    class AdvancedOption(
        name: String,
        val param: String,
        defValue: Boolean = false,
    ) : Filter.CheckBox(name, defValue), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state) {
                builder.appendQueryParameter(param, "on")
            }
        }
    }

    open class PageOption(name: String, private val queryKey: String) : Filter.Text(name), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state.isNotBlank()) {
                if (builder.build().getQueryParameters("f_sp").isEmpty()) {
                    builder.appendQueryParameter("f_sp", "on")
                }

                builder.appendQueryParameter(queryKey, state.trim())
            }
        }
    }

    private fun combineQuery(filters: FilterList): String {
        val stringBuilder = StringBuilder()
        val advSearch = filters.filterIsInstance<Filter.AutoComplete>().flatMap { filter ->
            filter.state.trimAll().dropBlank().mapNotNull { tag ->
                val split = tag.split(":").filterNot { it.isBlank() }
                if (split.size > 1) {
                    val namespace = split[0].removePrefix("-").removePrefix("~")
                    val exclude = split[0].startsWith("-")
                    val or = split[0].startsWith("~")

                    AdvSearchEntry(namespace to split[1], exclude, or)
                } else if (split.size == 1) {
                    val item = split.first()
                    val exclude = item.startsWith("-")
                    val or = item.startsWith("~")
                    AdvSearchEntry(null to item, exclude, or)
                } else {
                    null
                }
            }
        }

        advSearch.forEach { entry ->
            if (entry.exclude) stringBuilder.append("-")
            if (entry.or) stringBuilder.append("~")
            val namespace = entry.search.first?.let { "$it:" }.orEmpty()
            if (entry.search.second.contains(" ")) {
                stringBuilder.append(("""$namespace"${entry.search.second}$""""))
            } else {
                stringBuilder.append("$namespace${entry.search.second}$")
            }
            stringBuilder.append(" ")
        }

        return stringBuilder.toString().trim().also { xLogD(it) }
    }

    data class AdvSearchEntry(val search: Pair<String?, String>, val exclude: Boolean, val or: Boolean)

    class AutoCompleteTags :
        Filter.AutoComplete(
            name = "Tags",
            hint = "Search tags here (limit of 8)",
            values = EHTags.getNamespaces().map { "$it:" } + EHTags.getAllTags(),
            skipAutoFillTags = EHTags.getNamespaces().map { "$it:" },
            validPrefixes = listOf("-", "~"),
            state = emptyList(),
        )

    class MinPagesOption : PageOption("Minimum Pages", "f_spf")
    class MaxPagesOption : PageOption("Maximum Pages", "f_spt")

    class RatingOption :
        Filter.Select<String>(
            "Minimum Rating",
            arrayOf(
                "Any",
                "2 stars",
                "3 stars",
                "4 stars",
                "5 stars",
            ),
        ),
        UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state > 0) {
                builder.appendQueryParameter("f_srdd", (state + 1).toString())
                builder.appendQueryParameter("f_sr", "on")
            }
        }
    }

    class AdvancedGroup : UriGroup<Filter<*>>(
        "Advanced Options",
        listOf(
            AdvancedOption("Browse Expunged Galleries", "f_sh"),
            AdvancedOption("Require Gallery Torrent", "f_sto"),
            RatingOption(),
            MinPagesOption(),
            MaxPagesOption(),
            AdvancedOption("Disable custom Language filters", "f_sfl"),
            AdvancedOption("Disable custom Uploader filters", "f_sfu"),
            AdvancedOption("Disable custom Tag filters", "f_sft"),
        ),
    )

    class ReverseFilter : Filter.CheckBox("Reverse search results")

    class JumpSeekFilter : Filter.Text("Jump/Seek")

    override val name = if (exh) {
        "ExHentai"
    } else {
        "E-Hentai"
    }

    class GalleryNotFoundException(cause: Throwable) : RuntimeException("Gallery not found!", cause)

    // === URL IMPORT STUFF

    override val matchingHosts: List<String> = if (exh) {
        listOf(
            "exhentai.org",
        )
    } else {
        listOf(
            "g.e-hentai.org",
            "e-hentai.org",
        )
    }

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        return when (uri.pathSegments.firstOrNull()) {
            "g" -> {
                // Is already gallery page, do nothing
                uri.toString()
            }
            "s" -> {
                // Is page, fetch gallery token and use that
                getGalleryUrlFromPage(uri)
            }
            else -> null
        }
    }

    override fun cleanMangaUrl(url: String): String {
        return EHentaiSearchMetadata.normalizeUrl(super.cleanMangaUrl(url))
    }

    private fun getGalleryUrlFromPage(uri: Uri): String {
        val lastSplit = uri.pathSegments.last().split("-")
        val pageNum = lastSplit.last()
        val gallery = lastSplit.first()
        val pageToken = uri.pathSegments.elementAt(1)

        val json = buildJsonObject {
            put("method", "gtoken")
            put(
                "pagelist",
                buildJsonArray {
                    add(
                        buildJsonArray {
                            add(gallery.toInt())
                            add(pageToken)
                            add(pageNum.toInt())
                        },
                    )
                },
            )
        }

        val outJson = Json.decodeFromString<JsonObject>(
            client.newCall(
                Request.Builder()
                    .url(EH_API_BASE)
                    .post(json.toString().toRequestBody(JSON))
                    .build(),
            ).execute().body.string(),
        )

        val obj = outJson["tokenlist"]!!.jsonArray.first().jsonObject
        return "${uri.scheme}://${uri.host}/g/${obj["gid"]!!.jsonPrimitive.int}/${
            obj["token"]!!.jsonPrimitive.content
        }/"
    }

    override suspend fun getPagePreviewList(
        manga: SManga,
        chapters: List<SChapter>,
        page: Int,
    ): PagePreviewPage {
        val doc = client.newCall(
            exGet(
                (baseUrl + (chapters.lastOrNull()?.url ?: manga.url))
                    .toHttpUrl()
                    .newBuilder()
                    .removeAllQueryParameters("nw")
                    .addQueryParameter("p", (page - 1).toString())
                    .build()
                    .toString(),
            ),
        ).awaitSuccess().asJsoup()

        val body = doc.body()
        val previews = body
            .select("#gdt > div > div")
            .plus(body.select("#gdt > a"))
            .map {
                val preview = parseNormalPreview(it)
                PagePreviewInfo(preview.index, imageUrl = preview.toUrl())
            }
            .ifEmpty {
                body.select("#gdt div a img")
                    .map {
                        PagePreviewInfo(
                            it.attr("alt").toInt(),
                            imageUrl = it.attr("src"),
                        )
                    }
            }

        return PagePreviewPage(
            page = page,
            pagePreviews = previews,
            hasNextPage = doc.select("table.ptt tbody tr td")
                .last()!!
                .hasClass("ptdd")
                .not(),
            pagePreviewPages = doc.select("table.ptt tbody tr td a").asReversed()
                .firstNotNullOfOrNull { it.text().toIntOrNull() },
        )
    }

    override suspend fun fetchPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl?): Response {
        return client.newCachelessCallWithProgress(exGet(page.imageUrl, cacheControl = cacheControl), page)
            .awaitSuccess()
    }

    /**
     * Parse normal previews with regular expressions
     */
    private fun parseNormalPreview(element: Element): EHentaiThumbnailPreview {
        val imgElement = element.selectFirst("img")
        val index = imgElement?.attr("alt")?.toInt()
            ?: element.child(0).attr("title").removePrefix("Page ").substringBefore(":").toInt()
        val styleElement = if (imgElement != null) {
            element
        } else {
            element.child(0)
        }
        val styles = styleElement.attr("style").split(";").mapNotNull { it.trimOrNull() }
        val width = styles.first { it.startsWith("width:") }
            .removePrefix("width:")
            .removeSuffix("px")
            .toInt()

        val height = styles.first { it.startsWith("height:") }
            .removePrefix("height:")
            .removeSuffix("px")
            .toInt()

        val background = styles.first { it.startsWith("background:") }
            .removePrefix("background:")
            .split(" ")

        val url = background.first { it.startsWith("url(") }
            .removePrefix("url(")
            .removeSuffix(")")

        val widthOffset = background.first { it.startsWith("-") }
            .removePrefix("-")
            .removeSuffix("px")
            .toInt()

        return EHentaiThumbnailPreview(url, width, height, widthOffset, index)
    }
    data class EHentaiThumbnailPreview(
        val imageUrl: String,
        val width: Int,
        val height: Int,
        val widthOffset: Int,
        val index: Int,
    ) {
        fun toUrl(): String {
            return BLANK_PREVIEW_THUMB.toHttpUrl().newBuilder()
                .addQueryParameter("imageUrl", imageUrl)
                .addQueryParameter("width", width.toString())
                .addQueryParameter("height", height.toString())
                .addQueryParameter("widthOffset", widthOffset.toString())
                .build()
                .toString()
        }

        companion object {
            fun parseFromUrl(url: HttpUrl) = EHentaiThumbnailPreview(
                imageUrl = url.queryParameter("imageUrl")!!,
                width = url.queryParameter("width")!!.toInt(),
                height = url.queryParameter("height")!!.toInt(),
                widthOffset = url.queryParameter("widthOffset")!!.toInt(),
                index = -1,
            )
        }
    }

    private class ThumbnailPreviewInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            if (request.url.host == THUMB_DOMAIN && request.url.pathSegments.contains(BLANK_THUMB)) {
                val thumbnailPreview = EHentaiThumbnailPreview.parseFromUrl(request.url)
                val response = chain.proceed(request.newBuilder().url(thumbnailPreview.imageUrl).build())
                if (response.isSuccessful) {
                    val body = ByteArrayOutputStream()
                        .use {
                            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
                                ?: throw IOException("Null bitmap($thumbnailPreview)")
                            Bitmap.createBitmap(
                                bitmap,
                                thumbnailPreview.widthOffset,
                                0,
                                thumbnailPreview.width.coerceAtMost(bitmap.width - thumbnailPreview.widthOffset),
                                thumbnailPreview.height.coerceAtMost(bitmap.height),
                            ).compress(Bitmap.CompressFormat.JPEG, 100, it)
                            it.toByteArray()
                        }
                        .toResponseBody("image/jpeg".toMediaType())

                    return response.newBuilder().body(body).build()
                } else {
                    return response
                }
            }

            return chain.proceed(request)
        }
    }

    companion object {
        private const val TR_SUFFIX = "TR"
        private const val REVERSE_PARAM = "TEH_REVERSE"
        private val PAGE_COUNT_REGEX = "[0-9]*".toRegex()
        private val RATING_REGEX = "([0-9]*)px".toRegex()
        private const val THUMB_DOMAIN = "ehgt.org"
        private const val BLANK_THUMB = "blank.gif"
        private const val BLANK_PREVIEW_THUMB = "https://$THUMB_DOMAIN/g/$BLANK_THUMB"

        private val MATCH_YEAR_REGEX = "^\\d{4}\$".toRegex()
        private val MATCH_SEEK_REGEX = "^\\d{2,4}-\\d{1,2}(-\\d{1,2})?\$".toRegex()
        private val MATCH_JUMP_REGEX = "^\\d+(\$|d\$|w\$|m\$|y\$|-\$)".toRegex()

        private const val EH_API_BASE = "https://api.e-hentai.org/api.php"
        private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()!!

        private val FAVORITES_BORDER_HEX_COLORS = listOf(
            "000",
            "f00",
            "fa0",
            "dd0",
            "080",
            "9f4",
            "4bf",
            "00f",
            "508",
            "e8e",
        )

        fun buildCookies(cookies: Map<String, String>) = cookies.entries.joinToString(separator = "; ") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

        // KMK -->
        val languageMapping = mapOf(
            "ja" to "japanese",
            "en" to "english",
            "zh" to "chinese",
            "nl" to "dutch",
            "fr" to "french",
            "de" to "german",
            "hu" to "hungarian",
            "it" to "italian",
            "ko" to "korean",
            "pl" to "polish",
            "pt-BR" to "portuguese",
            "ru" to "russian",
            "es" to "spanish",
            "th" to "thai",
            "vi" to "vietnamese",
            "none" to "n/a",
            "other" to "other",
        )
        // KMK <--
    }
}
