# Komikku Desktop — Building, running and packaging

The desktop app is the Gradle module `:desktopApp` (Compose Multiplatform for the JVM). Plan and status:
[`PLAN.md`](PLAN.md), [`PROGRESS.md`](PROGRESS.md).

## Requirements

- JDK 21 (the version in `.github/.java-version`; the same JDK as the Android build). Packaging uses the
  JDK's `jpackage` / `jlink`, so a full JDK is needed, not a JRE.
- The Android SDK setup of the Android build: Gradle configures the whole project, Android modules included.
- Packaging only builds installers for the OS it runs on (jpackage cannot cross-build):
  - Linux: `.deb` needs `dpkg-deb` / `fakeroot`, `.rpm` needs `rpmbuild` (package `rpm`).
  - Windows: `.msi` / `.exe` need WiX 3; the Compose Gradle plugin downloads it.
  - macOS: `.dmg` uses the system tools.

## Run and test

```bash
./gradlew :desktopApp:run                    # start the app (development run, no installer)
./gradlew :desktopApp:compileKotlin          # compile only
./gradlew :desktopApp:test --max-workers=2   # unit + headless Compose UI tests (no display needed)
```

The tests boot the real DI graph and database in temporary directories (`komikku.dataDir` points at
`desktopApp/build/tmp/test-data`), one JVM per test class. They use fakes and `MockWebServer`; no source
site, tracker or extension repository is contacted.

When a change touches `app` files that the desktop borrows (`app-shared/borrowed-app-sources.gradle.kts`),
also run `./gradlew :app:verifyBorrowedSources :app:compileDebugKotlin`.

## `:source-js` (Mangayomi JavaScript sources)

Host for Mangayomi's JavaScript manga extensions ([`PLAN.md`](PLAN.md) section 8, specification
[`MANGAYOMI_CONTRACT.md`](MANGAYOMI_CONTRACT.md)). A Kotlin Multiplatform module with its code in `commonMain`;
targets: `androidTarget()` and `jvm("desktop")` (code shared by both in `src/jvmSharedMain`; iOS targets are
added automatically on macOS hosts).

```bash
./gradlew :source-js:compileKotlinDesktop                 # compile (desktop)
./gradlew :source-js:compileDebugKotlinAndroid            # compile (Android)
./gradlew :source-js:desktopTest --max-workers=2          # all tests (JVM)
./gradlew :source-js:desktopTest --tests '*Conformance*'  # the bridge conformance suite only
./gradlew :source-js:testReleaseUnitTest                  # commonTest on the Android host (part of CI's testReleaseUnitTest)
```

The Android host unit tests run on `quickjs-kt`'s JVM artifact (host natives): the build swaps it in for the
Android AAR, whose `libquickjs.so` only exists for Android ABIs (`useQuickJsKtJvmInAndroidHostTests()` in
`buildSrc`, shared with `:core:quickjs`). `desktopTest`-only tests (`OkHttpHostHttp`, JDK crypto checks, class
injection, forged throwables, the engine lifecycle lock and idle threads, out-of-memory crashes) do not run there.
`OutOfMemoryCrashTest` starts 16 child JVMs (one per scenario and heap limit, ~10 s), so a native crash fails that
test instead of aborting the run.

Tests: engine (`QuickJsEngineTest`), host core and limits (`HostCoreTest`), the error protocol
(`ErrorProtocolTest`), Mangayomi parity details (`MangayomiParityTest`), bytecode cache and metadata
(`HostEfficiencyTest`), result models (`ResultModelsTest`), how the extension text is loaded
(`ExtensionTextTest`), how a call ends (`CallLifecycleTest`), the prototype lockdown (`PrototypeLockdownTest`), the
host channels (`SessionChannelTest`) and the bridge conformance suite (`conformance/*`) live in `src/commonTest` with in-test JavaScript providers and a fake `HostHttp`, so they will run unchanged on
iOS; `src/desktopTest` adds `OkHttpHostHttp` against `MockWebServer`, the pure Kotlin crypto and SHA-256 checked
against the JDK, and the class-injection regression test (`ErrorClassInjectionTest`, payloads confined to a
temporary directory; one test is `@Ignore`d until `quickjs-kt` is fixed, see [`PROGRESS.md`](PROGRESS.md)). No
website is contacted and no third-party extension file is in the repository.

