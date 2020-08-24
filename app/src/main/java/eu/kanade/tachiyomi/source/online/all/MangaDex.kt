package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.md.handlers.ApiChapterParser
import exh.md.handlers.ApiMangaParser
import exh.md.handlers.MangaHandler
import exh.md.handlers.MangaPlusHandler
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.MangaDexDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import kotlin.reflect.KClass
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class MangaDex(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<MangaDexSearchMetadata, Response>,
    UrlImportableSource {
    override val lang: String = delegate.lang

    private val mdLang by lazy {
        MdLang.values().find { it.lang == lang }?.dexLang ?: lang
    }

    override val matchingHosts: List<String> = listOf("mangadex.org", "www.mangadex.org")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            super.fetchSearchManga(page, query, filters)
        }

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.toLowerCase() ?: return null

        return if (lcFirstPathSegment == "title" || lcFirstPathSegment == "manga") {
            "/manga/${uri.pathSegments[1]}/"
        } else {
            null
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return MangaHandler(client, headers, listOf(mdLang)).fetchMangaDetailsObservable(manga)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return MangaHandler(client, headers, listOf(mdLang)).fetchChapterListObservable(manga)
    }

    @ExperimentalSerializationApi
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return if (chapter.scanlator == "MangaPlus") {
            client.newCall(mangaPlusPageListRequest(chapter))
                .asObservableSuccess()
                .map { response ->
                    val chapterId = ApiChapterParser().externalParse(response)
                    MangaPlusHandler(client).fetchPageList(chapterId)
                }
        } else super.fetchPageList(chapter)
    }

    private fun mangaPlusPageListRequest(chapter: SChapter): Request {
        val chpUrl = chapter.url.substringBefore(MdUtil.apiChapterSuffix)
        return GET(MdUtil.baseUrl + chpUrl + MdUtil.apiChapterSuffix, headers, CacheControl.FORCE_NETWORK)
    }

    override fun fetchImage(page: Page): Observable<Response> {
        return if (page.imageUrl!!.contains("mangaplus", true)) {
            MangaPlusHandler(network.client).client.newCall(GET(page.imageUrl!!, headers))
                .asObservableSuccess()
        } else super.fetchImage(page)
    }

    override val metaClass: KClass<MangaDexSearchMetadata> = MangaDexSearchMetadata::class

    override fun getDescriptionAdapter(controller: MangaController): MangaDexDescriptionAdapter {
        return MangaDexDescriptionAdapter(controller)
    }

    override fun parseIntoMetadata(metadata: MangaDexSearchMetadata, input: Response) {
        ApiMangaParser(listOf(mdLang)).parseIntoMetadata(metadata, input)
    }
}
