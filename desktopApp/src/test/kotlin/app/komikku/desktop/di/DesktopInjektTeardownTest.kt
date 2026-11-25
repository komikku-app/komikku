package app.komikku.desktop.di

import android.app.Application
import eu.kanade.tachiyomi.data.download.DownloadCache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * [DesktopInjekt.stopDelayedWriters]: every registered stop runs (a failing one does not keep the others from
 * running), a writer created after the stop is stopped at once, the data folder is no longer re-created by the compat
 * folder getters (the download index cannot resurrect `cache/`), and the next [DesktopInjekt.init] starts over.
 */
class DesktopInjektTeardownTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `stopping attempts every writer and rethrows the first failure at the end`() {
        DesktopInjekt.init(dir.resolve("data"))
        val stopped = mutableListOf<String>()
        DesktopInjekt.onCreatedWriter {
            stopped += "a"
            error("first")
        }
        DesktopInjekt.onCreatedWriter {
            stopped += "b"
            error("second")
        }
        DesktopInjekt.onCreatedWriter { stopped += "c" }

        val error = shouldThrow<IllegalStateException> { runBlocking { DesktopInjekt.stopDelayedWriters() } }

        error.message shouldBe "first"
        stopped shouldContainExactly listOf("a", "b", "c")
        // Each stop runs once
        runBlocking { DesktopInjekt.stopDelayedWriters() }
        stopped shouldContainExactly listOf("a", "b", "c")
    }

    @Test
    fun `a writer created after the stop is stopped at once, until the next init`() {
        DesktopInjekt.init(dir.resolve("first"))
        runBlocking { DesktopInjekt.stopDelayedWriters() }
        var lateStops = 0
        DesktopInjekt.onCreatedWriter { lateStops++ }
        lateStops shouldBe 1

        DesktopInjekt.init(dir.resolve("second"))
        var stops = 0
        DesktopInjekt.onCreatedWriter { stops++ }
        stops shouldBe 0
        runBlocking { DesktopInjekt.stopDelayedWriters() }
        stops shouldBe 1
    }

    @Test
    fun `after the stop the download index cannot re-create the cache folder`() {
        val dataDir = dir.resolve("data")
        val app: Application = DesktopInjekt.init(dataDir)
        val cache = Injekt.get<DownloadCache>()
        app.cacheDir.isDirectory shouldBe true

        runBlocking { DesktopInjekt.stopDelayedWriters() }
        dataDir.resolve("cache").deleteRecursively() shouldBe true
        cache.invalidateCache()
        cache.cleanInvalidDownloads()
        app.cacheDir

        dataDir.resolve("cache").exists() shouldBe false
    }

    @Test
    fun `without a stop the folders are still created on access`() {
        val dataDir = dir.resolve("data")
        val app: Application = DesktopInjekt.init(dataDir)
        dataDir.resolve("cache").deleteRecursively()

        app.cacheDir.isDirectory shouldBe true
    }
}
