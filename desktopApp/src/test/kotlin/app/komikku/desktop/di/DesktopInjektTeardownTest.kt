package app.komikku.desktop.di

import android.app.Application
import eu.kanade.tachiyomi.data.download.DownloadCache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * [DesktopInjekt.stopDelayedWriters]: every registered stop runs to its end (a failing one does not keep the others
 * from running, a cancelled caller does not cut them short), a stop that does not finish in time is reported, a writer
 * created after the stop is stopped at once, and the data folder is no longer re-created by the compat folder getters
 * (the download index cannot resurrect `cache/`). The next [DesktopInjekt.init] resets only the teardown state: the
 * singletons already created (the download cache) survive it, still stopped, so a process is not re-initialisable
 * after a stop except for objects not created yet.
 */
class DesktopInjektTeardownTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `stopping attempts every writer and rethrows the first failure at the end`() {
        DesktopInjekt.init(dir.resolve("data"))
        val stopped = mutableListOf<String>()
        DesktopInjekt.onCreatedWriter("w1") {
            stopped += "a"
            error("first")
        }
        DesktopInjekt.onCreatedWriter("w2") {
            stopped += "b"
            error("second")
        }
        DesktopInjekt.onCreatedWriter("w3") { stopped += "c" }

        val error = shouldThrow<IllegalStateException> { runBlocking { DesktopInjekt.stopDelayedWriters() } }

        error.message shouldBe "first"
        stopped shouldContainExactly listOf("a", "b", "c")
        // Each stop runs once
        runBlocking { DesktopInjekt.stopDelayedWriters() }
        stopped shouldContainExactly listOf("a", "b", "c")
    }

    @Test
    fun `exit actions run once each, the last registered first, a failure not stopping the others`() {
        DesktopInjekt.init(dir.resolve("data"))
        val ran = mutableListOf<String>()
        DesktopInjekt.onExit("first") { ran += "first" }
        DesktopInjekt.onExit("failing") { error("broken") }
        DesktopInjekt.onExit("last") { ran += "last" }

        DesktopInjekt.runExitActions()
        DesktopInjekt.runExitActions()

        ran shouldContainExactly listOf("last", "first")
    }

    @Test
    fun `a writer created after the stop is stopped at once, until the next init`() {
        DesktopInjekt.init(dir.resolve("first"))
        runBlocking { DesktopInjekt.stopDelayedWriters() }
        var lateStops = 0
        DesktopInjekt.onCreatedWriter("w4") { lateStops++ }
        lateStops shouldBe 1

        DesktopInjekt.init(dir.resolve("second"))
        var stops = 0
        DesktopInjekt.onCreatedWriter("w5") { stops++ }
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
    fun `a cancelled caller still runs every stop to its end and gets its cancellation`() {
        DesktopInjekt.init(dir.resolve("data"))
        val stopped = mutableListOf<String>()
        DesktopInjekt.onCreatedWriter("w6") {
            delay(300)
            stopped += "a"
        }
        DesktopInjekt.onCreatedWriter("w7") {
            delay(10)
            stopped += "b"
        }

        val returned = runBlocking { withTimeoutOrNull(50.milliseconds) { DesktopInjekt.stopDelayedWriters() } }

        returned shouldBe null
        stopped shouldContainExactly listOf("a", "b")
    }

    @Test
    fun `a writer created after the stop keeps the thread's interrupt`() {
        DesktopInjekt.init(dir.resolve("data"))
        runBlocking { DesktopInjekt.stopDelayedWriters() }

        Thread.currentThread().interrupt()
        DesktopInjekt.onCreatedWriter("w8") { delay(1000) }

        Thread.interrupted() shouldBe true
    }

    @Test
    fun `the next init keeps the download cache already created, still bound to the first data folder`() {
        DesktopInjekt.init(dir.resolve("first"))
        val first = Injekt.get<DownloadCache>()
        runBlocking { DesktopInjekt.stopDelayedWriters() }

        DesktopInjekt.init(dir.resolve("second"))

        (Injekt.get<DownloadCache>() === first) shouldBe true
    }

    @Test
    fun `a stop that does not finish in time is returned by name and does not hold up the others`() {
        DesktopInjekt.init(dir.resolve("data"))
        var fastStopped = false
        DesktopInjekt.onCreatedWriter("slow") { awaitCancellation() }
        DesktopInjekt.onCreatedWriter("fast") { fastStopped = true }

        val unfinished = runBlocking { DesktopInjekt.stopDelayedWriters(perStop = 100.milliseconds) }

        unfinished shouldContainExactly listOf("slow")
        fastStopped shouldBe true
    }

    @Test
    fun `without a stop the folders are still created on access`() {
        val dataDir = dir.resolve("data")
        val app: Application = DesktopInjekt.init(dataDir)
        dataDir.resolve("cache").deleteRecursively()

        app.cacheDir.isDirectory shouldBe true
    }
}
