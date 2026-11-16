package app.komikku.desktop.download

import app.komikku.shared.download.DownloaderRunner
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 8.1 review fix: the desktop `DownloadJob`'s runner. Only the current run writes the "running" flag, so a
 * replaced run that finishes late (pause → resume, `downloadChapters` + `startDownloads`) cannot clear it.
 */
class DownloaderRunnerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `a replaced run that ends late keeps its replacement running`() = runBlocking<Unit> {
        val blockNextPoll = AtomicBoolean(false)
        val blockedPollEntered = CountDownLatch(1)
        val releaseBlockedPoll = CountDownLatch(1)
        val blockedPollReturned = CountDownLatch(1)
        val polls = AtomicInteger()
        val runner = DownloaderRunner(
            scope = scope,
            startDownloader = { true },
            isDownloaderRunning = {
                polls.incrementAndGet()
                if (blockNextPoll.compareAndSet(true, false)) {
                    blockedPollEntered.countDown()
                    releaseBlockedPoll.await(10, TimeUnit.SECONDS)
                    blockedPollReturned.countDown()
                }
                true
            },
            pollMillis = 5,
        )

        runner.start()
        withTimeout(5_000) { runner.running.first { it } }

        // The first run is stuck inside a poll, so it only notices its cancellation later
        blockNextPoll.set(true)
        blockedPollEntered.await(5, TimeUnit.SECONDS) shouldBe true
        val pollsBefore = polls.get()
        runner.start()
        // The replacement runs (it polls)
        withTimeout(5_000) { while (polls.get() <= pollsBefore + 1) delay(5) }
        runner.running.value shouldBe true

        // The first run ends now and must not reset the flag
        releaseBlockedPoll.countDown()
        blockedPollReturned.await(5, TimeUnit.SECONDS) shouldBe true
        delay(200)
        runner.running.value shouldBe true

        runner.stop()
        runner.running.value shouldBe false
    }

    @Test
    fun `a run whose downloader does not start reports not running`() = runBlocking<Unit> {
        val starts = AtomicInteger()
        val downloaderRunning = AtomicBoolean(true)
        val runner = DownloaderRunner(
            scope = scope,
            // The second start fails (nothing to download)
            startDownloader = { starts.incrementAndGet() == 1 },
            isDownloaderRunning = { downloaderRunning.get() },
            pollMillis = 5,
        )
        runner.start()
        withTimeout(5_000) { runner.running.first { it } }
        runner.start()
        withTimeout(5_000) { runner.running.first { !it } }

        // A run ends when the downloader stops by itself
        starts.set(0)
        runner.start()
        withTimeout(5_000) { runner.running.first { it } }
        downloaderRunning.set(false)
        withTimeout(5_000) { runner.running.first { !it } }
    }
}
