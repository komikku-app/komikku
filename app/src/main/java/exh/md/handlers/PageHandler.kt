package exh.md.handlers

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import exh.log.xLogD
import exh.log.xLogE
import exh.md.dto.AtHomeDto
import exh.md.service.MangaDexService
import exh.md.utils.MdApi
import exh.md.utils.MdUtil
import okhttp3.Call
import rx.Observable
import tachiyomi.core.common.util.lang.withIOContext
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.isAccessible

class PageHandler(
    // KMK -->
    private val mangadex: Source,
    // KMK <--
    private val service: MangaDexService,
    private val mangaPlusHandler: MangaPlusHandler,
    private val comikeyHandler: ComikeyHandler,
    private val bilibiliHandler: BilibiliHandler,
    private val azukiHandler: AzukiHandler,
    private val mangaHotHandler: MangaHotHandler,
    private val namicomiHandler: NamicomiHandler,
    // KMK -->
    private val kMangaHandler: KMangaHandler,
    private val mangaUpHandler: MangaUpHandler,
    // KMK <--
) {

    // KMK -->
    suspend fun fetchPageList(chapter: SChapter, usePort443Only: Boolean, dataSaver: Boolean): List<Page> {
        // KMK <--
        return withIOContext {
            val chapterResponse = service.viewChapter(MdUtil.getChapterId(chapter.url))

            if (chapterResponse.data.attributes.externalUrl != null && chapterResponse.data.attributes.pages == 0) {
                when {
                    chapter.scanlator.equals("mangaplus", true) -> mangaPlusHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                        dataSaver = dataSaver,
                        // KMK -->
                        mangadex.lang,
                        // KMK <--
                    )
                    /*chapter.scanlator.equals("comikey", true) -> comikeyHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl
                    )*/
                    chapter.scanlator.equals("bilibili comics", true) -> bilibiliHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                        chapterResponse.data.attributes.chapter.toString(),
                    )
                    // KMK -->
                    chapter.scanlator.equals("azuki manga", true) ||
                        azukiHandler.isChapterUrl(chapterResponse.data.attributes.externalUrl) ->
                        // KMK <--
                        azukiHandler.fetchPageList(
                            chapterResponse.data.attributes.externalUrl,
                        )
                    chapter.scanlator.equals("mangahot", true) -> mangaHotHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                    )
                    chapter.scanlator.equals("namicomi", true) -> namicomiHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                        dataSaver = dataSaver,
                    )
                    // KMK -->
                    chapter.scanlator.equals("K Manga", true) -> kMangaHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                    )
                    chapter.scanlator.equals("Manga UP!", true) -> mangaUpHandler.fetchPageList(
                        chapterResponse.data.attributes.externalUrl,
                        mangadex.lang,
                    )
                    // KMK <--
                    else -> throw Exception("${chapter.scanlator} not supported")
                }
            } else {
                val atHomeRequestUrl = if (usePort443Only) {
                    "${MdApi.atHomeServer}/${MdUtil.getChapterId(chapter.url)}?forcePort443=true"
                } else {
                    "${MdApi.atHomeServer}/${MdUtil.getChapterId(chapter.url)}"
                }

                // KMK -->
                updateExtensionVariable(atHomeRequestUrl)
                // KMK <--

                val atHomeResponse = service.getAtHomeServer(atHomeRequestUrl)

                pageListParse(atHomeRequestUrl, atHomeResponse, dataSaver)
            }
        }
    }

    // KMK -->
    @Suppress("UNCHECKED_CAST")
    private val tokenTracker: HashMap<String, Long>? by lazy {
        // Best effort: changed extension internals must not break loading chapters
        runCatching {
            // The helper property now appears in the current class
            val helperProperty = mangadex::class.declaredMemberProperties.find { it.name == "helper" }
                ?: mangadex::class.superclasses.first().declaredMemberProperties.find { it.name == "helper" }
                ?: return@lazy null
            helperProperty.isAccessible = true
            val helper = helperProperty.call(mangadex) ?: return@lazy null

            val tokenTrackerProperty = helper::class.declaredMemberProperties.find { it.name == "tokenTracker" }
                ?: return@lazy null
            tokenTrackerProperty.isAccessible = true
            tokenTrackerProperty.call(helper) as? HashMap<String, Long>
        }
            .onFailure { xLogE("Unable to access the MangaDex extension token tracker", it) }
            .getOrNull()
    }

    private fun updateExtensionVariable(atHomeRequestUrl: String) {
        tokenTracker?.set(atHomeRequestUrl, System.currentTimeMillis())
    }
    // KMK <--

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
            // KMK -->
            mangaPlusHandler.isImageUrl(page.imageUrl) -> {
                mangaPlusHandler.client.newCachelessCallWithProgress(
                    GET(
                        page.imageUrl!!,
                        if (page.url.isEmpty()) {
                            mangaPlusHandler.headers
                        } else {
                            mangaPlusHandler.headers.newBuilder().add("Plus-Vw-Token", page.url).build()
                        },
                    ),
                    page,
                )
            }
            // KMK <--
            page.imageUrl?.contains("comikey", true) == true -> {
                comikeyHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!), page)
            }
            page.imageUrl?.contains("/bfs/comic/", true) == true -> {
                bilibiliHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!, bilibiliHandler.headers), page)
            }
            page.imageUrl?.contains("azuki", true) == true -> {
                azukiHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!), page)
            }
            page.imageUrl?.contains("mangahot", true) == true -> {
                mangaHotHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!), page)
            }
            page.imageUrl?.contains("namicomi", true) == true -> {
                namicomiHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!), page)
            }
            // KMK -->
            kMangaHandler.isImageUrl(page.imageUrl) -> {
                kMangaHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!, kMangaHandler.headers), page)
            }
            page.imageUrl?.contains("manga-up", true) == true -> {
                mangaUpHandler.client.newCachelessCallWithProgress(GET(page.imageUrl!!, mangaUpHandler.headers), page)
            }
            // KMK <--
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
