# Draft: security report for dokar3/quickjs-kt

Status: draft for the maintainer to send privately (GitHub "Report a vulnerability" on
`dokar3/quickjs-kt`, or by e-mail to its author). Not sent. Komikku's decision on the risk is in
[`PLAN.md`](PLAN.md) 8.7 item 5.

---

**Title:** JNI error conversion instantiates arbitrary Java classes named by a JavaScript `Error`

**Affected:** `io.github.dokar3:quickjs-kt` JVM and Android targets, verified on 1.0.15; the code is
unchanged in 1.0.18 (`quickjs/native/jni/mapping/js_value_to_jobject.c`). Kotlin/Native targets are not
affected (no `FindClass`).

**Summary.** `js_error_to_java_error()` reads the `name` property of a JS `Error`, replaces `.` with
`/`, calls `FindClass(name)`, then `GetMethodID(cls, "<init>", "(Ljava/lang/String;)V")` and
`NewObject(cls, ctor, message)`. Nothing checks that the class is a `java.lang.Throwable`. Any script the
host evaluates can therefore make the JVM run the `(String)` constructor of any class reachable from the
library's class loader, including bootstrap classes, with an argument it chooses.

**Reachable from:**
1. an evaluation that throws or rejects with an `Error` (`js_value_to_jobject`, `JS_TAG_EXCEPTION`);
2. any unhandled promise rejection (`promise_rejection_handler.c` converts every reason with
   `js_value_to_jobject`) — the script does not need to return or throw anything to the host;
3. an `Error` passed as an argument to a binding defined with `function`/`asyncFunction`/`define`
   (`binding_bridge.c`), which then receives the constructed object as its argument.

**Proof of concept** (JVM, quickjs-kt-jvm 1.0.15; target path inside a temp directory):

```js
const e = new Error("/tmp/poc/pwned.txt");
e.name = "java.io.FileOutputStream";
Promise.reject(e);   // or: throw e;  or: someBinding(e);
1;
```

All three paths created `/tmp/poc/pwned.txt` (`FileOutputStream(String)` creates or truncates the file).
For path 3 the binding received a `java.io.FileOutputStream` instance and no exception was raised.

**Impact.** Hosts that evaluate untrusted JavaScript (for example scripts downloaded from websites) let
that script create or truncate any file the process can write and instantiate other classes with a
`(String)` constructor, including private ones (JNI ignores access checks). It also lets scripts make the
host see forged exception types such as `java.util.concurrent.CancellationException`. Running arbitrary
code was not demonstrated.

**Suggested fix** (keeps the "rethrow the original Java exception type" feature for real throwables):
after `FindClass` succeeds, resolve `java/lang/Throwable` once and only use the class when
`(*env)->IsAssignableFrom(env, java_error_cls, throwable_cls)` is true; otherwise fall through to the
existing `new_js_error_exception` fallback. A stricter variant only accepts class names that the library
itself attached when a Kotlin exception crossed into JavaScript.

**Workarounds for hosts until a release:** none complete from JavaScript (path 2 is unconditional).
Hosts can keep their own error and argument paths string-only, but unhandled rejections stay exposed.

**Related crash (same function).** When the named class exists but is not a `Throwable`, the constructed
object is passed to `(*env)->Throw` on the paths that throw directly — a getter that throws while
`object_to_java_js_object` stringifies a value, or `JS_TAG_EXCEPTION` in `js_value_to_jobject` — and the
JVM crashes (SIGSEGV in `ThreadGroup.uncaughtException` on the engine thread):

```js
throw { get a() { throw { name: "java.lang.StringBuilder", message: "x" }; } };
// also: Promise.reject(<same object>); or that object as the evaluation's result
```

The suggested `IsAssignableFrom` check fixes this too.

**Second issue: process-wide JNI state without a lock** (`jni_globals.c`, `jni_globals_generated.c`).
`instance_count` and the cached `JavaVM` are changed without synchronisation in `cache_java_vm` /
`clear_java_vm_cache`; `initGlobals` replaces the global references of `Unit` / `UByteArray` on every
create (`DeleteGlobalRef` of the previous one); `releaseGlobals` deletes all cached class references
(`clear_jni_refs_cache`) when the unsynchronised count reads zero. Engines created and closed at the same
time on different threads (8 threads creating, evaluating `[1, 2.5, 'x']` and closing in a loop) abort
under `-Xcheck:jni` within seconds ("Bad global or local ref passed to JNI" in `initGlobals`) and crash
in product mode after ~26k instances (`jni_NewObject` from `getEvaluateResult` on a deleted class
reference). Suggested fix: a mutex around the counter, the cache and the reference replacement (or create
the references once and never delete them). Komikku works around it by serialising create / close on one
lock and keeping one engine open for the process lifetime.

Found while integrating quickjs-kt into Komikku (a manga reader); happy to test a patched build.
