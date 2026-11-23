package app.komikku.sourcejs.mihon

import android.content.SharedPreferences
import app.komikku.sourcejs.JsExtension
import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.JsSourceException
import app.komikku.sourcejs.model.JsChapter
import app.komikku.sourcejs.model.JsManga
import app.komikku.sourcejs.model.JsMangaPage
import app.komikku.sourcejs.model.JsMangaStatus
import app.komikku.sourcejs.model.JsSourceMetadata
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.PreferenceScreen
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A Mangayomi JavaScript source ([extension]) as a Mihon source, so browsing, the library, the reader, downloads,
 * covers and backups use it like an extension's source (PLAN.md 8.3, I-D4). It is an [HttpSource] because the chapter
 * loader, the downloader and the cover fetchers only handle those; it never uses `HttpSource`'s request helpers or its
 * Injekt-provided network, so it can be built without Injekt.
 *
 * - **Identity:** [id] from [JsSourceIds.komikkuId], [lang] normalised ([JsSourceIds.normalizeLang]), [baseUrl] without
 *   trailing slashes.
 * - **Metadata** (`supportsLatest`, filters, preferences, `getHeaders(baseUrl)`) is read in one context
 *   ([JsExtension.metadata]) and cached, because Mihon reads [supportsLatest], [headers] and [getFilterList]
 *   synchronously. It is read again after the source's preferences change (they may depend on them): a change of the
 *   preference file schedules a refresh, and every call waits for a pending one ([awaitMetadata]).
 * - **Calls** run at most [permits] at a time (each one creates a JavaScript context with its own heap), on the host's
 *   threads.
 * - **Failures:** an unhandled network failure of the script is rethrown as the original `IOException` (so the app
 *   shows "offline" / "unknown host" as for any source); a source without `getLatestUpdates` or `search` gives an empty
 *   page (and latest is no longer offered); everything else is the [JsSourceException], whose message names the source,
 *   the operation and the cause.
 * - **Pages** carry the image URL and the page's own headers in `Page.url` ([JsPageRef]); [imageRequest] sends the
 *   source headers plus the page headers.
 *
 * @param clientProvider the app's HTTP client for images and covers (the script's own requests go through the host's
 *   `HostHttp`).
 * @param preferencesProvider the source's preference file ([JsSourceIds.preferencesName]); also the store of the script's
 *   `SharedPreferences` (`MangayomiJsRuntime`) and of [setupPreferenceScreen].
 * @param scope runs the metadata refreshes scheduled by preference changes; [close] cancels it.
 */
