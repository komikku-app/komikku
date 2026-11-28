package app.komikku.desktop.di

import eu.kanade.tachiyomi.data.cache.ChapterCache
import java.io.Closeable

/**
 * The one place the desktop app reaches into [ChapterCache]'s private disk cache: [ChapterCache] is master's file and
 * has no `close()`. Reflection into a master-owned class is allowed only for shutdown and test teardown, from desktop
 * code, behind a test that names the member (ANSWER 015); `ChapterCacheReflectionTest` fails if [DISK_CACHE_FIELD] is
 * renamed, removed or changes type. Replace it with `ChapterCache.close()` once master has one.
 */
object ChapterCacheReflection {

    /** [ChapterCache]'s private `com.jakewharton.disklrucache.DiskLruCache` field. */
    const val DISK_CACHE_FIELD = "diskCache"

    private val field by lazy {
        ChapterCache::class.java.getDeclaredField(DISK_CACHE_FIELD).apply { isAccessible = true }
    }

    /** The disk cache [cache] uses now (a cache size change replaces it). */
    fun diskCache(cache: ChapterCache): Closeable = field.get(cache) as Closeable

    /** Closes [cache]'s disk cache and so its journal; [cache] is unusable afterwards. */
    fun close(cache: ChapterCache) = diskCache(cache).close()
}
