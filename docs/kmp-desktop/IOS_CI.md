# iOS check (CI)

Status: **green since run #4 (2026-11-25, commit `d21b68cfc4`)**: 100 `:source-js` tests pass on the iOS
simulator, including deep recursion on the 4 MiB JS stack; 122 with I5a (run 45545772720, commit `eca24347e1`,
section below). Runs #1–#2 looked green only because the job was
non-blocking; they had one failing test (the forged-cancellation message keeps the error name on iOS), fixed in
`d21b68cfc4`. The job now blocks (no `continue-on-error`). PLAN.md section 8.4, step I5, should reference this page once it is
updated. PROGRESS.md's iOS bullets (iOS "not compiled", JS stack "clamped to 256 KiB") are outdated as of the
commits that added this page.

## What the job checks

Workflow `.github/workflows/build_ios_check.yml`, one job on `macos-15` (GitHub-hosted, Apple silicon):

| Task | What it proves |
|------|----------------|
| `:source-js:compileKotlinIosArm64` | `commonMain` + `iosMain` compile for iPhone devices |
| `:source-js:compileKotlinIosSimulatorArm64` | the same for the arm64 simulator |
| `:source-js:compileTestKotlinIosArm64` | `commonTest` + `iosTest` compile for devices |
| `:source-js:iosSimulatorArm64Test` | links the test binary (`linkDebugTestIosSimulatorArm64`, needs Xcode) and runs `commonTest` + `iosTest` on an iOS simulator |

The task names are the Kotlin Multiplatform plugin's conventions; they were checked with
`./gradlew -Pios-targets :source-js:tasks --all` (Kotlin 2.4.20).

It runs on `workflow_dispatch`, on pull requests, and on pushes to `master` and `claude/kmp-multiplatform-support`
(never on tags), when they touch `source-js/**`, the build setup (`gradle/**`, `buildSrc/**`, `settings.gradle.kts`,
`build.gradle.kts`, `gradle.properties`, `.github/.java-version`) or the workflow file. No signing, no secrets, no app (`:iosApp` is I6, an IPA is I7). Test reports are uploaded as
`ios-test-report-<sha>` when the job fails.

## Running it

```bash
gh workflow run build_ios_check.yml --ref claude/kmp-multiplatform-support
gh run list --workflow build_ios_check.yml --limit 3
gh run watch <run-id>
```

On a Mac, the same tasks run locally (the iOS targets are declared automatically on macOS hosts):

```bash
./gradlew :source-js:compileKotlinIosArm64 :source-js:iosSimulatorArm64Test --max-workers=2
```

### Compiling on Linux

`-Pios-targets` declares the iOS targets on any host (`source-js/build.gradle.kts`). Kotlin/Native's Linux
distribution ships the iOS platform libraries (`platform.Foundation`, `platform.posix`), so the main and test klibs
compile on Linux; only linking and the simulator run need macOS:

```bash
./gradlew -Pios-targets :source-js:compileKotlinIosArm64 :source-js:compileKotlinIosSimulatorArm64 \
  :source-js:compileTestKotlinIosArm64 :source-js:compileTestKotlinIosSimulatorArm64 --max-workers=2
```

This passed on Linux for the code added with this page (a deliberate typo in `iosMain` failed it, so the code is
really type-checked). The first run downloads the Kotlin/Native toolchain into `~/.konan`. Without the property,
Linux / Windows builds are unchanged.

## If it fails

1. **Configuration or toolchain**: the "Xcode and simulators" step prints the Xcode version and the available
   simulators. The Kotlin Gradle plugin picks its default test device from Xcode; if it cannot find one, set the
   test task's `device` (`tasks.withType<KotlinNativeSimulatorTest>`) to a listed simulator. A Kotlin / Xcode
   version mismatch shows as a warning from the Kotlin plugin before the link step.
2. **Compile errors** in `iosMain` / `iosTest`: reproduce on Linux with `-Pios-targets` (above).
3. **Link errors** (`linkDebugTestIosSimulatorArm64`): usually a dependency without an iOS simulator artifact
   (quickjs-kt, Ksoup, kotlinx). Only reproducible on macOS.
4. **Test failures or crashes**: download `ios-test-report-<sha>` (HTML report and JUnit XML). A crash with no
   report (the process dies) in a recursion test points at the engine thread's stack (below):
   `QuickJsEngineTest.deepRecursionIsAJsErrorNotACrash` runs with the full 4 MiB JS stack limit.
   Tests that rely on JVM-only behaviour belong in `jvmSharedTest` / `desktopTest`, not `commonTest`.

The job gates pushes and pull requests that touch `source-js/` or the build setup.

## iOS engine thread (decision)

