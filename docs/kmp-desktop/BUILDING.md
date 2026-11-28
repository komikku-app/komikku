# Komikku Desktop — Building, running and packaging

The desktop app is the Gradle module `:desktopApp` (Compose Multiplatform for the JVM). Plan and status:
[`PLAN.md`](PLAN.md), [`PROGRESS.md`](PROGRESS.md).

## Requirements

- JDK 21 (the version in `.github/.java-version`; the same JDK as the Android build). Packaging uses the
  JDK's `jpackage` / `jlink`, so a full JDK is needed, not a JRE.
- The Android SDK setup of the Android build: Gradle configures the whole project, Android modules included.
- Packaging only builds installers for the OS it runs on (jpackage cannot cross-build):
  - Linux: `.deb` needs `dpkg-deb` / `fakeroot`, `.rpm` needs `rpmbuild` (package `rpm`).
  - Windows: `.msi` / `.exe` need WiX 3. Locally the Compose Gradle plugin downloads WiX 3.11.2 (unverified); with
    the environment variable `WIX_PATH` set to an unpacked WiX folder it uses that one instead, as CI does (see
    "What CI downloads and runs").
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
                                                        # packageMsi / packageExe / packageDmg
./gradlew :desktopApp:createDistributable               # unpacked app image + bundled runtime only
                                                        # (binaries/main/app; the Linux .tar.gz)
