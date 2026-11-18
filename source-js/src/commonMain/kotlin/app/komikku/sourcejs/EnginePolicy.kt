package app.komikku.sourcejs

import app.komikku.sourcejs.engine.JsEngine
import app.komikku.sourcejs.engine.JsEngineFactory
import app.komikku.sourcejs.engine.JsEngineLimits

/**
 * When JavaScript contexts are created and released.
 *
 * The only policy now is [FreshContextPerCall], Mangayomi's observable behaviour for data calls (contract 2.3):
 * every provider call gets a new context (prelude and extension evaluated, method called, context closed), so an
 * extension never sees state from an earlier call. A pooling policy can be added behind this interface later; it
 * would have to reset the per-call bridge state the host installs.
 */
interface EnginePolicy {
    suspend fun <T> withEngine(factory: JsEngineFactory, limits: JsEngineLimits, block: suspend (JsEngine) -> T): T

    object FreshContextPerCall : EnginePolicy {
        override suspend fun <T> withEngine(
            factory: JsEngineFactory,
            limits: JsEngineLimits,
            block: suspend (JsEngine) -> T,
        ): T {
            val engine = factory.create(limits)
            try {
                return block(engine)
            } finally {
                engine.close()
            }
        }
    }
}

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