`newJsEngineThread` on iOS (`source-js/src/iosMain/.../engine/JsEngineThread.ios.kt`) used
`newSingleThreadContext`, which cannot set a thread stack size, so the JS stack limit was clamped to 256 KiB
(Darwin's secondary threads get 512 KiB). It now runs a small `CloseableCoroutineDispatcher` on an `NSThread`
whose `stackSize` is the JS limit plus 4 MiB headroom (as on the JVM), rounded up to 16 KiB; blocks wait in a
queue guarded by an `NSCondition` and each runs in its own autorelease pool (as Kotlin/Native workers do). The JS
stack limit is therefore the same 4 MiB as on Android and desktop. It is based on the stack size read back from the
thread: a smaller applied stack lowers it to the stack minus the headroom, and an ignored setter (Darwin's 512 KiB
default) falls back to the old 256 KiB clamp, so deep recursion cannot overrun the real stack. The public
expect / actual signatures are unchanged.

- `NSThread` instead of a raw `pthread` (`pthread_attr_setstacksize`): no C function pointers or `StableRef`s, and
  the same stack-size control.
- Differences from the JVM thread: it does not end after 30 s idle (only when the engine closes it, like the old
  `newSingleThreadContext`). A block dispatched after `close()` cancels its job and runs on `Dispatchers.Default`,
  as the JVM executor dispatcher does with a rejected block.
- `iosTest/.../JsEngineThreadIosTest` checks the thread's name and stack size, and the limit derived from the
  applied stack, on the simulator.
- Risk: compiled (Linux klib), never executed. If the simulator tests crash in deep recursion, the stack size was
  not applied although the read-back said so; the fallback is the old `newSingleThreadContext` with its 256 KiB clamp.

## I5a: a JavaScript source runs end to end on iOS

What PLAN.md 8.4 step I5 lists for `:source-js` (HTTP on Ktor Darwin, preferences; DOM and crypto were already
common code), so an iOS app (I6) gets a working `JsExtension` from one call. All of it is in `source-js/src/iosMain`
and `iosTest`; nothing JVM changed.

| Piece | File | What it is |
|-------|------|------------|
| `DarwinHostHttp` | `iosMain/.../bridge/DarwinHostHttp.kt` | `HostHttp` on Ktor's Darwin engine (NSURLSession) |
| `NSUserDefaultsHostPreferences` | `iosMain/.../bridge/NSUserDefaultsHostPreferences.kt` | the script's `SharedPreferences`, typed, one suite per source |
| `NSUserDefaultsKeyValueStore` | same file | a `JsKeyValueStore` on `NSUserDefaults` (for `KeyValueHostPreferences`; not used by the runtime) |
| `IosJsSourceRuntime` | `iosMain/.../IosJsSourceRuntime.kt` | `JsExtensionHost` with the iOS services: `IosJsSourceRuntime().extension(descriptor, code)` |

Library: **Ktor 3.6.1** (`ktor-client-core`, `ktor-client-darwin`; tests `ktor-client-mock`), the
latest release on Maven Central (2026-11-25); it is built with Kotlin 2.3.21 and kotlinx-coroutines 1.11.0, so it
works with the project's Kotlin 2.4.20 / coroutines 1.11.0. Catalog entries in `gradle/libs.versions.toml` (KMK
block); the dependencies are declared only for the iOS source sets of `source-js/build.gradle.kts`, so Android and
desktop do not see Ktor.

### Decisions

