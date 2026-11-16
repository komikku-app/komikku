package android.os

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * android-compat: `android.os.Handler` for `Runnable` posts (no `Message` support). Runnables run
 * on the [looper] thread in post order; delays use the monotonic clock.
 */
open class Handler(val looper: Looper) {

    /** Handler for the current thread's looper; throws off the main looper thread, like Android. */
    @Deprecated("Implicitly choosing a Looper is deprecated on Android too")
    constructor() : this(
        Looper.myLooper()
            ?: throw RuntimeException("Can't create handler inside thread ${Thread.currentThread()} that has not called Looper.prepare()"),
    )

    private val pending = HashMap<Runnable, MutableList<ScheduledFuture<*>>>()

    fun post(r: Runnable): Boolean = postDelayed(r, 0)

    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        if (looper.executor.isShutdown) return false
        synchronized(pending) {
            lateinit var future: ScheduledFuture<*>
            future = looper.executor.schedule(
                {
                    synchronized(pending) {
                        pending[r]?.let { futures ->
                            futures.remove(future)
                            if (futures.isEmpty()) pending.remove(r)
                        }
                    }
                    r.run()
                },
                delayMillis.coerceAtLeast(0),
                TimeUnit.MILLISECONDS,
            )
            pending.getOrPut(r) { mutableListOf() }.add(future)
        }
        return true
    }

    fun postAtTime(r: Runnable, uptimeMillis: Long): Boolean =
        postDelayed(r, uptimeMillis - SystemClock.uptimeMillis())

    fun removeCallbacks(r: Runnable) {
        synchronized(pending) { pending.remove(r) }?.forEach { it.cancel(false) }
    }

    /** Only `null` (remove everything) is supported as [token], since there are no messages. */
    fun removeCallbacksAndMessages(token: Any?) {
        if (token != null) return
        val all = synchronized(pending) { pending.values.flatten().also { pending.clear() } }
        all.forEach { it.cancel(false) }
    }

    fun hasCallbacks(r: Runnable): Boolean = synchronized(pending) { pending.containsKey(r) }
}
