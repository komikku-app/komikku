package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.newCallWithProgress
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlin.jvm.Throws
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Response
import rx.Observable

/**
 * A simple implementation for sources from a website, but for Coroutines.
 */
abstract class SuspendHttpSource : HttpSource() {

    /**
     * Returns an observable containing a page with a list of manga. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     */
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(runBlocking { fetchPopularMangaSuspended(page) })
    }

    open suspend fun fetchPopularMangaSuspended(page: Int): MangasPage {
        val response = client.newCall(popularMangaRequestSuspended(page)).await()
        return popularMangaParseSuspended(response)
    }

    /**
     * Returns the request for the popular manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun popularMangaRequest(page: Int): Request {
        return runBlocking { popularMangaRequestSuspended(page) }
    }

    protected abstract suspend fun popularMangaRequestSuspended(page: Int): Request

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun popularMangaParse(response: Response): MangasPage {
        return runBlocking { popularMangaParseSuspended(response) }
    }

    protected abstract suspend fun popularMangaParseSuspended(response: Response): MangasPage

    /**
     * Returns an observable containing a page with a list of manga. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.just(runBlocking { fetchSearchMangaSuspended(page, query, filters) })
    }

    open suspend fun fetchSearchMangaSuspended(page: Int, query: String, filters: FilterList): MangasPage {
        val response = client.newCall(searchMangaRequestSuspended(page, query, filters)).await()
        return searchMangaParseSuspended(response)
    }

    /**
     * Returns the request for the search manga given the page.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return runBlocking { searchMangaRequestSuspended(page, query, filters) }
    }

    protected abstract suspend fun searchMangaRequestSuspended(page: Int, query: String, filters: FilterList): Request

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun searchMangaParse(response: Response): MangasPage {
        return runBlocking { searchMangaParseSuspended(response) }
    }

    protected abstract suspend fun searchMangaParseSuspended(response: Response): MangasPage

    /**
     * Returns an observable containing a page with a list of latest manga updates.
     *
     * @param page the page number to retrieve.
     */
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return Observable.just(runBlocking { fetchLatestUpdatesSuspended(page) })
    }

    open suspend fun fetchLatestUpdatesSuspended(page: Int): MangasPage {
        val response = client.newCall(latestUpdatesRequestSuspended(page)).await()
        return latestUpdatesParseSuspended(response)
    }

    /**
     * Returns the request for latest manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun latestUpdatesRequest(page: Int): Request {
        return runBlocking { latestUpdatesRequestSuspended(page) }
    }

    protected abstract suspend fun latestUpdatesRequestSuspended(page: Int): Request

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun latestUpdatesParse(response: Response): MangasPage {
        return runBlocking { latestUpdatesParseSuspended(response) }
    }

    protected abstract suspend fun latestUpdatesParseSuspended(response: Response): MangasPage

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(runBlocking { fetchMangaDetailsSuspended(manga) })
    }

    open suspend fun fetchMangaDetailsSuspended(manga: SManga): SManga {
        val response = client.newCall(mangaDetailsRequestSuspended(manga)).await()
        return mangaDetailsParseSuspended(response).apply { initialized = true }
    }

    /**
     * Returns the request for the details of a manga. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     *
     * @param manga the manga to be updated.
     */
    override fun mangaDetailsRequest(manga: SManga): Request {
        return runBlocking { mangaDetailsRequestSuspended(manga) }
    }

    open suspend fun mangaDetailsRequestSuspended(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    /**
     * Parses the response from the site and returns the details of a manga.
     *
     * @param response the response from the site.
     */
    override fun mangaDetailsParse(response: Response): SManga {
        return runBlocking { mangaDetailsParseSuspended(response) }
    }

    protected abstract suspend fun mangaDetailsParseSuspended(response: Response): SManga

    /**
     * Returns an observable with the updated chapter list for a manga. Normally it's not needed to
     * override this method.  If a manga is licensed an empty chapter list observable is returned
     *
     * @param manga the manga to look for chapters.
     */
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return try {
            Observable.just(runBlocking { fetchChapterListSuspended(manga) })
        } catch (e: LicencedException) {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    @Throws(LicencedException::class)
    open suspend fun fetchChapterListSuspended(manga: SManga): List<SChapter> {
        return if (manga.status != SManga.LICENSED) {
            val response = client.newCall(chapterListRequestSuspended(manga)).await()
            chapterListParseSuspended(response)
        } else {
            throw LicencedException("Licensed - No chapters to show")
        }
    }

    /**
     * Returns the request for updating the chapter list. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param manga the manga to look for chapters.
     */
    override fun chapterListRequest(manga: SManga): Request {
        return runBlocking { chapterListRequestSuspended(manga) }
    }

    protected open suspend fun chapterListRequestSuspended(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        return runBlocking { chapterListParseSuspended(response) }
    }

    protected abstract suspend fun chapterListParseSuspended(response: Response): List<SChapter>

    /**
     * Returns an observable with the page list for a chapter.
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.just(runBlocking { fetchPageListSuspended(chapter) })
    }

    open suspend fun fetchPageListSuspended(chapter: SChapter): List<Page> {
        val response = client.newCall(pageListRequestSuspended(chapter)).await()
        return pageListParseSuspended(response)
    }

    /**
     * Returns the request for getting the page list. Override only if it's needed to override the
     * url, send different headers or request method like POST.
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    override fun pageListRequest(chapter: SChapter): Request {
        return runBlocking { pageListRequestSuspended(chapter) }
    }

    protected open suspend fun pageListRequestSuspended(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of pages.
     *
     * @param response the response from the site.
     */
    override fun pageListParse(response: Response): List<Page> {
        return runBlocking { pageListParseSuspended(response) }
    }

    protected abstract suspend fun pageListParseSuspended(response: Response): List<Page>

    /**
     * Returns an observable with the page containing the source url of the image. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @param page the page whose source image has to be fetched.
     */
    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(runBlocking { fetchImageUrlSuspended(page) })
    }

    open suspend fun fetchImageUrlSuspended(page: Page): String {
        val response = client.newCall(imageUrlRequestSuspended(page)).await()
        return imageUrlParseSuspended(response)
    }

    /**
     * Returns the request for getting the url to the source image. Override only if it's needed to
     * override the url, send different headers or request method like POST.
     *
     * @param page the chapter whose page list has to be fetched
     */
    override fun imageUrlRequest(page: Page): Request {
        return runBlocking { imageUrlRequestSuspended(page) }
    }

    protected open suspend fun imageUrlRequestSuspended(page: Page): Request {
        return GET(page.url, headers)
    }

    /**
     * Parses the response from the site and returns the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    override fun imageUrlParse(response: Response): String {
        return runBlocking { imageUrlParseSuspended(response) }
    }

    protected abstract suspend fun imageUrlParseSuspended(response: Response): String

    /**
     * Returns an observable with the response of the source image.
     *
     * @param page the page whose source image has to be downloaded.
     */
    override fun fetchImage(page: Page): Observable<Response> {
        return Observable.just(runBlocking { fetchImageSuspended(page) })
    }

    open suspend fun fetchImageSuspended(page: Page): Response {
        return client.newCallWithProgress(imageRequestSuspended(page), page).await()
    }

    /**
     * Returns the request for getting the source image. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param page the chapter whose page list has to be fetched
     */
    override fun imageRequest(page: Page): Request {
        return runBlocking { imageRequestSuspended(page) }
    }

    protected open suspend fun imageRequestSuspended(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }

    /**
     * Called before inserting a new chapter into database. Use it if you need to override chapter
     * fields, like the title or the chapter number. Do not change anything to [manga].
     *
     * @param chapter the chapter to be added.
     * @param manga the manga of the chapter.
     */
    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        runBlocking { prepareNewChapterSuspended(chapter, manga) }
    }

    open suspend fun prepareNewChapterSuspended(chapter: SChapter, manga: SManga) {
    }

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = runBlocking { getFilterListSuspended() }

    open suspend fun getFilterListSuspended() = FilterList()

    companion object {
        data class LicencedException(override val message: String?) : Exception()
    }
}