./gradlew :desktopApp:runDistributable                  # run that image
```

Output: `desktopApp/build/compose/binaries/main/<format>/`.

Configuration (`desktopApp/build.gradle.kts`, `nativeDistributions`):

| Setting | Value |
|---------|-------|
| Version | `versionName` / `versionCode` of `app/build.gradle.kts`, read by the build script (single source of truth); also passed to the app as `-Dkomikku.version` / `-Dkomikku.versionCode` |
| Formats | Msi, Exe, Dmg, Deb, Rpm, plus the app image *directory* of `createDistributable` (packed as the Linux `.tar.gz`; turning it into a single `.AppImage` file needs `appimagetool`, not part of the build). No `TargetFormat.AppImage`: its `packageAppImage` writes the same directory as `createDistributable`, which the DMG / DEB / RPM tasks read, and Gradle refuses that (first CI run, M1) |
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
`.deb` / `.rpm` / `.tar.gz` / `.AppImage`) and CPU architecture (an asset name with `x64` / `amd64` / `x86_64` or
`arm64` / `aarch64` counts only for that architecture, one without counts for any); the release pipeline below
attaches such assets to a release only when the maintainer asks for it.

CI: `.github/workflows/build_desktop.yml` (manual, or a `desktop-v*` tag) packages on Ubuntu, Windows
and macOS runners and uploads the installers as workflow artifacts (on Linux it also runs `:desktopApp:test`,
`:core:quickjs:desktopTest` and `:source-js:desktopTest` first). On a pull request into `multiplatform` the same
workflow runs only its `pr-check` job (`:desktopApp:compileKotlin`, `:desktopApp:test`, then
`:core:quickjs:desktopTest :source-js:desktopTest`), which, while the port is experimental, does not fail the PR check
(`continue-on-error`); `build_pull_request.yml` is `master`'s, unchanged.

**Testing on a Mac** (getting the CI `.dmg` or building it, opening the unsigned app, data and logs, a first test
pass, what to send back): [`TESTING_MAC.md`](TESTING_MAC.md).

**Installing on Linux:** `.deb`: `sudo apt install ./Komikku-<v>-linux-x64.deb`. The `.rpm` is **unsigned**.
`sudo dnf install ./Komikku-<v>-linux-x64.rpm` installs it as it is (a local file; Fedora 45 in the smoke job, which
also resolves the dependencies), but Fedora's plain `rpm` refuses it ("package komikku-<v>-1.x86_64 does not verify: no
signature"): use `sudo rpm -i --nosignature ./Komikku-<v>-linux-x64.rpm` (upgrade: `sudo rpm -U --nosignature …`).
Either way nothing checks a signature: skipping the check
means nothing proves that the package was built by Komikku and not changed since, so compare its SHA-256 with the
release's checksum file first. The signing hook is built but off until the maintainer's key is set ("Signing", Linux);
a signed RPM installs after `sudo rpm --import Komikku-<v>-linux-x64-rpm-signing-key.asc` without either flag. The
`.deb` needs nothing: `apt` does not check signatures of a local `.deb` file. The `.tar.gz` needs no installation:
unpack it and start `Komikku/bin/Komikku`. The same text for users: the release notes template
(`.github/scripts/desktop_release_notes.md`) and [`TESTING_LINUX.md`](TESTING_LINUX.md).

## Release pipeline

`build_desktop.yml` (P9, [`PROGRESS.md`](PROGRESS.md#p9--desktop-release-pipeline-and-signing-hooks)) — first run
in M1 ([`PROGRESS.md`](PROGRESS.md#m1--first-macos-ci-build)), artifacts only; the release job has never run.
**Temporary bring-up trigger:** while the port is on `multiplatform`, a push to that branch that
changes `.github/desktop-build-trigger` (bump the counter in it) starts a run with release mode `none` (a
dispatch-only workflow off the default branch cannot be started; edits of the workflow alone do not start one); remove
the trigger and the marker file when the port reaches master.

- **Jobs:** `plan` (`contents: read`: decides the mode and tag and, in a release mode, checks that the release tag
  points to the run's commit), `package` (`contents: read`; one job per runner: `ubuntu-24.04` x64, `windows-2022`
  x64, `macos-15` arm64, `macos-15-intel` x64; the packaged app's self-test with the bundled fixture extension only),
  `extension-check` (`contents: read`, no secrets, no checkout; per runner, unpacks the installers `package` uploaded
  and runs the self-test with the Keiyoushi extensions pinned by commit and SHA-256 in
  `.github/desktop-test-extensions.txt`, the only place third-party extension code runs; never gates a release; bump the
  list with `.github/scripts/desktop_test_extensions.sh pin .github/desktop-test-extensions.txt` in a reviewed commit),
  `extension-breadth` (non-blocking, `contents: read`, no secrets; on the weekly schedule once the workflow is on the
  default branch, and on this branch's bring-up runs: the link checker inside the packaged Linux app over every APK
  of Keiyoushi's live index, a static check; summary in the job summary, full report in the artifact
  `desktop-extension-breadth-<sha>`), `release` (`contents: write`; needs `plan` and `package`; only when the mode is
  not `none`).
- **Non-blocking jobs** (`continue-on-error`; `release` never depends on them): `windows-test` runs `:desktopApp:test`
  on `windows-2022` and uploads the test report when it fails; `smoke` installs the `package` job's `.deb` / `.msi` on
  `ubuntu-24.04` (Xvfb + openbox) and `windows-2022`, starts the installed app on a fresh data folder, takes
  screenshots, switches the tabs with Ctrl+1…5, closes it and checks a clean exit and the log
  (`.github/scripts/desktop_smoke_linux.sh` / `desktop_smoke_windows.ps1`; how to read its artifact
  `desktop-smoke-<os>-X64-<sha>`: the comment above the job in the workflow).
- **Concurrency:** no workflow-level group. A newer run of the same ref cancels an older run's `package` job per runner
  (that older run then ends cancelled); the `release` job has its own group per release tag that never cancels, so an
  upload in progress always finishes and two runs for one tag upload one after the other (of several runs waiting for
  that group, GitHub keeps only the newest).
- **Build:** `./gradlew :desktopApp:packageDistributionForCurrentOS -Penable-updater` (the update check on, stable
  channel), after the tests on Linux. The version is the `desktopVersionName=<v>` line of
  `./gradlew -q :desktopApp:printVersionName` (the full Android `versionName`); it must be `x.y.z` without a suffix
  (a `-rc1` style name fails before the build: the packages would use the part before `-`, the asset names and the
  update check the whole name), and with a release mode the release tag must be `v<that version>`, checked before the
  build.
- **Release commit:** in a release mode the release tag must point to the commit the run builds (`plan` fetches the tag
  and compares `refs/tags/<tag>^{commit}` with the run's commit), because master keeps the released `versionName`
  until the next bump. Manual runs: "Use workflow from" the tag `v<x>` (or a branch whose head is that commit). Tag
  `desktop-v<x>`: it must be on the same commit as `v<x>`, otherwise the run fails with both commits named.
- **Asset names** (`.github/scripts/desktop_release_assets.sh`, the same on a developer machine:
  `.github/scripts/desktop_release_assets.sh desktopApp/build/compose/binaries/main <version> <windows|macos|linux> <x64|arm64> <out dir>`):

  | Runner | Assets |
  |--------|--------|
  | Linux x64 | `Komikku-<v>-linux-x64.deb`, `Komikku-<v>-linux-x64.rpm`, `Komikku-<v>-linux-x64.tar.gz` (the app image directory, packed: one folder with `bin/` and `lib/`; no `.AppImage` file, that needs `appimagetool`) |
  | Windows x64 | `Komikku-<v>-windows-x64.msi`, `Komikku-<v>-windows-x64.exe` |
  | macOS arm64 / x64 | `Komikku-<v>-macos-arm64.dmg` / `Komikku-<v>-macos-x64.dmg` |

  plus `Komikku-<v>-linux-x64-rpm-signing-key.asc` when the RPM is signed ("Signing", Linux; never without the
  secret), plus `Komikku-<v>-<os>-<arch>-SHA256SUMS.txt` per job; the release gets one `Komikku-<v>-desktop-SHA256SUMS.txt`
  (`sha256sum -c` format; every line `<hash>  <name>`, also where Git Bash writes `<hash> *<name>`, sorted by name). `DesktopAppUpdateCheckerTest` ("the release pipeline's asset names …") checks that the update
  check picks exactly these per OS and architecture next to the Android APKs. The Windows / macOS app-image folders
  are no longer uploaded (the installers contain them).
- **Workflow artifacts** always: `Komikku-desktop-<os>-<ARCH>-<sha>` with the assets of one job.
- **Release, only on request** (`release_mode`):

  | Trigger | Mode from | Release tag |
  |---------|-----------|-------------|
  | Run workflow (manual) | input `release_mode`: `none` (default), `existing`, `draft` | input `release_tag` (`v<versionName>`) |
  | Tag `desktop-v<versionName>` | repository variable `DESKTOP_RELEASE_MODE` (unset = `none`) | `v<versionName>` |

  The upload is `.github/scripts/desktop_release_upload.sh` (with `gh`). The release is looked up in the API's list of
  releases (drafts included); a failing request fails the job and never counts as "no release", and more than one
  release with the tag fails too. `existing`: the release must exist (draft or published). `draft`: the draft with that
  tag gets the files; only if the tag has no release at all is one created **as a draft** (a published release fails),
  with the notes of `.github/scripts/desktop_release_notes.md` (the version filled in; its RPM paragraph says
  "unsigned" with the `dnf` / `rpm` commands, or "signed" when the assets have the signing key). The notes of an
  existing release are never touched: paste that text into them by hand.
  A draft gets every file (same-named desktop files replaced). A published release only gets the files it is missing;
  a file it already has is compared by SHA-256 (the asset's `digest`, or the downloaded file when the API gives none):
  identical files are skipped, a different one fails the job before anything is uploaded. The checksum file goes last.
  **Resuming a partly failed upload:** "Re-run failed jobs" on the same run (it reuses the run's artifacts, so the
  uploaded files are identical and skipped); a new run rebuilds the installers, which are not byte-identical, so it
  stops at the first desktop file already on the published release (delete the partial desktop files by hand first).
  The workflow never publishes, edits or deletes a release and only uploads files named `Komikku-<version>-…`, which
  no Android asset is named like (`Komikku-v<x>.apk`, `Komikku-<abi>-v<x>.apk`).
- **Which release:** the existing `v<versionName>` release of `komikku-app/komikku`, the one P6's stable check reads
  and compares with the app's version. The usual order: push `v<x>` → the Android release workflow creates its draft
  → run the desktop builder from the tag `v<x>` with `release_mode: existing`, `release_tag: v<x>` → check the draft →
  publish it by hand. The preview channel (`komikku-app/komikku-preview`, `r<n>`, `-Pdesktop-preview`) is not covered:
  it is another repository (a token with access to it would be needed).

## Signing

Off unless set: a build without these settings is exactly the unsigned one (macOS: Gatekeeper warns, open with
right-click → Open; Windows: SmartScreen warns; Linux: the `.rpm` is unsigned, which `dnf` installs as it is and
Fedora's plain rpm refuses without `--nosignature`, see "Installing on Linux" under "Package"). The `.deb` is never
signed (below). Nothing
secret is in the repository.

**macOS** (Compose plugin `macOS { signing { … }; notarization { … } }`, configured in `desktopApp/build.gradle.kts`
from these Gradle properties or environment variables; blank = not set):

| Gradle property | Environment variable | Contents |
|-----------------|----------------------|----------|
| `komikku.desktop.macSigningIdentity` | `KOMIKKU_MAC_SIGNING_IDENTITY` | the Developer ID Application identity: `Developer ID Application: <Name> (<TEAMID>)`, or `<Name> (<TEAMID>)` (the plugin adds the prefix) |
| `komikku.desktop.macSigningKeychain` | `KOMIKKU_MAC_SIGNING_KEYCHAIN` | optional: the keychain holding it (CI sets it to its temporary keychain) |
| `komikku.desktop.macNotarizationAppleId` | `KOMIKKU_MAC_NOTARIZATION_APPLE_ID` | the Apple ID (e-mail) of the developer account |
| `komikku.desktop.macNotarizationPassword` | `KOMIKKU_MAC_NOTARIZATION_PASSWORD` | an app-specific password of that Apple ID |
| `komikku.desktop.macNotarizationTeamId` | `KOMIKKU_MAC_NOTARIZATION_TEAM_ID` | the 10-character team ID |

Notarization needs all three of its settings and signing; anything else fails at configuration with a message.
Locally on a Mac with the certificate in the login keychain:
`./gradlew :desktopApp:packageDmg :desktopApp:notarizeDmg -Pkomikku.desktop.macSigningIdentity="…" …` (the Compose
task submits with `notarytool`, waits and staples).

**Repository secrets** for `build_desktop.yml` (Settings → Secrets and variables → Actions; each part runs only when
its secrets are all set):

| Secret | Contents | How to make it |
|--------|----------|----------------|
| `MACOS_CERTIFICATE_P12_BASE64` | the "Developer ID Application" certificate **with its private key**, `.p12`, base64 | Apple Developer account (paid) → Certificates → "Developer ID Application" (create a CSR in Keychain Access → Certificate Assistant); install it, export certificate + key from Keychain Access as `.p12` with a password; `base64 -i cert.p12 \| pbcopy` |
| `MACOS_CERTIFICATE_PASSWORD` | the `.p12` export password | chosen at export |
| `MACOS_SIGNING_IDENTITY` | as `KOMIKKU_MAC_SIGNING_IDENTITY` above | `security find-identity -v -p codesigning` shows it |
| `MACOS_NOTARIZATION_APPLE_ID` | the Apple ID e-mail | — |
| `MACOS_NOTARIZATION_PASSWORD` | an app-specific password | account.apple.com → Sign-In and Security → App-Specific Passwords |
| `MACOS_NOTARIZATION_TEAM_ID` | the team ID | developer.apple.com → Account → Membership details |
| `WINDOWS_CERTIFICATE_PFX_BASE64` | an exportable code-signing certificate with its private key, `.pfx`, base64 | from a code-signing CA (OV / EV); `base64 -w0 cert.pfx` |
| `WINDOWS_CERTIFICATE_PASSWORD` | the `.pfx` password | chosen at export |
| `RPM_SIGNING_KEY` | the OpenPGP secret key that signs the `.rpm`, ASCII-armoured (one primary key; a signing subkey is used if it has one) | `gpg --quick-gen-key "Komikku <…>" rsa4096 sign 3y` (or an existing key), `gpg --armor --export-secret-keys <fingerprint>`; keep a backup outside GitHub: the secret cannot be read back |
| `RPM_SIGNING_KEY_PASSPHRASE` | its passphrase (optional: unset for a key without one) | chosen when the key was made |

Optional repository variables: `WINDOWS_TIMESTAMP_URL` (RFC 3161 timestamp server, default
`http://timestamp.digicert.com`), `DESKTOP_RELEASE_MODE` (see "Release pipeline").

