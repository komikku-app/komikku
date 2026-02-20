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
import exh.metadata.metadata.HBrowseSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.urlImportFetchSearchManga
import exh.util.urlImportFetchSearchMangaSuspend
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HBrowse(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<HBrowseSearchMetadata, Document>,
    UrlImportableSource,
    NamespaceSource {
    override val metaClass = HBrowseSearchMetadata::class
    override fun newMetaInstance() = HBrowseSearchMetadata()
    override val lang = "en"

    // Support direct URL importing
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchManga"))
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        urlImportFetchSearchManga(context, query) {
            @Suppress("DEPRECATION")
            super<DelegatedHttpSource>.fetchSearchManga(page, query, filters)
        }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        return urlImportFetchSearchMangaSuspend(context, query) {
            super<DelegatedHttpSource>.getSearchManga(page, query, filters)
        }
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.newCall(mangaDetailsRequest(manga)).awaitSuccess()
        return response.use { parseToManga(manga, it.asJsoup()) }
    }

    override suspend fun parseIntoMetadata(metadata: HBrowseSearchMetadata, input: Document) {
        val tables = parseIntoTables(input)
        with(metadata) {
            hbUrl = input.location().removePrefix("$baseUrl/thumbnails")

            hbId = hbUrl!!.removePrefix("/").substringBefore("/").toLong()

            tags.clear()
            ((tables[""] ?: error("")) + (tables["categories"] ?: error(""))).forEach { (k, v) ->
                when (val lowercaseNs = k.lowercase()) {
                    "title" -> title = v.text()
                    "length" -> length = v.text().substringBefore(" ").toInt()
                    else -> {
                        v.getElementsByTag("a").forEach {
                            tags += RaisedTag(
                                lowercaseNs,
                                it.text(),
                                HBrowseSearchMetadata.TAG_TYPE_DEFAULT,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun parseIntoTables(doc: Document): Map<String, Map<String, Element>> {
        return doc.select("#main > .listTable").associate { ele ->
            val tableName = ele.previousElementSibling()?.text()?.lowercase().orEmpty()
            tableName to ele.select("tr")
                .filter { element -> element.childrenSize() > 1 }
                .associate {
                    it.child(0).text() to it.child(1)
                }
        }
    }

    override val matchingHosts = listOf(
        "www.hbrowse.com",
        "hbrowse.com",
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        return uri.pathSegments.firstOrNull()?.let { "/$it/c00001/" }
    }
}
