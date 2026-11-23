package app.komikku.quickjs

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * KMK: the dedicated thread one `app.cash.quickjs.QuickJs` instance runs on.
 *
 * QuickJS checks its JavaScript stack limit against the native stack of the thread it runs on, and the callers of
 * the old library ran it on whatever thread they were on (often small worker stacks). Every call therefore runs on
 * this thread, which has an explicit [STACK_BYTES] stack, well above the JS stack limit, so deep recursion ends in
 * a JavaScript `RangeError` instead of a native stack overflow. Calls from other threads block until done (the
 * public API is synchronous); calls made on this thread itself (a Java binding calling back) run directly.
 *
 * The thread ends after [idleMillis] without calls and a new one (same stack size) starts with the next call:
 * quickjs-kt re-bases QuickJS's stack limit on the current thread at every entry into the engine, so the runtime
 * does not depend on the thread it was created on, and an instance that is never closed does not keep a thread.
 */
internal class EngineThread(idleMillis: Long = IDLE_MILLIS) {

    /** The current (or last) thread of the executor, which has at most one. */
    @Volatile
    private var thread: Thread? = null

    private val executor = ThreadPoolExecutor(1, 1, idleMillis, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { runnable ->
        Thread(null, runnable, "QuickJs-${counter.incrementAndGet()}", STACK_BYTES)
            .apply { isDaemon = true }
            .also { thread = it }
    }.apply { allowCoreThreadTimeOut(true) }

    val isCurrent: Boolean
        get() = Thread.currentThread() === thread

    /**
     * Runs [block] on the engine thread and returns its result, rethrowing what it throws. Waiting is not
     * interruptible (like the old native calls); an interrupt that arrives meanwhile is restored afterwards.
     *
     * @throws RejectedExecutionException when the thread was shut down.
     */
    fun <T> run(block: () -> T): T {
        if (isCurrent) return block()
        val future = executor.submit(Callable(block))
        var interrupted = false
        try {
            while (true) {
                try {
                    return future.get()
                } catch (e: InterruptedException) {
                    interrupted = true
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    /** Queues [block] after the pending calls without waiting for it, then lets the thread end. */
    fun runLastAndShutdown(block: () -> Unit) {
        try {
            executor.execute(block)
        } catch (_: RejectedExecutionException) {
            // Already shut down.
        }
        executor.shutdown()
    }

    companion object {
        /** Native stack of an engine thread; the JS stack limit ([QuickJsRuntime.MAX_JS_STACK_BYTES]) is far below. */
        const val STACK_BYTES = 4L * 1024 * 1024

        /** How long an idle engine thread is kept. */
        const val IDLE_MILLIS = 30_000L

        private val counter = AtomicInteger()
    }
}