- **HTTP semantics match `OkHttpHostHttp`.** All methods; request headers in order, minus the source's
  `Accept-Encoding` (NSURLSession negotiates `gzip, deflate, br` and decompresses; Ktor's Darwin engine then drops
  `Content-Encoding` / `Content-Length`, like OkHttp's transparent gzip) and minus `Transfer-Encoding` / `Upgrade`
  (Ktor refuses them). OkHttp's body rules: an empty body for `POST` / `PUT` / `PATCH` / `PROPPATCH` / `REPORT`
  without one, `GET` / `HEAD` with a body fail. The response: status, headers (NSURLResponse already joins repeated
  names with `", "`, the bridge joins Ktor's separate values the same way), the body capped at `maxBodyBytes`
  (declared `Content-Length` first, then at most cap + 1 bytes read) with `HttpBridge.tooLargeMessage`. Error
  statuses are returned. TLS is NSURLSession's default validation (no challenge handler).
- **Redirects are followed by `DarwinHostHttp`, not by Ktor.** Ktor's Darwin engine already disables NSURLSession's
  own redirects; Ktor's `HttpRedirect` follows only `GET` / `HEAD`, keeps the method and body on 301 / 302 / 303 and
  refuses HTTPS → HTTP. So the client runs with `followRedirects = false` and `DarwinHostHttp` applies OkHttp's
  `RetryAndFollowUpInterceptor` rules: 300 / 301 / 302 / 303 / 307 / 308 with a `Location`, resolved by `NSURL`
  against the request URL; up to 20 follow-ups (`Too many follow-up requests: 21`); 307 / 308 (and `PROPFIND`) keep
  method and body, the others turn into a body-less `GET` without `Content-Type` / `Content-Length` /
  `Transfer-Encoding`; `Authorization` dropped when scheme, host or port change; HTTPS → HTTP followed (OkHttp's
  `followSslRedirects` default). Cookies are the session's (`NSHTTPCookieStorage.sharedHTTPCookieStorage`), per hop.
- **Timeout.** `timeoutMillis` bounds the whole call (hops and body) like OkHttp's call timeout, with a coroutine
  timeout instead of Ktor's `HttpTimeout`, so it fails with an `IOException("timeout")` like OkHttp's
  `InterruptedIOException`. Without it, the default engine's NSURLSession idle timeout is 30 s
  (`timeoutIntervalForRequest`, the app's OkHttp read timeout).
- **Cancellation**: cancelling the coroutine cancels Ktor's call, whose Darwin engine cancels the `NSURLSessionTask`.
  The caller's cancellation stays a `CancellationException`.
- `useDefaultTransformers = false`: no `Accept: */*` / `Accept-Charset` the source did not send, the body is read raw.
- **Preferences: one `NSUserDefaults` suite per source**, named like the source's preference file on Android and
  desktop, `source_<komikkuId>` (`JsSourceIds.preferencesName`), rather than a key prefix in the standard defaults:
  a source's values can be deleted at once (`IosJsSourceRuntime.clearPreferences`, `removePersistentDomainForName`)
  and a later backup / settings mapping uses the same name on every platform. Values are typed like the JVM's
  `SharedPreferencesHostPreferences`: check box / switch → `Bool` (`setBool`), list / edit text → string,
  multi-select → array of strings; a value whose type does not fit the declared preference reads as missing (the
  script gets the default); multi-select values come back in declared order; the free `getString` / `setString`
  store uses the JVM's key prefix `mangayomi_str:`. The declarations come from `IosJsSourceRuntime`'s
  `declaredPreferences` (the app's cached metadata; empty by default, then every stored type reads as stored).
- **Log**: `NSLog` (`[JS <source>] LEVEL: message`).

### Tests (`iosTest`, run on the simulator by the CI job)

- `DarwinHostHttpTest` (12): the `OkHttpHostHttpTest` cases on Ktor's `MockEngine`: method / headers / body, every
  method, `Accept-Encoding` dropped and no added `Accept`, body rules, raw bytes, redirects followed or not (relative
  location), OkHttp's method / header changes on 302 / 307 / cross-host, the 20 follow-up limit, the timeout, the
  body cap (declared, streamed, exact, unbounded), cancellation reaching the engine, a source through `Client`.
- `DarwinHostHttpServerTest` (5): the real Darwin engine against `LoopbackHttpServer`, a small HTTP/1.1 server on
  POSIX sockets and `NSThread`s inside the simulator test process, on `127.0.0.1` (App Transport Security does not
  apply to IP addresses): the session's own `Accept-Encoding`, repeated response headers, gzip decompressed with the
  encoding headers gone, no native redirects, timeout and body cap, cancellation of a real task. A Ktor CIO server
  was tried first and never started in the simulator process (every start timed out, run 45544362098), so the test
  server has no dependency.
- The iOS tests run in `timedTest` (a `runBlocking` with a 30 s limit), and the native test tasks log each test's
  start and result (`source-js/build.gradle.kts`), so a hang names its test in the CI log instead of stalling the job
  (the first run, 45540635477, hung silently and was cancelled).
- `NSUserDefaultsHostPreferencesTest` (4): native types in the suite, typing by declarations and declared order,
  the free string store, the key-value store.
- `IosJsSourceRuntimeTest` (1): end to end: a fixture source's `getPopular` fetches HTML over `DarwinHostHttp`
  (mock engine) with a header from its preferences, parses it with `Document` (Ksoup) and returns a `JsMangaPage`;
  the declared defaults land in the source's suite with native types; a changed preference is seen by the next call.

### What the next iOS step needs

- **I6 app**: create one `IosJsSourceRuntime` at start, pass `declaredPreferences` from the store's cached
  metadata (`JsExtension.metadata()`), call `clearPreferences` on uninstall, `close()` at shutdown. The store of
  installed sources (index fetch, install, hash check, I4 on JVM) has no iOS twin yet: `repo/` parsing is common,
  the download and file cache are not.
- No WebView (`evaluateJavascriptViaWebview` still fails as unsupported; a `WKWebView` `HostWebView` is an I6 item).
- Not covered on iOS: NSURLSession's cookie policy versus the app's (`NetworkHelper`'s cookie jar, Cloudflare
  interceptor, DNS over HTTPS and rate limits have no iOS counterpart); proxy settings are the system's.
