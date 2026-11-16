package app.cash.quickjs

import app.komikku.desktop.compat.unsupported
import java.io.Closeable

/** android-compat: `app.cash.quickjs.QuickJs` (no JavaScript engine on desktop yet). */
class QuickJs private constructor() : Closeable {

    fun evaluate(script: String, fileName: String): Any? = unsupported("QuickJs.evaluate")

    fun evaluate(script: String): Any? = evaluate(script, "?")

    override fun close() = Unit

    companion object {
        @JvmStatic
        fun create(): QuickJs = unsupported("QuickJs")
    }
}
