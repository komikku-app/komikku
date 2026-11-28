# iOS review findings (the iOS session's commits since `5efbba2`)

Code review, track B of INSTRUCTION 001 (ATLAS_INSTRUCTIONS.md), 2026-11-28. Reviewer role; the owning roles fix.
Line numbers are those at the review's `HEAD` of `multiplatform` (the iOS files did not change after `11632ee809`).

## Reviewed range

The commits of `5efbba2..HEAD` that touch `source-js/**/ios*`, an iOS source set (`**/src/ios*`, `**/iosMain`,
`**/iosTest`) or `.github/workflows/build_ios_check.yml`: 34 commits, net 22 files, about 4,800 added lines
(`source-js/src/iosMain`, `source-js/src/iosTest`, the workflow). The last three only touch the workflow and come from
other sessions: `98e84f7ed8` (self-hosted runner) and `7618a310e8` cancel each other out on the workflow, and
`6bc87481ba` renames the pushed branch to `multiplatform`; the net diff was reviewed. `source-js/build.gradle.kts`
and the Ktor entries of `gradle/libs.versions.toml` (from `e8c4134f44`, `8684a46702`) were read as context.

| Commit | Subject |
|--------|---------|
| `f938cbc2d0` | ci(kmp): Add a non-blocking macOS job compiling :source-js for iOS and running its tests on the simulator |
| `bcf4ca45b0` | fix(kmp): Base the iOS JS stack limit on the applied thread stack and drain an autorelease pool per block |
| `bd18e95055` | ci(kmp): Run the iOS check on pushes to master and the KMP branch only, and on build setup changes |
| `5ceada21ef` | ci(kmp): Make the iOS check blocking now that it passes |
| `e8c4134f44` | feat(kmp): Run JavaScript sources on iOS with Ktor Darwin HTTP and NSUserDefaults preferences |
| `b257c7e6ee` | test(kmp): Cover the iOS HTTP, preferences and an end-to-end source on the simulator |
| `3cb2b85e66` | test(kmp): Time-limit the iOS tests and log each simulator test |
| `8684a46702` | test(kmp): Serve the Darwin HTTP tests from a POSIX loopback server |
| `000d393c39` | fix(kmp): Store and send cookies in DarwinHostHttp's own engine |
| `e0c549d8cc` | fix(kmp): Refuse URLs other than http and https in DarwinHostHttp |
| `716228a4a8` | fix(kmp): Give HEAD, 204 and 304 responses an empty body in DarwinHostHttp |
| `0501bf16e7` | fix(kmp): Resolve redirects against the URL Ktor sent in DarwinHostHttp |
| `32c89164ae` | feat(kmp): Add an iOS store of installed Mangayomi JavaScript sources |
| `61c5114f6c` | fix(kmp): Make the iOS store's manifest write and state swap non-cancellable |
| `844dceefbf` | fix(kmp): Only build iOS store paths from a well-formed SHA-256 |
| `ae4854d300` | fix(kmp): Order the iOS store's repository list updates |
| `b3d92c44b2` | fix(kmp): Read the iOS store's repository list again before changing it |
| `e042f8d1b3` | fix(kmp): Treat an empty icon as no icon in the iOS store |
| `f64e8e7bcb` | fix(kmp): Give an iOS store download one deadline across its redirects |
| `bd8725ca6b` | test(kmp): Serve the given text in the iOS store test's installFixture |
| `203adcea04` | docs(kmp): Describe the iOS store's install order and cancellation |
| `b523ab6aaf` | feat(kmp): Evaluate JavaScript sources' pages in a WKWebView on iOS |
| `26aa31f065` | fix(kmp): Only look for the key window in an iOS app process |
| `68b90c0274` | test(kmp): Run the iOS WebKit tests inside a simulator app |
| `ea07a52611` | fix(kmp): Let page cookie changes settle before copying them back on iOS |
| `089b9b4ade` | test(kmp): Measure the iOS web view's deallocation without holding it |
| `b85f4a63f6` | fix(kmp): Copy only the target host's cookies into the iOS web view and diff against WebKit's copy |
| `bb3b1db27a` | test(kmp): Cover the iOS web view's per-host cookie copy, its baseline and its deallocation |
| `cd8b7b43db` | ci(kmp): Require the expected WebKit test count and pick the newest iOS simulator by version |
| `dade15ac21` | fix(kmp): Count the iOS web view as live before the creation hook runs |
| `11632ee809` | test(kmp): Expire the iOS cookie during a slow load, and report the web view's deallocation with controls |
| `98e84f7ed8` | ci(kmp): Run the macOS jobs of the desktop and iOS workflows on the self-hosted Mac runner |
| `7618a310e8` | docs(kmp): Note the parked self-hosted runner change and its branch |
| `6bc87481ba` | docs(kmp): Make multiplatform the non-Android branch and set the coordination rules |