The Mihon source adapter (`app.komikku.sourcejs.mihon`, I3): its pure helpers are tested in `src/commonTest`
(`AdapterHelpersTest`), the filter mapping in `src/jvmSharedTest` (`JsFiltersTest`, desktop and Android host),
the preference store / screen and the adapter itself in `src/desktopTest` (`JsPreferencesTest` on the compat
`androidx.preference` model, `MangayomiJsSourceTest` against `MockWebServer`). The desktop app's
`JsSourceBrowseUiTest` browses and reads a JavaScript source through the desktop catalogue, details, settings and
chapter-loader code paths:

```bash
./gradlew :desktopApp:test --tests '*JsSourceBrowseUiTest*' --max-workers=2
```

The repository store (I4): index parsing, `repo.json`, version comparison and statuses are pure
(`app.komikku.sourcejs.repo`, `MangayomiIndexTest` in `src/commonTest`); the store itself
(`app.komikku.shared.jssource.MangayomiSourceStore` in `:app-shared`) is tested end to end against `MockWebServer`
in a temporary directory by `MangayomiSourceStoreTest`, the source-manager merge by `JsSourceManagerTest` and the
desktop pane headlessly by `JsSourcesUiTest`. `MockWebServer` serves plain HTTP, so these tests build the store with
an `allowUrl` that also accepts the server's host; the app's store accepts `https` only.

```bash
./gradlew :desktopApp:test --tests 'app.komikku.desktop.jssource.*' --tests '*JsSourcesUiTest*' --max-workers=2
```

Test stack: this module uses `kotlin.test` (with `kotlinx-coroutines-test`) instead of the JUnit 5 + Kotest +
MockK stack that `AGENTS.md` names for the Android modules, because `commonTest` must compile for Kotlin/Native
(iOS) too, where JUnit, Kotest's JVM assertions and MockK are not available; fakes are hand-written. On the JVM
`kotlin.test` runs on the JUnit Platform (`useJUnitPlatform()` in `source-js/build.gradle.kts`).

## `:core:quickjs` (`app.cash.quickjs.QuickJs`)

The JavaScript API Mihon extensions and `JavaScriptEngine` use, on `quickjs-kt` for Android and desktop
(replaces the zhanghai `quickjs-android` AAR; [`PROGRESS.md`](PROGRESS.md), "QuickJS parity"). Tests live in
`src/test/kotlin` and run on both targets; the Android host run uses the same JVM-artifact swap as `:source-js`.

```bash
./gradlew :core:quickjs:desktopTest --max-workers=2
./gradlew :core:quickjs:testReleaseUnitTest
```

`QuickJsErrorClassTest` writes only to a JUnit temporary directory; two of its tests are `@Disabled` until
`quickjs-kt` stops instantiating Java classes named by JavaScript errors. The other classes: parity
(`QuickJsTest`), completion values (`QuickJsCompletionTest`), `close()` of a stuck call (`QuickJsCloseTest`), the
finalizer (`QuickJsFinalizerTest`), the process-wide engine lifecycle lock (`EngineLifecycleTest`) and idle engine
threads (`EngineThreadTest`); the classes that can hang on a regression have a JUnit timeout. CI runs
`:core:quickjs:desktopTest` next to `:source-js:desktopTest` (both workflows, Linux) and the Android host run
through `testReleaseUnitTest`.

## Package

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS   # every installer format of this OS
./gradlew :desktopApp:packageDeb                        # one format: packageDeb / packageRpm /
                                                        # packageAppImage / packageMsi / packageExe / packageDmg
