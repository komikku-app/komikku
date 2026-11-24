package app.komikku.sourcejs.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import platform.Foundation.NSCondition
import platform.Foundation.NSThread
import kotlin.coroutines.CoroutineContext

/**
 * Native stack reserved on top of the JS stack limit for Kotlin/Native, the host callbacks and QuickJS's own C frames
 * (the same headroom as the JVM engine thread).
 */
private const val STACK_HEADROOM_BYTES = 4L * 1024 * 1024

/** `NSThread.stackSize` must be a multiple of 4 KiB; 16 KiB is also the arm64 page size. */
private const val STACK_GRANULE_BYTES = 16L * 1024

/**
 * iOS engine thread: an `NSThread` with an explicit stack size (Kotlin/Native's `newSingleThreadContext` cannot set
 * one and gets Darwin's 512 KiB secondary-thread default), so the JS stack limit is the same 4 MiB as on Android and
 * desktop. Unlike the JVM thread it does not end when idle, only when the engine closes it (docs/kmp-desktop/IOS_CI.md).
 * Not run on a device or simulator yet; the iOS CI job (`build_ios_check.yml`) is its first check.
 */
internal actual fun newJsEngineThread(name: String, jsStackBytes: Long): JsEngineThread {
    val stackBytes = (jsStackBytes + STACK_HEADROOM_BYTES + STACK_GRANULE_BYTES - 1) / STACK_GRANULE_BYTES * STACK_GRANULE_BYTES
    return JsEngineThread(NSThreadDispatcher(name, stackBytes), jsStackBytes)
}

/** Runs dispatched blocks in order on one `NSThread`; [close] lets it finish the queued blocks and end. */
internal class NSThreadDispatcher(name: String, stackBytes: Long) : CloseableCoroutineDispatcher() {
    private val condition = NSCondition()
    private val queue = ArrayDeque<Runnable>()
    private var closed = false

    /** The thread's configured stack size in bytes (test hook). */
    val stackSize: Long

    init {
        val thread = NSThread(block = ::runLoop)
        thread.name = name
        thread.stackSize = stackBytes.toULong()
        stackSize = thread.stackSize.toLong()
        thread.start()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        condition.lock()
        val accepted = try {
            if (!closed) {
                queue.addLast(block)
                condition.signal()
            }
            !closed
        } finally {
            condition.unlock()
        }
        if (!accepted) {
            // As the JVM executor dispatcher does with a rejected block: cancel its job and run it elsewhere.
            context[Job]?.cancel(CancellationException("The JavaScript engine thread was closed"))
            Dispatchers.Default.dispatch(context, block)
        }
    }

    override fun close() {
        condition.lock()
        try {
            closed = true
            condition.broadcast()
        } finally {
            condition.unlock()
        }
    }

    private fun runLoop() {
        while (true) {
            condition.lock()
            val next = try {
                while (queue.isEmpty() && !closed) condition.wait()
                queue.removeFirstOrNull()
            } finally {
                condition.unlock()
            }
            // Empty only once closed: the queued blocks have run.
            if (next == null) return
            next.run()
        }
    }
}