**Windows:** the Compose plugin has no Windows signing configuration, so the workflow signs the finished MSI and EXE
with `signtool` (Windows SDK on the runner: SHA-256, RFC 3161 timestamp, then `signtool verify /pa`) before the
checksums are taken; the app's own launcher inside them stays unsigned. Since 2023 CAs issue new OV / EV code-signing
keys only on hardware tokens or cloud HSMs, so an exportable `.pfx` may not be obtainable: with Azure Trusted Signing,
SignPath or a similar service that step has to be replaced by that service's action (not done; no account exists).

Unverified: none of this has run (no certificates exist); in particular whether the notary service accepts the bundled
runtime and the native libraries inside the JARs as the Compose plugin signs them (it signs them itself; Compose's
default entitlements for the JVM are used).

**The macOS signing key in CI (untested):** the workflow imports the `.p12` into a temporary keychain with
`security import … -T /usr/bin/codesign` and `security set-key-partition-list -S apple-tool:,apple:,codesign:`, so only
`codesign` may use the private key (never `-A`, which would let every process on the runner use it). The list comes from
the Compose plugin 1.12.2 code: `MacSigner` signs with `/usr/bin/codesign` and reads the certificate with
`/usr/bin/security find-certificate` (no key access); jpackage's app-image signing also calls `codesign`; notarization
(`xcrun notarytool` with the app-specific password, `stapler`) and the DMG tools (`hdiutil`, `osascript`) do not use
the key; no `.pkg` is built.

