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
 *   [JsSourceException.BridgeError] (channel `http`) the script can catch.
 */
data class JsHostLimits(
    val callTimeoutMillis: Long = 180_000,
    val engine: JsEngineLimits = JsEngineLimits(),
    val maxResponseBodyBytes: Long = 50L * 1024 * 1024,
)
