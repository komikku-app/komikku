package app.komikku.quickjs

import app.cash.quickjs.QuickJsException
import com.dokar.quickjs.binding.FunctionBinding
import com.dokar.quickjs.binding.JsObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import com.dokar.quickjs.QuickJs as Engine
import com.dokar.quickjs.QuickJsException as EngineException

/**
 * KMK: the engine behind one `app.cash.quickjs.QuickJs` (docs/kmp-desktop/PLAN.md, I2b): a quickjs-kt runtime on
 * its own [EngineThread], with quickjs-java's conversions, errors and `set` / `get` bindings.
 *
 * State marked "engine thread" is only touched on that thread.
 */
internal class QuickJsRuntime private constructor(
    private val thread: EngineThread,
    private val engine: Engine,
) {
    private val closed = AtomicBoolean(false)

    /** Java exception thrown by (or recorded for) a `set` binding during the current call (engine thread). */
    private var pendingJavaException: Throwable? = null

    /** Number of Java binding methods running (engine thread); quickjs-kt cannot evaluate from inside one. */
    private var bindingDepth = 0

    /** Arguments of the `get` proxy call in progress, handed to JavaScript by the pull binding (engine thread). */
    private var proxyArguments: List<Any?> = emptyList()

    /** Global name of the `get` invoker, installed on first use (engine thread). */
    private var invokerName: String? = null

    fun evaluate(script: String, fileName: String): Outcome = call {
        evaluateToJava { engine.evaluate<Any?>(script, fileName) }
    }

    fun execute(bytecode: ByteArray): Outcome = call {
        evaluateToJava { engine.evaluate<Any?>(bytecode) }
    }

    /** Compiles on the engine thread; allowed from inside a binding (quickjs-kt does not lock for it). */
    fun compile(sourceCode: String, fileName: String): Outcome = onEngine {
        ensureOpen()
        try {
            Outcome.Value(engine.compile(sourceCode, fileName, false))
        } catch (e: Throwable) {
            failure(e)
        }
    }

    /** `QuickJs.set` once the interface was validated: installs [name] with [specs] calling [target]. */
    fun set(name: String, target: Any, specs: List<MethodSpec>): Outcome {
        val outcome = call {
            val rawName = hiddenName()
            engine.defineBinding(rawName, FunctionBinding { args -> dispatch(target, specs, args) })
            runBlocking { engine.evaluate<Any?>(setScript(rawName, name, specs), "?") }
        }
        if (outcome is Outcome.Value && outcome.value == false) {
            throw IllegalArgumentException("A global object called $name already exists")
        }
        return outcome
    }

    /** `QuickJs.get` once the interface was validated: checks the global [name] and returns a proxy of [type]. */
    fun <T> get(name: String, type: Class<T>, specs: List<MethodSpec>): Outcome {
        val outcome = call {
            runBlocking { engine.evaluate<Any?>(getCheckScript(name, specs.map { it.method.name }), "?") }
        }
        if (outcome !is Outcome.Value) return outcome
        val status = outcome.value as? String ?: "notobject"
        when {
            status == "missing" ->
                throw IllegalArgumentException("A global JavaScript object called $name was not found")
            status == "notobject" -> throw IllegalArgumentException("JavaScript global called $name is not an object")
            status.startsWith("nomethod:") -> throw QuickJsException(
                "JavaScript global $name has no method called ${specs[status.substringAfter(':').toInt()].method.name}",
            )
            status.startsWith("notcallable:") -> throw QuickJsException(
                "JavaScript property $name.${specs[status.substringAfter(':').toInt()].method.name} not callable",
            )
        }
        val byMethod = specs.associateBy { it.method }
        val handler = object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
                // Methods of Object go to the handler itself, as in quickjs-java.
                if (method.declaringClass == Any::class.java) return method.invoke(this, *(args ?: emptyArray()))
                val spec = byMethod[method] ?: throw QuickJsException("Could not find method $name.${method.name}")
                return invokeProxy(name, spec, args ?: emptyArray()).unwrap(spliceAbove = RUNTIME_CLASS_NAME)
            }

            override fun toString() = "QuickJsProxy{name=$name, type=${type.name}}"
        }
        return Outcome.Value(Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler))
    }

    /** Closes the runtime after the calls already queued, waits for it and ends the engine thread. */
    fun close() {
        if (thread.isCurrent && bindingDepth > 0) {
            throw IllegalStateException("Cannot close QuickJs from within a Java method bound with set()")
        }
        if (!closed.compareAndSet(false, true)) return
        try {
            thread.run { engine.close() }
        } catch (_: RejectedExecutionException) {
            // Already shut down.
        } finally {
            thread.runLastAndShutdown {}
        }
    }

    /** Frees a leaked instance (finalizer): never blocks the caller. */
    fun closeLeaked() {
        if (closed.compareAndSet(false, true)) thread.runLastAndShutdown { engine.close() }
    }

    private fun invokeProxy(name: String, spec: MethodSpec, args: Array<out Any?>): Outcome {
        val arguments = buildList {
            val method = spec.method
            val fixed = if (method.isVarArgs) spec.parameters.size - 1 else spec.parameters.size
            for (i in 0 until minOf(fixed, args.size)) add(toJs(args[i], spec.parameters[i]))
            if (method.isVarArgs && args.size == spec.parameters.size) {
                val rest = toJs(args.last(), spec.parameters.last())
                if (rest is List<*>) addAll(rest) else add(rest)
            }
        }
        val outcome = call {
            val invoker = invokerName ?: installInvoker()
            proxyArguments = arguments
            try {
                runBlocking {
                    engine.evaluate<Any?>(
                        "$invoker(${jsString(name)}, ${jsString(spec.method.name)}, ${spec.result.js})",
                        "?",
                    )
                }
            } finally {
                proxyArguments = emptyList()
            }
        }
        if (outcome !is Outcome.Value) return outcome
        val encoded = outcome.value as? String ?: throw QuickJsException("Unexpected result from JavaScript")
        return when {
            encoded.startsWith("v") -> Outcome.Value(fromTransport(TransportReader(encoded.substring(1)).read(), spec.result))
            encoded.startsWith("F1") -> throw IllegalArgumentException(encoded.substring(2))
            else -> throw QuickJsException(encoded.substring(2))
        }
    }

    /** Engine thread. */
    private fun installInvoker(): String {
        val pullName = hiddenName()
        val name = hiddenName()
        engine.defineBinding(pullName, FunctionBinding { proxyArguments })
        runBlocking { engine.evaluate<Any?>(invokerScript(pullName, name), "?") }
        invokerName = name
        return name
    }

    /**
     * The raw `set` binding (engine thread). Never throws into the engine: failures are recorded as the pending
     * Java exception and reported to the script-side wrapper, which throws `InternalError("Java Exception")`.
     */
    private fun dispatch(target: Any, specs: List<MethodSpec>, args: Array<Any?>): List<Any?> {
        try {
            val op = args.getOrNull(0) as? Number
            val operand = args.getOrNull(1) as? Number
            val payload = args.getOrNull(2) as? String ?: ""
            when (op?.toInt()) {
                0 -> {
                    val spec = specs[operand!!.toInt()]
                    val reader = TransportReader(payload)
                    val values = spec.parameters.map { fromTransport(reader.read(), it) }
                    require(reader.isAtEnd) { "Malformed arguments from JavaScript" }
                    val result = try {
                        bindingDepth++
                        spec.method.invoke(target, *values.toTypedArray())
                    } catch (e: InvocationTargetException) {
                        pendingJavaException = e.cause ?: e
                        return FAILED
                    } finally {
                        bindingDepth--
                    }
                    return listOf(0, toJs(result, spec.result))
                }
                1 -> {
                    pendingJavaException = if (operand?.toInt() == 2) {
                        QuickJsException(payload)
                    } else {
                        IllegalArgumentException(payload)
                    }
                    return FAILED
                }
                else -> throw IllegalArgumentException("Malformed call from JavaScript")
            }
        } catch (e: Throwable) {
            pendingJavaException = e
            return FAILED
        }
    }

    /** Runs a JavaScript evaluation on the engine thread and turns failures into an [Outcome]. */
    private fun call(block: () -> Any?): Outcome = onEngine {
        ensureOpen()
        if (bindingDepth > 0) {
            throw UnsupportedOperationException(
                "QuickJs cannot evaluate JavaScript from inside a Java method bound with set() (quickjs-kt)",
            )
        }
        pendingJavaException = null
        try {
            Outcome.Value(block())
        } catch (e: Throwable) {
            failure(e)
        } finally {
            pendingJavaException = null
        }
    }

    /** Engine thread. */
    private fun failure(e: Throwable): Outcome {
        if (e is MarshalException) return Outcome.ScriptFailure(e.message, null)
        val stack = (e as? EngineException)?.stack
        pendingJavaException?.let { return Outcome.JavaFailure(it, stack) }
        return when (e) {
            is EngineException -> Outcome.ScriptFailure(scriptMessage(e.message, stack), stack)
            // quickjs-kt instantiated a Java class named by the error (PLAN.md 8.7 item 5): report its message.
            else -> Outcome.ScriptFailure(e.message, null)
        }
    }

    private fun <T> onEngine(block: () -> T): T = try {
        thread.run(block)
    } catch (e: RejectedExecutionException) {
        throw closedException()
    }

    private fun ensureOpen() {
        if (closed.get()) throw closedException()
    }

    /** Result of a call: a value, or a failure the public API turns into an exception on the caller's thread. */
    sealed interface Outcome {
        class Value(val value: Any?) : Outcome

        /** Becomes a [QuickJsException] with the JavaScript stack spliced in. */
        class ScriptFailure(val message: String?, val stack: String?) : Outcome

        /** A Java binding threw: rethrown as is, with the JavaScript stack spliced in (quickjs-java). */
        class JavaFailure(val throwable: Throwable, val stack: String?) : Outcome

        /**
         * The value, or the failure thrown on the calling thread with the JavaScript stack spliced in above the first
         * frame of [spliceAbove] (`app.cash.quickjs.QuickJs` for its methods; a Java binding's exception was thrown
         * on the engine thread, where the runtime's own frames mark the call).
         */
        fun unwrap(spliceAbove: String = QUICKJS_CLASS_NAME): Any? = when (this) {
            is Value -> value
            is ScriptFailure -> throw QuickJsException(message).apply {
                if (stack != null) addJavaScriptStack(this, stack, spliceAbove, dropAbove = true)
            }
            is JavaFailure -> {
                if (stack != null) addJavaScriptStack(throwable, stack, RUNTIME_CLASS_NAME)
                throw throwable
            }
        }
    }

    /** An array element quickjs-java could not marshal ("Cannot marshal value … to Java"). */
    private class MarshalException(message: String) : RuntimeException(message)

    companion object {
        /**
         * JavaScript stack limit: 4× QuickJS's default (256 KiB, what quickjs-android used) and a quarter of the
         * engine thread's native stack, so the limit is always hit first.
         */
        const val MAX_JS_STACK_BYTES = 1024L * 1024

        private const val CLOSED_MESSAGE = "Null QuickJs context - did you close your QuickJs?"

        private val RUNTIME_CLASS_NAME = QuickJsRuntime::class.java.name
        private const val QUICKJS_CLASS_NAME = "app.cash.quickjs.QuickJs"

        private val FAILED = listOf(1)

        private const val UNSUPPORTED_VALUE = "Unsupported js value type:"

        /** quickjs-kt's message for a thrown plain object (its `toString()`); QuickJS would say this. */
        private const val JS_OBJECT_PREFIX = "com.dokar.quickjs.binding.JsObject@"

        /** Error names QuickJS prepends to the message (`TypeError: …`); quickjs-java reports the message only. */
        private val ERROR_NAME_PREFIX = Regex("^[^\\s:]*: ")

        /** What quickjs-kt returns for a promise it did not await (one nested in the completion value). */
        private val PROMISE_STRINGS = setOf(
            "Promise { <state>: \"fulfilled\" }",
            "Promise { <state>: \"rejected\" }",
            "Promise { <state>: \"pending\" }",
        )

        fun create(): QuickJsRuntime {
            val thread = EngineThread()
            val engine = try {
                thread.run { Engine.create(Dispatchers.Default).apply { maxStackSize = MAX_JS_STACK_BYTES } }
            } catch (e: Throwable) {
                thread.runLastAndShutdown {}
                if (e is EngineException) throw OutOfMemoryError("Cannot create QuickJs instance").apply { initCause(e) }
                throw e
            }
            return QuickJsRuntime(thread, engine)
        }

        private fun closedException() = NullPointerException(CLOSED_MESSAGE)

        /**
         * quickjs-kt's message is `Name: message` plus `\n` + the stack for error objects; quickjs-java used the
         * `message` property alone (or `String(value)` for values without one, which quickjs-kt also does).
         */
        private fun scriptMessage(message: String?, stack: String?): String? {
            if (message != null && message.startsWith(JS_OBJECT_PREFIX)) return "[object Object]"
            if (message == null || stack == null) return message
            var text = message
            if (text.endsWith("\n$stack")) text = text.dropLast(stack.length + 1)
            ERROR_NAME_PREFIX.find(text)?.let { text = text.substring(it.value.length) }
            return text
        }

        /**
         * Evaluates with [evaluation] and converts the completion value like quickjs-java: primitives as `Boolean` /
         * `Integer` / `Double` / `String`, arrays as `Object[]` (elements strictly), anything else `null`.
         */
        private fun evaluateToJava(evaluation: suspend () -> Any?): Any? {
            val value = try {
                runBlocking { evaluation() }
            } catch (e: EngineException) {
                // quickjs-kt cannot convert symbols and BigInts; quickjs-java returned null for them.
                if (e.stack == null && e.message.orEmpty().startsWith(UNSUPPORTED_VALUE)) return null
                throw e
            }
            return toJavaResult(value)
        }

        private fun toJavaResult(value: Any?): Any? = when (value) {
            null, Unit -> null
            is String -> if (value in PROMISE_STRINGS) null else value
            is List<*> -> Array(value.size) { arrayElement(value[it]) }
            is Boolean, is Int, is Double -> value
            is Long -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else value.toDouble()
            is Number -> value.toDouble()
            else -> null
        }

        private fun arrayElement(value: Any?): Any? = when (value) {
            null, Unit -> null
            is String -> if (value in PROMISE_STRINGS) cannotMarshal("[object Promise]") else value
            is List<*> -> Array(value.size) { arrayElement(value[it]) }
            is Boolean, is Int, is Double -> value
            is Long -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else value.toDouble()
            is Number -> value.toDouble()
            is JsObject -> cannotMarshal("[object Object]")
            is Map<*, *> -> cannotMarshal("[object Map]")
            is Set<*> -> cannotMarshal("[object Set]")
            is Throwable -> cannotMarshal("Error: ${value.message}")
            else -> cannotMarshal("[object Object]")
        }

        private fun cannotMarshal(value: String): Nothing = throw MarshalException("Cannot marshal value $value to Java")
    }
}
