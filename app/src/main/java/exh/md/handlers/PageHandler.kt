package exh.md.handlers

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.dto.AtHomeDto
import exh.md.dto.ChapterDto
import exh.md.service.MangaDexService
import exh.md.utils.MdUtil
import okhttp3.Headers

class PageHandler(
    private val headers: Headers,
    private val service: MangaDexService,
    private val mangaPlusHandler: MangaPlusHandler,
    private val preferences: PreferencesHelper,
    private val mdList: MdList,
) {

    suspend fun fetchPageList(chapter: SChapter, isLogged: Boolean, usePort443Only: Boolean, dataSaver: Boolean): List<Page> {
        return withIOContext {
            val chapterResponse = service.viewChapter(MdUtil.getChapterId(chapter.url))

            if (chapter.scanlator.equals("mangaplus", true)) {
                mangaPlusHandler.fetchPageList(
                    chapterResponse.data.attributes.data
                        .first()
                        .substringAfterLast("/")
                )
            } else {
                val headers = if (isLogged) {
                    MdUtil.getAuthHeaders(headers, preferences, mdList)
                } else {
                    headers
                }

                val (atHomeRequestUrl, atHomeResponse) = service.getAtHomeServer(headers, MdUtil.getChapterId(chapter.url), usePort443Only)

                pageListParse(chapterResponse, atHomeRequestUrl, atHomeResponse, dataSaver)
            }
        }
    }

    fun pageListParse(
        chapterDto: ChapterDto,
        atHomeRequestUrl: String,
        atHomeDto: AtHomeDto,
        dataSaver: Boolean,
    ): List<Page> {
        val hash = chapterDto.data.attributes.hash
        val pageArray = if (dataSaver) {
            chapterDto.data.attributes.dataSaver.map { "/data-saver/$hash/$it" }
        } else {
            chapterDto.data.attributes.data.map { "/data/$hash/$it" }
        }
        val now = System.currentTimeMillis()

        val pages = pageArray.mapIndexed { pos, imgUrl ->
            Page(pos + 1, "${atHomeDto.baseUrl},$atHomeRequestUrl,$now", imgUrl)
        }

        return pages
    }
}
