package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullArray
import com.github.salomonbrys.kotson.nullLong
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.nullString
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.NHentaiDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import okhttp3.Response
import rx.Observable

class NHentai(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<NHentaiSearchMetadata, Response>,
    UrlImportableSource {
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
                    tags.add(RaisedTag(it.first!!, it.second!!, if (it.first == "category") RaisedSearchMetadata.TAG_TYPE_VIRTUAL else NHentaiSearchMetadata.TAG_TYPE_DEFAULT))
                }
            }
        }
    }

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

        private val GALLERY_JSON_REGEX = Regex(".parse\\(\"(.*)\"\\);")
        private val UNICODE_ESCAPE_REGEX = Regex("\\\\u([0-9a-fA-F]{4})")
    }
}