**Checklist for the first signed build** (the first run with the macOS secrets set):

1. The `Import macOS signing certificate` step passes and `security find-identity -v -p codesigning "$keychain"` (add it
   temporarily if needed) lists the identity.
2. `Package desktop app` signs without a key-access error. If a tool that is not in the `-T` list uses the key, the run
   cannot show the keychain's permission dialog and that tool fails, typically with `errSecInternalComponent` or
   `User interaction is not allowed.` next to the tool's name. Fix: add `-T <full path of that tool>` to the
   `security import` line in `build_desktop.yml` (and its name to the comment above it); never `-A`.
3. `codesign --verify --deep --strict --verbose=2` on the `.app` inside the `.dmg` passes, `spctl -a -vv` says
   `source=Notarized Developer ID` after notarization, and `xcrun stapler validate` passes (the workflow runs it).
4. Then remove "untested" here.

**Linux (RPM, ATLAS_INSTRUCTIONS.md ANSWER 018):** with `RPM_SIGNING_KEY` set, the `package` job's step `Sign RPM`
runs `.github/scripts/desktop_rpm_sign.sh` after the packaged app's self-test (the last step that runs project code)
and before the checksums are taken. It imports the key into a keyring of its own (`$RUNNER_TEMP/komikku-rpm-gnupg`,
owner-only, deleted when the script ends, also on failure, and again by the `always()` step `Remove RPM signing
keyring`), signs the RPM in place with `rpmsign --addsign` (SHA-256; the passphrase through gpg's loopback pinentry from
a file in that keyring folder, never on a command line), then checks it with `rpm -K` against a temporary rpm database
holding only that public key ("digests signatures OK" or the job fails). The public key becomes the asset
`Komikku-<v>-linux-x64-rpm-signing-key.asc` (in the checksum file). Without the secret the step is skipped and the
assets are the unsigned build's, byte for byte. **Proven in CI with a throwaway key:** the `smoke` job (non-blocking,
`desktop_smoke_rpm.sh`) generates a key on the runner (never a real one, no secret), signs a copy of the run's RPM with
the same script, imports the public key in the pinned Fedora container, checks `rpm -K`, installs and erases it without
`--nosignature`, and checks that a tampered copy fails `rpm -K`; each is its own row in the job summary. Untested with
the maintainer's key: the first signed release should show "digests signatures OK" in `Sign RPM` and
`rpm -K Komikku-<v>-linux-x64.rpm` on a Fedora machine after `rpm --import` of the attached key.

