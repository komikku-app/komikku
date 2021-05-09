package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.EightMusesSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.EightMusesDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.source.model.MangaInfo

class EightMuses(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<EightMusesSearchMetadata, Document>,
    UrlImportableSource,
    NamespaceSource {
    override val metaClass = EightMusesSearchMetadata::class
    override val lang = "en"

    // Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        urlImportFetchSearchManga(context, query) {
            super.fetchSearchManga(page, query, filters)
        }

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        val response = client.newCall(mangaDetailsRequest(manga.toSManga())).await()
        return parseToManga(manga, response.asJsoup())
    }

    data class SelfContents(val albums: List<Element>, val images: List<Element>)

    private fun parseSelf(doc: Document): SelfContents {
        // Parse self
        val gc = doc.select(".gallery .c-tile")

        // Check if any in self
        val selfAlbums = gc.filter { it.attr("href").startsWith("/comics/album") }
        val selfImages = gc.filter { it.attr("href").startsWith("/comics/picture") }

        return SelfContents(selfAlbums, selfImages)
    }

    override suspend fun parseIntoMetadata(metadata: EightMusesSearchMetadata, input: Document) {
        with(metadata) {
            path = input.location().toUri().pathSegments

            val breadcrumbs = input.selectFirst(".top-menu-breadcrumb > ol")

            title = breadcrumbs.selectFirst("li:nth-last-child(1) > a").text()

            thumbnailUrl = parseSelf(input).let { it.albums + it.images }.firstOrNull()
                ?.selectFirst(".lazyload")
                ?.attr("data-src")?.let {
                    baseUrl + it
                }

            tags.clear()
            tags += RaisedTag(
                EightMusesSearchMetadata.ARTIST_NAMESPACE,
                breadcrumbs.selectFirst("li:nth-child(2) > a").text(),
                EightMusesSearchMetadata.TAG_TYPE_DEFAULT
            )
            tags += input.select(".album-tags a").map {
                RaisedTag(
                    EightMusesSearchMetadata.TAGS_NAMESPACE,
                    it.text(),
                    EightMusesSearchMetadata.TAG_TYPE_DEFAULT
                )
            }
        }
    }

    override val matchingHosts = listOf(
        "www.8muses.com",
        "comics.8muses.com",
        "8muses.com"
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String {
        var path = uri.pathSegments.drop(2)
        if (uri.pathSegments[1].toLowerCase() == "picture") {
            path = path.dropLast(1)
        }
        return "/comics/album/${path.joinToString("/")}"
    }

    override fun getDescriptionAdapter(controller: MangaController): EightMusesDescriptionAdapter {
        return EightMusesDescriptionAdapter(controller)
    }
}
