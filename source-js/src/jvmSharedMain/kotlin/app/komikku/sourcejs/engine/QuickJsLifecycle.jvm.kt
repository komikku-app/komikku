package app.komikku.sourcejs.engine

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers

/** Never closed; guarded by the lock below. */
private var sentinel: QuickJs? = null

/**
 * quickjs-kt's JNI layer keeps an instance counter, the cached `JavaVM` and cached global class references
 * process-wide without a lock (the last close deletes them, every create replaces two of them), so engines created
 * or closed at the same time on different threads crashed the JVM. Creation and closing are serialised on the monitor
 * of the `com.dokar.quickjs.QuickJs` class, the same one `:core:quickjs` uses for `app.cash.quickjs.QuickJs`, and a
 * sentinel engine created under that lock and never closed keeps the instance count above zero.
 */
internal actual fun <T> quickJsLifecycle(block: () -> T): T = synchronized(QuickJs::class.java) {
    if (sentinel == null) sentinel = QuickJs.create(Dispatchers.Default)
    block()
}
