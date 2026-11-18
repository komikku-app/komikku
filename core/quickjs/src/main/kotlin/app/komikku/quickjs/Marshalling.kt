package app.komikku.quickjs

import java.lang.reflect.Method
import java.util.UUID

/*
 * KMK: value conversion between Java and JavaScript for `QuickJs.set` / `QuickJs.get`, with the rules of
 * quickjs-java (`Context::getJsToJavaConverter` / `getJavaToJsConverter`, docs/kmp-desktop/PROGRESS.md "QuickJS
 * parity").
 *
 * JavaScript → Java values never cross as JavaScript objects: the script-side code ([JS_HELPERS]) converts them
 * by the Java type and encodes the result into one string (`n`, `t`, `f`, `i<int>;`, `d<double>;`,
 * `s<length>:<chars>`, `a<count>:<items>`), which is all that reaches quickjs-kt. quickjs-kt would otherwise turn a
 * JavaScript `Error` argument into `new <error.name>(message)` for any Java class (PLAN.md 8.7 item 5).
 */

/** How a Java parameter / return type is converted; [js] is its literal in [JS_HELPERS] (`'S'`, `['A','i']`, …). */
internal class Kind private constructor(val code: Char, val type: Class<*>, val element: Kind?) {

    val js: String = if (element == null) "'$code'" else "['A',${element.js}]"

    companion object {
        /**
         * The kind of [type]; [boxed] makes primitives coerce like their boxes (quickjs-java's `boxed` flag).
         *
         * @throws IllegalArgumentException for types quickjs-java does not support.
         */
        fun of(type: Class<*>, boxed: Boolean): Kind {
            if (type.isArray) {
                val component = type.componentType
                val strict = component == java.lang.Double.TYPE || component == Integer.TYPE ||
                    component == java.lang.Boolean.TYPE
                return Kind('A', type, of(component, boxed = !strict))
            }
            val code = when (type) {
                String::class.java -> 'S'
                java.lang.Double::class.java -> 'D'
                Integer::class.java -> 'I'
                java.lang.Boolean::class.java -> 'B'
                java.lang.Double.TYPE -> if (boxed) 'D' else 'd'
                Integer.TYPE -> if (boxed) 'I' else 'i'
                java.lang.Boolean.TYPE -> if (boxed) 'B' else 'b'
                Any::class.java -> 'O'
                Void.TYPE -> 'V'
                else -> throw IllegalArgumentException("Unsupported Java type ${type.name}")
            }
            return Kind(code, type, null)
        }
    }
}

/** A method of a bound interface with the kinds of its parameters and return value. */
internal class MethodSpec(val method: Method, boxed: Boolean) {
    val parameters: List<Kind> = method.parameterTypes.map { Kind.of(it, boxed) }
    val result: Kind = Kind.of(method.returnType, boxed)

    init {
        // quickjs-java called through JNI, which ignores Java access checks (e.g. a package-private interface).
        try {
            method.isAccessible = true
        } catch (_: RuntimeException) {
            // Keep the default; the call reports the access error.
        }
    }
}

/** Converts a decoded transport value ([TransportReader]) to the Java value of [kind]. */
internal fun fromTransport(value: Any?, kind: Kind): Any? = when (kind.code) {
    'S' -> value as String?
    'D', 'd' -> (value as Number?)?.toDouble()
    'I', 'i' -> (value as Number?)?.toInt()
    'B', 'b' -> value as Boolean?
    'O' -> objectFromTransport(value)
    'A' -> (value as List<*>?)?.let { items -> arrayFromTransport(items, kind) }
    else -> null
}

private fun arrayFromTransport(items: List<*>, kind: Kind): Any {
    val element = kind.element!!
    return when (kind.type.componentType) {
        java.lang.Double.TYPE -> DoubleArray(items.size) { (items[it] as Number).toDouble() }
        Integer.TYPE -> IntArray(items.size) { (items[it] as Number).toInt() }
        java.lang.Boolean.TYPE -> BooleanArray(items.size) { items[it] as Boolean }
        else -> java.lang.reflect.Array.newInstance(kind.type.componentType, items.size).also { array ->
            items.forEachIndexed { index, item -> java.lang.reflect.Array.set(array, index, fromTransport(item, element)) }
        }
    }
}

private fun objectFromTransport(value: Any?): Any? = when (value) {
    is List<*> -> Array(value.size) { objectFromTransport(value[it]) }
    else -> value
}

/**
 * Converts the Java [value] of [kind] to a value quickjs-kt hands to JavaScript as is (Kotlin → JavaScript is
 * safe): `null`, `Boolean`, `Int`, `Double`, `String` or a `List` of those.
 *
 * @throws IllegalArgumentException for an `Object` whose runtime type is not supported.
 */
