package app.komikku.shared.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.service.SourceManager
import java.io.File
import java.io.IOException
import java.net.URI

/**
 * KMK: memory cache key of a desktop cover, like Android's `MangaCoverKeyer`: with a custom cover in [coverCache] (which
 * the fetcher serves first) the entry's id plus the last modification time, otherwise its URL plus that time. A custom
 * cover that is deleted without bumping `coverLastModified` (the shared `removeMangas` / `removeCovers`) therefore
 * changes the key and is not served from the memory cache any more.
 */
class DesktopMangaCoverKeyer(private val coverCache: CoverCache? = null) : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String =
        if (coverCache?.getCustomCoverFile(data.mangaId)?.exists() == true) {
            "${data.mangaId};${data.lastModified}"
        } else {
            "${data.url};${data.lastModified}"
        }
}

/**
 * KMK: minimal desktop counterpart of `MangaCoverFetcher`.
 *
 * - `http(s)` URLs: fetched with the source's OkHttp client and headers when the source is an
 *   [HttpSource] known to [sourceManager] (otherwise the app client), stored in Coil's disk cache;
 * - `file:` URIs and absolute paths (local source covers): read from disk;
 * - a custom cover in [coverCache] (set from the details, P10) comes first, like Android's fetcher with its default
 *   `USE_CUSTOM_COVER`. Covers of library entries are not copied into the [CoverCache] cover folder (Android does);
 *   they stay in Coil's disk cache.
 */
class DesktopMangaCoverFetcher(
    private val cover: MangaCover,
    private val options: Options,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val sourceManager: SourceManager?,
    private val imageLoader: ImageLoader,
    private val coverCache: CoverCache? = null,
) : Fetcher {

    private val diskCacheKey: String get() = diskCacheKey(cover, options)

    override suspend fun fetch(): FetchResult {
        coverCache?.getCustomCoverFile(cover.mangaId)?.takeIf { it.exists() }?.let { return fileLoader(it) }
        val url = cover.url ?: error("No cover specified")
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> httpLoader(url)
            url.startsWith("file:") -> fileLoader(File(URI(url)))
            url.startsWith("/") || File(url).isAbsolute -> fileLoader(File(url))
            else -> error("Invalid cover url: $url")
        }
    }

    private fun fileLoader(file: File): FetchResult = SourceFetchResult(
        source = ImageSource(file = file.toOkioPath(), fileSystem = FileSystem.SYSTEM),
        mimeType = "image/*",
        dataSource = DataSource.DISK,
    )

    private suspend fun httpLoader(url: String): FetchResult {
        val diskCache = imageLoader.diskCache
        if (diskCache != null && options.diskCachePolicy.readEnabled) {
            diskCache.openSnapshot(diskCacheKey)?.let { return it.toFetchResult(diskCache, DataSource.DISK) }
        }

        val source = cover.sourceId.let { sourceManager?.get(it) } as? HttpSource
        val client = source?.client ?: callFactoryLazy.value
        val request = Request.Builder().url(url).apply { source?.headers?.let(::headers) }.build()
        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} for $url")
        }

        if (diskCache != null && options.diskCachePolicy.writeEnabled) {
            writeToDiskCache(diskCache, response)?.let { return it.toFetchResult(diskCache, DataSource.NETWORK) }
        }
        return SourceFetchResult(
            source = ImageSource(source = response.body.source(), fileSystem = FileSystem.SYSTEM),
            mimeType = response.body.contentType()?.toString() ?: "image/*",
            dataSource = DataSource.NETWORK,
        )
    }

    private fun writeToDiskCache(diskCache: DiskCache, response: Response): DiskCache.Snapshot? {
        val editor = diskCache.openEditor(diskCacheKey) ?: return null
        return try {
            diskCache.fileSystem.write(editor.data) {
                response.body.source().use { writeAll(it) }
            }
            editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            runCatching { editor.abort() }
            throw e
        } finally {
            response.close()
        }
    }

    private fun DiskCache.Snapshot.toFetchResult(diskCache: DiskCache, dataSource: DataSource) = SourceFetchResult(
        source = ImageSource(
            file = data,
            fileSystem = diskCache.fileSystem,
            diskCacheKey = diskCacheKey,
            closeable = this,
        ),
        mimeType = "image/*",
        dataSource = dataSource,
    )

    companion object {
        /** The disk cache key of a source cover ([options]' own key, else its URL), also read by the details' "Save". */
        fun diskCacheKey(cover: MangaCover, options: Options? = null): String = options?.diskCacheKey ?: cover.url.orEmpty()
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
        private val sourceManager: SourceManager?,
        private val coverCache: CoverCache? = null,
    ) : Fetcher.Factory<MangaCover> {
        override fun create(data: MangaCover, options: Options, imageLoader: ImageLoader): Fetcher =
            DesktopMangaCoverFetcher(data, options, callFactoryLazy, sourceManager, imageLoader, coverCache)
    }
}
