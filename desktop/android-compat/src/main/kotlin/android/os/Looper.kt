package android.os

import app.komikku.desktop.compat.unsupported
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory

/**
 * android-compat: `android.os.Looper`. Only the main looper exists on desktop: a dedicated daemon
 * thread ("android-compat-main") that runs [Handler] posts in order. It is not the AWT / Compose
 * UI thread. `prepare()` / `loop()` for other threads are not supported.
 */
class Looper private constructor() {

    internal val executor: ScheduledThreadPoolExecutor

    @Volatile
    private var looperThread: Thread? = null

    init {
        val factory = ThreadFactory { runnable ->
            Thread(runnable, "android-compat-main").apply {
                isDaemon = true
                looperThread = this
            }
        }
        executor = ScheduledThreadPoolExecutor(1, factory).apply {
            removeOnCancelPolicy = true
            prestartAllCoreThreads()
        }
    }

    val thread: Thread
        get() = checkNotNull(looperThread)

    val isCurrentThread: Boolean
        get() = Thread.currentThread() === looperThread

    fun quit(): Unit = throw IllegalStateException("Main thread not allowed to quit.")

    fun quitSafely(): Unit = quit()

    override fun toString(): String = "Looper (${thread.name}, tid ${thread.id})"

    companion object {
        private val main by lazy { Looper() }

        @JvmStatic
        fun getMainLooper(): Looper = main

        /** The main looper when called on its thread, `null` on any other thread (as on Android). */
        @JvmStatic
        fun myLooper(): Looper? = main.takeIf { it.isCurrentThread }

        @JvmStatic
        fun prepare(): Unit = unsupported("Looper.prepare()")

        @JvmStatic
        fun prepareMainLooper(): Unit = unsupported("Looper.prepareMainLooper()")

        @JvmStatic
        fun loop(): Unit = unsupported("Looper.loop()")
    }
}
