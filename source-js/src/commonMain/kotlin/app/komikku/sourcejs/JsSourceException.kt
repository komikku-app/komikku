package app.komikku.sourcejs

/**
 * A failed call into a JavaScript source; nothing else leaves a `JsExtension` call except cancellation. The message
 * follows Mangayomi's `Extension "<source name>" failed while <operation>: <detail>` (contract 2.4).
 *
 * @property sourceId the source's [JsSourceDescriptor.id].
 * @property sourceName the source's display name.
 * @property operation what the host was doing, e.g. `getPopular(1)` or `loading the source`.
 * @property detail the cause in one line.
 */
sealed class JsSourceException(
    val sourceId: Long,
    val sourceName: String,
    val operation: String,
    val detail: String,
    cause: Throwable? = null,
) : Exception("Extension \"$sourceName\" failed while $operation: $detail", cause) {

    /** The script threw or a promise rejected. [jsStack] is the JS stack trace when the engine had one. */
    class ScriptError(
        sourceId: Long,
        sourceName: String,
        operation: String,
        val jsMessage: String,
        val jsStack: String?,
    ) : JsSourceException(sourceId, sourceName, operation, jsMessage)

    /**
     * The source does not implement the async [member] (`getLatestUpdates`, `search`, …): the `MProvider` default ran.
     * Sync members fall back to Mangayomi's defaults instead and never throw this.
     */
    class NotImplemented(
        sourceId: Long,
        sourceName: String,
        operation: String,
        val member: String,
    ) : JsSourceException(sourceId, sourceName, operation, "$member not implemented")

    /** The host's own limit of [timeoutMillis] (wall clock, including network waits) was reached. */
    class Timeout(
        sourceId: Long,
        sourceName: String,
        operation: String,
        val timeoutMillis: Long,
    ) : JsSourceException(sourceId, sourceName, operation, "timed out after $timeoutMillis ms")

    /** The JavaScript context exceeded its memory limit. */
    class OutOfMemory(
        sourceId: Long,
        sourceName: String,
        operation: String,
        val memoryLimitBytes: Long,
    ) : JsSourceException(sourceId, sourceName, operation, "out of memory (limit $memoryLimitBytes bytes)")

    /** The source used a host feature Komikku does not implement, named by [feature]. */
    class Unsupported(
        sourceId: Long,
        sourceName: String,
        operation: String,
        val feature: String,
        reason: String,
    ) : JsSourceException(sourceId, sourceName, operation, "$feature is not supported: $reason")

    /**
     * A host service failed and the script did not handle it, or the host itself failed. [channel] names what failed:
     * - `http` — a `Client` request (network error, response body over `JsHostLimits.maxResponseBodyBytes`);
     * - `pref_default` — `SharedPreferences.get` of a key the source does not declare; other `pref_*` channels —
     *   the preference store;
     * - `dom_*`, `crypto_*`, `deobfuscate_js_password`, `webview`, `log`, `clear_timer`, `default_filters` — the DOM,
     *   crypto, WebView, console, timer and default-filter services;
     * - `engine` — the JavaScript engine (interrupted, could not be created) or the host's own handling of a result;
     * - `host` — any other failure of the host's code;
     * - any other value — a channel the prelude does not know (a host bug).
     */
    class BridgeError(
        sourceId: Long,
        sourceName: String,
        operation: String,
        val channel: String,
        message: String,
        cause: Throwable?,
    ) : JsSourceException(sourceId, sourceName, operation, message, cause)

    /** The method returned something that does not fit the contract's result shape. */
    class MalformedResult(
        sourceId: Long,
        sourceName: String,
        operation: String,
        val method: String,
        val field: String,
        problem: String,
    ) : JsSourceException(sourceId, sourceName, operation, "malformed result of $method at $field: $problem")
}

/**
 * A host function failure. It never crosses into the engine: the session records it under an id and the script
 * sees an `Error` with the same message branded with that id (`JsPrelude`); if the script does not catch it the
 * host turns it into the matching [JsSourceException].
 */
internal sealed class HostFunctionFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class Unsupported(val feature: String, val reason: String) :
        HostFunctionFailure("$feature is not supported: $reason")

    class Bridge(val channel: String, message: String, cause: Throwable? = null) : HostFunctionFailure(message, cause)
}
