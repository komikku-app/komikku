package exh.source

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Response
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.injectLazy

@Suppress("OverridingDeprecatedMember", "DEPRECATION")
class EnhancedHttpSource(
    val originalSource: HttpSource,
    val enhancedSource: HttpSource
) : HttpSource() {
    private val prefs: PreferencesHelper by injectLazy()

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
    override val baseUrl get() = source().baseUrl

    /**
     * Headers used for requests.
     */
    override val headers get() = source().headers

    /**
     * Whether the source has support for latest updates.
     */
    override val supportsLatest get() = source().supportsLatest

    /**
     * Name of the source.
     */
    override val name get() = source().name

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang get() = source().lang

    // ===> OPTIONAL FIELDS

    /**
     * Id of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string: sourcename/language/versionId
     * Note the generated id sets the sign bit to 0.
     */
    override val id get() = source().id

    /**
     * Default network client for doing requests.
     */
    override val client get() = originalSource.client // source().client

    /**
     * Visible name of the source.
     */
    override fun toString() = source().toString()

    /**
     * Returns an observable containing a page with a list of manga. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     */
    override fun fetchPopularManga(page: Int) = source().fetchPopularManga(page)

    /**
     * Returns an observable containing a page with a list of manga. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        source().fetchSearchManga(page, query, filters)

    /**
     * Returns an observable containing a page with a list of latest manga updates.
     *
     * @param page the page number to retrieve.
     */
    override fun fetchLatestUpdates(page: Int) = source().fetchLatestUpdates(page)

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    override fun fetchMangaDetails(manga: SManga) = source().fetchMangaDetails(manga)

    /**
     * [1.x API] Get the updated details for a manga.
     */
    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo = source().getMangaDetails(manga)

    /**
     * Returns the request for the details of a manga. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     *
     * @param manga the manga to be updated.
     */
    override fun mangaDetailsRequest(manga: SManga) = source().mangaDetailsRequest(manga)

    /**
     * Returns an observable with the updated chapter list for a manga. Normally it's not needed to
     * override this method.  If a manga is licensed an empty chapter list observable is returned
     *
     * @param manga the manga to look for chapters.
     */
    override fun fetchChapterList(manga: SManga) = source().fetchChapterList(manga)

    /**
     * [1.x API] Get all the available chapters for a manga.
     */
    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> = source().getChapterList(manga)

    /**
     * Returns an observable with the page list for a chapter.
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    override fun fetchPageList(chapter: SChapter) = source().fetchPageList(chapter)

    /**
     * [1.x API] Get the list of pages a chapter has.
     */
    override suspend fun getPageList(chapter: ChapterInfo): List<tachiyomi.source.model.Page> = source().getPageList(chapter)

    /**
     * Returns an observable with the page containing the source url of the image. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @param page the page whose source image has to be fetched.
     */
    override fun fetchImageUrl(page: Page) = source().fetchImageUrl(page)

    /**
     * Returns an observable with the response of the source image.
     *
     * @param page the page whose source image has to be downloaded.
     */
    override fun fetchImage(page: Page) = source().fetchImage(page)

    /**
     * Called before inserting a new chapter into database. Use it if you need to override chapter
     * fields, like the title or the chapter number. Do not change anything to [manga].
     *
     * @param chapter the chapter to be added.
     * @param manga the manga of the chapter.
     */
    override fun prepareNewChapter(chapter: SChapter, manga: SManga) =
        source().prepareNewChapter(chapter, manga)

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = source().getFilterList()

    fun source(): HttpSource {
        return if (prefs.delegateSources().get()) {
            enhancedSource
        } else {
            originalSource
        }
    }
}