./gradlew :desktopApp:createDistributable               # unpacked app image + bundled runtime only
./gradlew :desktopApp:runDistributable                  # run that image
```

Output: `desktopApp/build/compose/binaries/main/<format>/`.

Configuration (`desktopApp/build.gradle.kts`, `nativeDistributions`):

| Setting | Value |
|---------|-------|
| Version | `versionName` / `versionCode` of `app/build.gradle.kts`, read by the build script (single source of truth); also passed to the app as `-Dkomikku.version` / `-Dkomikku.versionCode` |
| Formats | Msi, Exe, Dmg, Deb, Rpm, AppImage (the AppImage target produces an app-image *directory*; turning it into a single `.AppImage` file needs `appimagetool`, not part of the build) |
| Runtime | jlink image with the modules listed in `modules(...)` (jdeps + run-time additions, see the comment there); `includeAllModules = true` is the documented fallback if a packaged build misses a JDK module |
| Icons | `desktopApp/icons/komikku.{png,ico,icns}`, generated from the Android launcher icon's vector layers by `python3 desktopApp/icons/generate_icons.py` (Pillow + numpy, offline); re-run it when the launcher icon changes |
| Windows | menu group "Komikku", desktop shortcut, per-user install, directory chooser, fixed upgrade UUID (never change it: it lets newer MSIs replace older installs) |
| macOS | bundle ID `app.komikku`, category Entertainment |
| Linux | package `komikku`, menu group / categories `Graphics;Viewer;`, shortcut |
| ProGuard | the `release` build type's ProGuard step is disabled: extensions are loaded reflectively against host classes (source API, OkHttp, kotlinx, compat layer) that shrinking would remove or rename. Enabling it needs keep rules for that shared surface |

App update check (P6, [`PROGRESS.md`](PROGRESS.md#p6--app-update-check)): off unless the build gets Android's flag,
e.g. `./gradlew :desktopApp:packageDistributionForCurrentOS -Penable-updater` (passes `-Dkomikku.updater=true` to the
launcher); add `-Pdesktop-preview` for the preview channel (`komikku-app/komikku-preview`, `r<commit count>` tags,
`-Dkomikku.preview=true -Dkomikku.commitCount=…`; the build fails on a shallow clone or without a commit count, so CI
needs `fetch-depth: 0`, which `build_desktop.yml` sets). Stable builds read `komikku-app/komikku` and compare
`v<versionName>`. A release counts only when it carries an installer for the running OS (`.msi` / `.exe`, `.dmg`,
`.deb` / `.rpm` / `.AppImage`) and CPU architecture (an asset name with `x64` / `amd64` / `x86_64` or `arm64` /
`aarch64` counts only for that architecture, one without counts for any); `build_desktop.yml` does not publish any yet (workflow artifacts only), so the check finds nothing until
the release pipeline attaches them.

CI: `.github/workflows/build_desktop.yml` (manual, or a `desktop-v*` tag) packages on Ubuntu, Windows
and macOS runners and uploads the installers as workflow artifacts (on Linux it also runs `:desktopApp:test`,
`:core:quickjs:desktopTest` and `:source-js:desktopTest` first). The PR workflow has a separate `desktop` job
(`:desktopApp:compileKotlin`, `:desktopApp:test`, then `:core:quickjs:desktopTest :source-js:desktopTest`) that
cannot block the Android build and, while the port is
experimental, does not fail the PR check (`continue-on-error`).

Signing: none. macOS packages are neither signed nor notarized (Gatekeeper will warn; open them with
right-click → Open), Windows installers are unsigned (SmartScreen warning). The TODOs in
`build_desktop.yml` / `desktopApp/build.gradle.kts` list what signing needs (Apple Developer ID +
notarization credentials, a Windows code-signing certificate).

## Data directory

| OS | Default location |
|----|------------------|
| Windows | `%APPDATA%\Komikku` |
| macOS | `~/Library/Application Support/Komikku` |
| Linux / others | `$XDG_DATA_HOME/komikku`, else `~/.local/share/komikku` |

Override with the `komikku.dataDir` system property (portable installs, separate profiles, tests), e.g.
`JAVA_TOOL_OPTIONS=-Dkomikku.dataDir=/path/to/data ./gradlew :desktopApp:run` or the same environment
variable for an installed launcher. Contents: `databases/tachiyomi.db` (library), `preferences/*.json`,
`extensions/` (installed APKs and converted jars), `storage/` (default downloads / local source folder,
changeable in Settings), `cache/` (covers, chapter pages), `files/`, `keys/` (CBZ encryption key).

## Extension link check

Lists the Android symbols an extension needs that the desktop compat layer lacks (inputs: APKs or
converted jars you already have locally; nothing is downloaded):

```bash
./gradlew :desktop:extension-runtime:linkCheck --args="path/to/ext.apk path/to/dir --output=build/linkcheck.txt"
```

Details in [`PROGRESS.md`](PROGRESS.md#link-check-53).

## WebView and Cloudflare (P7, P8)

Nothing is downloaded or bundled for this: the desktop app has **no embedded browser**. Cloudflare challenges go
through `app.komikku.network.browser.BrowserBackends.current`, which the app sets to the FlareSolverr backend (P8)
when the network is created; with its address empty (the default) it is unavailable, so a challenged request fails with
a message that points the user to "Open in WebView" (catalogue top bar or error screen, details top bar): the page
opens in the system browser and a dialog takes the pasted cookies and User-Agent. The cookies go to
`<dataDir>/cookies.json` (pasted ones host-only, Cloudflare's `cf_*` for the site's registrable domain, replaced when the
site sets its own); the User-Agent is sent to that site at once (the `desktop_cloudflare_user_agents` preference, bound
to the clearance cookie) and, only with the dialog's "Also use … as the default" box ticked, becomes the
`default_user_agent` preference (extension sources pick that up after a restart).

**Automatic solving (P8), for users:** Settings → Browse → "Cloudflare solver (FlareSolverr)". Enter the address of a
[FlareSolverr](https://github.com/FlareSolverr/FlareSolverr)-compatible service you run yourself (for example
`docker run -p 8191:8191 ghcr.io/flaresolverr/flaresolverr` → `http://localhost:8191`; the API address `…/v1` works
too; only `http` / `https`; user info in the address is sent as basic authentication and never logged), then "Test
connection". From then on challenges during browsing, library updates, global search and downloads are solved by that
service (`POST /v1`, `request.get`, at least 60 s per solve): its cookies and User-Agent are stored and the request is
sent again. **Privacy:** the service loads the protected pages itself, so it sees their addresses and the cookies the
app sends with them; run it on a machine you trust. Empty address = off. "Open in WebView" stays the manual fallback
(the service shows no pages). The setting is desktop-only (`desktop_flaresolverr_url`; Android has no such setting).

Tests use a fake backend (`FakeBrowserBackend`) and `MockWebServer` (also as the stand-in solver,
`FlareSolverrBackendTest`); CI starts no browser and no solver.

An embedded backend (jcefmaven was evaluated as the candidate, see [`PROGRESS.md`](PROGRESS.md#p7--webview-and-cloudflare))
would add 120–140 MB of Chromium natives per platform (downloaded at first use or bundled), JVM `--add-opens` flags
and, on macOS, signed helper app bundles; none of that is configured yet.

## Known limitations

- Packaging and the desktop workflow have not been run yet: the configuration was checked with
  `:desktopApp:tasks --all`, a dry run (`-m`) of `packageDistributionForCurrentOS`, `createRuntimeImage`
  (jlink with the chosen modules) and YAML parsing only. Installers, icons inside them, file associations
  and upgrade behaviour are unverified.
- No signing / notarization, no in-app update download / install (the update check only opens the release page), no
  file associations, no embedded browser (Cloudflare checks by hand or through a FlareSolverr-compatible service you
  run, logins by hand; see above).
- The parity gaps with Android (features the desktop app does not have yet) are listed in
  [`PROGRESS.md`](PROGRESS.md#remaining-parity-gaps).
- Only Linux has run the test suite; Windows / macOS specifics (paths, file dialogs, key handling) are
  untested.
