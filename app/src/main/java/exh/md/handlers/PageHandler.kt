package exh.md.handlers

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.log.xLogD
import exh.md.dto.AtHomeDto
import exh.md.service.MangaDexService
import exh.md.utils.MdApi
import exh.md.utils.MdUtil
import okhttp3.Headers
import okhttp3.Response
import rx.Observable
import tachiyomi.source.Source
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
    private val preferences: PreferencesHelper,
    private val mdList: MdList,
) {

    suspend fun fetchPageList(chapter: SChapter, isLogged: Boolean, usePort443Only: Boolean, dataSaver: Boolean, mangadex: Source): List<Page> {
        return withIOContext {
            val chapterResponse = service.viewChapter(MdUtil.getChapterId(chapter.url))

            if (chapterResponse.data.attributes.externalUrl != null && chapterResponse.data.attributes.pages == 0) {
                when {
                    chapter.scanlator.equals("mangaplus", true) -> mangaPlusHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
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
                    else -> throw Exception("${chapter.scanlator} not supported")
                }
            } else {
                val headers = if (isLogged) {
                    MdUtil.getAuthHeaders(headers, preferences, mdList)
                } else {
                    headers
                }

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

    fun fetchImage(page: Page, superMethod: (Page) -> Observable<Response>): Observable<Response> {
        xLogD(page.imageUrl)
        return when {
            page.imageUrl?.contains("mangaplus", true) == true -> {
                mangaPlusHandler.client.newCall(GET(page.imageUrl!!, headers))
                    .asObservableSuccess()
            }
            page.imageUrl?.contains("comikey", true) == true -> {
                comikeyHandler.client.newCall(GET(page.imageUrl!!, comikeyHandler.headers))
                    .asObservableSuccess()
            }
            page.imageUrl?.contains("/bfs/comic/", true) == true -> {
                bilibiliHandler.client.newCall(GET(page.imageUrl!!, bilibiliHandler.headers))
                    .asObservableSuccess()
            }
            page.imageUrl?.contains("azuki", true) == true -> {
                azukiHandler.client.newCall(GET(page.imageUrl!!, azukiHandler.headers))
                    .asObservableSuccess()
            }
            page.imageUrl?.contains("mangahot", true) == true -> {
                mangaHotHandler.client.newCall(GET(page.imageUrl!!, mangaHotHandler.headers))
                    .asObservableSuccess()
            }
            else -> superMethod(page)
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
}
