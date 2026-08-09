package eu.kanade.tachiyomi.data.coil

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.buffer
import okio.source
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

// KMK -->
@OptIn(ExperimentalCoroutinesApi::class)
class SourceImageCallLimiterTest {

    private fun bodyOf(size: Int) = ByteArrayInputStream(ByteArray(size)).source().buffer()

    @Test
    fun `draining the body releases the permit without a close`() = runTest {
        val permit = SourceImageCallLimiter.acquire(sourceId = 1L)
        val otherPermit = SourceImageCallLimiter.acquire(sourceId = 1L)
        val guarded = permit.releaseWhenConsumed(bodyOf(64))
        var waiterStarted = false
        backgroundScope.launch {
            SourceImageCallLimiter.acquire(sourceId = 1L).release()
            waiterStarted = true
        }
        testScheduler.runCurrent()
        waiterStarted shouldBe false

        // Coil's file-backed path drains the source and keeps it open.
        guarded.readAll(Buffer())
        testScheduler.runCurrent()

        waiterStarted shouldBe true
        otherPermit.release()
    }

    @Test
    fun `closing without draining releases the permit`() = runTest {
        val permit = SourceImageCallLimiter.acquire(sourceId = 2L)
        val otherPermit = SourceImageCallLimiter.acquire(sourceId = 2L)
        val guarded = permit.releaseWhenConsumed(bodyOf(64))
        var waiterStarted = false
        backgroundScope.launch {
            SourceImageCallLimiter.acquire(sourceId = 2L).release()
            waiterStarted = true
        }
        testScheduler.runCurrent()
        waiterStarted shouldBe false

        guarded.close()
        testScheduler.runCurrent()

        waiterStarted shouldBe true
        otherPermit.release()
    }

    @Test
    fun `draining and then closing releases only once`() = runTest {
        val permit = SourceImageCallLimiter.acquire(sourceId = 3L)
        val otherPermit = SourceImageCallLimiter.acquire(sourceId = 3L)
        val guarded = permit.releaseWhenConsumed(bodyOf(64))

        guarded.readAll(Buffer())
        guarded.close()
        permit.release()

        val releaseWaiters = CompletableDeferred<Unit>()
        var waitersStarted = 0
        repeat(2) {
            backgroundScope.launch {
                val waiterPermit = SourceImageCallLimiter.acquire(sourceId = 3L)
                waitersStarted++
                releaseWaiters.await()
                waiterPermit.release()
            }
        }
        testScheduler.runCurrent()

        // Only the permit that was actually released is available.
        waitersStarted shouldBe 1
        otherPermit.release()
        testScheduler.runCurrent()
        waitersStarted shouldBe 2
        releaseWaiters.complete(Unit)
        testScheduler.runCurrent()
    }

    @Test
    fun `the permit is held while the body is only partly read`() = runTest {
        val permit = SourceImageCallLimiter.acquire(sourceId = 4L)
        val otherPermit = SourceImageCallLimiter.acquire(sourceId = 4L)
        val guarded = permit.releaseWhenConsumed(bodyOf(64))
        var waiterStarted = false
        backgroundScope.launch {
            SourceImageCallLimiter.acquire(sourceId = 4L).release()
            waiterStarted = true
        }

        guarded.readByte()
        testScheduler.runCurrent()
        waiterStarted shouldBe false

        guarded.close()
        testScheduler.runCurrent()
        waiterStarted shouldBe true
        otherPermit.release()
    }

    @Test
    fun `a source runs at most two image requests at once`() = runTest {
        val releaseRequests = CompletableDeferred<Unit>()
        var started = 0
        repeat(4) {
            backgroundScope.launch {
                val permit = SourceImageCallLimiter.acquire(sourceId = 5L)
                started++
                releaseRequests.await()
                permit.release()
            }
        }
        testScheduler.runCurrent()

        started shouldBe 2
        releaseRequests.complete(Unit)
        testScheduler.runCurrent()
        started shouldBe 4
    }
}
// KMK <--
