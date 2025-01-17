package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.online.MetadataSource
import eu.kanade.tachiyomi.animesource.online.NamespaceSource
import eu.kanade.tachiyomi.animesource.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.PururinSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedAnimeHttpSource
import exh.util.dropBlank
import exh.util.trimAll
import exh.util.urlImportFetchSearchManga
import exh.util.urlImportFetchSearchMangaSuspend
import org.jsoup.nodes.Document
import rx.Observable

class Pururin(delegate: AnimeHttpSource, val context: Context) :
    DelegatedAnimeHttpSource(delegate),
    MetadataSource<PururinSearchMetadata, Document>,
    UrlImportableSource,
    NamespaceSource {
    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang = "en"

    /**
     * The class of the metadata used by this source
     */
    override val metaClass = PururinSearchMetadata::class
    override fun newMetaInstance() = PururinSearchMetadata()

    // Support direct URL importing
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        val trimmedIdQuery = query.trim().removePrefix("id:")
        val newQuery = if ((trimmedIdQuery.toIntOrNull() ?: -1) >= 0) {
            "$baseUrl/gallery/$trimmedIdQuery/-"
        } else {
            query
        }

        return urlImportFetchSearchManga(context, newQuery) {
            @Suppress("DEPRECATION")
            super<DelegatedAnimeHttpSource>.fetchSearchAnime(page, query, filters)
        }
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val trimmedIdQuery = query.trim().removePrefix("id:")
        val newQuery = if ((trimmedIdQuery.toIntOrNull() ?: -1) >= 0) {
            "$baseUrl/gallery/$trimmedIdQuery/-"
        } else {
            query
        }
        return urlImportFetchSearchMangaSuspend(context, newQuery) {
            super<DelegatedAnimeHttpSource>.getSearchAnime(page, query, filters)
        }
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val response = client.newCall(animeDetailsRequest(anime)).awaitSuccess()
        return parseToManga(anime, response.asJsoup())
    }

    override suspend fun parseIntoMetadata(metadata: PururinSearchMetadata, input: Document) {
        val selfLink = input.select("[itemprop=name]").last()!!.parent()
        val parsedSelfLink = selfLink!!.attr("href").toUri().pathSegments

        with(metadata) {
            prId = parsedSelfLink[parsedSelfLink.lastIndex - 1].toInt()
            prShortLink = parsedSelfLink.last()

            val contentWrapper = input.selectFirst(".content-wrapper")
            title = contentWrapper!!.selectFirst(".title h1")!!.text()
            altTitle = contentWrapper.selectFirst(".alt-title")?.text()

            thumbnailUrl = "https:" + input.selectFirst(".cover-wrapper v-lazy-image")!!.attr("src")

            tags.clear()
            contentWrapper.select(".table-gallery-info > tbody > tr").forEach { ele ->
                val key = ele.child(0).text().lowercase()
                val value = ele.child(1)
                when (key) {
                    "pages" -> {
                        val split = value.text().split("(").trimAll().dropBlank()

                        pages = split.first().toIntOrNull()
                        fileSize = split.last().removeSuffix(")").trim()
                    }
                    "ratings" -> {
                        ratingCount = value.selectFirst("[itemprop=ratingCount]")!!.attr("content").toIntOrNull()
                        averageRating = value.selectFirst("[itemprop=ratingValue]")!!.attr("content").toDoubleOrNull()
                    }
                    "uploader" -> {
                        uploaderDisp = value.text()
                        uploader = value.child(0).attr("href").toUri().lastPathSegment
                    }
                    else -> {
                        value.select("a").forEach { link ->
                            val searchUrl = link.attr("href").toUri()
                            val namespace = searchUrl.pathSegments[searchUrl.pathSegments.lastIndex - 2]
                            tags += RaisedTag(
                                namespace,
                                searchUrl.lastPathSegment!!.substringBefore("."),
                                if (namespace != PururinSearchMetadata.TAG_NAMESPACE_CATEGORY) {
                                    PururinSearchMetadata.TAG_TYPE_DEFAULT
                                } else {
                                    RaisedSearchMetadata.TAG_TYPE_VIRTUAL
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override val matchingHosts = listOf(
        "pururin.io",
        "www.pururin.io",
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String {
        return "${PururinSearchMetadata.BASE_URL}/gallery/${uri.pathSegments.getOrNull(1)}/${uri.lastPathSegment}"
    }
}
