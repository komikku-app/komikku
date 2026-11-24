package app.komikku.shared.coil

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import tachiyomi.domain.source.service.SourceManager
import java.io.File

/**
 * KMK: Coil 3 image loader for desktop, the counterpart of `App.newImageLoader` on Android.
 *
 * - network: OkHttp through [NetworkHelper.client] (cookies, DoH, user agent, cache);
 * - covers: [DesktopMangaCoverFetcher] / [DesktopMangaCoverKeyer] for `MangaCover`, using the
 *   source's client and headers when [sourceManager] knows the source;
 * - disk cache in [cacheDir] (`<dataDir>/cache/image_cache`), 2 % of the free space like Android.
 *
 * - custom covers from [CoverCache] when one is given (P10).
 *
 * Not yet ported from Android: library covers kept in `CoverCache` outside the disk cache, page previews and cover-based colors. Android's `TachiyomiImageDecoder` (AVIF / JXL / HEIF) has no desktop decoder:
 * those images go to Skia and, when it cannot decode them, fail with [UnsupportedImageFormatDecoder]'s error
 * (PROGRESS "P5").
 */
object DesktopImageLoader {

    fun create(
        context: PlatformContext,
        networkHelper: NetworkHelper,
        sourceManager: SourceManager?,
        cacheDir: File,
        coverCache: CoverCache? = null,
    ): ImageLoader {
        val callFactoryLazy: Lazy<Call.Factory> = lazy { networkHelper.client }
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { callFactoryLazy.value }))
                add(DesktopMangaCoverFetcher.Factory(callFactoryLazy, sourceManager, coverCache))
                add(DesktopMangaCoverKeyer())
                // AVIF / JPEG XL / HEIF: Skia first, then a clear "unsupported format" error instead of its generic one (P5)
                add(UnsupportedImageFormatDecoder.Factory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .crossfade(300)
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
            .build()
    }
}
