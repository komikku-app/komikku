package app.komikku.sourcejs.engine

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/** Native stack reserved on top of the JS stack limit for the JVM, JNI and QuickJS's own C frames. */
private const val STACK_HEADROOM_BYTES = 4L * 1024 * 1024

internal actual fun newJsEngineThread(name: String, jsStackBytes: Long): JsEngineThread {
    val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(null, runnable, name, jsStackBytes + STACK_HEADROOM_BYTES).apply { isDaemon = true }
    }
    return JsEngineThread(executor.asCoroutineDispatcher(), jsStackBytes)
}
