package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.PagePreviewInfo
import eu.kanade.tachiyomi.source.PagePreviewPage
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.trimOrNull
import exh.util.urlImportFetchSearchManga
import exh.util.urlImportFetchSearchMangaSuspend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Response
import org.jsoup.nodes.Document

class NHentai(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<NHentaiSearchMetadata, Response>,
    UrlImportableSource,
    NamespaceSource,
    PagePreviewSource {
    override val metaClass = NHentaiSearchMetadata::class
    override fun newMetaInstance() = NHentaiSearchMetadata()
    override val lang = delegate.lang

    private val sourcePreferences: SharedPreferences by lazy {
        context.getSharedPreferences("source_$id", 0x0000)
    }

    private val preferredTitle: Int
        get() = when (sourcePreferences.getString(TITLE_PREF, "full")) {
            "full" -> NHentaiSearchMetadata.TITLE_TYPE_ENGLISH
            else -> NHentaiSearchMetadata.TITLE_TYPE_SHORT
        }

    // Support direct URL importing
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchManga"))
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        urlImportFetchSearchManga(context, query) {
            @Suppress("DEPRECATION")
            super<DelegatedHttpSource>.fetchSearchManga(page, query, filters)
        }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val idQuery = when {
            query.startsWith("id:", ignoreCase = true) -> query.removePrefix("id:").trim()
            query.toLongOrNull() != null -> query.trim()
            else -> null
        }

        if (!idQuery.isNullOrBlank()) {
            val manga = SManga.create().apply {
                url = "/g/$idQuery/"
                title = idQuery
            }

            val response = client.newCall(mangaDetailsRequest(manga)).awaitSuccess()
            val body = response.body.string()
            val doc = response.asJsoup(body)

            val fallback = buildFallbackMangaFromHtml(manga, doc, body)
            fallback.url = manga.url

            return MangasPage(listOf(fallback), false)
        }

        return urlImportFetchSearchMangaSuspend(context, query) {
            super<DelegatedHttpSource>.getSearchManga(page, query, filters)
        }
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val metadataResponse = client.newCall(mangaDetailsRequest(manga)).awaitSuccess()

        val refreshedMetadata = newMetaInstance()
        parseIntoMetadata(refreshedMetadata, metadataResponse)

        val fallbackResponse = client.newCall(mangaDetailsRequest(manga)).awaitSuccess()
        val body = fallbackResponse.body.string()
        val doc = fallbackResponse.asJsoup(body)
        applyHtmlFallback(refreshedMetadata, doc, body)

        val mangaId = manga.id()
        if (mangaId != null) {
            refreshedMetadata.mangaId = mangaId
            insertFlatMetadata.await(refreshedMetadata)
        }

        val parsed = refreshedMetadata.createMangaInfo(manga)

        if (parsed.thumbnail_url.isNullOrBlank() || parsed.description.isNullOrBlank() || parsed.title.isNullOrBlank()) {
            val fallbackManga = buildFallbackMangaFromHtml(manga, doc, body)

            if (!fallbackManga.thumbnail_url.isNullOrBlank()) {
                parsed.thumbnail_url = fallbackManga.thumbnail_url
            }
            if (!fallbackManga.description.isNullOrBlank()) {
                parsed.description = fallbackManga.description
            }
            if (!fallbackManga.title.isNullOrBlank()) {
                parsed.title = fallbackManga.title
            }
            if (!fallbackManga.author.isNullOrBlank()) {
                parsed.author = fallbackManga.author
            }
            if (!fallbackManga.artist.isNullOrBlank()) {
                parsed.artist = fallbackManga.artist
            }
            if (!fallbackManga.genre.isNullOrBlank()) {
                parsed.genre = fallbackManga.genre
            }
        }

        return parsed
    }

    override suspend fun parseIntoMetadata(metadata: NHentaiSearchMetadata, input: Response) {
        val body = input.body.string()
        val doc = input.asJsoup(body)
        val fallbackNhId = input.request.url.pathSegments.lastOrNull { it.isNotBlank() }?.toLongOrNull()

        if ("just a moment" in doc.title().lowercase() || doc.selectFirst("#challenge-error-text") != null) {
            with(metadata) {
                preferredTitle = this@NHentai.preferredTitle
                if (nhId == null) {
                    nhId = fallbackNhId
                }
            }
            return
        }

        val server = MEDIA_SERVER_REGEX.find(body)?.groupValues?.get(1)?.toInt() ?: 1
        val jsonEscaped = extractEscapedGalleryJson(body)

        if (jsonEscaped.isNullOrBlank()) {
            with(metadata) {
                preferredTitle = this@NHentai.preferredTitle
                mediaServer = server
                if (nhId == null) {
                    nhId = fallbackNhId
                }
                applyHtmlFallback(this, doc, body)
            }
            return
        }

        val json = jsonEscaped.replace(
            UNICODE_ESCAPE_REGEX,
        ) { it.groupValues[1].toInt(radix = 16).toChar().toString() }
        val jsonResponse = runCatching { jsonParser.decodeFromString<JsonResponse>(json) }.getOrNull()
        if (jsonResponse == null) {
            with(metadata) {
                preferredTitle = this@NHentai.preferredTitle
                mediaServer = server
                if (nhId == null) {
                    nhId = fallbackNhId
                }
                applyHtmlFallback(this, doc, body)
            }
            return
        }

        with(metadata) {
            nhId = jsonResponse.id

            uploadDate = jsonResponse.uploadDate

            favoritesCount = jsonResponse.numFavorites

            mediaId = jsonResponse.mediaId

            mediaServer = server

            jsonResponse.title?.let { title ->
                japaneseTitle = title.japanese
                shortTitle = title.pretty
                englishTitle = title.english
            }

            preferredTitle = this@NHentai.preferredTitle

            jsonResponse.images?.let { images ->
                coverImageType = doc.parseCoverType()
                    ?: images.cover?.type
                images.pages.mapNotNull {
                    it.type
                }.let {
                    pageImageTypes = it
                }
                thumbnailImageType = images.thumbnail?.type
            }

            // Some responses provide num_pages but omit per-page image types.
            if (pageImageTypes.isEmpty()) {
                val fallbackCount = jsonResponse.numPages ?: extractPageCount(doc, normalizeEscapedBody(body))
                if (fallbackCount != null && fallbackCount > 0) {
                    val fallbackType = thumbnailImageType ?: coverImageType ?: "j"
                    pageImageTypes = List(fallbackCount) { fallbackType }
                }
            }

            scanlator = jsonResponse.scanlator?.trimOrNull()

            tags.clear()
            jsonResponse.tags.mapNotNull { tag ->
                val type = tag.type ?: return@mapNotNull null
                val name = tag.name ?: return@mapNotNull null
                RaisedTag(
                    type,
                    name,
                    if (type == NHentaiSearchMetadata.NHENTAI_CATEGORIES_NAMESPACE) {
                        RaisedSearchMetadata.TAG_TYPE_VIRTUAL
                    } else {
                        NHentaiSearchMetadata.TAG_TYPE_DEFAULT
                    },
                )
            }.let(tags::addAll)

            applyHtmlFallback(this, doc, body)
        }
    }

    // KMK -->
    /**
     * Site JSON is saying cover of type `w` but instead it's using cover like `cover.jpg.webp`
     */
    private fun Document.parseCoverType(): String? {
        val coverUrl = selectFirst("#cover > a > img")?.let {
            it.attr("abs:data-src").ifBlank {
                it.attr("abs:data-lazy-src").ifBlank {
                    it.attr("abs:src")
                }
            }
        }?.ifBlank {
            selectFirst("meta[property=\"og:image\"]")?.attr("abs:content")
        }

        return coverUrl
            ?.substringAfterLast('/')
            ?.substringAfter('.')
            ?.lowercase()
            ?.let(::extensionToNhentaiType)
    }

    private fun applyHtmlFallback(metadata: NHentaiSearchMetadata, doc: Document, body: String) {
        val normalizedBody = normalizeEscapedBody(body)

        if (metadata.mediaId.isNullOrBlank()) {
            metadata.mediaId = extractMediaId(doc, normalizedBody)
        }

        metadata.coverImageType = metadata.coverImageType ?: doc.parseCoverType()
        metadata.thumbnailImageType = metadata.thumbnailImageType ?: metadata.coverImageType

        val pageTypesFromThumbs = extractPageTypesFromThumbnails(normalizedBody)
        if (metadata.pageImageTypes.isEmpty() && pageTypesFromThumbs.isNotEmpty()) {
            metadata.pageImageTypes = pageTypesFromThumbs
        }

        val fallbackPageCount = extractPageCount(doc, normalizedBody)
        if (metadata.pageImageTypes.isEmpty() && fallbackPageCount != null && fallbackPageCount > 0) {
            val fallbackType = metadata.thumbnailImageType ?: metadata.coverImageType ?: "j"
            metadata.pageImageTypes = List(fallbackPageCount) { fallbackType }
        }

        if (metadata.uploadDate == null) {
            metadata.uploadDate = extractUploadDate(doc, normalizedBody)
        }

        if (metadata.favoritesCount == null) {
            metadata.favoritesCount = extractFavoritesCount(doc, normalizedBody)
        }

        if (metadata.tags.isEmpty()) {
            val tags = extractTagsFromHtml(doc)
            if (tags.isNotEmpty()) {
                metadata.tags.addAll(tags)
            }
        }

        if (metadata.englishTitle.isNullOrBlank() || metadata.shortTitle.isNullOrBlank()) {
            val title = doc.selectFirst("#info h1.title .pretty")?.text()
                ?: doc.selectFirst("#info h1")?.text()
                ?: doc.title().substringBefore("|").trim()
            if (metadata.englishTitle.isNullOrBlank()) metadata.englishTitle = title
            if (metadata.shortTitle.isNullOrBlank()) metadata.shortTitle = title
        }
    }

    private fun extractTagsFromHtml(doc: Document): List<RaisedTag> {
        return doc.select("#tags .tag-container a[href]")
            .mapNotNull { link ->
                val href = link.attr("href").trim()
                val namespace = href.removePrefix("/").substringBefore('/').trim().lowercase()
                if (namespace.isBlank()) return@mapNotNull null

                val name = link.selectFirst(".name")?.text()?.trim()
                    ?: link.ownText().trim()
                if (name.isBlank()) return@mapNotNull null

                RaisedTag(
                    namespace,
                    name,
                    if (namespace == NHentaiSearchMetadata.NHENTAI_CATEGORIES_NAMESPACE) {
                        RaisedSearchMetadata.TAG_TYPE_VIRTUAL
                    } else {
                        NHentaiSearchMetadata.TAG_TYPE_DEFAULT
                    },
                )
            }
            .distinctBy { it.namespace + ":" + it.name }
    }

    private fun extractEscapedGalleryJson(body: String): String? {
        return GALLERY_JSON_REGEX.find(body)?.groupValues?.getOrNull(1)
            ?: GALLERY_JSON_REGEX_SINGLE_QUOTE.find(body)?.groupValues?.getOrNull(1)
            ?: GALLERY_JSON_REGEX_DECODE_URI.find(body)?.groupValues?.getOrNull(1)
            ?: GALLERY_JSON_REGEX_DECODE_URI_SINGLE_QUOTE.find(body)?.groupValues?.getOrNull(1)
    }

    private fun extractMediaId(doc: Document, body: String): String? {
        return MEDIA_ID_REGEX.find(body)?.groupValues?.getOrNull(1)?.parseDigitsToLong()?.toString()
            ?: Regex("""/galleries/(\\d+)/""").find(doc.selectFirst("meta[property=\"og:image\"]")?.attr("abs:content").orEmpty())?.groupValues?.getOrNull(1)
            ?: Regex("""/galleries/(\\d+)/""").find(
                doc.selectFirst("#cover > a > img")?.let {
                    it.attr("abs:data-src").ifBlank {
                        it.attr("abs:data-lazy-src").ifBlank {
                            it.attr("abs:src")
                        }
                    }
                }.orEmpty(),
            )?.groupValues?.getOrNull(1)
    }

    private fun extractPageTypesFromThumbnails(body: String): List<String> {
        return THUMB_REGEX.findAll(body)
            .mapNotNull { match ->
                val page = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val type = extensionToNhentaiType(match.groupValues[3]) ?: return@mapNotNull null
                page to type
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .map { it.second }
            .toList()
    }

    private fun extensionToNhentaiType(ext: String): String? {
        return when (ext.lowercase()) {
            "j", "jpg", "jpeg" -> "j"
            "p", "png" -> "p"
            "g", "gif" -> "g"
            "w", "webp" -> "w"
            else -> null
        }
    }

    private fun extractMediaServerFromBodyOrHost(body: String): Int {
        val normalizedBody = normalizeEscapedBody(body)
        val mediaServer = MEDIA_SERVER_REGEX.find(normalizedBody)?.groupValues?.get(1)?.toIntOrNull()
        if (mediaServer != null) return mediaServer

        val thumbHost = THUMB_CDN_HOST_REGEX.find(normalizedBody)?.groupValues?.getOrNull(1)
        return thumbHost?.toIntOrNull() ?: 1
    }

    private fun extractPageCount(doc: Document, body: String): Int? {
        return NUM_PAGES_REGEX.find(body)?.groupValues?.getOrNull(1)?.parseDigitsToInt()
            ?: doc.select(".thumb-container img, .thumbnail-container img, #thumbnail-container img").size
                .takeIf { it > 0 }
            ?: doc.select(".tag-container:contains(Pages) .name")
                .firstOrNull()
                ?.text()
                ?.filter { it.isDigit() }
                ?.toIntOrNull()
    }

    private fun extractUploadDate(doc: Document, body: String): Long? {
        return UPLOAD_DATE_REGEX.find(body)?.groupValues?.getOrNull(1)?.parseDigitsToLong()
            ?: doc.selectFirst("time[datetime]")
                ?.attr("data-unix")
                ?.toLongOrNull()
            ?: doc.selectFirst("time[datetime]")
                ?.attr("datetime")
                ?.toLongOrNull()
    }

    private fun extractFavoritesCount(doc: Document, body: String): Long? {
        return NUM_FAVORITES_REGEX.find(body)?.groupValues?.getOrNull(1)?.parseDigitsToLong()
            ?: doc.select(".tag-container:contains(Favorites) .name")
                .firstOrNull()
                ?.text()
                ?.replace(",", "")
                ?.toLongOrNull()
    }

    private fun extractDirectThumbnailUrls(body: String): List<String> {
        val normalizedBody = normalizeEscapedBody(body)
        val thumbHost = THUMB_CDN_HOST_REGEX.find(normalizedBody)?.groupValues?.getOrNull(1)
            ?.let { "https://t$it.nhentai.net" }

        return THUMB_URL_REGEX.findAll(normalizedBody)
            .mapNotNull { match ->
                val pageNumber = match.groupValues[2].toIntOrNull()
                    ?: match.groupValues[4].toIntOrNull()
                    ?: return@mapNotNull null

                val raw = match.value
                val absolute = when {
                    raw.startsWith("http://") || raw.startsWith("https://") -> raw
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("/galleries/") -> {
                        val host = thumbHost ?: return@mapNotNull null
                        "$host$raw"
                    }
                    else -> return@mapNotNull null
                }

                pageNumber to absolute
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .map { it.second }
            .toList()
    }

    private fun extractThumbnailUrlsFromDocument(doc: Document): List<String> {
        return doc.select("#thumbnail-container img, .thumb-container img, .gallerythumb img, .thumb img")
            .mapNotNull { img ->
                val direct = img.attr("abs:data-src").ifBlank {
                    img.attr("abs:data-lazy-src").ifBlank {
                        img.attr("abs:src")
                    }
                }

                val srcset = img.attr("data-srcset").ifBlank { img.attr("srcset") }
                val srcsetUrl = srcset.substringBefore(',').substringBefore(' ').trim()

                val url = direct.ifBlank { srcsetUrl }
                when {
                    url.isBlank() -> null
                    url.startsWith("http://") || url.startsWith("https://") -> url
                    url.startsWith("//") -> "https:$url"
                    else -> null
                }
            }
            .distinct()
    }

    private fun normalizeEscapedBody(body: String): String {
        return body.replace("\\/", "/").replace("\\\"", "\"")
    }

    private fun String.parseDigitsToInt(): Int? {
        return filter { it.isDigit() }.toIntOrNull()
    }

    private fun String.parseDigitsToLong(): Long? {
        return filter { it.isDigit() }.toLongOrNull()
    }
    // KMK <--

    @Serializable
    data class JsonResponse(
        val id: Long,
        @SerialName("media_id")
        val mediaId: String? = null,
        val title: JsonTitle? = null,
        val images: JsonImages? = null,
        val scanlator: String? = null,
        @SerialName("upload_date")
        val uploadDate: Long? = null,
        val tags: List<JsonTag> = emptyList(),
        @SerialName("num_pages")
        val numPages: Int? = null,
        @SerialName("num_favorites")
        val numFavorites: Long? = null,
    )

    @Serializable
    data class JsonTitle(
        val english: String? = null,
        val japanese: String? = null,
        val pretty: String? = null,
    )

    @Serializable
    data class JsonImages(
        val pages: List<JsonPage> = emptyList(),
        val cover: JsonPage? = null,
        val thumbnail: JsonPage? = null,
    )

    @Serializable
    data class JsonPage(
        @SerialName("t")
        val type: String? = null,
        @SerialName("w")
        val width: Long? = null,
        @SerialName("h")
        val height: Long? = null,
    )

    @Serializable
    data class JsonTag(
        val id: Long? = null,
        val type: String? = null,
        val name: String? = null,
        val url: String? = null,
        val count: Long? = null,
    )

    override val matchingHosts = listOf(
        "nhentai.net",
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        if (uri.pathSegments.firstOrNull()?.lowercase() != "g") {
            return null
        }

        return "$baseUrl/g/${uri.pathSegments[1]}/"
    }

    override suspend fun getPagePreviewList(manga: SManga, chapters: List<SChapter>, page: Int): PagePreviewPage {
        return runCatching {
            val metadata = fetchOrLoadMetadata(manga.id()) {
                client.newCall(mangaDetailsRequest(manga)).awaitSuccess()
            }

            val fallbackResponse = client.newCall(mangaDetailsRequest(manga)).awaitSuccess()
            val body = fallbackResponse.body.string()
            val doc = fallbackResponse.asJsoup(body)

            if ("just a moment" in doc.title().lowercase() || doc.selectFirst("#challenge-error-text") != null) {
                return@runCatching PagePreviewPage(page, emptyList(), false, 1)
            }

            val domThumbnailUrls = extractThumbnailUrlsFromDocument(doc)
            if (domThumbnailUrls.isNotEmpty()) {
                return@runCatching PagePreviewPage(
                    page,
                    domThumbnailUrls.mapIndexed { index, url ->
                        PagePreviewInfo(index + 1, imageUrl = url)
                    },
                    false,
                    1,
                )
            }

            val directThumbnailUrls = extractDirectThumbnailUrls(body)
            if (directThumbnailUrls.isNotEmpty()) {
                return@runCatching PagePreviewPage(
                    page,
                    directThumbnailUrls.mapIndexed { index, url ->
                        PagePreviewInfo(index + 1, imageUrl = url)
                    },
                    false,
                    1,
                )
            }

            val mediaId = metadata.mediaId
            val pageTypes = metadata.pageImageTypes
            if (!mediaId.isNullOrBlank() && pageTypes.isNotEmpty()) {
                return@runCatching PagePreviewPage(
                    page,
                    pageTypes.mapIndexed { index, s ->
                        thumbnailUrlFromType(
                            mediaId,
                            metadata.mediaServer ?: 1,
                            index + 1,
                            s,
                        )?.let { url ->
                            PagePreviewInfo(index + 1, imageUrl = url)
                        }
                    }.filterNotNull(),
                    false,
                    1,
                )
            }

            val fallbackMediaId = extractMediaId(doc, body)
            val fallbackServer = extractMediaServerFromBodyOrHost(body)
            val fallbackTypes = extractPageTypesFromThumbnails(body).ifEmpty {
                val count = NUM_PAGES_REGEX.find(body)?.groupValues?.getOrNull(1)?.parseDigitsToInt()
                val fallbackType = doc.parseCoverType() ?: "j"
                if (count != null && count > 0) List(count) { fallbackType } else emptyList()
            }

            if (!fallbackMediaId.isNullOrBlank() && fallbackTypes.isNotEmpty()) {
                return@runCatching PagePreviewPage(
                    page,
                    fallbackTypes.mapIndexed { index, s ->
                        thumbnailUrlFromType(
                            fallbackMediaId,
                            fallbackServer,
                            index + 1,
                            s,
                        )?.let { url ->
                            PagePreviewInfo(index + 1, imageUrl = url)
                        }
                    }.filterNotNull(),
                    false,
                    1,
                )
            }

            // Last resort: show cover as at least one preview so section is visible.
            val cover = buildFallbackMangaFromHtml(manga, doc, body).thumbnail_url
            if (!cover.isNullOrBlank()) {
                PagePreviewPage(page, listOf(PagePreviewInfo(1, cover)), false, 1)
            } else {
                PagePreviewPage(page, emptyList(), false, 1)
            }
        }.getOrElse {
            PagePreviewPage(page, emptyList(), false, 1)
        }
    }

    private fun buildFallbackMangaFromHtml(manga: SManga, doc: Document, body: String): SManga {
        val fullTitle = doc.selectFirst("#info h1.title .pretty")?.text()
            ?: doc.selectFirst("#info h1")?.text()
            ?: doc.title().substringBefore("|").trim().ifBlank { manga.title.ifBlank { "Untitled" } }
        val shortTitle = fullTitle.replace(SHORTEN_TITLE_REGEX, "").trim().ifBlank { fullTitle }
        val title = if (preferredTitle == NHentaiSearchMetadata.TITLE_TYPE_SHORT) shortTitle else fullTitle

        val coverUrl = doc.selectFirst("#cover > a > img")?.let {
            it.attr("abs:data-src").ifBlank {
                it.attr("abs:data-lazy-src").ifBlank {
                    it.attr("abs:src")
                }
            }
        }?.ifBlank {
            doc.selectFirst("meta[property=\"og:image\"]")?.attr("abs:content")
        } ?: extractDirectThumbnailUrls(body).firstOrNull()

        val pageCount = NUM_PAGES_REGEX.find(body)?.groupValues?.getOrNull(1)?.parseDigitsToInt()
            ?: extractPageTypesFromThumbnails(body).size.takeIf { it > 0 }
            ?: extractPageCount(doc, body)
        val favorites = NUM_FAVORITES_REGEX.find(body)?.groupValues?.getOrNull(1)?.parseDigitsToLong()
            ?: extractFavoritesCount(doc, body)

        val artist = doc.select("a[href^=/artist/] span.name")
            .eachText()
            .joinToString(", ")
            .ifBlank { "Unknown" }
        val groups = doc.select("a[href^=/group/] span.name")
            .eachText()
            .joinToString(", ")

        val tags = doc.select("a[href^=/tag/] span.name")
            .eachText()
            .joinToString(", ")
            .ifBlank { "Unknown" }

        val description = buildString {
            append("Full English and Japanese titles:\n")
            append(title)
            append("\n\n")
            append("Pages: ")
            append(pageCount ?: 0)
            append("\n")
            append("Favorited by: ")
            append(favorites ?: 0)
        }

        return SManga.create().apply {
            url = manga.url
            this.title = title
            thumbnail_url = coverUrl
            this.artist = artist
            this.author = groups.ifBlank { artist }
            genre = tags
            this.description = description
            status = SManga.COMPLETED
        }
    }

    private fun thumbnailUrlFromType(
        mediaId: String,
        mediaServer: Int,
        page: Int,
        t: String,
    ) = NHentaiSearchMetadata.typeToExtension(t)?.let {
        "https://t$mediaServer.nhentai.net/galleries/$mediaId/${page}t.$it"
    }

    override suspend fun fetchPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl?): Response {
        val requestHeaders = headersBuilder()
            .add("Referer", "$baseUrl/")
            .build()

        return client.newCachelessCallWithProgress(
            if (cacheControl != null) {
                GET(page.imageUrl, headers = requestHeaders, cache = cacheControl)
            } else {
                GET(page.imageUrl, headers = requestHeaders)
            },
            page,
        ).awaitSuccess()
    }

    companion object {
        private val jsonParser = Json {
            ignoreUnknownKeys = true
        }

        private val GALLERY_JSON_REGEX = Regex("""JSON\\.parse\\(\\s*\"(.*?)\"\\s*\\)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val GALLERY_JSON_REGEX_SINGLE_QUOTE = Regex("""JSON\\.parse\\(\\s*'(.*?)'\\s*\\)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val GALLERY_JSON_REGEX_DECODE_URI = Regex("""JSON\\.parse\\(\\s*decodeURIComponent\\(\\s*\"(.*?)\"\\s*\\)\\s*\\)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val GALLERY_JSON_REGEX_DECODE_URI_SINGLE_QUOTE = Regex("""JSON\\.parse\\(\\s*decodeURIComponent\\(\\s*'(.*?)'\\s*\\)\\s*\\)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val MEDIA_SERVER_REGEX = Regex("media_server\\s*:\\s*(\\d+)")
        private val MEDIA_ID_REGEX = Regex("""\"media_id\"\s*:\s*\"?([0-9,]+)\"?""")
        private val NUM_PAGES_REGEX = Regex("""\"num_pages\"\s*:\s*\"?([0-9,]+)\"?""")
        private val NUM_FAVORITES_REGEX = Regex("""\"num_favorites\"\s*:\s*\"?([0-9,]+)\"?""")
        private val UPLOAD_DATE_REGEX = Regex("""\"upload_date\"\s*:\s*\"?([0-9,]+)\"?""")
        private val THUMB_REGEX = Regex("""/galleries/(\\d+)/(\\d+)t\\.([a-zA-Z0-9]+)""")
        private val THUMB_URL_REGEX = Regex("""(?:https?:)?//[^\"'\s>]+/galleries/(\\d+)/(\\d+)t\\.[a-zA-Z0-9]+|/galleries/(\\d+)/(\\d+)t\\.[a-zA-Z0-9]+""")
        private val THUMB_CDN_HOST_REGEX = Regex("""thumb_cdn_urls\\s*:\\s*\[[^\]]*?t(\\d+)\\.nhentai\\.net""")
        private val UNICODE_ESCAPE_REGEX = Regex("\\\\u([0-9a-fA-F]{4})")
        private val SHORTEN_TITLE_REGEX = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
        private const val TITLE_PREF = "Display manga title as:"
    }
}
