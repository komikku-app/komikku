package exh.md.handlers

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import exh.log.xLogD
import exh.md.dto.AtHomeDto
import exh.md.service.MangaDexService
import exh.md.utils.MdApi
import exh.md.utils.MdUtil
import okhttp3.Call
import okhttp3.Headers
import rx.Observable
import tachiyomi.core.common.util.lang.withIOContext
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.isAccessible

class PageHandler(
    private val headers: Headers,
    private val service: MangaDexService,
    private val mangaPlusHandler: MangaPlusHandler,
    private val comikeyHandler: ComikeyHandler,
    private val bilibiliHandler: BilibiliHandler,
    private val azukiHandler: AzukiHandler,
    private val mangaHotHandler: MangaHotHandler,
    private val namicomiHandler: NamicomiHandler,
    private val preferences: TrackPreferences,
    private val mdList: MdList,
) {

    suspend fun fetchPageList(chapter: SChapter, usePort443Only: Boolean, dataSaver: Boolean, mangadex: Source): List<Page> {
        return withIOContext {
            val chapterResponse = service.viewChapter(MdUtil.getChapterId(chapter.url))

            if (chapterResponse.data.attributes.externalUrl != null && chapterResponse.data.attributes.pages == 0) {
                when {
                    chapter.scanlator.equals("mangaplus", true) -> mangaPlusHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                        dataSaver = dataSaver,
                    )
                    /*chapter.scanlator.equals("comikey", true) -> comikeyHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl
                    )*/
                    chapter.scanlator.equals("bilibili comics", true) -> bilibiliHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                        chapterResponse.data.attributes.chapter.toString(),
                    )
                    chapter.scanlator.equals("azuki manga", true) -> azukiHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                    )
                    chapter.scanlator.equals("mangahot", true) -> mangaHotHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                    )
                    chapter.scanlator.equals("namicomi", true) -> namicomiHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                        dataSaver = dataSaver,
                    )
                    else -> throw Exception("${chapter.scanlator} not supported")
                }
            } else {
                val atHomeRequestUrl = if (usePort443Only) {
                    "${MdApi.atHomeServer}/${MdUtil.getChapterId(chapter.url)}?forcePort443=true"
                } else {
                    "${MdApi.atHomeServer}/${MdUtil.getChapterId(chapter.url)}"
                }

                updateExtensionVariable(mangadex, atHomeRequestUrl)

                val atHomeResponse = service.getAtHomeServer(atHomeRequestUrl, headers)

                pageListParse(atHomeRequestUrl, atHomeResponse, dataSaver)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateExtensionVariable(mangadex: Source, atHomeRequestUrl: String) {
        val mangadexSuperclass = mangadex::class.superclasses.first()

        val helperCallable = mangadexSuperclass.members.find { it.name == "helper" } ?: return
        helperCallable.isAccessible = true
        val helper = helperCallable.call(mangadex) ?: return

        val tokenTrackerCallable = helper::class.members.find { it.name == "tokenTracker" } ?: return
        tokenTrackerCallable.isAccessible = true
        val tokenTracker = tokenTrackerCallable.call(helper) as? HashMap<String, Long> ?: return
        tokenTracker[atHomeRequestUrl] = System.currentTimeMillis()
    }

    private fun pageListParse(
        atHomeRequestUrl: String,
        atHomeDto: AtHomeDto,
        dataSaver: Boolean,
    ): List<Page> {
        val hash = atHomeDto.chapter.hash
        val pageArray = if (dataSaver) {
            atHomeDto.chapter.dataSaver.map { "/data-saver/$hash/$it" }
        } else {
            atHomeDto.chapter.data.map { "/data/$hash/$it" }
        }
        val now = System.currentTimeMillis()

        return pageArray.mapIndexed { pos, imgUrl ->
            Page(pos, "${atHomeDto.baseUrl},$atHomeRequestUrl,$now", imgUrl)
        }
    }

    fun getImageCall(page: Page): Call? {
        xLogD(page.imageUrl)
        return when {
            page.imageUrl?.contains("mangaplus", true) == true -> {
                mangaPlusHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!, headers), page)
            }
            page.imageUrl?.contains("comikey", true) == true -> {
                comikeyHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!, comikeyHandler.headers), page)
            }
            page.imageUrl?.contains("/bfs/comic/", true) == true -> {
                bilibiliHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!, bilibiliHandler.headers), page)
            }
            page.imageUrl?.contains("azuki", true) == true -> {
                azukiHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!, azukiHandler.headers), page)
            }
            page.imageUrl?.contains("mangahot", true) == true -> {
                mangaHotHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!, mangaHotHandler.headers), page)
            }
            page.imageUrl?.contains("namicomi", true) == true -> {
                mangaHotHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!, mangaHotHandler.headers), page)
            }
            else -> null
        }
    }

    fun fetchImageUrl(page: Page, superMethod: (Page) -> Observable<String>): Observable<String> {
        return when {
            page.url.contains("/bfs/comic/") -> {
                bilibiliHandler.fetchImageUrl(page)
            }
            else -> superMethod(page)
        }
    }

    suspend fun getImageUrl(page: Page, superMethod: suspend (Page) -> String): String {
        return when {
            page.url.contains("/bfs/comic/") -> {
                bilibiliHandler.getImageUrl(page)
            }
            else -> superMethod(page)
        }
    }
}