internal fun toJs(value: Any?, kind: Kind): Any? {
    if (value == null) return null
    return when (kind.code) {
        'S' -> value as String
        'D', 'd' -> (value as Number).toDouble()
        'I', 'i' -> (value as Number).toInt()
        'B', 'b' -> value as Boolean
        'O' -> {
            val type = value.javaClass
            if (type == Any::class.java) throw IllegalArgumentException("Unsupported Java type ${type.name}")
            toJs(value, Kind.of(type, boxed = true))
        }
        'A' -> when (value) {
            is DoubleArray -> value.toList()
            is IntArray -> value.toList()
            is BooleanArray -> value.toList()
            is Array<*> -> value.map { toJs(it, kind.element!!) }
            else -> throw IllegalArgumentException("Unsupported Java type ${value.javaClass.name}")
        }
        else -> null
    }
}

/** Reads the string encoding produced by [JS_HELPERS]. */
internal class TransportReader(private val text: String) {
    private var position = 0

    val isAtEnd: Boolean get() = position >= text.length

    fun read(): Any? = when (text[position++]) {
        'n' -> null
        't' -> true
        'f' -> false
        'i' -> until(';').toInt()
        'd' -> until(';').toDouble()
        's' -> {
            val length = until(':').toInt()
            text.substring(position, position + length).also { position += length }
        }
        'a' -> {
            val count = until(':').toInt()
            List(count) { read() }
        }
        else -> throw IllegalArgumentException("Malformed value from JavaScript")
    }

    private fun until(delimiter: Char): String {
        val end = text.indexOf(delimiter, position)
        require(end >= 0) { "Malformed value from JavaScript" }
        return text.substring(position, end).also { position = end + 1 }
    }
}

/** [value] as a JavaScript string literal (safe to embed in generated code). */
internal fun jsString(value: String): String = buildString(value.length + 2) {
    append('"')
    for (c in value) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' || c == ' ' || c == ' ' || c.isSurrogate() || c.code > 0x7e ->
                append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
    }
    append('"')
}

/** An unguessable global name for a raw binding that only exists until the generated code captured it. */
internal fun hiddenName(): String = "__kmk_qjs_" + UUID.randomUUID().toString().replace('-', '_')

/**
 * Script-side conversion by Java type, in a function scope; uses operators and `typeof` only (no built-in methods
 * a script could have replaced), except `Array.isArray` and `String`, captured when the code runs.
 *
 * `conv(kind, value)` returns the encoded value or throws a `ConvFail` (`type` 1: `IllegalArgumentException`,
 * 2: `QuickJsException`) with quickjs-java's message.
 */
internal const val JS_HELPERS = """
'use strict';
var isArray = Array.isArray, toStr = String;
function ConvFail(type, message) { this.type = type; this.message = message; }
function str(v) { try { return '' + toStr(v); } catch (e) { return '[object]'; } }
function isInt(v) { return typeof v === 'number' && (v | 0) === v && (v !== 0 || 1 / v > 0); }
function num(v) { return isInt(v) ? 'i' + v + ';' : 'd' + (v === 0 ? '-0' : '' + v) + ';'; }
function prim(v) {
  if (v === null || v === undefined) return 'n';
  if (v === true) return 't';
  if (v === false) return 'f';
  if (typeof v === 'number') return num(v);
  return 's' + v.length + ':' + v;
}
function fail(what, v) { throw new ConvFail(1, 'Cannot convert value ' + str(v) + ' to ' + what); }
function arr(k, v, start, end) {
  var out = 'a' + (end > start ? end - start : 0) + ':';
  for (var i = start; i < end; i++) out += conv(k, v[i]);
  return out;
}
function obj(v) {
  if (v === null || v === undefined) return 'n';
  var t = typeof v;
  if (t === 'string' || t === 'boolean' || t === 'number') return prim(v);
  if (isArray(v)) return arr('O', v, 0, v.length | 0);
  throw new ConvFail(2, 'Cannot marshal value ' + str(v) + ' to Java');
}
function conv(k, v) {
  if (typeof k !== 'string') return v === null || v === undefined ? 'n' : arr(k[1], v, 0, v.length | 0);
  switch (k) {
    case 'S': if (v === null || v === undefined) return 'n'; if (typeof v === 'string') return prim(v); return fail('String', v);
    case 'D': return v === null || v === undefined ? 'n' : num(+v);
    case 'd': if (typeof v === 'number') return num(v); return fail('double', v);
    case 'I': return v === null || v === undefined ? 'n' : num(v | 0);
    case 'i': if (isInt(v)) return num(v); return fail('int', v);
    case 'B': return v === null || v === undefined ? 'n' : (v ? 't' : 'f');
    case 'b': if (typeof v === 'boolean') return prim(v); return fail('boolean', v);
    case 'O': return obj(v);
    default: return 'n';
  }
}
"""