## Summary

| Severity | Count | Findings |
|----------|-------|----------|
| High | 0 | – |
| Medium | 1 | 1 |
| Low | 2 | 2, 3 |
| Design-level → Atlas | 2 | 1 (in part), D1 |

## Findings

### 1. Medium: every web view call leaves its `WKWebView` alive (unbounded growth); must be resolved before I6 ships it

- **File, line:** `source-js/src/iosMain/kotlin/app/komikku/sourcejs/bridge/WKWebViewHostWebView.kt` lines 182–237
  (`Call.run`: a new `WKWebViewConfiguration`, `nonPersistentDataStore` and `WKWebView` per call) and 353–362
  (`tearDown`).
- **Problem:** the iOS session found and documented it (IOS_CI.md "Not covered"): the deallocation test reports
  `WEBVIEW_DEALLOCATED=false` 8 s after a call with `GC.collect()` every 200 ms, while the two controls (a web view
  that never loaded, a plain `NSObject`) are freed. The host's own references are gone (delegate weak and set to
  `nil`, message handler removed, page replaced), so WebKit keeps the view after a load. Each call therefore leaves a
  `WKWebView`, its configuration and its non-persistent data store (and possibly a web content process connection)
  until the process ends. `permits` (`maxConcurrent`) and `liveCount` only bound views *in use*: `liveCount` is
  decremented at teardown, so nothing bounds the views created. A source that evaluates a page per chapter or per
  page list on iOS grows memory with every call, and iOS kills the app (jetsam) rather than slowing down. The test
  only reports the value, so nothing fails when it regresses or when it is fixed.
- **Proposed fix:** before I6 calls this from the app: check with Instruments (Allocations, retain history of a
  `WKWebView`) after a few calls, as IOS_CI.md says; then either fix the retaining reference, or reuse web views (a
  small pool, each with its own non-persistent data store cleared between calls with
  `removeDataOfTypes(allWebsiteDataTypes, modifiedSince: distantPast)`), and turn the deallocation report into an
  assertion of the chosen invariant (freed, or the pool size never exceeded). Until then, a hard cap on views created
  per process with a clear error is better than a jetsam kill.
- **Owner:** iOS engineer. **Design-level:** in part → Atlas: reusing views trades the per-call isolation of
  cookies / storage (the reason for the per-call data store) for bounded memory; that trade-off is Atlas's call.

### 2. Low: a navigation decision still pending at teardown is answered "allow" after the page was replaced

- **File, line:** `source-js/src/iosMain/kotlin/app/komikku/sourcejs/bridge/WKWebViewHostWebView.kt` lines 291–304
  (`beforeNavigation`), 222–225 (`finally`: `cancelChildren()` then `tearDown`), 371–382 (the delegate's
  `decidePolicyForNavigationAction`).
- **Problem:** when a navigation to a not yet copied host waits for `copyCookiesIn` and the call ends (timeout,
  cancellation, result), `cancelChildren()` cancels that child, `tearDown` runs at once (delegate closed, page
  replaced by `loadHTMLString("")`), and the child's `finally` later calls `proceed()`, which answers
  `WKNavigationActionPolicyAllow` for the old navigation. If WebKit honours a late answer for a navigation that
  `loadHTMLString` should have superseded, the site loads into the detached view after teardown: its scripts and
  requests run with no delegate and no owner, in a view that is never freed (finding 1). Calling the handler is
  required (WebKit raises for a handler never called), allowing is not.
- **Proposed fix:** answer `Cancel` when the call is closed: have the delegate pass a decision function, and in the
  `proceed` path check `closed` (`decisionHandler(if (closed) Cancel else Allow)`). A simulator test: a page that
  navigates to a second host with stored cookies, a call that times out during the cookie copy, then check that the
  second host's request never reaches the loopback server.
- **Owner:** iOS engineer. **Design-level:** no.

### 3. Low: the WebKit CI step checks a hard-coded test count floor, not the tests that should run

- **File, line:** `.github/workflows/build_ios_check.yml` lines 126–137.
- **Problem:** the app's exit status is ignored (`… | tee … || true`), and success means "at least 25 passed, no
  `[ FAILED ]`, no `SKIPPED:`". The three test classes have 25 tests today, so any new WebKit test lowers the margin
  only if someone remembers to raise `MIN_TESTS`; tests that silently fall out of the filter (a renamed class, a test
  moved to another class) go unnoticed as long as the total stays at 25 or more; a crash after the summary line would
  pass. Not broken today, but it is the kind of step that keeps passing while coverage shrinks.
