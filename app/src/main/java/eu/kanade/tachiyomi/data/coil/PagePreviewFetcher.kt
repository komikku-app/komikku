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
import eu.kanade.tachiyomi.source.online.HttpSource
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.HTTP_NOT_MODIFIED
import okio.Path.Companion.toOkioPath
import okio.Source
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * A [Fetcher] that fetches page preview image for [PagePreview] object.
 *
 * Disk caching is handled by [PagePreviewCache], otherwise
 * handled by Coil's [DiskCache].
 */
class PagePreviewFetcher(
    private val page: PagePreview,
    private val options: Options,
    private val pagePreviewFile: () -> File,
    private val isInCache: () -> Boolean,
    private val writeToCache: (Source) -> Unit,
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
        if (isInCache() && options.diskCachePolicy.readEnabled) {
            return fileLoader(pagePreviewFile())
        }
        var snapshot = readFromDiskCache()
        try {
            // Fetch from disk cache
            if (snapshot != null) {
                val snapshotPagePreviewCache = moveSnapshotToPagePreviewCache(snapshot)
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
                val responsePagePreviewCache = writeResponseToPagePreviewCache(response)
                if (responsePagePreviewCache != null) {
                    return fileLoader(responsePagePreviewCache)
                }

                // Read from disk cache
                snapshot = writeToDiskCache(response)
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
                responseBody.close()
                throw e
            }
        } catch (e: Exception) {
            snapshot?.close()
            throw e
        }
    }

    private suspend fun executeNetworkRequest(): Response {
        val response = sourceLazy.value?.fetchPreviewImage(
            page.getPagePreviewInfo(), getCacheControl(),
        ) ?: callFactoryLazy.value.newCall(newRequest()).await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw HttpException(response)
        }
        return response
    }

    private fun getCacheControl(): CacheControl {
        return when {
            options.networkCachePolicy.readEnabled -> {
                // don't take up okhttp cache
                CACHE_CONTROL_NO_STORE
            }
            else -> {
                // This causes the request to fail with a 504 Unsatisfiable Request.
                CACHE_CONTROL_NO_NETWORK_NO_CACHE
            }
        }
    }

    private fun newRequest(): Request {
        val request = Request.Builder()
            .url(page.imageUrl)
            .headers((sourceLazy.value as? HttpSource)?.headers ?: options.headers)
            // Support attaching custom data to the network request.
            .tag(Parameters::class.java, options.parameters)

        request.cacheControl(getCacheControl())

        return request.build()
    }

    private fun moveSnapshotToPagePreviewCache(snapshot: DiskCache.Snapshot): File? {
        return try {
            diskCacheLazy.value.run {
                fileSystem.source(snapshot.data).use { input ->
                    writeSourceToPagePreviewCache(input)
                }
                remove(diskCacheKey)
            }
            return if (isInCache()) {
                pagePreviewFile()
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write snapshot data to page preview cache $diskCacheKey" }
            null
        }
    }

    private fun writeResponseToPagePreviewCache(response: Response): File? {
        if (!options.diskCachePolicy.writeEnabled) return null
        return try {
            response.peekBody(Long.MAX_VALUE).source().use { input ->
                writeSourceToPagePreviewCache(input)
            }
            return if (isInCache()) {
                pagePreviewFile()
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write response data to page preview cache $diskCacheKey" }
            null
        }
    }

    private fun writeSourceToPagePreviewCache(input: Source) {
        writeToCache(input)
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) diskCacheLazy.value.openSnapshot(diskCacheKey) else null
    }

    private fun writeToDiskCache(
        response: Response,
    ): DiskCache.Snapshot? {
        val editor = diskCacheLazy.value.openEditor(diskCacheKey) ?: return null
        try {
            diskCacheLazy.value.fileSystem.write(editor.data) {
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
                pagePreviewFile = { pagePreviewCache.getImageFile(data.imageUrl) },
                isInCache = { pagePreviewCache.isImageInCache(data.imageUrl) },
                writeToCache = { pagePreviewCache.putImageToCache(data.imageUrl, it) },
                diskCacheKeyLazy = lazy { PagePreviewKeyer().key(data, options) },
                sourceLazy = lazy { sourceManager.get(data.source) as? PagePreviewSource },
                callFactoryLazy = callFactoryLazy,
                diskCacheLazy = diskCacheLazy,
            )
        }
    }

    companion object {
        private val CACHE_CONTROL_NO_STORE = CacheControl.Builder().noStore().build()
        private val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()
    }
}
