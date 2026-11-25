package app.komikku.sourcejs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runMode
import platform.posix.usleep
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * `runBlocking` with a time limit: a test that hangs on the simulator fails with its name instead of stalling the CI
 * job until its own timeout.
 */
internal fun timedTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeoutMillis: Long = 30_000,
    block: suspend CoroutineScope.() -> Unit,
) = runBlocking(context) {
    withTimeout(timeoutMillis) { block() }
}

/**
 * [timedTest] for code that needs the main thread (`Dispatchers.Main`, WebKit). The tests run on the process's main
 * thread, where `runBlocking` would block the main dispatch queue for good: [block] runs on `Dispatchers.Default`
 * while this thread runs its run loop, which serves the main queue, until [block] ends. A [block] still running after
 * [timeoutMillis] (plus a margin for its own cancellation) fails the test instead of hanging it, also when this is not
 * the main thread (the main queue is then served elsewhere, or never).
 */
internal fun mainRunLoopTest(timeoutMillis: Long = 30_000, block: suspend CoroutineScope.() -> Unit) {
    val job = GlobalScope.async(Dispatchers.Default) { withTimeout(timeoutMillis) { block() } }
    val onMainThread = NSThread.isMainThread
    val deadline = TimeSource.Monotonic.markNow() + (timeoutMillis + 5_000).milliseconds
    while (!job.isCompleted && deadline.hasNotPassedNow()) {
        if (onMainThread) {
            // Returns at once when nothing is queued for this mode: do not spin.
            val ran = NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.05))
            if (!ran) usleep(2_000u)
        } else {
            usleep(10_000u)
        }
    }
    if (!job.isCompleted) {
        job.cancel()
        throw AssertionError("Test still running ${timeoutMillis + 5_000} ms after it started (on the main thread: $onMainThread)")
    }
    job.getCompletionExceptionOrNull()?.let { throw it }
}
