package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.lang.runAsObservable
import exh.metadata.metadata.HentaiCafeSearchMetadata
import exh.metadata.metadata.HentaiCafeSearchMetadata.Companion.TAG_TYPE_DEFAULT
import exh.metadata.metadata.base.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.HentaiCafeDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo

class HentaiCafe(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<HentaiCafeSearchMetadata, Document>,
    UrlImportableSource {
    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang = "en"

    /**
     * The class of the metadata used by this source
     */
    override val metaClass = HentaiCafeSearchMetadata::class

    // Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        urlImportFetchSearchManga(context, query) {
            super.fetchSearchManga(page, query, filters)
        }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .flatMap {
                parseToManga(manga, it.asJsoup()).andThen(
                    Observable.just(
                        manga.apply {
                            initialized = true
                        }
                    )
                )
            }
    }

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        val response = client.newCall(mangaDetailsRequest(manga.toSManga())).await()
        return parseToManga(manga, response.asJsoup())
    }

    /**
     * Parse the supplied input into the supplied metadata object
     */
    override fun parseIntoMetadata(metadata: HentaiCafeSearchMetadata, input: Document) {
        with(metadata) {
            url = input.location()
            title = input.select("h3").text()
            val contentElement = input.select(".entry-content").first()
            thumbnailUrl = contentElement.child(0).child(0).attr("src")

            fun filterableTagsOfType(type: String) = contentElement.select("a")
                .filter { "$baseUrl/hc.fyi/$type/" in it.attr("href") }
                .map { it.text() }

            tags.clear()
            tags += filterableTagsOfType("tag").map {
                RaisedTag(null, it, TAG_TYPE_DEFAULT)
            }

            val artists = filterableTagsOfType("artist")

            artist = artists.joinToString()
            tags += artists.map {
                RaisedTag("artist", it, TAG_TYPE_VIRTUAL)
            }

            readerId = input.select("[title=Read]").attr("href").toHttpUrlOrNull()!!.pathSegments[2]
        }
    }

    override fun fetchChapterList(manga: SManga) = runAsObservable({
        fetchOrLoadMetadata(manga.id) {
            val response = client.newCall(mangaDetailsRequest(manga)).await()
            response.asJsoup()
        }
    }).map {
        listOf(
            SChapter.create().apply {
                url = "/manga/read/${it.readerId}/en/0/1/"
                name = "Chapter"
                chapter_number = 0.0f
            }
        )
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val metadata = fetchOrLoadMetadata(manga.id()) {
            val response = client.newCall(mangaDetailsRequest(manga.toSManga())).await()
            response.asJsoup()
        }
        return listOf(
            ChapterInfo(
                key = "/manga/read/${metadata.readerId}/en/0/1/",
                name = "Chapter",
                number = 0F
            )
        )
    }

    override val matchingHosts = listOf(
        "hentai.cafe"
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.takeUnless { it.equals("manga", true) } ?: return null

        return if (lcFirstPathSegment.equals("hc.fyi", true)) {
            "/$lcFirstPathSegment/${uri.pathSegments[1]}"
        } else null
    }

    override fun getDescriptionAdapter(controller: MangaController): HentaiCafeDescriptionAdapter {
        return HentaiCafeDescriptionAdapter(controller)
    }
}
