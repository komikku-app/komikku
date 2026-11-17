package app.komikku.sourcejs

/**
 * A failed call into a JavaScript source. The message follows Mangayomi's
 * `Extension "<source name>" failed while <operation>: <detail>` (contract 2.4).
 *
 * @property sourceName the source's display name.
 * @property operation what the host was doing, e.g. `getPopular(1)` or `loading the source`.
 * @property detail the cause in one line.
 */
sealed class JsSourceException(
    val sourceName: String,
    val operation: String,
    val detail: String,
    cause: Throwable? = null,
) : Exception("Extension \"$sourceName\" failed while $operation: $detail", cause) {

    /** The script threw or a promise rejected. [jsStack] is the JS stack trace when the engine had one. */
    class ScriptError(
        sourceName: String,
        operation: String,
        val jsMessage: String,
        val jsStack: String?,
    ) : JsSourceException(sourceName, operation, jsMessage)

    /** The call took longer than [timeoutMillis] (wall clock, including network waits). */
    class Timeout(
        sourceName: String,
        operation: String,
        val timeoutMillis: Long,
    ) : JsSourceException(sourceName, operation, "timed out after $timeoutMillis ms")

    /** The JavaScript context exceeded its memory limit. */
    class OutOfMemory(
        sourceName: String,
        operation: String,
        val memoryLimitBytes: Long,
    ) : JsSourceException(sourceName, operation, "out of memory (limit $memoryLimitBytes bytes)")

    /** The source used a host feature Komikku does not implement, named by [feature]. */
    class Unsupported(
        sourceName: String,
        operation: String,
        val feature: String,
        reason: String,
    ) : JsSourceException(sourceName, operation, "$feature is not supported: $reason")

    /** A host service (HTTP, preferences, …) failed and the script did not handle it. */
    class BridgeError(
        sourceName: String,
        operation: String,
        val channel: String,
        message: String,
        cause: Throwable?,
    ) : JsSourceException(sourceName, operation, message, cause)

    /** The method returned something that does not fit the contract's result shape. */
    class MalformedResult(
        sourceName: String,
        operation: String,
        val method: String,
        val field: String,
        problem: String,
    ) : JsSourceException(sourceName, operation, "malformed result of $method at $field: $problem")
}

/**
 * Raised inside host functions. The script sees it as an `Error` whose message is [message] (the engine uses the
 * throwable's string form, hence [toString]); if the script does not catch it the host turns it into the matching
 * [JsSourceException].
 */
internal sealed class HostFunctionFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    override fun toString(): String = message.orEmpty()

    class Unsupported(val feature: String, val reason: String) :
        HostFunctionFailure("$feature is not supported: $reason")

    class Bridge(val channel: String, message: String, cause: Throwable? = null) : HostFunctionFailure(message, cause)
}
