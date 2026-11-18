package app.cash.quickjs

import app.komikku.quickjs.MethodSpec
import app.komikku.quickjs.QuickJsRuntime
import app.komikku.quickjs.interfaceMethods
import java.io.Closeable

/**
 * KMK: `app.cash.quickjs.QuickJs`, the JavaScript API Mihon extensions link against (Square's `quickjs-java`, as
 * published by zhanghai's `quickjs-android`), implemented on QuickJS through `quickjs-kt` for Android and desktop
 * (docs/kmp-desktop/PLAN.md, I2b). Binary compatible with that class; behaviour and differences:
 * docs/kmp-desktop/PROGRESS.md, "QuickJS parity".
 *
 * Each instance runs on its own thread with a large native stack and a 1 MiB JavaScript stack limit; the methods
 * block until the call is done. Results are converted like quickjs-java: `null`, `Boolean`, `Integer` /
 * `Double`, `String`, arrays as `Object[]`; other objects are `null`. Script errors throw [QuickJsException] with
 * the JavaScript stack in the stack trace. Parameters are nullable so Java callers get the old library's
 * behaviour instead of Kotlin's null checks.
 */
class QuickJs private constructor(private val runtime: QuickJsRuntime) : Closeable {

    /**
     * Evaluates [script] and returns its completion value; [fileName] is used in error reporting.
     *
     * @throws QuickJsException if there is an error evaluating the script.
     */
    fun evaluate(script: String?, fileName: String?): Any? =
        runtime.evaluate(script ?: throw NullPointerException("script == null"), fileName ?: "?").unwrap()

    /**
     * Evaluates [script] and returns its completion value.
     *
     * @throws QuickJsException if there is an error evaluating the script.
     */
    fun evaluate(script: String?): Any? = evaluate(script, "?")

    /**
     * Provides [object] to JavaScript as a global object called [name]. [type] defines the interface implemented by
     * [object] that will be accessible to JavaScript: an interface that does not extend other interfaces and has no
     * overloaded methods. Methods may take and return `boolean`, `int`, `double`, their boxes, `String`, `Object`
     * and arrays of these (or return `void`).
     */
    fun <T> set(name: String?, type: Class<T>?, `object`: T) {
        type!!
        if (!type.isInterface) throw UnsupportedOperationException("Only interfaces can be bound. Received: $type")
        if (type.interfaces.isNotEmpty()) throw UnsupportedOperationException("$type must not extend other interfaces")
        if (!type.isInstance(`object`)) {
            throw IllegalArgumentException("${`object`!!.javaClass} is not an instance of $type")
        }
        val specs = interfaceMethods(type).map { MethodSpec(it, boxed = false) }
        runtime.set(name!!, `object`!!, specs).unwrap()
    }

    /**
     * Attaches to a global JavaScript object called [name] that implements [type] (an interface that does not
     * extend other interfaces and has no overloaded methods; supported types as for [set]) and returns a proxy that
     * calls it.
     */
    fun <T> get(name: String?, type: Class<T>?): T {
        type!!
        if (!type.isInterface) throw UnsupportedOperationException("Only interfaces can be proxied. Received: $type")
        if (type.interfaces.isNotEmpty()) throw UnsupportedOperationException("$type must not extend other interfaces")
        val specs = interfaceMethods(type).map { MethodSpec(it, boxed = true) }
        @Suppress("UNCHECKED_CAST")
        return runtime.get(name!!, type, specs).unwrap() as T
    }

    /**
     * Compiles [sourceCode] and returns the bytecode for [execute]; [fileName] is used in error reporting.
     *
     * @throws QuickJsException if the source code could not be compiled.
     */
    fun compile(sourceCode: String?, fileName: String?): ByteArray =
        runtime.compile(sourceCode ?: throw NullPointerException("sourceCode == null"), fileName ?: "?")
            .unwrap() as ByteArray

    /**
     * Loads and executes [bytecode] and returns the result.
     *
     * @throws QuickJsException if there is an error loading or executing the code.
     */
    fun execute(bytecode: ByteArray?): Any? =
        runtime.execute(bytecode ?: throw NullPointerException("bytecode == null")).unwrap()

    /** Releases the engine and its thread. Idempotent; later calls throw `NullPointerException`, as before. */
    override fun close() {
        runtime.close()
    }

    /** Frees an instance that was never closed (quickjs-java only logged the leak). */
    protected fun finalize() {
        runtime.closeLeaked()
    }

    companion object {
        /** Creates a new interpreter instance; [close] it when done. */
        @JvmStatic
        fun create(): QuickJs = QuickJs(QuickJsRuntime.create())
    }
}