- **Proposed fix:** check per class: grep the runner's `[ RUN ]` / `[ OK ]` lines and require at least one passed test
  for each filtered class, and derive the expected total from the test sources (count of `@Test` in the three files)
  instead of a constant; also keep the launch's exit status (`set -o pipefail` with `PIPESTATUS[0]`) and fail on a
  non-zero one.
- **Owner:** Test and release engineer. **Design-level:** no.

## Design-level → Atlas

### D1. iOS (and other KMP-only) dependencies in the `master`-owned version catalog

`e8c4134f44` and `8684a46702` add the Ktor version and three libraries (`ktor-client-core`, `-darwin`, `-mock`) to
`gradle/libs.versions.toml`, which `master` owns, inside `# KMK` markers. They are only used by `:source-js`'s iOS
source sets. Per COORDINATION.md "The branch", an edit to a file `master` has is a future merge conflict and should be
the third choice; the edits are also not recorded in PROGRESS.md under "Changes to files owned by master" (that
section does not exist yet, as UPSTREAM_AUDIT.md already notes). Option: a catalog `master` does not have
(`gradle/kmp.versions.toml`, registered once in `settings.gradle.kts`) for every KMP-only entry (Ktor, quickjs-kt,
Ksoup, the desktop libraries), so later additions never touch `master`'s files; cost: one more edit to
`settings.gradle.kts` now, a moved block in `libs.versions.toml`, and updating Renovate's config if it lists catalog
files. Atlas to decide whether that is worth it or whether the KMK-marked entries stay (and get recorded).

## Checked and found fine

- **`DarwinHostHttp`:** only `http` / `https` URLs; its own `NSURLSession` cookie handling mirrors OkHttp's cookie
  jar semantics; redirects followed by the host (at most `MAX_FOLLOW_UPS`) with RFC 3986 resolution against the URL
  Ktor sent, `Authorization` dropped across origins, body headers dropped when the method changes to `GET` (OkHttp's rules); `HEAD` /
  204 / 304 have empty bodies; body cap read as `maxBytes + 1` without overflow; default TLS validation; a source's
  `Accept-Encoding`, `Transfer-Encoding`, `Upgrade` dropped (the last two are the only headers Ktor 3 refuses); a
  source's `Content-Length` is replaced by the real body length by Ktor's header merge, as OkHttp's bridge does;
  timeouts mapped separately from cancellation. Repeated request headers are comma-joined (documented; Mangayomi
  passes headers as a map, so no repeats).
- **`WKWebViewHostWebView`, other than the findings:** a non-persistent data store per call; only the target host's
  stored cookies (and its parent domains') copied in, then each navigated host's on demand; the copy-back writes only
  what the page added, changed or deleted against WebKit's own copy (a value the HTTP client stored meanwhile wins);
  the page's cookies reaching the shared storage is the same as Android's shared `CookieManager`; the permit is
  released exactly once (`held` covers the timeout / acquire race); the bridge in the page world is the accepted
  Mangayomi parity risk; a URL other than `http` / `https` with a host is refused before loading; the key window is only looked for in an app process.
- **`IosJsSourceStore` / `StoreFiles`:** code paths only from a well-formed SHA-256 (64 hex digits), icons from
  source ids (`icons/<id>`), so no path component from the network; the text is fetched again at install and must match the confirmed SHA-256;
  writes are temp file + `fsync` + `rename(2)`; the manifest write and state swap are `NonCancellable`; one deadline
  across a download's redirects; size limits on index, text and icon; the repository list is re-read before each
  change and its updates ordered. (No `fsync` of the parent directory after the rename: on power loss the old
  manifest can come back, which is consistent; acceptable.)
- **`JsEngineThread.ios.kt`:** an `NSThread` with an explicit stack rounded to 16 KiB; the JS stack limit is derived
  from the stack size read back, with the old 256 KiB clamp when the setter was ignored; an autorelease pool per
  block; a block dispatched after `close` cancels its job and runs on `Dispatchers.Default`, as the JVM dispatcher
  does; queued blocks run before the thread ends.
- **Tests:** `timedTest` / `mainRunLoopTest` time-limit every simulator test and serve the main queue from the main
  run loop; WebKit tests skip only outside an app process and fail there when `KMK_WEBKIT_REQUIRED=1`, which the CI app
  step sets; HTTP tests run against a real POSIX loopback server as well as Ktor's mock engine.
- **CI:** actions pinned by commit hash, `permissions: contents: read`, no secrets, job and step timeouts, the simulator
  picked by numeric runtime version and failing loudly when there is none; the path filter covers everything the iOS
  targets compile (`source-api` is only a dependency of the JVM / Android `jvmShared` source set).
- **Markers, ownership, secrets:** all iOS code is in `:source-js` iOS source sets (a module `master` does not have);
  the workflow is KMK-only and says so in its header; no secrets, tokens or private paths.
