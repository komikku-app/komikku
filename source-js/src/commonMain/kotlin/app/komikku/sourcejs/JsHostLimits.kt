package app.komikku.sourcejs

import app.komikku.sourcejs.engine.JsEngineLimits

/**
 * Limits for one provider call.
 *
 * @property callTimeoutMillis wall-clock limit for the whole call (context creation, extension evaluation, the
 *   method and its network waits). Mangayomi has none. The default is above the app's HTTP call timeout (120 s), so
 *   one slow request fails as a network error first; QuickJS offers no limit on JavaScript running time alone.
 * @property engine per-context memory and stack limits.
 * @property maxResponseBodyBytes largest HTTP response body a source may receive; a larger one is a
 *   [JsSourceException.BridgeError] (channel `http`) the script can catch. The default is an eighth of the engine's
 *   heap ([defaultMaxResponseBodyBytes]): the body reaches the script as one reply string and is sliced from it, so
 *   it is in the heap twice, at up to two bytes per character (QuickJS stores a string with any character above
 *   U+00FF as UTF-16), which leaves half of the heap to the source. A larger cap needs a larger heap.
 */
data class JsHostLimits(
    val callTimeoutMillis: Long = 180_000,
    val engine: JsEngineLimits = JsEngineLimits(),
    val maxResponseBodyBytes: Long = defaultMaxResponseBodyBytes(engine),
) {
    companion object {
        /** An eighth of [engine]'s heap limit; 50 MiB without a limit. */
        fun defaultMaxResponseBodyBytes(engine: JsEngineLimits): Long =
            if (engine.memoryLimitBytes > 0) engine.memoryLimitBytes / 8 else 50L * 1024 * 1024
    }
}
