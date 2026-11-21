package eu.kanade.tachiyomi.data.coil

import androidx.core.net.toUri
import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.getOrDefault
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher.Companion.USE_CUSTOM_COVER_KEY
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

/**
 * A [Fetcher] that fetches cover image for [Manga] object.
 *
 * It uses [Manga.thumbnailUrl] if custom cover is not set by the user.
 * Disk caching for library items is handled by [CoverCache], otherwise
 * handled by Coil's [DiskCache].
 *
 * Available request parameter:
 * - [USE_CUSTOM_COVER_KEY]: Use custom cover if set by user, default is true
 */
class MangaCoverFetcher(
    // KMK -->
    private val mangaCover: MangaCover,
    private val url: String? = mangaCover.url,
    // private val url: String?,
    // KMK <--
    private val isLibraryManga: Boolean,
    private val options: Options,
    private val coverFileLazy: Lazy<File?>,
    private val customCoverFileLazy: Lazy<File>,
    private val diskCacheKeyLazy: Lazy<String>,
    private val sourceLazy: Lazy<HttpSource?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    // KMK -->
    private val scope by lazy { CoroutineScope(Dispatchers.IO) }
    private val uiPreferences = Injekt.get<UiPreferences>()
    private val themeCoverBased = uiPreferences.themeCoverBased().get()
    private val preloadLibraryColor = uiPreferences.preloadLibraryColor().get()
    // KMK <--

    private val diskCacheKey: String
        get() = diskCacheKeyLazy.value

    /**
     * Called each time a cover is displayed
     */
    override suspend fun fetch(): FetchResult {
        // Use custom cover if exists
        val useCustomCover = options.extras.getOrDefault(USE_CUSTOM_COVER_KEY)
        if (useCustomCover) {
            val customCoverFile = customCoverFileLazy.value
            if (customCoverFile.exists()) {
                return fileLoader(customCoverFile)
            }
        }

        // diskCacheKey is thumbnail_url
        if (url == null) error("No cover specified")
        return when (getResourceType(url)) {
            Type.File -> fileLoader(File(url.substringAfter("file://")))
            Type.URI -> fileUriLoader(url)
            Type.URL -> httpLoader()
            null -> error("Invalid image")
        }
    }

    private fun fileLoader(file: File): FetchResult {
        // KMK -->
        setRatioAndColorsInScope(mangaCover = mangaCover, ogFile = file)
        // KMK <--
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = diskCacheKey,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private fun fileUriLoader(uri: String): FetchResult {
        // KMK -->
        setRatioAndColorsInScope(mangaCover = mangaCover)
        // KMK <--
        val source = UniFile.fromUri(options.context, uri.toUri())!!
            .openInputStream()
            .source()
            .buffer()
        return SourceFetchResult(
            source = ImageSource(source = source, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(): FetchResult {
        // Only cache separately if it's a library item
        val libraryCoverCacheFile = if (isLibraryManga) {
            coverFileLazy.value ?: error("No cover specified")
        } else {
            null
        }
        if (libraryCoverCacheFile?.exists() == true && options.diskCachePolicy.readEnabled) {
            return fileLoader(libraryCoverCacheFile)
        }

        var snapshot = readFromDiskCache()
        try {
            // Fetch from disk cache
            if (snapshot != null) {
                val snapshotCoverCache = moveSnapshotToCoverCache(snapshot, libraryCoverCacheFile)
                if (snapshotCoverCache != null) {
                    // Read from cover cache after added to library
                    return fileLoader(snapshotCoverCache)
                }

                // Read from snapshot
                // KMK -->
                setRatioAndColorsInScope(mangaCover = mangaCover, snapshot = snapshot)
                // KMK <--
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            // Fetch from network
            val response = executeNetworkRequest()
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                // Read from cover cache after library manga cover updated
                val responseCoverCache = writeResponseToCoverCache(response, libraryCoverCacheFile)
                if (responseCoverCache != null) {
                    return fileLoader(responseCoverCache)
                }

                // Read from disk cache
                snapshot = writeToDiskCache(response)
                if (snapshot != null) {
                    // KMK -->
                    setRatioAndColorsInScope(mangaCover = mangaCover, snapshot = snapshot)
                    // KMK <--
                    return SourceFetchResult(
                        source = snapshot.toImageSource(),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK,
                    )
                }

                // KMK -->
                // Metadata extraction must never share the live response stream with Coil: two
                // concurrent readers of one HTTP/2 stream crash with "Unbalanced enter/exit".
                // So give it an independent in-memory copy, peeked here before Coil starts reading.
                setRatioAndColorsInScope(mangaCover = mangaCover) { peekCoverForMetadata(response) }
                // KMK <--
                // Read from response if cache is unused or unusable
                return SourceFetchResult(
                    source = ImageSource(source = responseBody.source(), fileSystem = FileSystem.SYSTEM),
                    mimeType = "image/*",
                    dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
                )
            } catch (e: Exception) {
                responseBody.close()
                throw e
            }
        } catch (e: Exception) {
            snapshot?.close()
            throw e
        }
    }

    private suspend fun executeNetworkRequest(): Response {
        val client = sourceLazy.value?.client ?: callFactoryLazy.value
        val response = client.newCall(newRequest()).await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException(response.message)
        }
        return response
    }

    private fun newRequest(): Request {
        val request = Request.Builder().apply {
            url(url!!)

            val sourceHeaders = sourceLazy.value?.headers
            if (sourceHeaders != null) {
                headers(sourceHeaders)
            }
        }

        when {
            options.networkCachePolicy.readEnabled -> {
                // don't take up okhttp cache
                request.cacheControl(CACHE_CONTROL_NO_STORE)
            }
            else -> {
                // This causes the request to fail with a 504 Unsatisfiable Request.
                request.cacheControl(CACHE_CONTROL_NO_NETWORK_NO_CACHE)
            }
        }

        return request.build()
    }

    private fun moveSnapshotToCoverCache(snapshot: DiskCache.Snapshot, cacheFile: File?): File? {
        if (cacheFile == null) return null
        return try {
            imageLoader.diskCache?.run {
                fileSystem.source(snapshot.data).use { input ->
                    writeSourceToCoverCache(input, cacheFile)
                }
                remove(diskCacheKey)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write snapshot data to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeResponseToCoverCache(response: Response, cacheFile: File?): File? {
        if (cacheFile == null || !options.diskCachePolicy.writeEnabled) return null
        return try {
            response.peekBody(Long.MAX_VALUE).source().use { input ->
                writeSourceToCoverCache(input, cacheFile)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write response data to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeSourceToCoverCache(input: Source, cacheFile: File) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.delete()
        try {
            cacheFile.sink().buffer().use { output ->
                output.writeAll(input)
            }
        } catch (e: Exception) {
            cacheFile.delete()
            throw e
        }
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) {
            imageLoader.diskCache?.openSnapshot(diskCacheKey)
        } else {
            null
        }
    }

    private fun writeToDiskCache(
        response: Response,
    ): DiskCache.Snapshot? {
        val diskCache = imageLoader.diskCache
        val editor = diskCache?.openEditor(diskCacheKey) ?: return null
        try {
            diskCache.fileSystem.write(editor.data) {
                response.body.source().readAll(this)
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    // KMK -->
    /**
     * Copy the cover out of [response] without consuming it, so the body can still be read by Coil.
     *
     * @return null if the cover is larger than [MAX_COVER_PEEK_BYTES], to avoid buffering a large image
     *  in memory or decoding a truncated one.
     */
    private fun peekCoverForMetadata(response: Response): BufferedSource? {
        if (response.body.contentLength() > MAX_COVER_PEEK_BYTES) return null
        // Peek one more byte than the cap to detect a body of unknown length which exceeds it
        val peeked = response.peekBody(MAX_COVER_PEEK_BYTES + 1)
        return peeked.source().takeIf { peeked.contentLength() <= MAX_COVER_PEEK_BYTES }
    }
    // KMK <--

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
        return ImageSource(
            file = data,
            fileSystem = FileSystem.SYSTEM,
            diskCacheKey = diskCacheKey,
            closeable = this,
        )
    }

    private fun getResourceType(cover: String?): Type? {
        return when {
            cover.isNullOrEmpty() -> null
            cover.startsWith("http", true) || cover.startsWith("Custom-", true) -> Type.URL
            cover.startsWith("/") || cover.startsWith("file://") -> Type.File
            cover.startsWith("content") -> Type.URI
            else -> null
        }
    }

    // KMK -->
    /**
     * [setRatioAndColorsInScope] is called whenever a cover is loaded with [MangaCoverFetcher.fetch]
     *
     * @param ogFile if not null then it will load bitmap from [File]. If it's null then it will try to load bitmap
     *  from [CoverCache] using either [CoverCache.customCoverCacheDir] or [CoverCache.cacheDir]
     * @param force if true then it will re-calculate ratio & colors even if they are known, except in the cases
     *  where [MangaCoverMetadata.needsColorUpdate] always skips.
     * @param openSource if it returns a [BufferedSource] then it will load bitmap from that, regardless of [ogFile].
     *  If it returns null or fails then nothing is extracted, it doesn't fall back to [ogFile] or [CoverCache].
     *  It's called on the calling thread, and only when the bitmap is actually going to be decoded. The source
     *  must be independent from the one handed to Coil, it is read on another thread and closed after use.
     */
    private fun setRatioAndColorsInScope(
        mangaCover: MangaCover,
        ogFile: File? = null,
        onlyFavorite: Boolean = !themeCoverBased,
        force: Boolean = false,
        openSource: (() -> BufferedSource?)? = null,
    ) {
        if (!preloadLibraryColor) return
        // This runs for every displayed cover and most of them already have their colors, so do the cheap
        // bookkeeping here and only launch a coroutine when a bitmap is actually going to be decoded.
        if (!mangaCover.isMangaFavorite) {
            with(MangaCoverMetadata) { mangaCover.remove() }
        }
        if (!MangaCoverMetadata.needsColorUpdate(mangaCover, onlyFavorite, force)) return
        val bufferedSource = if (openSource != null) {
            val source = try {
                openSource()
            } catch (e: Exception) {
                // Expected when the request is cancelled or the connection drops, Coil will report it if it matters
                logcat(LogPriority.WARN, e) { "Failed to open cover source for metadata" }
                null
            } catch (e: OutOfMemoryError) {
                // Colors are optional, don't fail the cover's fetch because its copy doesn't fit in memory
                logcat(LogPriority.ERROR, e) { "Not enough memory to open cover source for metadata" }
                null
            }
            // Skip rather than fall back to CoverCache, which may still hold an older cover than the one being loaded
            source ?: return
        } else {
            null
        }
        scope.launch {
            try {
                bufferedSource.use {
                    MangaCoverMetadata.setRatioAndColors(
                        mangaCover = mangaCover,
                        bufferedSource = it,
                        ogFile = ogFile,
                        onlyDominantColor = onlyFavorite,
                        force = force,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Colors are optional, never let them crash the app
                logcat(LogPriority.ERROR, e) { "Failed to extract cover ratio & colors" }
            } catch (e: OutOfMemoryError) {
                // Same for a cover which is too large to decode, it's only this bitmap which doesn't fit
                logcat(LogPriority.ERROR, e) { "Not enough memory to extract cover ratio & colors" }
            }
        }
    }

    /**
     * [setRatioAndColorsInScope] reading the cover through a new handle on the [snapshot]'s file,
     * independent from the one handed to Coil.
     */
    private fun setRatioAndColorsInScope(mangaCover: MangaCover, snapshot: DiskCache.Snapshot) {
        setRatioAndColorsInScope(mangaCover = mangaCover) { FileSystem.SYSTEM.source(snapshot.data).buffer() }
    }
    // KMK <--

    private enum class Type {
        File,
        URI,
        URL,
    }

    class MangaFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<Manga> {

        private val coverCache: CoverCache by injectLazy()
        private val sourceManager: SourceManager by injectLazy()

        override fun create(data: Manga, options: Options, imageLoader: ImageLoader): Fetcher {
            return MangaCoverFetcher(
                // KMK -->
                // url = data.thumbnailUrl,
                mangaCover = data.asMangaCover(),
                // KMK <--
                isLibraryManga = data.favorite,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.thumbnailUrl) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.id) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                sourceLazy = lazy { sourceManager.get(data.source) as? HttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    class MangaCoverFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<MangaCover> {

        private val coverCache: CoverCache by injectLazy()
        private val sourceManager: SourceManager by injectLazy()

        override fun create(data: MangaCover, options: Options, imageLoader: ImageLoader): Fetcher {
            return MangaCoverFetcher(
                // KMK -->
                // url = data.url,
                mangaCover = data,
                // KMK <--
                isLibraryManga = data.isMangaFavorite,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.url) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.mangaId) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                sourceLazy = lazy { sourceManager.get(data.sourceId) as? HttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    companion object {
        val USE_CUSTOM_COVER_KEY = Extras.Key(true)

        private val CACHE_CONTROL_NO_STORE = CacheControl.Builder().noStore().build()
        private val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()

        private const val HTTP_NOT_MODIFIED = 304

        // KMK -->
        /** Maximum bytes to peek from the network response for cover metadata extraction. */
        private const val MAX_COVER_PEEK_BYTES = 5L * 1024 * 1024 // 5 MiB
        // KMK <--
    }
}
