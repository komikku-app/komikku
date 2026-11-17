package app.komikku.sourcejs.engine

import kotlinx.coroutines.newSingleThreadContext

/**
 * JS stack limit on iOS. Kotlin/Native worker threads use the platform's default secondary-thread stack (512 KiB on
 * Darwin), so the 4 MiB Mangayomi limit cannot be honoured; keep well below the thread's stack. Not verified on a
 * device yet (PLAN.md 8.4, I5).
 */
private const val IOS_MAX_JS_STACK_BYTES = 256L * 1024

internal actual fun newJsEngineThread(name: String, jsStackBytes: Long): JsEngineThread =
    JsEngineThread(newSingleThreadContext(name), minOf(jsStackBytes, IOS_MAX_JS_STACK_BYTES))
