package app.cash.quickjs

import app.komikku.quickjs.addJavaScriptStack

/**
 * KMK: `app.cash.quickjs.QuickJsException` of the zhanghai `quickjs-android` library (Square's `quickjs-java`
 * API, Apache-2.0), kept binary compatible for Mihon extensions (docs/kmp-desktop/PLAN.md, I2b).
 *
 * As in that library, a JavaScript stack ([jsStackTrace], lines like `    at f (file.js:10:5)`) is spliced into
 * the Java stack trace as `JavaScript.f(file.js:10:5)` frames right above the innermost [QuickJs] frame.
 */
class QuickJsException(detailMessage: String?, jsStackTrace: String?) : RuntimeException(detailMessage) {

    constructor(detailMessage: String?) : this(detailMessage, null)

    init {
        if (jsStackTrace != null) addJavaScriptStack(this, jsStackTrace)
    }
}