class MangayomiJsSource(
    val extension: JsExtension,
    initialMetadata: JsSourceMetadata,
    private val clientProvider: () -> OkHttpClient,
    private val preferencesProvider: () -> SharedPreferences,
    private val permits: Semaphore = Semaphore(DEFAULT_CONCURRENT_CALLS),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : HttpSource(), ConfigurableSource {

    val descriptor: JsSourceDescriptor get() = extension.source

    override val id: Long = JsSourceIds.komikkuId(descriptor.id)

    override val name: String = descriptor.name

    override val lang: String = JsSourceIds.normalizeLang(descriptor.lang)

    override val baseUrl: String = JsSourceUrls.normalizeBaseUrl(descriptor.baseUrl)

    private class Cached(val metadata: JsSourceMetadata, val headers: Headers)

    @Volatile
    private var cached = Cached(initialMetadata, initialMetadata.headers.toSafeHeaders())

    private val stale = AtomicBoolean(false)
    private val refreshLock = Mutex()

    /** Set once `getLatestUpdates` turned out not to be implemented although `supportsLatest` said so. */
    @Volatile
    private var latestMissing = false

    /** The cached metadata (possibly older than a preference change whose refresh is still running). */
    val metadata: JsSourceMetadata get() = cached.metadata

    override val supportsLatest: Boolean get() = cached.metadata.supportsLatest && !latestMissing

    override val supportsRelatedMangas: Boolean get() = false

    override val client: OkHttpClient get() = clientProvider()

    /** `getHeaders(baseUrl)` of the cached metadata; also used for covers and images. */
    override val headers: Headers get() = cached.headers

    // Held here: Android keeps preference listeners only weakly.
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || !key.startsWith(SharedPreferencesHostPreferences.STRING_KEY_PREFIX)) requestMetadataRefresh()
    }

    init {
        preferencesProvider().registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    // ---- Metadata ----

    /** Marks the metadata stale and reads it again in the background (calls wait for it). */
    fun requestMetadataRefresh() {
        stale.set(true)
        scope.launch { awaitMetadata() }
    }

    /** Reads the metadata again now. A failure keeps the cached metadata (logged). */
    suspend fun refreshMetadata(): JsSourceMetadata {
        stale.set(true)
        return awaitMetadata()
    }

    /** The metadata, read again first if a preference changed since it was read. */
    suspend fun awaitMetadata(): JsSourceMetadata {
        if (!stale.get()) return cached.metadata
        refreshLock.withLock {
            if (stale.getAndSet(false)) {
                try {
                    val metadata = permits.withPermit { extension.metadata() }
                    cached = Cached(metadata, metadata.headers.toSafeHeaders())
                    latestMissing = false
                } catch (e: CancellationException) {
                    stale.set(true)
                    throw e
                } catch (e: JsSourceException) {
                    logcat(LogPriority.WARN, e) { "Keeping the cached metadata of $name" }
                }
            }
        }
        return cached.metadata
    }

    /** Stops listening to preference changes and cancels pending refreshes (the source is being removed). */
    fun close() {
        preferencesProvider().unregisterOnSharedPreferenceChangeListener(preferenceListener)
        scope.cancel()
    }

    // ---- Catalogue ----

    override fun getFilterList(): FilterList = JsFilters.toFilterList(cached.metadata.filters)

    override suspend fun getPopularManga(page: Int): MangasPage = call { it.getPopular(page) }.toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = try {
        call { it.getLatestUpdates(page) }.toMangasPage()
    } catch (_: JsSourceException.NotImplemented) {
        latestMissing = true
        MangasPage(emptyList(), hasNextPage = false)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = try {
        val jsFilters = JsFilters.toJsFilters(filters, awaitMetadata().filters)
        call { it.search(query, page, jsFilters) }.toMangasPage()
    } catch (_: JsSourceException.NotImplemented) {
        MangasPage(emptyList(), hasNextPage = false)
    }

    /**
     * One `getDetail` for details and chapters. Details the source leaves out (`null`, empty, status unknown) keep the
     * values of [manga], because the app writes every returned field.
     */
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) return SMangaUpdate(manga, chapters)
        val detail = call { it.getDetail(manga.url) }
        return SMangaUpdate(
            manga = if (fetchDetails) mergeDetails(manga, detail) else manga,
            chapters = if (fetchChapters) detail.chapters.map { it.toSChapter() } else chapters,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        call { it.getPageList(chapter.url) }
            .mapNotNull { page -> JsSourceUrls.resolveCoverUrl(baseUrl, page.url)?.let { JsPageRef(it, page.headers) } }
            .distinctBy { it.url }
            .mapIndexed { index, page -> Page(index, url = page.encode(), imageUrl = page.url) }

    /** Only needed when `Page.imageUrl` was cleared: the URL kept in `Page.url`. */
    override suspend fun getImageUrl(page: Page): String =
        JsPageRef.decode(page.url)?.url ?: throw IllegalStateException("Not a page of the JavaScript source $name: ${page.url}")

    /** [headers] plus the page's own headers (which win), for `Page.imageUrl` (the data saver may have changed it). */
    override fun imageRequest(page: Page): Request {
        val ref = JsPageRef.decode(page.url)
        val url = page.imageUrl ?: ref?.url ?: throw IllegalStateException("Page ${page.index} of $name has no image URL")
        val headers = if (ref == null || ref.headers.isEmpty()) headers else headers.newBuilder().setSafely(ref.headers).build()
        return GET(url, headers)
    }

    // ---- Web pages (WebView, share): links that are not URLs or paths open the home page ----

    override fun getMangaUrl(manga: SManga): String = JsSourceUrls.resolveWebUrl(baseUrl, manga.url)

    override fun getChapterUrl(chapter: SChapter): String = JsSourceUrls.resolveWebUrl(baseUrl, chapter.url)

    @Deprecated("The helper functions are inherently limiting and hides the underlying implementation.")
    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    @Deprecated("The helper functions are inherently limiting and hides the underlying implementation.")
    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    @Deprecated("The helper functions are inherently limiting and hides the underlying implementation.")
    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    // ---- Preferences ----

    override fun getSourcePreferences(): SharedPreferences = preferencesProvider()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        JsPreferenceScreens.setup(screen, cached.metadata.preferences)
    }

    // ---- Mapping ----

    private suspend fun <T> call(block: suspend (JsExtension) -> T): T {
        awaitMetadata()
        return try {
            permits.withPermit { block(extension) }
        } catch (e: JsSourceException.BridgeError) {
            val cause = e.cause
            // The script did not handle a network failure: report it like any source's.
            if (e.channel == "http" && cause is IOException) throw cause
            throw e
        }
    }

    private fun JsMangaPage.toMangasPage(): MangasPage = MangasPage(list.mapNotNull { it.toSManga() }, hasNextPage)

    /** A list entry; entries without a link cannot be opened again and are dropped. */
    private fun JsManga.toSManga(): SManga? {
        if (link.isBlank()) return null
        return SManga.create().also {
            it.url = link
            it.title = name.ifBlank { link }
            it.thumbnail_url = JsSourceUrls.resolveCoverUrl(baseUrl, imageUrl)
            it.author = author
            it.artist = artist
            it.description = description
            it.genre = genre.takeIf { genres -> genres.isNotEmpty() }?.joinToString(", ")
            it.status = status.toMihonStatus()
        }
    }

    private fun mergeDetails(manga: SManga, detail: JsManga): SManga = SManga.create().also {
        it.url = manga.url
        it.title = detail.name.ifBlank { manga.title }
        it.thumbnail_url = JsSourceUrls.resolveCoverUrl(baseUrl, detail.imageUrl) ?: manga.thumbnail_url
        it.author = detail.author ?: manga.author
        it.artist = detail.artist ?: manga.artist
        it.description = detail.description ?: manga.description
        it.genre = detail.genre.takeIf { genres -> genres.isNotEmpty() }?.joinToString(", ") ?: manga.genre
        it.status = if (detail.status == JsMangaStatus.UNKNOWN) manga.status else detail.status.toMihonStatus()
        it.update_strategy = manga.update_strategy
        it.memo = manga.memo
        it.initialized = true
    }

    private fun JsChapter.toSChapter(): SChapter = SChapter.create().also {
        it.url = url
        it.name = name.ifBlank { url }
        it.date_upload = dateUpload ?: 0L
        it.scanlator = scanlator
        // Recognised from the name by the app.
        it.chapter_number = -1f
    }

    private fun Map<String, String>.toSafeHeaders(): Headers = Headers.Builder().setSafely(this).build()

    /**
     * Sets every header OkHttp accepts (non-ASCII values through `addUnsafeNonAscii`); `Accept-Encoding` is left to
     * OkHttp, which then decompresses (as for the script's own requests).
     */
    private fun Headers.Builder.setSafely(values: Map<String, String>): Headers.Builder = apply {
        values.forEach { (headerName, value) ->
            if (headerName.equals("Accept-Encoding", ignoreCase = true)) return@forEach
            try {
                set(headerName, value)
            } catch (_: IllegalArgumentException) {
                try {
                    removeAll(headerName)
                    addUnsafeNonAscii(headerName, value)
                } catch (_: IllegalArgumentException) {
                    logcat(LogPriority.WARN) { "Ignoring the invalid header \"$headerName\" of $name" }
                }
            }
        }
    }

    companion object {
        /** Calls of one source that may run at once (each holds a JavaScript context of up to 64 MiB). */
        const val DEFAULT_CONCURRENT_CALLS = 2
    }
}