**The `.deb` and a future apt repository:** `apt install ./file.deb` does not check signatures (apt checks repository
metadata, not single packages), so the `.deb` stays unsigned with no warning. A future apt repository (a `sources.list`
entry for automatic updates) would need its `Release` file signed with a key the users import; not planned, no work
now.

## What CI downloads and runs

What the desktop jobs in `build_desktop.yml` fetch from outside the repository, and how each is pinned:

| What | Where | Pinned by | Verified by |
|------|-------|-----------|-------------|
| GitHub Actions (`checkout`, `setup-java`, `setup-gradle`, `wrapper-validation`, `upload-artifact`, `download-artifact`) | every job | full commit SHA (the tag in a comment) | the commit SHA itself |
| Runner images | every job | label (`ubuntu-24.04`, `windows-2022`, `macos-15`, `macos-15-intel`); GitHub updates the image under a label | — |
| JDK (Temurin) | `pr-check`, `package`, `windows-test` | major version from `.github/.java-version` (21); the patch release is the runner's cached one, or the latest when not cached | `setup-java` `verify-signature: true`: Adoptium's GPG signature, only when it downloads (the runner's tool cache is used as is) |
| Gradle wrapper JAR | every Gradle job | `gradle/wrapper/gradle-wrapper.jar` in the repository | `gradle/actions/wrapper-validation` (`package`) and `setup-gradle`'s `validate-wrappers` (default on): checksum against Gradle's published ones |
| Gradle distribution | every Gradle job | version in `gradle/wrapper/gradle-wrapper.properties` (9.3.1) | **not verified**: the file has no `distributionSha256Sum` (it is the Android build's file; adding it is a change for master) |
| Maven dependencies and Gradle plugins | every Gradle job | versions in the version catalogs (`gradle/*.versions.toml`) | **not verified**: no `gradle/verification-metadata.xml` (shared with the Android build) |
| WiX 3.11.2 (`wix311-binaries.zip`) | `package` on Windows | version and URL in the step `Download WiX (pinned)` | SHA-256 `2c1888d5…9e2e` in that step; a mismatch fails the job before anything is unpacked; the Compose plugin gets the folder through `WIX_PATH` and downloads nothing |
| `rpm` (`rpmbuild`; `rpmsign`, `rpmkeys` for the signing hook) | `package` on Linux; `smoke` on Linux (the throwaway-key signing) | `apt-get` from Ubuntu's archive, unversioned | the archive's signatures (apt) |
| `gpg` (signs for `rpmsign`) | `package` on Linux (only with `RPM_SIGNING_KEY`), `smoke` on Linux | the runner image's | the runner image |
| Smoke test tools (Xvfb, openbox, xdotool, …) | `smoke` on Linux | `apt-get`, unversioned | the archive's signatures (apt) |
| Fedora container image (`fedora:45`) | `smoke` on Linux (RPM test, `desktop_smoke_rpm.sh`) | image digest in that script | the digest itself (docker) |
| The RPM's dependencies and `rpmrebuild` | `smoke` on Linux, inside that container | `dnf` from Fedora's repositories, unversioned | the repositories' signatures (dnf) |
| Keiyoushi extension APKs and `index.pb` | `extension-check` (no secrets) | repository commit in `.github/desktop-test-extensions.txt` | SHA-256 per APK in that file, before anything is loaded |
| Keiyoushi live index and every APK in it | `extension-breadth` (no secrets, non-blocking) | not pinned (it checks the current catalogue) | not verified; only read by the link checker, never run, never in a job with secrets or a shipped artifact |

No `appimagetool` or other packaging tool is downloaded: the Linux `.tar.gz` is the app image directory packed with
`tar`, `.deb` / `.rpm` come from jpackage with the runner's `dpkg-deb` / `rpmbuild`, the `.dmg` from the macOS system
tools, `signtool` from the Windows SDK on the runner image.

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
changeable in Settings), `cache/` (covers, chapter pages), `files/`, `keys/` (CBZ encryption key),
`instance.lock` (empty; the running app holds an exclusive lock on it so that a second start on the same data folder
shows the first one's window instead of opening the database twice) and `instance.port` (written by that running app:
the loopback port and token a second start uses to ask it to show its window; removed when it quits).

## Extension link check

Lists the Android symbols extensions reference that the desktop host lacks, anywhere in their code (every method body,
not only what creating the sources touches), and optionally verifies every class, creates the sources and lists the
compat classes they use. One tool, two class paths:

- `:desktopApp:extensionLinkCheck` checks against the desktop app's class path, the host extensions really run on.
  Use it for measurements; `--create-sources` works only here (the sources resolve the app's Injekt graph, set up over
  a data folder in `--work`).
- `:desktop:extension-runtime:linkCheck` checks against the runtime module's class path (no app libraries such as
  `okhttp-zstd`, no source creation).

