package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.PururinSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.PururinDescriptionAdapter
import exh.util.dropBlank
import exh.util.trimAll
import exh.util.urlImportFetchSearchManga
import org.jsoup.nodes.Document
import rx.Observable
import tachiyomi.source.model.MangaInfo

class Pururin(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
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

    // Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val trimmedIdQuery = query.trim().removePrefix("id:")
        val newQuery = if (trimmedIdQuery.toIntOrNull() ?: -1 >= 0) {
            "$baseUrl/gallery/$trimmedIdQuery/-"
        } else query

        return urlImportFetchSearchManga(context, newQuery) {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        val response = client.newCall(mangaDetailsRequest(manga.toSManga())).await()
        return parseToManga(manga, response.asJsoup())
    }

    override suspend fun parseIntoMetadata(metadata: PururinSearchMetadata, input: Document) {
        val selfLink = input.select("[itemprop=name]").last().parent()
        val parsedSelfLink = selfLink.attr("href").toUri().pathSegments

        with(metadata) {
            prId = parsedSelfLink[parsedSelfLink.lastIndex - 1].toInt()
            prShortLink = parsedSelfLink.last()

            val contentWrapper = input.selectFirst(".content-wrapper")
            title = contentWrapper.selectFirst(".title h1").text()
            altTitle = contentWrapper.selectFirst(".alt-title")?.text()

            thumbnailUrl = "https:" + input.selectFirst(".cover-wrapper v-lazy-image").attr("src")

            tags.clear()
            contentWrapper.select(".table-gallery-info > tbody > tr").forEach { ele ->
                val key = ele.child(0).text().toLowerCase()
                val value = ele.child(1)
                when (key) {
                    "pages" -> {
                        val split = value.text().split("(").trimAll().dropBlank()

                        pages = split.first().toIntOrNull()
                        fileSize = split.last().removeSuffix(")").trim()
                    }
                    "ratings" -> {
                        ratingCount = value.selectFirst("[itemprop=ratingCount]").attr("content").toIntOrNull()
                        averageRating = value.selectFirst("[itemprop=ratingValue]").attr("content").toDoubleOrNull()
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
                                if (namespace != PururinSearchMetadata.TAG_NAMESPACE_CATEGORY) PururinSearchMetadata.TAG_TYPE_DEFAULT else RaisedSearchMetadata.TAG_TYPE_VIRTUAL
                            )
                        }
                    }
                }
            }
        }
    }

    override val matchingHosts = listOf(
        "pururin.io",
        "www.pururin.io"
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String {
        return "${PururinSearchMetadata.BASE_URL}/gallery/${uri.pathSegments.getOrNull(1)}/${uri.lastPathSegment}"
    }

    override fun getDescriptionAdapter(controller: MangaController): PururinDescriptionAdapter {
        return PururinDescriptionAdapter(controller)
    }
}
