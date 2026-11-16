package app.cash.quickjs

import app.komikku.desktop.compat.unsupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import com.dokar.quickjs.QuickJs as QuickJsEngine
import com.dokar.quickjs.QuickJsException as QuickJsEngineException

/**
 * android-compat: `app.cash.quickjs.QuickJs` (the API extensions use through quickjs-android),
 * backed by the same QuickJS engine through the `quickjs-kt` JVM binding.
 *
 * Results are converted like quickjs-android does: `null` for null / undefined, `Boolean`,
 * `Int` for numbers that fit an int, otherwise `Double`, `String`, and arrays as `Array<Any?>`
 * (objects come back as `Map`). Engine errors are rethrown as [QuickJsException].
 *
 * Not supported on desktop: [set] / [get] (binding Java interfaces into JavaScript).
 */
class QuickJs private constructor(
    private val engine: QuickJsEngine,
) : Closeable {

    /** Evaluates [script] and returns the value of its last expression. */
    fun evaluate(script: String, fileName: String): Any? = blocking { engine.evaluate<Any?>(script, fileName) }

    fun evaluate(script: String): Any? = evaluate(script, "?")

    /** Compiles [sourceCode] to QuickJS bytecode for [execute]. */
    fun compile(sourceCode: String, fileName: String): ByteArray = mapErrors { engine.compile(sourceCode, fileName) }

    fun execute(bytecode: ByteArray): Any? = blocking { engine.evaluate<Any?>(bytecode) }

    fun <T> set(name: String, type: Class<T>, obj: T): Unit = unsupported("QuickJs.set")

    fun <T> get(name: String, type: Class<T>): T = unsupported("QuickJs.get")

    override fun close() {
        engine.close()
    }

    private fun blocking(block: suspend () -> Any?): Any? = mapErrors { runBlocking { block() } }.toQuickJsValue()

    private fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: QuickJsEngineException) {
        throw QuickJsException(e.message).apply { initCause(e) }
    }

    companion object {
        @JvmStatic
        fun create(): QuickJs = QuickJs(QuickJsEngine.create(Dispatchers.Default))

        private fun Any?.toQuickJsValue(): Any? = when (this) {
            null, is Boolean, is String, is Int, is Double -> this
            is Long -> if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else toDouble()
            is Float -> toDouble()
            is Number -> toDouble()
            is Array<*> -> Array(size) { this[it].toQuickJsValue() }
            is List<*> -> Array(size) { this[it].toQuickJsValue() }
            is Unit -> null
            else -> this
        }
    }
}
