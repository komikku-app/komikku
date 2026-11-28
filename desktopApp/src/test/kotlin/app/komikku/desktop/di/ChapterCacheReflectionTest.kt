package app.komikku.desktop.di

import android.app.Application
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.DesktopPreferenceStore
import java.io.File

/**
 * [ChapterCacheReflection] reaches into master's [ChapterCache]: these fail loudly when master renames, removes or
 * retypes that member, instead of the desktop tests' teardown failing on Windows with a locked journal.
 */
class ChapterCacheReflectionTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `ChapterCache still has the private disk cache field the desktop app closes`() {
        val field = runCatching { ChapterCache::class.java.getDeclaredField(ChapterCacheReflection.DISK_CACHE_FIELD) }
            .getOrElse {
                throw AssertionError(
                    "ChapterCache has no field '${ChapterCacheReflection.DISK_CACHE_FIELD}' any more: update " +
                        "ChapterCacheReflection (or use ChapterCache.close() if master added one)",
                    it,
                )
            }
        field.type.name shouldBe "com.jakewharton.disklrucache.DiskLruCache"
    }

    @Test
    fun `close closes the disk cache the chapter cache uses`() {
        val cache = ChapterCache(
            Application(File(dir, "data")),
            Json,
            ReaderPreferences(DesktopPreferenceStore(File(dir, "preferences.json"))),
        )
        cache.isImageInCache("https://example.org/1.png") shouldBe false

        ChapterCacheReflection.close(cache)

        shouldThrow<IllegalStateException> { cache.isImageInCache("https://example.org/1.png") }.message shouldBe
            "cache is closed"
    }
}
