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

CI: `.github/workflows/build_desktop.yml` (manual, or a `desktop-v*` tag) packages on Ubuntu, Windows
and macOS runners and uploads the installers as workflow artifacts. The PR workflow has a separate
`desktop` job (`:desktopApp:compileKotlin` + `:desktopApp:test`) that cannot block the Android build and,
while the port is experimental, does not fail the PR check (`continue-on-error`).

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

## Known limitations

- Packaging and the desktop workflow have not been run yet: the configuration was checked with
  `:desktopApp:tasks --all`, a dry run (`-m`) of `packageDistributionForCurrentOS`, `createRuntimeImage`
  (jlink with the chosen modules) and YAML parsing only. Installers, icons inside them, file associations
  and upgrade behaviour are unverified.
- No signing / notarization, no auto-update, no file associations.
- The parity gaps with Android (features the desktop app does not have yet) are listed in
  [`PROGRESS.md`](PROGRESS.md#remaining-parity-gaps).
- Only Linux has run the test suite; Windows / macOS specifics (paths, file dialogs, key handling) are
  untested.