```bash
# Local files: APKs, converted jars or folders of them
./gradlew :desktopApp:extensionLinkCheck --args="path/to/ext.apk path/to/dir --output=build/linkcheck.txt"

# A whole store index, one APK at a time (downloaded into --work, checked, deleted), in batches of 250
commit=$(git ls-remote https://github.com/keiyoushi/extensions.git refs/heads/repo | cut -f 1)
./gradlew :desktopApp:extensionLinkCheck --args="--index=https://raw.githubusercontent.com/keiyoushi/extensions/$commit/index.pb \
  --from=0 --count=250 --all-checks --work=/tmp/linkcheck --output=build/linkcheck-0.txt"

# One summary (Markdown) of several reports
./gradlew :desktopApp:extensionLinkCheck --args="--summarize build/linkcheck-0.txt build/linkcheck-250.txt --summary=build/summary.md"
```

The packaged app runs it too: `java -cp 'Komikku/lib/app/*' app.komikku.desktop.extension.linkcheck.LinkCheckMainKt …`
(the scheduled breadth job in `build_desktop.yml`). `--help`, or any unknown option, prints the usage.

| Option | Effect |
|---|---|
| `--index=<url or file>` | every APK of a store index (Keiyoushi `index.pb`, gzip or not; Tachiyomi `index.min.json`) |
| `--from=<n>` / `--count=<n>` | a slice of the inputs (batches) |
| `--work=<dir>` | downloads, converted jars and the run's data folder (default: a temporary folder, deleted) |
| `--verify` | load and link every class (the JVM verifier): `Verification: <n> verified, <n> rejected, <n> unresolved` |
| `--create-sources` | instantiate the sources like the extension manager: `Sources: <n> created`, `Sources: unsupported on desktop: needs <member>`, `Sources: failed: <cause>` |
| `--compat-usage` | `Compat classes used: …` (the counts in `desktop/android-compat/COMPAT_REGISTRY.md`) |
| `--all-checks` | the three above |
| `--output=<file>` | the report, written as it goes |
| `--summary=<file>` | a Markdown summary: how many convert, resolve, verify, create sources; per missing `android.*` class and member, how many extensions reference it and how many of those do not create their sources; compat class usage |
| `--summarize` | the inputs are earlier reports: write only their summary |
| `--strict` | exit code 1 when a symbol is missing |

