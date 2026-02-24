package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.EightMusesSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.urlImportFetchSearchManga
import exh.util.urlImportFetchSearchMangaSuspend
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class EightMuses(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<EightMusesSearchMetadata, Document>,
    UrlImportableSource,
    NamespaceSource {
    override val metaClass = EightMusesSearchMetadata::class
    override fun newMetaInstance() = EightMusesSearchMetadata()
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

    data class SelfContents(val albums: List<Element>, val images: List<Element>)

    private fun parseSelf(doc: Document): SelfContents {
        // Parse self
        val gc = doc.select(".gallery .c-tile")

        // Check if any in self
        val selfAlbums = gc.filter { element -> element.attr("href").startsWith("/comics/album") }
        val selfImages = gc.filter { element -> element.attr("href").startsWith("/comics/picture") }

        return SelfContents(selfAlbums, selfImages)
    }

    override suspend fun parseIntoMetadata(metadata: EightMusesSearchMetadata, input: Document) {
        with(metadata) {
            path = input.location().toUri().pathSegments

            val breadcrumbs = input.selectFirst(".top-menu-breadcrumb > ol")

            title = breadcrumbs!!.selectFirst("li:nth-last-child(1) > a")!!.text()

            thumbnailUrl = parseSelf(input).let { it.albums + it.images }.firstOrNull()
                ?.selectFirst(".lazyload")
                ?.attr("data-src")?.let {
                    baseUrl + it
                }

            tags.clear()
            tags += RaisedTag(
                EightMusesSearchMetadata.ARTIST_NAMESPACE,
                breadcrumbs.selectFirst("li:nth-child(2) > a")!!.text(),
                EightMusesSearchMetadata.TAG_TYPE_DEFAULT,
            )
            tags += input.select(".album-tags a").map {
                RaisedTag(
                    EightMusesSearchMetadata.TAGS_NAMESPACE,
                    it.text(),
                    EightMusesSearchMetadata.TAG_TYPE_DEFAULT,
                )
            }
        }
    }

    override val matchingHosts = listOf(
        "www.8muses.com",
        "comics.8muses.com",
        "8muses.com",
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String {
        var path = uri.pathSegments.drop(2)
        if (uri.pathSegments[1].lowercase() == "picture") {
            path = path.dropLast(1)
        }
        return "/comics/album/${path.joinToString("/")}"
    }
}
