# iOS check (CI)

Status: **green since run #4 (2026-11-25, commit `d21b68cfc4`)**: 100 `:source-js` tests pass on the iOS
simulator, including deep recursion on the 4 MiB JS stack; 122 with I5a (run 45545772720, commit `eca24347e1`,
section below); 132 with the I5a review fixes (cookies, non-HTTP URLs, `HEAD` bodies, redirect resolution; run
45567078024, commit `0501bf16e7`); 145 with I5b, the store of installed sources (run 45570113228, commit `32c89164ae`, section below); 150 with the I5b review fixes (run 45574226158, commit `97ea1bb4fd`). Runs #1–#2 looked green only because the job was
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

- **HTTP semantics match `OkHttpHostHttp`.** Only `http` / `https` URLs (`ws` / `wss` sent as `http` / `https`,
  like OkHttp's `Request.Builder`): any other URL fails with OkHttp's `IllegalArgumentException` message (`Expected
  URL scheme 'http' or 'https' but was 'data'`) before Ktor sees it, which the bridge reports as a catchable `http`
  error. It must not reach NSURLSession: a `data:` or `file:` URL gets a plain `NSURLResponse`, and Ktor's Darwin
  engine casts every response to `NSHTTPURLResponse` inside a delegate callback, which kills the process. All methods; request headers in order, minus the source's
  `Accept-Encoding` (NSURLSession negotiates `gzip, deflate, br` and decompresses; Ktor's Darwin engine then drops
  `Content-Encoding` / `Content-Length`, like OkHttp's transparent gzip) and minus `Transfer-Encoding` / `Upgrade`
  (Ktor refuses them). OkHttp's body rules: an empty body for `POST` / `PUT` / `PATCH` / `PROPPATCH` / `REPORT`
  without one, `GET` / `HEAD` with a body fail. The response: status, headers (NSURLResponse already joins repeated
  names with `", "`, the bridge joins Ktor's separate values the same way), the body capped at `maxBodyBytes`
  (declared `Content-Length` first, then at most cap + 1 bytes read) with `HttpBridge.tooLargeMessage`. A `HEAD`
  response and a 204 / 304 have an empty body and no length check (OkHttp's `promisesBody`): Ktor keeps
  `Content-Length` for `HEAD`, where it is the `GET`'s length. A `Content-Length` next to a kept `Content-Encoding`
  (Ktor keeps both for `OPTIONS`) is the compressed length and is not checked either. Error statuses are returned.
  TLS is NSURLSession's default validation (no challenge handler).
- **Redirects are followed by `DarwinHostHttp`, not by Ktor.** Ktor's Darwin engine already disables NSURLSession's
  own redirects; Ktor's `HttpRedirect` follows only `GET` / `HEAD`, keeps the method and body on 301 / 302 / 303 and
  refuses HTTPS → HTTP. So the client runs with `followRedirects = false` and `DarwinHostHttp` applies OkHttp's
  `RetryAndFollowUpInterceptor` rules: 300 / 301 / 302 / 303 / 307 / 308 with a `Location`, resolved with Ktor's
  `URLBuilder.takeFrom` against the URL Ktor sent (`response.call.request.url`, already percent-encoded; `NSURL`
  returns `nil` for the script's raw text with spaces or non-ASCII on iOS 16 and older) by RFC 3986's rules (a
  reference with a scheme or authority stands alone, a path or query replaces the base query, dot segments removed);
  only `http` / `https` targets; up to 20 follow-ups (`Too many follow-up requests: 21`); 307 / 308 (and
  `PROPFIND`) keep method and body, the others turn into a body-less `GET` without `Content-Type` / `Content-Length` /
  `Transfer-Encoding`; `Authorization` dropped when scheme, host or port change; HTTPS → HTTP followed (OkHttp's
  `followSslRedirects` default).
- **Cookies: the app's `NSHTTPCookieStorage.sharedHTTPCookieStorage`.** Ktor's Darwin engine sets the session's
  cookie storage to `nil` before it runs `configureSession` (`createSession` in `DarwinSession.kt`), so without more
  every `Set-Cookie` is dropped. `DarwinHostHttp`'s own engine calls `DarwinHostHttp.configureCookies()` there: the
  shared storage, accept policy `Always` (the app's OkHttp jar, `AndroidCookieJar`, keeps every cookie too) and
  `HTTPShouldSetCookies`. Each redirect hop is its own `NSURLSessionTask` started after the previous response, so a
  cookie set on a redirect is sent on the next hop. A caller-supplied Darwin engine needs `configureCookies()` in its
  own `configureSession`.
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

- `DarwinHostHttpTest` (18): the `OkHttpHostHttpTest` cases on Ktor's `MockEngine`: method / headers / body, every
  method, `Accept-Encoding` dropped and no added `Accept`, body rules, raw bytes, redirects followed or not (relative
  location), OkHttp's method / header changes on 302 / 307 / cross-host, redirects from a URL with spaces and
  non-ASCII (relative and `..` locations, the base query not carried over), a scheme-relative location to another
  host without the base's user info or port, a non-HTTP location returned as is, the 20 follow-up limit, the
  timeout, the body cap (declared, streamed, exact, unbounded), `HEAD` / 204 / 304 without body whatever their
  `Content-Length`, non-HTTP URLs refused (and a catchable `http` error through `Client`), cancellation reaching the
  engine, a source through `Client`.
- `DarwinHostHttpServerTest` (9): the real Darwin engine against `LoopbackHttpServer`, a small HTTP/1.1 server on
  POSIX sockets and `NSThread`s inside the simulator test process, on `127.0.0.1` (App Transport Security does not
  apply to IP addresses): the session's own `Accept-Encoding`, repeated response headers, gzip decompressed with the
  encoding headers gone, no native redirects, cookies stored and sent (also on a redirect hop), a redirect from a URL
  with a space and non-ASCII, `data:` / `file:` URLs failing before the session, a `HEAD` declaring a length above
  the cap, timeout and body cap, cancellation of a real task. A Ktor CIO server was tried first and never started in
  the simulator process (every start timed out, run 45544362098), so the test server has no dependency.
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
  the download and file cache are not. *(Done in I5b below: `IosJsSourceStore` creates the runtime and does all
  three.)*
- No WebView (`evaluateJavascriptViaWebview` still fails as unsupported; a `WKWebView` `HostWebView` is an I6 item).
- Not covered on iOS: `NetworkHelper`'s Cloudflare interceptor, DNS over HTTPS and rate limits have no iOS
  counterpart (cookies are the shared `NSHTTPCookieStorage`, see above); proxy settings are the system's.

## I5b: the store of installed JavaScript sources on iOS

`IosJsSourceStore` (`source-js/src/iosMain/.../store/`) is the iOS twin of app-shared's JVM `MangayomiSourceStore`
(I4): an I6 app lists repositories, installs, updates and uninstalls JavaScript sources the way the desktop app does.
All of it is in `iosMain` / `iosTest`; the only change to existing code is `DarwinHostHttp.resolve` moving to its
companion (`internal`), so the store resolves its redirects with the same RFC 3986 rules. No common code, no new
library.

| Piece | File | What it is |
|-------|------|------------|
| `IosJsSourceStore` | `iosMain/.../store/IosJsSourceStore.kt` | repositories, refresh, download / install / update / uninstall, load at start-up |
| models | `iosMain/.../store/IosJsSourceStoreModels.kt` | `InstalledJsSource`, `JsSourceItem`, `JsSourceRepository`, `JsSourceDownload`, `JsSourceStoreException` (the JVM names and fields), `IosJsSource` (record + `JsExtension`) |
| `StoreFiles` | `iosMain/.../store/StoreFiles.kt` | Application Support path, read, atomic write, delete (Foundation + POSIX) |

### Behaviour (the same as the JVM store)

- **Repositories**: index URLs; `addRepository` reads the index (`MangayomiIndex`, JavaScript manga only) and its
  optional `repo.json` (`MangayomiRepoInfo.infoUrl`) once before adding; `refresh` reads them all again (a failed read
  keeps the last good entries and records the error) and fetches the offered text of installed sources whose version
  did not change, so a changed text shows as `TEXT_CHANGED`. Offers merge by id, highest version first
  (`JsSourceOffer.merge`). Nothing is fetched or updated on its own.
- **Install in two steps**: `download(offer)` returns the host that served the text (after redirects), the requested
  host, SHA-256 and size for the user to confirm; `install(offer, confirmedSha256)` fetches it again and refuses another
  text (`SHA_MISMATCH`). It then reads the source's metadata (this runs the script; a text that does not load is
  `LOAD_FAILED`) while the installed version stays in use, writes the text and the manifest, and only then swaps the
  new source in, deletes the old text when nothing else uses it and stores the icon (best effort).
- **A failed update changes nothing**: a download failure, a hash mismatch, a script that does not load or a file
  that cannot be written leaves the installed version, its files and its `JsExtension` as they were (a new text file
  is removed again).
- **States**: `installed` (`null` until read), `isLoaded`, `isRefreshing`, `sources` (loaded, NSFW ones only while
  `showNsfw`), `repositories`, `available`, `items` with `JsSourceStatus` (`UP_TO_DATE`, `UPDATE_AVAILABLE`,
  `TEXT_CHANGED`, `OBSOLETE`; not obsolete while a repository was not read or none is added) and the load error.
- **Errors**: `JsSourceStoreException` with the JVM's `Reason`s (`INSECURE_URL`, `DOWNLOAD_FAILED`, `TOO_LARGE`,
  `INVALID_INDEX`, `SHA_MISMATCH`, `NOT_FOUND`, `LOAD_FAILED`, `STORAGE_FAILED`); warnings go to `NSLog`
  (`[JS store] …`, injectable).

### Decisions

- **Files under `<Application Support>/mangayomi-js`** (`NSFileManager.URLsForDirectory`, the JVM directory name;
  `root` can be overridden, tests use a temporary directory): `manifest.json` (the JVM manifest's shape),
  `code/<sha256>.js`, `icons/<mangayomi id>`. Every write is a temporary file next to the target, written and
  `fsync`ed with POSIX calls, then `rename(2)`d over it (atomic replace). Application Support is in the device
  backup; the directory is not marked as excluded (the manifest is user data, the texts are small).
- **The metadata cache is the manifest**: each record keeps its `JsSourceMetadata` (supports-latest, filters,
  declared preferences, headers), as on the JVM. At start-up the store builds a `JsExtension` per record from its
  text (checked against the SHA-256) and does not run any script; a record without metadata gets it read once.
  `refreshMetadata(id)` reads it again (after a preference change), since there is no adapter that re-reads it in
  the background as on the JVM.
- **The store creates the runtime** (wiring for I6): `IosJsSourceStore(...).runtime` is an `IosJsSourceRuntime` on
  the store's HTTP client whose `declaredPreferences` is `store.declaredPreferences` (the cached declarations), so
  the `NSUserDefaults` values are typed before a script ran. The `runtime` parameter builds it from that function
  (tests pass a suite prefix and a log). I6 creates one store at start, calls `IosJsSource.extension` for the
  provider calls, and `store.close()` at shutdown (it closes the runtime and the HTTP client it created).
- **Uninstall clears the preferences** (`IosJsSourceRuntime.clearPreferences`, the source's suite), unlike the JVM
  store, which keeps them. Done after the manifest was written, so a failed uninstall keeps them.
- **Downloads through `HostHttp`** (`DarwinHostHttp` by default, injectable): `GET` with `Cache-Control: no-cache`,
  `followRedirects = false` and `maxBodyBytes` set to the limit; the store follows up to 5 redirects itself and
  checks each target (`https` only by default, `allowUrl` injectable) before requesting it, so a redirect to plain
  HTTP is refused, not followed. The body cap is enforced twice: `DarwinHostHttp`'s cap error becomes `TOO_LARGE`,
  and the body is checked again (a client without a cap). Limits as on the JVM: index 8 MiB, `repo.json` 64 KiB,
  text 4 MiB, icon 1 MiB, plus one 120 s deadline per download, redirects included (each request is given the time
  left). The icon is fetched before `install` takes the store's lock; an empty icon (a 204 or an empty 200) is no
  icon, and `iconFileForSource` returns files with a size only.
- **Repositories in `NSUserDefaults`** (standard defaults, injectable) under the JVM preference key
  `mangayomi_js_repos`, an array of strings, so a later settings backup maps them by the same key. Each add or
  remove reads the stored list again and saves it under one process-wide lock (`NSLock`), so changes made elsewhere
  (a restored backup, another store) are kept and the list saved last is the newest.
- **A corrupt cache is not fatal**: a manifest that does not decode is set aside as `manifest.json.corrupt-<time>`
  and the store starts empty (warning logged); a text file that is missing or does not match its SHA-256 is a load
  error of that source only (shown in `items`, left out of `sources`), and reinstalling repairs it; a record whose
  SHA-256 is not 64 hex digits is a load error, and every read or delete built from it is skipped, so it can never
  name another path (`codePath` accepts 64 hex digits only; icon paths are named by the numeric id).
- **Cancellation**: the manifest write and the state swap after it run under `NonCancellable` (`install`,
  `uninstall`, `refreshMetadata`): once the manifest names the new state, the text, the in-memory state, file
  deletions and preferences follow it even if the caller was cancelled meanwhile.

### Tests (`iosTest/.../store/IosJsSourceStoreTest`, 18, simulator only)

The first 13 passed in run 45570113228 (job `success`, 145 `:source-js` tests in total, none failed); all 18,
with the 5 added for the review fixes below, passed in run 45574226158 (job `success`, 150 in total, none failed).

All with `FakeHostHttp` (a URL → response table that records requests and can enforce the body cap like
`DarwinHostHttp`) and a temporary root; no network:

- index read through to an installed source (repo name, only the JS manga entry offered, download host / SHA-256 /
  size, files and manifest with metadata, icon, no `.tmp` left, `declaredPreferences` from the cache, the requests'
  `followRedirects = false` and cap);
- end to end: an installed fixture source runs `getPopular` through the store's `IosJsSourceRuntime` (HTML from the
  fake site parsed by the DOM bridge); a changed preference seen by `refreshMetadata` and cached; a stored value of
  the wrong type reads as missing;
- a new version is an update (installed / updated times, old text deleted); a changed text under the same version
  shows as `TEXT_CHANGED` and updates;
- failed updates keep the installed version working: a text that does not load, a network failure, HTTP 500, a
  manifest that cannot be written (also for uninstall, which keeps the preferences); then the update goes through;
- SHA-256 mismatch; texts above the limit refused by the client's cap and by the store's own check; an index above
  its limit;
- `http`, `file:` and malformed URLs refused, broken and missing indexes, a redirect chain to another `https` host
  (relative location) named in the confirmation, a redirect to `http` not followed, endless redirects;
- uninstall removes the record, text, icon and the preferences (declared and free strings);
- a reloaded store uses the cached metadata: no request and no script ran at start-up;
- a cancellation while the manifest is written (a test hook, `afterManifestWrite`, cancels the caller) during
  install, `refreshMetadata` and uninstall: the store, its files and a reloaded store agree with the manifest;
- a malformed SHA-256 (`../../sentinel-…`) reinstalled over and uninstalled: the file it names outside the root
  survives;
- an empty icon (204, empty 200, a 0-byte file) is no icon;
- one time limit for a download and its redirects (a fake client that takes 400 ms per request, 1 s limit);
- an add or remove keeps repositories another store added to the stored list;
- a corrupt manifest set aside; a tampered or missing text and a malformed SHA-256 as load errors while the other
  source loads; NSFW filtering; obsolete status after removing a repository, and not with none left.

### Review fixes

1. Cancellation during the manifest write: `withContext(Dispatchers.IO)` finished the write and then threw, so
   `install` deleted the text the new manifest named (a broken source at the next start), `uninstall` left its state,
   files and preferences, and `refreshMetadata` skipped its state update. Fixed with `NonCancellable` (above). The
   JVM `MangayomiSourceStore` (app-shared) has the same pattern and is not changed here.
2. Path traversal through a malformed `sha256`: the 64-hex check guarded the start-up read only; `install` (the old
   text) and `uninstall` deleted `code/<sha256>.js` recursively for any value. `codePath` now refuses it.
3. The repository list: the in-memory update and the defaults write were not ordered, and `refresh` / `addRepository`
   read the list outside their listings update. One lock; the list is read inside the update.
4. A 0-byte icon was returned by `iconFileForSource`.
5. The repository list was read from the defaults once, so a change overwrote ones made elsewhere.
6. The download timeout applied per redirect hop (about 6 × 120 s) while `install` held the lock for the icon.
7. The test helper `installFixture(text)` ignored `text`.

### Not covered

- Not run on Linux: compiled with `-Pios-targets` (main and test klibs); the tests run on the simulator in CI only.
  No test against the real `DarwinHostHttp` (the HTTP semantics are I5a's tests) or a real repository.
- No UI (I6), no background refresh or update notifications, no migration of obsolete sources (P23 is JVM-only).
- Not shared with the JVM store: the models and logic are a copy in `iosMain` (app-shared is JVM-only and owned by
  the desktop work). Moving both stores onto one common implementation would need `app-shared` changes.
- `Cache-Control: no-cache` is a request header only; whether NSURLSession's shared `URLCache` still answers
  from its cache is not checked (the JVM store forces the network).
- The device backup / iCloud exclusion of the store's directory, file protection classes and disk-full handling
  beyond reporting `STORAGE_FAILED` are not decided.
