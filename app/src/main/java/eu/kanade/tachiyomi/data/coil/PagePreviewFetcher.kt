package eu.kanade.tachiyomi.data.coil

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.network.HttpException
import coil.request.Options
import coil.request.Parameters
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.buffer
import okio.sink
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.HttpURLConnection

/**
 * A [Fetcher] that fetches page preview image for [PagePreview] object.
 *
 * Disk caching is handled by [PagePreviewCache], otherwise
 * handled by Coil's [DiskCache].
 */
class PagePreviewFetcher(
    private val page: PagePreview,
    private val options: Options,
    private val pagePreviewFileLazy: Lazy<File>,
    private val diskCacheKeyLazy: Lazy<String>,
    private val sourceLazy: Lazy<PagePreviewSource?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val diskCacheLazy: Lazy<DiskCache>,
) : Fetcher {

    private val diskCacheKey: String
        get() = diskCacheKeyLazy.value

    override suspend fun fetch(): FetchResult {
        return httpLoader()
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceResult(
            source = ImageSource(file = file.toOkioPath(), diskCacheKey = diskCacheKey),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(): FetchResult {
        if (pagePreviewFileLazy.value.exists() && options.diskCachePolicy.readEnabled) {
            return fileLoader(pagePreviewFileLazy.value)
        }
        var snapshot = readFromDiskCache()
        try {
            // Fetch from disk cache
            if (snapshot != null) {
                val snapshotPagePreviewCache = moveSnapshotToPagePreviewCache(snapshot, pagePreviewFileLazy.value)
                if (snapshotPagePreviewCache != null) {
                    // Read from page preview cache
                    return fileLoader(snapshotPagePreviewCache)
                }

                // Read from snapshot
                return SourceResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            // Fetch from network
            val response = executeNetworkRequest()
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                // Read from page preview cache after page preview updated
                val responsePagePreviewCache = writeResponseToPagePreviewCache(response, pagePreviewFileLazy.value)
                if (responsePagePreviewCache != null) {
                    return fileLoader(responsePagePreviewCache)
                }

                // Read from disk cache
                snapshot = writeToDiskCache(snapshot, response)
                if (snapshot != null) {
                    return SourceResult(
                        source = snapshot.toImageSource(),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK,
                    )
                }

                // Read from response if cache is unused or unusable
                return SourceResult(
                    source = ImageSource(source = responseBody.source(), context = options.context),
                    mimeType = "image/*",
                    dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
                )
            } catch (e: Exception) {
                responseBody.closeQuietly()
                throw e
            }
        } catch (e: Exception) {
            snapshot?.closeQuietly()
            throw e
        }
    }

    private suspend fun executeNetworkRequest(): Response {
        val response = sourceLazy.value?.fetchPreviewImage(page.getPagePreviewInfo(), getCacheControl()) ?: callFactoryLazy.value.newCall(newRequest()).await()
        if (!response.isSuccessful && response.code != HttpURLConnection.HTTP_NOT_MODIFIED) {
            response.body?.closeQuietly()
            throw HttpException(response)
        }
        return response
    }

    private fun getCacheControl(): CacheControl? {
        val diskRead = options.diskCachePolicy.readEnabled
        val networkRead = options.networkCachePolicy.readEnabled
        return when {
            !networkRead && diskRead -> {
                CacheControl.FORCE_CACHE
            }
            networkRead && !diskRead -> if (options.diskCachePolicy.writeEnabled) {
                CacheControl.FORCE_NETWORK
            } else {
                CACHE_CONTROL_FORCE_NETWORK_NO_CACHE
            }
            !networkRead && !diskRead -> {
                // This causes the request to fail with a 504 Unsatisfiable Request.
                CACHE_CONTROL_NO_NETWORK_NO_CACHE
            }
            else -> null
        }
    }

    private fun newRequest(): Request {
        val request = Request.Builder()
            .url(page.imageUrl)
            .headers((sourceLazy.value as? HttpSource)?.headers ?: options.headers)
            // Support attaching custom data to the network request.
            .tag(Parameters::class.java, options.parameters)

        val cacheControl = getCacheControl()
        if (cacheControl != null) {
            request.cacheControl(cacheControl)
        }

        return request.build()
    }

    private fun moveSnapshotToPagePreviewCache(snapshot: DiskCache.Snapshot, cacheFile: File): File? {
        return try {
            diskCacheLazy.value.run {
                fileSystem.source(snapshot.data).use { input ->
                    writeSourceToPagePreviewCache(input, cacheFile)
                }
                remove(diskCacheKey)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write snapshot data to page preview cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeResponseToPagePreviewCache(response: Response, cacheFile: File): File? {
        if (!options.diskCachePolicy.writeEnabled) return null
        return try {
            response.peekBody(Long.MAX_VALUE).source().use { input ->
                writeSourceToPagePreviewCache(input, cacheFile)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write response data to page preview cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeSourceToPagePreviewCache(input: Source, cacheFile: File) {
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
        return if (options.diskCachePolicy.readEnabled) diskCacheLazy.value[diskCacheKey] else null
    }

    private fun writeToDiskCache(
        snapshot: DiskCache.Snapshot?,
        response: Response,
    ): DiskCache.Snapshot? {
        if (!options.diskCachePolicy.writeEnabled) {
            snapshot?.closeQuietly()
            return null
        }
        val editor = if (snapshot != null) {
            snapshot.closeAndEdit()
        } else {
            diskCacheLazy.value.edit(diskCacheKey)
        } ?: return null
        try {
            diskCacheLazy.value.fileSystem.write(editor.data) {
                response.body!!.source().readAll(this)
            }
            return editor.commitAndGet()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
        return ImageSource(file = data, diskCacheKey = diskCacheKey, closeable = this)
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
        private val diskCacheLazy: Lazy<DiskCache>,
    ) : Fetcher.Factory<PagePreview> {

        private val pagePreviewCache: PagePreviewCache by injectLazy()
        private val sourceManager: SourceManager by injectLazy()

        override fun create(data: PagePreview, options: Options, imageLoader: ImageLoader): Fetcher {
            return PagePreviewFetcher(
                page = data,
                options = options,
                pagePreviewFileLazy = lazy { pagePreviewCache.getImageFile(data.imageUrl) },
                diskCacheKeyLazy = lazy { PagePreviewKeyer().key(data, options) },
                sourceLazy = lazy { sourceManager.get(data.source) as? PagePreviewSource },
                callFactoryLazy = callFactoryLazy,
                diskCacheLazy = diskCacheLazy,
            )
        }
    }

    companion object {
        private val CACHE_CONTROL_FORCE_NETWORK_NO_CACHE = CacheControl.Builder().noCache().noStore().build()
        private val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()
    }
}
