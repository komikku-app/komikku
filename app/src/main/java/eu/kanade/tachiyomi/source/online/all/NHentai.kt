package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.NHentaiDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import rx.Observable

class NHentai(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<NHentaiSearchMetadata, Response>,
    UrlImportableSource,
    NamespaceSource {
    override val metaClass = NHentaiSearchMetadata::class
    override val lang = if (id == otherId) "all" else delegate.lang

    // Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            super.fetchSearchManga(page, query, filters)
        }

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

    override fun parseIntoMetadata(metadata: NHentaiSearchMetadata, input: Response) {
        val json = GALLERY_JSON_REGEX.find(input.body!!.string())!!.groupValues[1].replace(
            UNICODE_ESCAPE_REGEX
        ) { it.groupValues[1].toInt(radix = 16).toChar().toString() }
        val jsonResponse = jsonParser.decodeFromString<JsonResponse>(json)

        with(metadata) {
            nhId = jsonResponse.id

            uploadDate = jsonResponse.uploadDate

            favoritesCount = jsonResponse.numFavorites

            mediaId = jsonResponse.mediaId

            jsonResponse.title?.let { title ->
                japaneseTitle = title.japanese
                shortTitle = title.pretty
                englishTitle = title.english
            }

            jsonResponse.images?.let { images ->
                coverImageType = images.cover?.type
                images.pages.mapNotNull {
                    it.type
                }.let {
                    pageImageTypes = it
                }
                thumbnailImageType = images.thumbnail?.type
            }

            scanlator = jsonResponse.scanlator

            jsonResponse.tags.map {
                it.type to it.name
            }.apply {
                tags.clear()
            }.forEach {
                if (it.first != null && it.second != null) {
                    tags.add(RaisedTag(it.first!!, it.second!!, if (it.first == "category") RaisedSearchMetadata.TAG_TYPE_VIRTUAL else NHentaiSearchMetadata.TAG_TYPE_DEFAULT))
                }
            }
        }
    }

    @Serializable
    data class JsonResponse(
        val id: Long,
        @SerialName("media_id") val mediaId: String? = null,
        val title: JsonTitle? = null,
        val images: JsonImages? = null,
        val scanlator: String? = null,
        @SerialName("upload_date") val uploadDate: Long? = null,
        val tags: List<JsonTag> = emptyList(),
        @SerialName("num_pages") val numPages: Int? = null,
        @SerialName("num_favorites") val numFavorites: Long? = null
    )

    @Serializable
    data class JsonTitle(
        val english: String? = null,
        val japanese: String? = null,
        val pretty: String? = null
    )

    @Serializable
    data class JsonImages(
        val pages: List<JsonPage> = emptyList(),
        val cover: JsonPage? = null,
        val thumbnail: JsonPage? = null
    )

    @Serializable
    data class JsonPage(
        @SerialName("t") val type: String? = null,
        @SerialName("w") val width: String? = null,
        @SerialName("h") val height: String? = null
    )

    @Serializable
    data class JsonTag(
        val id: Long? = null,
        val type: String? = null,
        val name: String? = null,
        val url: String? = null,
        val count: Long? = null
    )

    override fun toString() = "$name (${lang.toUpperCase()})"

    override fun ensureDelegateCompatible() {
        if (versionId != delegate.versionId) {
            throw IncompatibleDelegateException("Delegate source is not compatible (versionId: $versionId <=> ${delegate.versionId})!")
        }
    }

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
        const val otherId = 7309872737163460316L

        private val jsonParser = Json {
            ignoreUnknownKeys = true
        }

        private val GALLERY_JSON_REGEX = Regex(".parse\\(\"(.*)\"\\);")
        private val UNICODE_ESCAPE_REGEX = Regex("\\\\u([0-9a-fA-F]{4})")
    }
}
