package app.komikku.sourcejs.engine

import kotlinx.coroutines.CloseableCoroutineDispatcher

/**
 * The single thread a JavaScript context lives on.
 *
 * @property maxJsStackBytes the largest JS stack limit this thread's native stack can hold safely; engines clamp
 *   [JsEngineLimits.maxStackSizeBytes] to it.
 */
internal class JsEngineThread(
    val dispatcher: CloseableCoroutineDispatcher,
    val maxJsStackBytes: Long,
)

/** Starts a new thread for one JavaScript context that can hold a JS stack of [jsStackBytes]. */
internal expect fun newJsEngineThread(name: String, jsStackBytes: Long): JsEngineThread
