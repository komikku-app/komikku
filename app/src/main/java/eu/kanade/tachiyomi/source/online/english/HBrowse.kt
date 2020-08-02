package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.HBrowseSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.HBrowseDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class HBrowse(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    LewdSource<HBrowseSearchMetadata, Document>,
    UrlImportableSource {
    override val metaClass = HBrowseSearchMetadata::class
    override val lang = "en"

    // Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            super.fetchSearchManga(page, query, filters)
        }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .flatMap {
                parseToManga(manga, it.asJsoup()).andThen(Observable.just(manga))
            }
    }

    override fun parseIntoMetadata(metadata: HBrowseSearchMetadata, input: Document) {
        val tables = parseIntoTables(input)
        with(metadata) {
            val uri = input.location().toUri()
            hbId = uri.pathSegments[1].toLong()

            hbUrlExtra = uri.pathSegments[2]

            tags.clear()
            ((tables[""] ?: error("")) + (tables["categories"] ?: error(""))).forEach { (k, v) ->
                when (val lowercaseNs = k.toLowerCase()) {
                    "title" -> title = v.text()
                    "length" -> length = v.text().substringBefore(" ").toInt()
                    else -> {
                        v.getElementsByTag("a").forEach {
                            tags += RaisedTag(
                                lowercaseNs,
                                it.text(),
                                HBrowseSearchMetadata.TAG_TYPE_DEFAULT
                            )
                        }
                    }
                }
            }
        }
    }

    private fun parseIntoTables(doc: Document): Map<String, Map<String, Element>> {
        return doc.select("#main > .listTable").map { ele ->
            val tableName = ele.previousElementSibling()?.text()?.toLowerCase() ?: ""
            tableName to ele.select("tr").map {
                it.child(0).text() to it.child(1)
            }.toMap()
        }.toMap()
    }

    override val matchingHosts = listOf(
        "www.hbrowse.com",
        "hbrowse.com"
    )

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        return "$baseUrl/${uri.pathSegments.first()}"
    }

    override fun getDescriptionAdapter(controller: MangaController): HBrowseDescriptionAdapter {
        return HBrowseDescriptionAdapter(controller)
    }
}