The report keeps one block per extension, `== <apk>: <n> classes checked` (or `== <apk>: failed: <error>`), then the
missing classes, methods and fields (`  <symbol>  <- <users>`), then the lines of the enabled checks; the breadth job's
`desktop_extension_breadth.py` reads the same blocks. Results of the last full run: `EXTENSION_COMPAT.md`.

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

**Automatic solving (P8), for users:** Settings → Advanced (Browse before P12) → "Cloudflare solver (FlareSolverr)". Enter the address of a
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

## Logs

The desktop log goes to stderr and (P12, from `main`) to `<data folder>/logs/komikku.log` (at most two files of about
2 MB: `komikku.log` and the previous `komikku.1.log`), uncaught exceptions included. Settings → Advanced → "Open the
log folder" opens it in the file manager. The level follows SY's "Log level" in the same screen (minimal = info, extra
= debug, extreme = verbose, applied at once); the system property `komikku.log.level` (verbose, debug, info, warn,
error) overrides it when the app's JVM is started with it (`-Dkomikku.log.level=debug`).

## Known limitations

- The desktop workflow has built installers in CI (M1: macOS arm64 `.dmg` and Windows `.msi` / `.exe`; Linux and
  macOS Intel see [`PROGRESS.md`](PROGRESS.md#m1--first-macos-ci-build)), but no installer has been installed or
  started yet: icons inside them, first start, file associations and upgrade behaviour are unverified. The release
  job has never run.
- Signing / notarization only as hooks (never run, no certificates), no in-app update download / install (the update check only opens the release page), no
  file associations, no embedded browser (Cloudflare checks by hand or through a FlareSolverr-compatible service you
  run, logins by hand; see above).
- The parity gaps with Android (features the desktop app does not have yet) are listed in
  [`PROGRESS.md`](PROGRESS.md#remaining-parity-gaps).
- Only Linux has run the test suite; Windows / macOS specifics (paths, file dialogs, key handling) are
  untested.
