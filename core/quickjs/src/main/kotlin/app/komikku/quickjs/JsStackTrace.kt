package app.komikku.quickjs

/** KMK: JavaScript stack splicing of `app.cash.quickjs.QuickJsException` (behaviour of quickjs-java). */

private const val QUICKJS_CLASS_NAME = "app.cash.quickjs.QuickJs"

/** Java stack trace elements need a class name; JavaScript frames use this one. */
private const val STACK_TRACE_CLASS_NAME = "JavaScript"

/**
 * QuickJS stack lines look like `at func (file.ext:line)`; frames without a function are native and skipped.
 * Same pattern as quickjs-java: the position stays part of the file name and the line number is unknown (-1).
 */
private val STACK_TRACE_PATTERN = Regex("""\s*at ([^\s]+) \(([^\s]+(?<!cpp))[:(\d+)]?\).*$""")

/**
 * Splices the JavaScript frames parsed from [jsStackTrace] into [throwable]'s stack trace, right above the first
 * frame of [className] or one of its nested classes (by default `app.cash.quickjs.QuickJs`, where the call into
 * JavaScript happened). Nothing is added when there is no such frame. [dropAbove] also removes the frames above
 * that point (the runtime's own, which quickjs-java's native code did not have).
 */
internal fun addJavaScriptStack(
    throwable: Throwable,
    jsStackTrace: String,
    className: String = QUICKJS_CLASS_NAME,
    dropAbove: Boolean = false,
) {
    val jsElements = jsStackTrace.split("\n").mapNotNull(::toStackTraceElement)
    val stack = throwable.stackTrace
    val index = stack.indexOfFirst { it.className == className || it.className.startsWith("$className$") }
    if (index < 0) return
    val kept = if (dropAbove) emptyList() else stack.take(index)
    throwable.stackTrace = (kept + jsElements + stack.drop(index)).toTypedArray()
}

private fun toStackTraceElement(line: String): StackTraceElement? {
    val match = STACK_TRACE_PATTERN.matchEntire(line) ?: return null
    return StackTraceElement(STACK_TRACE_CLASS_NAME, match.groupValues[1], match.groupValues[2], -1)
}
