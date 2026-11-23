package app.komikku.desktop.scheduler

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** P3: the desktop scheduler on an injected clock (due / not due / catch-up / no overlap / disabled / outcomes). */
class DesktopSchedulerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 1_000_000_000_000L
    private var interval = 12.hours.inWholeMilliseconds
    private var lastRun = 0L
    private val runs = AtomicInteger()
    private var outcome = DesktopScheduler.Outcome.Done

    @AfterEach
    fun tearDown() = scope.cancel()

    private fun task(run: suspend () -> DesktopScheduler.Outcome = { outcome }) = DesktopScheduler.Task(
        name = "task",
        intervalMillis = { interval },
        lastRun = { lastRun },
        run = {
            runs.incrementAndGet()
            run()
        },
    )

    private fun scheduler(task: DesktopScheduler.Task) = DesktopScheduler(scope, listOf(task), now = { clock })

    private fun DesktopScheduler.check() = runBlocking {
        checkNow().also { awaitRunning() }
    }

    @Test
    fun `due once the interval has passed since the last run, not before`() {
        val task = task()
        val scheduler = scheduler(task)
        lastRun = clock - 11.hours.inWholeMilliseconds
        scheduler.check() shouldBe emptyList()
        clock += 1.hours.inWholeMilliseconds
        scheduler.isDue(task) shouldBe true
        scheduler.check() shouldBe listOf("task")
        runs.get() shouldBe 1
        // The task did not store a new last run: the attempt still counts, no run on the next check
        scheduler.check() shouldBe emptyList()
        clock += 12.hours.inWholeMilliseconds
        scheduler.check() shouldBe listOf("task")
        runs.get() shouldBe 2
    }

    @Test
    fun `a missed interval is caught up once on the first check`() {
        val scheduler = scheduler(task())
        lastRun = clock - 3 * 24.hours.inWholeMilliseconds
        scheduler.check() shouldBe listOf("task")
        scheduler.check() shouldBe emptyList()
        runs.get() shouldBe 1
        // Never run before (0, a fresh start of the app): due at once
        lastRun = 0L
        scheduler(task()).check() shouldBe listOf("task")
    }

    @Test
    fun `a disabled task never runs`() {
        val task = task()
        val scheduler = scheduler(task)
        interval = 0
        lastRun = 0
        scheduler.check() shouldBe emptyList()
        clock += 1_000.hours.inWholeMilliseconds
        scheduler.isDue(task) shouldBe false
        runs.get() shouldBe 0
        // Turned on later: due from the stored last run
        interval = 24.hours.inWholeMilliseconds
        scheduler.check() shouldBe listOf("task")
    }

    @Test
    fun `a task never runs twice at once`() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val started = Channel<Unit>(Channel.UNLIMITED)
        val scheduler = scheduler(
            task {
                started.send(Unit)
                gate.await()
                DesktopScheduler.Outcome.Done
            },
        )
        scheduler.checkNow() shouldBe listOf("task")
        withTimeout(5_000) { started.receive() }
        clock += 100.hours.inWholeMilliseconds
        scheduler.checkNow() shouldBe emptyList()
        scheduler.checkNow() shouldBe emptyList()
        gate.complete(Unit)
        scheduler.awaitRunning()
        runs.get() shouldBe 1
        // Once it ended: due again, as its attempt started 100 hours (more than an interval) ago
        scheduler.checkNow() shouldBe listOf("task")
        scheduler.awaitRunning()
    }

    @Test
    fun `busy retries after the retry delay, failure waits a whole interval`() {
        val scheduler = scheduler(task())
        outcome = DesktopScheduler.Outcome.Busy
        scheduler.check() shouldBe listOf("task")
        clock += 9.minutes.inWholeMilliseconds
        scheduler.check() shouldBe emptyList()
        clock += 1.minutes.inWholeMilliseconds
        outcome = DesktopScheduler.Outcome.Failed
        scheduler.check() shouldBe listOf("task")
        clock += 11.hours.inWholeMilliseconds
        scheduler.check() shouldBe emptyList()
        clock += 1.hours.inWholeMilliseconds
        scheduler.check() shouldBe listOf("task")
        runs.get() shouldBe 3
    }

    @Test
    fun `an exception counts as a failure and does not stop the scheduler`() {
        val scheduler = scheduler(task { error("boom") })
        scheduler.check() shouldBe listOf("task")
        scheduler.check() shouldBe emptyList()
        clock += 12.hours.inWholeMilliseconds
        scheduler.check() shouldBe listOf("task")
        runs.get() shouldBe 2
    }

    @Test
    fun `start checks at once, then on wake-ups, and stop ends the checks`() = runBlocking<Unit> {
        val ran = Channel<Unit>(Channel.UNLIMITED)
        val wakeUps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val task = task {
            ran.send(Unit)
            DesktopScheduler.Outcome.Done
        }
        val scheduler = DesktopScheduler(scope, listOf(task), now = { clock }, checkInterval = 1.hours, wakeUps = wakeUps)
        scheduler.start()
        scheduler.start()
        withTimeout(5_000) { ran.receive() }
        scheduler.awaitRunning()
        // A preference change (wake-up) after the interval passed runs it without waiting for the next check
        clock += 12.hours.inWholeMilliseconds
        withTimeout(5_000) {
            while (ran.tryReceive().isFailure) {
                wakeUps.tryEmit(Unit)
                delay(20)
            }
        }
        scheduler.stop()
        scheduler.awaitRunning()
        val count = runs.get()
        clock += 100.hours.inWholeMilliseconds
        wakeUps.tryEmit(Unit)
        delay(200)
        runs.get() shouldBe count
    }

    @Test
    fun `after the clock moves back, stored times count from the first check that saw them`() {
        val task = task()
        val scheduler = scheduler(task)
        // A persisted last run in the future: one interval from the first check that saw it, not until the clock
        // caught up with it
        lastRun = clock + 100.hours.inWholeMilliseconds
        scheduler.check() shouldBe emptyList()
        clock += 12.hours.inWholeMilliseconds
        scheduler.check() shouldBe listOf("task")
        // The attempt (in memory) ends up in the future
        clock -= 50.hours.inWholeMilliseconds
        scheduler.check() shouldBe emptyList()
        clock += 12.hours.inWholeMilliseconds
        scheduler.check() shouldBe listOf("task")
        runs.get() shouldBe 2
    }

    @Test
    fun `after the clock moves back, a busy retry waits the retry delay`() {
        val scheduler = scheduler(task())
        outcome = DesktopScheduler.Outcome.Busy
        scheduler.check() shouldBe listOf("task")
        clock -= 5.hours.inWholeMilliseconds
        scheduler.check() shouldBe emptyList()
        clock += 10.minutes.inWholeMilliseconds
        scheduler.check() shouldBe listOf("task")
    }
}