/**
 * Installs the global object [name] whose methods ([specs]) call the raw binding `globalThis[rawName]`, which it
 * captures and deletes first. Its completion value is `false` when [name] already exists (nothing installed).
 *
 * The raw binding is `raw(0, methodIndex, encodedArguments)` → `[0, result]` or `[1]` (Java exception pending), and
 * `raw(1, failureType, message)` → `[1]` (records the conversion failure).
 */
internal fun setScript(rawName: String, name: String, specs: List<MethodSpec>): String {
    val specLiteral = specs.joinToString(",", "[", "]") { spec ->
        val parameters = spec.parameters.joinToString(",", "[", "]") { it.js }
        "[${jsString(spec.method.name)},$parameters,${spec.method.isVarArgs},${spec.result.js}]"
    }
    return """
(function (rawName, name, specs) {
$JS_HELPERS
var raw = globalThis[rawName];
delete globalThis[rawName];
if (name in globalThis) return false;
var defineProperty = Object.defineProperty, RangeErr = RangeError;
var InternalErr = typeof InternalError === 'function' ? InternalError : Error;
function call(op, a, s) { return raw(op, a, typeof s === 'string' ? s : ''); }
function method(index, spec) {
  var kinds = spec[1], varargs = spec[2], ret = spec[3], n = kinds.length, min = varargs ? n - 1 : n;
  var f = function () {
    var argc = arguments.length;
    if (min > argc || (!varargs && min < argc)) throw new RangeErr('Wrong number of arguments');
    var payload = '';
    try {
      for (var i = 0; i < min; i++) payload += conv(kinds[i], arguments[i]);
      if (varargs) payload += arr(kinds[n - 1][1], arguments, min, argc);
    } catch (e) {
      if (e instanceof ConvFail) { call(1, e.type, '' + e.message); throw new InternalErr('Java Exception'); }
      throw e;
    }
    var r = call(0, index, payload);
    if (r[0] !== 0) throw new InternalErr('Java Exception');
    return ret === 'V' ? undefined : r[1];
  };
  defineProperty(f, 'name', { value: spec[0], configurable: true });
  defineProperty(f, 'length', { value: n, configurable: true });
  return f;
}
var proxy = {};
for (var i = 0; i < specs.length; i++) {
  defineProperty(proxy, specs[i][0], { value: method(i, specs[i]), writable: true, configurable: true });
}
globalThis[name] = proxy;
return true;
})(${jsString(rawName)}, ${jsString(name)}, $specLiteral)
"""
}

/**
 * Installs the hidden, non-configurable global function [invokerName]`(name, method, resultKind)` used by
 * `QuickJs.get` proxies: it takes the arguments from the raw binding `globalThis[pullName]` (captured and deleted
 * first), calls `globalThis[name][method]` and returns `'v' + encoded result`, or `'F<type><message>'` when the
 * result cannot be converted.
 */
internal fun invokerScript(pullName: String, invokerName: String): String = """
(function (pullName, invokerName) {
$JS_HELPERS
var pull = globalThis[pullName];
delete globalThis[pullName];
var apply = Reflect.apply;
Object.defineProperty(globalThis, invokerName, {
  value: function (name, method, kind) {
    var args = pull();
    var o = globalThis[name];
    var r = apply(o[method], o, args);
    try {
      return 'v' + conv(kind, r);
    } catch (e) {
      if (e instanceof ConvFail) return 'F' + e.type + e.message;
      throw e;
    }
  },
});
})(${jsString(pullName)}, ${jsString(invokerName)});
void 0;
"""

/**
 * Checks `QuickJs.get`'s preconditions: completion value `ok`, `missing`, `notobject`, or `nomethod:<i>` /
 * `notcallable:<i>` for the first of [methods] that is absent / not a function.
 */
internal fun getCheckScript(name: String, methods: List<String>): String = """
(function (name, methods) {
  var o = globalThis[name];
  if (o === undefined) return 'missing';
  if (o === null || (typeof o !== 'object' && typeof o !== 'function')) return 'notobject';
  for (var i = 0; i < methods.length; i++) {
    var p = o[methods[i]];
    if (typeof p !== 'function') return (p === undefined ? 'nomethod:' : 'notcallable:') + i;
  }
  return 'ok';
})(${jsString(name)}, ${methods.joinToString(",", "[", "]") { jsString(it) }})
"""
