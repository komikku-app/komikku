package app.komikku.quickjs

import kotlinx.coroutines.Dispatchers
import com.dokar.quickjs.QuickJs as Engine

/**
 * KMK: creating and closing quickjs-kt engines (docs/kmp-desktop/PROGRESS.md, "Review fixes (I2b)").
 *
 * quickjs-kt's JNI layer keeps process-wide state without a lock: an instance counter and the cached `JavaVM`
 * (`cache_java_vm` / `clear_java_vm_cache`), the cached global class references, which the last close deletes
 * (`clear_jni_refs_cache`), and the `Unit` / `UByteArray` class references, replaced on every create. Engines
 * created or closed at the same time on different threads therefore crashed the JVM (use of a deleted global
 * reference). Both are serialised process-wide on the monitor of the `com.dokar.quickjs.QuickJs` class, which
 * `:source-js` uses as well (it cannot depend on this module), and a sentinel engine created under the same lock
 * and never closed keeps the instance count above zero, so the cached references are never deleted while other
 * engines use them. Residual: the per-create replacement of the `Unit` / `UByteArray` references can still race
 * with an evaluation on another thread that reads them.
 */
internal object EngineLifecycle {

    /** Never closed; guarded by [lock]. */
    private var sentinel: Engine? = null

    private val lock: Any get() = Engine::class.java

    fun create(): Engine = synchronized(lock) {
        if (sentinel == null) sentinel = Engine.create(Dispatchers.Default)
        Engine.create(Dispatchers.Default)
    }

    fun close(engine: Engine) {
        synchronized(lock) { engine.close() }
    }
}
