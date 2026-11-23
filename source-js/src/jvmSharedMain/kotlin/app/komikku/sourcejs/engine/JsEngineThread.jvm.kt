package app.komikku.sourcejs.engine

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Native stack reserved on top of the JS stack limit for the JVM, JNI and QuickJS's own C frames. */
private const val STACK_HEADROOM_BYTES = 4L * 1024 * 1024

/** How long an idle engine thread is kept; the next call starts a new one. */
private const val IDLE_MILLIS = 30_000L

internal actual fun newJsEngineThread(name: String, jsStackBytes: Long): JsEngineThread =
    newJsEngineThread(name, jsStackBytes, IDLE_MILLIS)

/**
 * The thread ends after [idleMillis] without work (an engine that is never closed does not keep it), and the next
 * call starts a new one of the same size: quickjs-kt re-bases QuickJS's stack limit on the current thread at every
 * entry into the engine.
 */
internal fun newJsEngineThread(name: String, jsStackBytes: Long, idleMillis: Long): JsEngineThread {
    val executor = ThreadPoolExecutor(1, 1, idleMillis, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { runnable ->
        Thread(null, runnable, name, jsStackBytes + STACK_HEADROOM_BYTES).apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }
    return JsEngineThread(executor.asCoroutineDispatcher(), jsStackBytes)
}
