package exh.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Suppress("OverridingDeprecatedMember", "DEPRECATION")
abstract class DelegatedHttpSource(val delegate: HttpSource) : HttpSource() {
    /**
     * Returns the request for the popular manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun popularMangaParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Returns the request for the search manga given the page.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Returns the request for latest manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns the details of a manga.
     *
     * @param response the response from the site.
     */
    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a SChapter Object.
     *
     * @param response the response from the site.
     */
    override fun chapterPageParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns a list of pages.
     *
     * @param response the response from the site.
     */
    override fun pageListParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Parses the response from the site and returns the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Should never be called!")

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    override val baseUrl get() = delegate.baseUrl

    /**
     * Headers used for requests.
     */
    override val headers get() = delegate.headers

    /**
     * Whether the source has support for latest updates.
     */
    override val supportsLatest get() = delegate.supportsLatest

    /**
     * Name of the source.
     */
    final override val name get() = delegate.name

    // ===> OPTIONAL FIELDS

    /**
     * Id of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string: sourcename/language/versionId
     * Note the generated id sets the sign bit to 0.
     */
    override val id get() = delegate.id

    /**
     * Default network client for doing requests.
     */
    final override val client get() = delegate.client

    /**
     * You must NEVER call super.client if you override this!
     */
    open val baseHttpClient: OkHttpClient? = null
    open val networkHttpClient: OkHttpClient get() = network.client

    /**
     * Visible name of the source.
     */
    override fun toString() = delegate.toString()

    /**
     * Returns an observable containing a page with a list of manga. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularManga"))
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        ensureDelegateCompatible()
        return delegate.fetchPopularManga(page)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        ensureDelegateCompatible()
        return delegate.getPopularManga(page)
    }

    /**
     * Returns an observable containing a page with a list of manga. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchManga"))
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        ensureDelegateCompatible()
        return delegate.fetchSearchManga(page, query, filters)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        ensureDelegateCompatible()
        return delegate.getSearchManga(page, query, filters)
    }

    /**
     * Returns an observable containing a page with a list of latest manga updates.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        ensureDelegateCompatible()
        return delegate.fetchLatestUpdates(page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        ensureDelegateCompatible()
        return delegate.getLatestUpdates(page)
    }

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getMangaDetails"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        ensureDelegateCompatible()
        return delegate.fetchMangaDetails(manga)
    }

    /**
     * [1.x API] Get the updated details for a manga.
     */
    override suspend fun getMangaDetails(manga: SManga): SManga {
        ensureDelegateCompatible()
        return delegate.getMangaDetails(manga)
    }

    /**
     * Returns the request for the details of a manga. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     *
     * @param manga the manga to be updated.
     */
    override fun mangaDetailsRequest(manga: SManga): Request {
        ensureDelegateCompatible()
        return delegate.mangaDetailsRequest(manga)
    }

    /**
     * Returns an observable with the updated chapter list for a manga. Normally it's not needed to
     * override this method.  If a manga is licensed an empty chapter list observable is returned
     *
     * @param manga the manga to look for chapters.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getChapterList"))
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        ensureDelegateCompatible()
        return delegate.fetchChapterList(manga)
    }

    /**
     * [1.x API] Get all the available chapters for a manga.
     */
    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        ensureDelegateCompatible()
        return delegate.getChapterList(manga)
    }

    /**
     * Returns an observable with the page list for a chapter.
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        ensureDelegateCompatible()
        return delegate.fetchPageList(chapter)
    }

    /**
     * [1.x API] Get the list of pages a chapter has.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        ensureDelegateCompatible()
        return delegate.getPageList(chapter)
    }

    /**
     * Returns an observable with the page containing the source url of the image. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @param page the page whose source image has to be fetched.
     */
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getImageUrl"))
    override fun fetchImageUrl(page: Page): Observable<String> {
        ensureDelegateCompatible()
        return delegate.fetchImageUrl(page)
    }

    override suspend fun getImageUrl(page: Page): String {
        ensureDelegateCompatible()
        return delegate.getImageUrl(page)
    }

    /**
     * Returns the response of the source image.
     *
     * @param page the page whose source image has to be downloaded.
     */
    override suspend fun getImage(page: Page): Response {
        ensureDelegateCompatible()
        return delegate.getImage(page)
    }

    /**
     * Returns the url of the provided manga
     *
     * @since extensions-lib 1.4
     * @param manga the manga
     * @return url of the manga
     */
    override fun getMangaUrl(manga: SManga): String {
        ensureDelegateCompatible()
        return delegate.getMangaUrl(manga)
    }

    /**
     * Returns the url of the provided chapter
     *
     * @since extensions-lib 1.4
     * @param chapter the chapter
     * @return url of the chapter
     */
    override fun getChapterUrl(chapter: SChapter): String {
        ensureDelegateCompatible()
        return delegate.getChapterUrl(chapter)
    }

    /**
     * Called before inserting a new chapter into database. Use it if you need to override chapter
     * fields, like the title or the chapter number. Do not change anything to [manga].
     *
     * @param chapter the chapter to be added.
     * @param manga the manga of the chapter.
     */
    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        ensureDelegateCompatible()
        return delegate.prepareNewChapter(chapter, manga)
    }

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = delegate.getFilterList()

    protected open fun ensureDelegateCompatible() {
        if (versionId != delegate.versionId || lang != delegate.lang) {
            throw IncompatibleDelegateException(
                "Delegate source is not compatible (" +
                    "versionId: $versionId <=> ${delegate.versionId}, lang: $lang <=> ${delegate.lang}" +
                    ")!",
            )
        }
    }

    class IncompatibleDelegateException(message: String) : RuntimeException(message)

    init {
        delegate.bindDelegate(this)
    }
}
