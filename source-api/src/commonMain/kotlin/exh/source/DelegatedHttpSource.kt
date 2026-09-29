package exh.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle

@Suppress("OverridingDeprecatedMember", "DEPRECATION")
abstract class DelegatedHttpSource(val delegate: HttpSource) : HttpSource() {

    override val lang get() = delegate.lang

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    override val baseUrl get() = delegate.baseUrl

    /**
     * Returns the base (home) URL of the website as a string.
     *
     * This is typically the root address that serves as the main entry point
     * to the site's content, such as "https://mihon.tech".
     *
     * This method is used in the browse screen to determine the URL
     * opened when tapping "Open in WebView".
     *
     * @return The website’s home page URL. Defaults to [baseUrl].
     */
    override fun getHomeUrl(): String = delegate.getHomeUrl()

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
    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getPopularManga"))
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
    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getSearchManga"))
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
    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        ensureDelegateCompatible()
        return delegate.fetchLatestUpdates(page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        ensureDelegateCompatible()
        return delegate.getLatestUpdates(page)
    }

    /**
     * Fetches updated information for a manga.
     *
     * Depending on the provided flags or source availability, this may include
     * updated manga metadata, available chapters, or both.
     *
     * If a value is not requested, the existing provided value can be returned as-is.
     * The host app may apply any returned updates regardless of the flags,
     * so care should be taken to only return accurate and intentional changes.
     *
     * @since tachiyomix 1.6
     * @param manga The manga to fetch updates for.
     * @param chapters Existing chapters of the manga
     * @param fetchDetails Whether to fetch updated manga details.
     * @param fetchChapters Whether to fetch available chapters.
     */
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        ensureDelegateCompatible()
        // KMK -->
        return supervisorScope {
            val asyncManga = if (fetchDetails) async { fetchMangaDetails(manga).awaitSingle() } else null
            val asyncChapters = if (fetchChapters) async { fetchChapterList(manga).awaitSingle() } else null
            SMangaUpdate(asyncManga?.await() ?: manga, asyncChapters?.await() ?: chapters)
        }
        // KMK <--
    }

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    @Deprecated("Use the combined suspend API instead", replaceWith = ReplaceWith("getMangaUpdate"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        ensureDelegateCompatible()
        return delegate.fetchMangaDetails(manga)
    }

    /**
     * Returns the request for the details of a manga. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     *
     * @param manga the manga to be updated.
     */
    @Deprecated("The helper functions are inherently limiting and hides the underlying implementation. Source developers should make their own implementation according to their needs.")
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
    @Deprecated("Use the combined suspend API instead", replaceWith = ReplaceWith("getMangaUpdate"))
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        ensureDelegateCompatible()
        return delegate.fetchChapterList(manga)
    }

    /**
     * Returns an observable with the page list for a chapter.
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getPageList"))
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
    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getImageUrl"))
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
    @Deprecated("All modifications should be done when constructing the chapter")
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
