package app.komikku.quickjs

import java.util.UUID

/*
 * KMK: the completion value of `QuickJs.evaluate` / `execute`, converted like quickjs-java without letting
 * quickjs-kt's native conversion see anything but primitives and plain arrays (docs/kmp-desktop/PROGRESS.md,
 * "Review fixes (I2b)").
 *
 * quickjs-kt evaluates a script as an async global script: QuickJS resolves its promise with an object
 * `{ value: <completion value> }`, and quickjs-kt converts `value` natively (objects through `JSON.stringify`,
 * running getters and `toJSON`, throwing on cycles and BigInts, and crashing the JVM when a getter throws an error
 * named after a non-`Throwable` class). The script itself stays a global script (top-level `let` / `const`
 * persist, file names and line numbers are the script's own); one statement is appended to it ([markCompletion]):
 * `let {} = helper();`, a lexical declaration that binds nothing and so keeps the completion value. The helper
 * ([completionHelperScript]) briefly defines an `Object.prototype.then` getter: QuickJS looks up `then` on the
 * `{ value }` object when the script's promise is resolved right after that statement, and the getter (removed
 * again on its first call, or by a microtask at the latest) replaces `value` with
 * `[token, value]` for primitives, `[token, copy]` for arrays (a fresh array of the converted elements, so number
 * tags survive), `[token, undefined, message]` when an element cannot be marshalled, and `[token]` for anything
 * else (which quickjs-java turned into `null`). It returns `undefined`, so the `{ value }` object is never a
 * thenable, whatever `Object.prototype.then` the script defined.
 *
 * When the helper could not run (the script ended inside an `if (…)` that skipped it, `Object.prototype` is frozen,
 * `Object.prototype.then` is non-configurable), quickjs-kt converts the raw value as before; see
 * [QuickJsRuntime]'s fallback.
 */

/** Global name of the helper; one per process, so bytecode from [QuickJsRuntime.compile] runs in every instance. */
private val COMPLETION_HELPER = hiddenName()

/** First element of a converted completion value; a script cannot produce it by accident. */
internal val COMPLETION_TOKEN: String = "__kmk_qjs_completion_" + UUID.randomUUID().toString().replace('-', '_')

/**
 * [script] followed by the statement that converts its completion value. A lexical declaration is not allowed
 * where a statement is expected, so an incomplete script (`if (x)`, `while (x)`, `label:`) stays a syntax error
 * instead of getting the appended statement as its body.
 */
internal fun markCompletion(script: String): String =
    "$script\nlet {} = typeof $COMPLETION_HELPER === 'function' ? $COMPLETION_HELPER() : 0;\n"

/** Defines the helper as a non-writable, non-configurable, non-enumerable global; evaluated before any script. */
internal fun completionHelperScript(): String = """
(function (name, token) {
'use strict';
var G = globalThis, OP = Object.prototype, defineProperty = Object.defineProperty;
var getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor, hasOwn = Object.hasOwn;
var isArray = Array.isArray, toStr = String;
var armed = false, saved, failing;
function restore() {
  if (!armed) return;
  armed = false;
  var d = saved;
  saved = undefined;
  try {
    if (d === undefined) delete OP.then; else defineProperty(OP, 'then', d);
  } catch (e) {}
}
function str(v) { try { return '' + toStr(v); } catch (e) { return '[object]'; } }
function messageOf(e) {
  try {
    if (e !== null && (typeof e === 'object' || typeof e === 'function')) {
      var m = e.message;
      if (m !== undefined) return '' + toStr(m);
    }
  } catch (x) {}
  return str(e);
}
function cell(v) { return { __proto__: null, value: v, writable: true, enumerable: true, configurable: true }; }
function isPrimitive(v) {
  var t = typeof v;
  return v === null || v === undefined || t === 'string' || t === 'number' || t === 'boolean';
}
function copy(a) {
  var n = a.length, out = [];
  for (var i = 0; i < n; i++) {
    var v = a[i];
    if (!isPrimitive(v)) {
      if (typeof v === 'object' && isArray(v)) {
        v = copy(v);
      } else {
        failing = { message: 'Cannot marshal value ' + str(v) + ' to Java' };
        throw failing;
      }
    }
    defineProperty(out, i, cell(v));
  }
  return out;
}
function getter() {
  var wrapper = this;
  if (!armed) return undefined;
  restore();
  try {
    var v = wrapper.value, out;
    if (isPrimitive(v)) {
      out = [token, v];
    } else if (typeof v === 'object' && isArray(v)) {
      try {
        out = [token, copy(v)];
      } catch (e) {
        out = [token, undefined, e === failing ? failing.message : messageOf(e)];
      }
    } else {
      out = [token];
    }
    defineProperty(wrapper, 'value', cell(out));
  } catch (e) {}
  return undefined;
}
async function later() {
  await undefined;
  restore();
}
function arm() {
  restore();
  var d;
  try {
    d = getOwnPropertyDescriptor(OP, 'then');
    if (d !== undefined) {
      if (!d.configurable) return 0;
      d = hasOwn(d, 'get')
        ? { __proto__: null, get: d.get, set: d.set, enumerable: d.enumerable, configurable: true }
        : { __proto__: null, value: d.value, writable: d.writable, enumerable: d.enumerable, configurable: true };
    }
    defineProperty(OP, 'then', { __proto__: null, get: getter, enumerable: false, configurable: true });
  } catch (e) {
    return 0;
  }
  saved = d;
  armed = true;
  later();
  return 0;
}
defineProperty(G, name, { __proto__: null, value: arm, writable: false, enumerable: false, configurable: false });
})(${jsString(COMPLETION_HELPER)}, ${jsString(COMPLETION_TOKEN)});
void 0;
"""
