package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.metadata.metadata.TsuminoSearchMetadata
import exh.metadata.metadata.TsuminoSearchMetadata.Companion.TAG_TYPE_DEFAULT
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.dropBlank
import exh.util.trimAll
import exh.util.urlImportFetchSearchManga
import exh.util.urlImportFetchSearchMangaSuspend
import org.jsoup.nodes.Document
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Tsumino(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<TsuminoSearchMetadata, Document>,
    UrlImportableSource,
    NamespaceSource {
    override val metaClass = TsuminoSearchMetadata::class
    override fun newMetaInstance() = TsuminoSearchMetadata()
    override val lang = "en"

    // Support direct URL importing
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchManga"))
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            @Suppress("DEPRECATION")
            super<DelegatedHttpSource>.fetchSearchManga(page, query, filters)
        }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        return urlImportFetchSearchMangaSuspend(context, query) {
            super<DelegatedHttpSource>.getSearchManga(page, query, filters)
        }
    }

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.lowercase(Locale.ROOT) ?: return null
        if (lcFirstPathSegment != "read" && lcFirstPathSegment != "book" && lcFirstPathSegment != "entry") {
            return null
        }
        return "https://tsumino.com/Book/Info/${uri.lastPathSegment}"
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.newCall(mangaDetailsRequest(manga)).awaitSuccess()
        return response.use { parseToManga(manga, it.asJsoup()) }
    }

    override suspend fun parseIntoMetadata(metadata: TsuminoSearchMetadata, input: Document) {
        with(metadata) {
            tmId = TsuminoSearchMetadata.tmIdFromUrl(input.location())!!.toInt()
            tags.clear()

            input.select("meta[property=og:title]").firstOrNull()?.attr("content")?.let {
                title = it.trim()
            }

            input.getElementById("Artist")?.children()?.first()?.attr("data-define")?.trim()?.let { artistString ->
                artistString.split("|").trimAll().dropBlank().forEach {
                    tags.add(RaisedTag("artist", it, TAG_TYPE_DEFAULT))
                }
                tags.add(RaisedTag("artist", artistString, TAG_TYPE_VIRTUAL))
                artist = artistString
            }

            input.getElementById("Uploader")?.children()?.first()?.text()?.trim()?.let {
                uploader = it
            }

            input.getElementById("Uploaded")?.text()?.let {
                uploadDate = TM_DATE_FORMAT.parse(it.trim())!!.time
            }

            input.getElementById("Pages")?.text()?.let {
                length = it.trim().toIntOrNull()
            }

            input.getElementById("Rating")?.text()?.let {
                ratingString = it.trim()
                val ratingString = ratingString
                if (!ratingString.isNullOrBlank()) {
                    averageRating = RATING_FLOAT_REGEX.find(ratingString)?.groups?.get(1)?.value?.toFloatOrNull()
                    userRatings = RATING_USERS_REGEX.find(ratingString)?.groups?.get(1)?.value?.toLongOrNull()
                    favorites = RATING_FAVORITES_REGEX.find(ratingString)?.groups?.get(1)?.value?.toLongOrNull()
                }
            }

            input.getElementById("Category")?.children()?.first()?.attr("data-define")?.let {
                category = it.trim()
                tags.add(RaisedTag("genre", it, TAG_TYPE_VIRTUAL))
            }

            input.getElementById("Collection")?.children()?.first()?.attr("data-define")?.let {
                collection = it.trim()
                tags.add(RaisedTag("collection", it, TAG_TYPE_DEFAULT))
            }

            input.getElementById("Group")?.children()?.first()?.attr("data-define")?.let {
                group = it.trim()
                tags.add(RaisedTag("group", it, TAG_TYPE_DEFAULT))
            }

            parody = input.getElementById("Parody")?.children()?.map {
                val entry = it.attr("data-define").trim()
                tags.add(RaisedTag("parody", entry, TAG_TYPE_DEFAULT))
                entry
            }.orEmpty()

            character = input.getElementById("Character")?.children()?.map {
                val entry = it.attr("data-define").trim()
                tags.add(RaisedTag("character", entry, TAG_TYPE_DEFAULT))
                entry
            }.orEmpty()

            input.getElementById("Tag")?.children()?.let { tagElements ->
                tags.addAll(
                    tagElements.map {
                        RaisedTag("tags", it.attr("data-define").trim(), TAG_TYPE_DEFAULT)
                    },
                )
            }
        }
    }

    override val matchingHosts = listOf(
        "www.tsumino.com",
        "tsumino.com",
    )

    companion object {
        val TM_DATE_FORMAT = SimpleDateFormat("yyyy MMM dd", Locale.US)
        val RATING_FLOAT_REGEX = "([0-9].*) \\(".toRegex()
        val RATING_USERS_REGEX = "\\(([0-9].*) users".toRegex()
        val RATING_FAVORITES_REGEX = "/ ([0-9].*) favs".toRegex()
    }
}
