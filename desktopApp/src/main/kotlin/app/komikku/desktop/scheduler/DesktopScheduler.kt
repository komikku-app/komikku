package app.komikku.desktop.scheduler

import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * KMK: the desktop counterpart of Android's periodic WorkManager jobs (`LibraryUpdateJob.setupTask`,
 * `BackupCreateJob.setupTask`), owned by the app's lifetime: while the app runs, [start] checks every [checkInterval]
 * (and on every [wakeUps] emission, e.g. a changed interval preference) which [Task]s are due and runs them in [scope].
 *
 * A task is due when its interval is on (> 0) and the interval has passed since the later of its persisted last run
 * ([Task.lastRun], so a run missed while the app was closed is caught up on the first check after start, once) and its
 * last attempt in this process. A task never runs twice at once. Outcomes follow WorkManager's: [Outcome.Done] and
 * [Outcome.Failed] wait a whole interval from the attempt (a failing task is not retried every check), [Outcome.Busy]
 * (the work is already running elsewhere, e.g. a manual update; Android's `Result.retry()`) retries after
 * [Task.retryDelay]. [now] is the clock (epoch milliseconds), injected for tests; when it moves back, stored times in
 * the future count from the check that first saw them.
 */
class DesktopScheduler(
    private val scope: CoroutineScope,
    private val tasks: List<Task>,
    private val now: () -> Long = System::currentTimeMillis,
    private val checkInterval: Duration = 1.minutes,
    private val wakeUps: Flow<Unit> = emptyFlow(),
) {

    class Task(
        val name: String,
        /** The interval in milliseconds; 0 or less = off (Android: "never" / "off"). Read at every check. */
        val intervalMillis: () -> Long,
        /** The persisted time of the last run (epoch milliseconds, 0 = never), e.g. Android's "last updated" key. */
        val lastRun: () -> Long,
        /** Android's back-off after `Result.retry()`: 10 minutes for both jobs. */
        val retryDelay: Duration = 10.minutes,
        /** Runs the work to its end (suspends while it runs). */
        val run: suspend () -> Outcome,
    )

    enum class Outcome { Done, Busy, Failed }

    private class TaskState {
        var job: Job? = null
        var lastAttempt = 0L
        var retryAt = 0L

        /** A persisted last run found in the future (clock moved back) and when it was first seen. */
        var futureLastRun = 0L
        var futureSeenAt = 0L
    }

    private val states = tasks.associate { it.name to TaskState() }
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null

    /** Starts the checks (the first one at once: the catch-up after start). Calling it again does nothing. */
    @Synchronized
    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            launch { wakeUps.collect { wake.trySend(Unit) } }
            while (isActive) {
                checkNow()
                withTimeoutOrNull(checkInterval) { wake.receive() }
            }
        }
    }

    /**
     * Stops the checks, cancels the running tasks and waits until they ended (app exit). A task's [Task.run] stops the
     * work it started when it is cancelled (see `DesktopScheduledJobs`), so this returns once that work ended too; the
     * caller bounds the wait.
     */
    suspend fun stop() {
        val running = synchronized(this) {
            loop?.cancel()
            loop = null
            states.values.mapNotNull { it.job?.apply { cancel() } }
        }
        running.joinAll()
    }

    /** Waits for the running tasks and their outcome bookkeeping (tests). */
    internal suspend fun awaitRunning() {
        val jobs = synchronized(this) { states.values.mapNotNull { it.job } }
        jobs.forEach { it.join() }
    }

    /** Whether [task] is due at [at] (and not running). */
    @Synchronized
    fun isDue(task: Task, at: Long = now()): Boolean {
        val state = states.getValue(task.name)
        if (state.job?.isActive == true) return false
        val interval = task.intervalMillis()
        if (interval <= 0) return false
        // The clock moved back: times in the future count from the first check that saw them (once, not "now" at
        // every check, which would stall the task until the clock caught up again).
        if (state.lastAttempt > at) state.lastAttempt = at
        state.retryAt = state.retryAt.coerceAtMost(at + task.retryDelay.inWholeMilliseconds)
        val stored = task.lastRun()
        val persisted = if (stored > at) {
            if (state.futureLastRun != stored || state.futureSeenAt > at) {
                state.futureLastRun = stored
                state.futureSeenAt = at
            }
            state.futureSeenAt
        } else {
            stored
        }
        val last = maxOf(persisted, state.lastAttempt)
        return at >= last + interval && at >= state.retryAt
    }

    /** One check: starts every due task; returns the names of the started ones. */
    @Synchronized
    fun checkNow(): List<String> {
        val at = now()
        return tasks.filter { isDue(it, at) }.onEach { task ->
            val state = states.getValue(task.name)
            state.job = scope.launch {
                val outcome = try {
                    task.run()
                } catch (e: CancellationException) {
                    // The scheduler stopped: no bookkeeping. Otherwise the work was cancelled on its own (e.g. the
                    // user cancelled the automatic backup on the data screen): a failure, so it waits a whole
                    // interval instead of restarting on the next check.
                    ensureActive()
                    Outcome.Failed
                } catch (e: Throwable) {
                    xLogE("Scheduled ${task.name} failed", e)
                    Outcome.Failed
                }
                synchronized(this@DesktopScheduler) {
                    when (outcome) {
                        Outcome.Done, Outcome.Failed -> {
                            state.lastAttempt = at
                            state.retryAt = 0L
                        }
                        Outcome.Busy -> state.retryAt = at + task.retryDelay.inWholeMilliseconds
                    }
                }
            }
        }.map { it.name }
    }
}
