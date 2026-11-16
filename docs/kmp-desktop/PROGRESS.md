# Komikku Desktop — Progress Log

See [`PLAN.md`](PLAN.md). Update this file in the same commit as each step.

## Step status

| Step | Status | Commit | Notes |
|------|--------|--------|-------|
| 0.1 Rebase PR #1671 on master, CMP bump | done | `3fb80ae833` | Compose Multiplatform 1.12.2 for Kotlin 2.4.20 |
| 0.2 Compose plugin clash fix, Material 3 | done | `3ac6fd7abd` | `compose` catalog registered as `composeLibs`; default-package `Project.compose` accessor in buildSrc keeps every upstream `compose.*` reference unchanged; `org.jetbrains.compose` applied in `:desktopApp` (extension accessed by type), `compose.desktop.currentOs` + `nativeDistributions`; Material 3 demo UI (CMP material3 1.9.0) |
| 0.3 Remove `NetworkClient` / `:core:network` | done | `b42ef9f3bf` | Module, settings include and `:desktopApp` usage removed; it had no catalog entries of its own. The real network stack (`eu.kanade.tachiyomi.network`) gets a desktop target in Phase 1 |
| 0.4 File-backed desktop preference store | done | `b04aa953a0` | `DesktopPreferenceStore(file)` / `DesktopPreferenceStore(name)` → `<dataDir>/preferences/<name>.json`; typed JSON (`{"version":1,"values":{key:{"type","value"}}}`), `getAll()` returns Android-like typed values, batch `edit {}` for the future `SharedPreferences` shim, `changes()` from an in-memory `StateFlow` snapshot, writes serialized + temp file/fsync/atomic rename, corrupt file moved aside. `DesktopDirs` (D7, `komikku.dataDir` override). 22 unit tests in `:core:preference:desktopTest` |
| 0.5 Desktop target for `i18n-sy`, `i18n-kmk` | done | `24f65d1b10` | `jvm("desktop")` in both; desktop jars contain the moko bundles (`localization/tachiyomii18n{sy,kmk}_mokoBundle*.properties`). `:desktopApp` depends on all three i18n modules and shows one MR / SYMR / KMR string each via `StringResource.localized()` |
| 0.6 `mihon.library.kmp` convention plugin | done | see log | `buildSrc/src/main/kotlin/mihon.library.kmp.gradle.kts` + `mihon/buildlogic/KmpLibrary.kt`; `:core:preference` converted (sources stay in `src/commonMain/kotlin`, compiled through `jvmSharedMain`). Adoption mechanism verified with a throwaway (uncommitted) trial, see below |

## Files moved out of upstream paths

Record every file moved to `src/androidMain` (old path → new path) so upstream cherry-picks can be
remapped.

_None yet._

## Android compat classes

List classes added to `:desktop:android-compat` and which module/extension needed them.

_None yet._

## Deviations from the plan and open issues

- **0.2 — compose name clash.** Renaming only the `Project.compose` accessor in buildSrc is not
  enough: Gradle registers every version catalog as a project extension with the catalog's name, so
  `org.jetbrains.compose` fails with "Cannot add extension with name 'compose'". Fix: the catalog is
  registered as `composeLibs` (root and buildSrc `settings.gradle.kts`), and
  `buildSrc/src/main/kotlin/ComposeCatalogAccessor.kt` declares `val Project.compose` in the
  *default package*. Build scripts are compiled in the default package, so that declaration takes
  precedence over the generated (star-imported) accessors: upstream build files keep `compose.*`
  unchanged, and in modules that apply `org.jetbrains.compose` the JetBrains extension is accessed
  by type (`extensions.configure<ComposeExtension> { ... }`). Verified in `:desktopApp`, where
  `compose.bom` still resolves to the catalog.
- **0.2 — Material 3 version.** CMP publishes `org.jetbrains.compose.material3` on its own version
  line; `material3:1.9.0` is what CMP 1.12.2's own `compose.material3` alias points to, so the
  catalog pins that (`compose-multiplatform-material3`). Re-check when CMP is bumped.
- **0.2 — Native runtime.** `compose.desktop.currentOs` (`ComposePlugin.DesktopDependencies.currentOs`)
  is still host-specific; that is the standard Compose Desktop setup (packages are built on each
  OS, Phase 9). The hand-written OS detection is gone.
- **0.4 — `DesktopDirs` location.** It lives in `:core:preference` `desktopMain`
  (package `tachiyomi.core.common.desktop`) because that is the only desktop module with sources
  today; `core:common` re-exports `:core:preference` via `api`, so later desktop code sees it. It
  can move to `core:common`'s `desktopMain` in Phase 1 (KMK-owned file, no upstream impact).
- **0.4 — synchronous writes.** `Preference.set()` writes the file on the calling thread (small
  file, fsync + rename). Simple and durable; if it ever shows up on the UI thread, switch to a
  coalescing single-thread writer with a flush on shutdown.
- **0.4 — one instance per file.** Two `DesktopPreferenceStore` instances over the same file do not
  see each other's writes; DI must hand out a single instance per store name.
- **0.6 — source set names.** The intermediate source sets come from a custom hierarchy template
  group `jvmShared` (`applyDefaultHierarchyTemplate { common { group("jvmShared") { withAndroidTarget(); withJvm() } } }`),
  so they are named **`jvmSharedMain`** and **`jvmSharedTest`** (not `jvmShared`). This avoids the
  "explicit dependsOn" hierarchy warning and gives tests a shared set for free.
- **0.6 — how a `com.android.library` + `kotlin("android")` module adopts the plugin.** Replace the
  two plugin lines with `id("mihon.library.kmp")`; nothing else moves:
  - `src/main/java`, `src/main/kotlin` (and `src/commonMain/kotlin` for modules that are already
    KMP) → `jvmSharedMain`; `src/test/{java,kotlin}` → `jvmSharedTest`, so existing unit tests run
    on Android (`testReleaseUnitTest`) and desktop (`desktopTest`).
  - The Android `main`/`test` source sets keep `src/main/AndroidManifest.xml`, `res`, `assets` and
    `resources`; their java/kotlin dirs are re-pointed to `src/androidMain/...` /
    `src/androidUnitTest/...` because KGP otherwise feeds `src/main/java` and the "Android style"
    `src/main/kotlin` into `androidMain` too (duplicate compilation). The manifest has to be set
    *after* `androidTarget()`, which points it to `src/androidMain/AndroidManifest.xml`.
  - Dependencies declared directly on top-level `api`/`implementation`/`compileOnly`/`runtimeOnly`/
    `test*` are mirrored (lazily, `addAllLater`) to the `jvmShared*` configurations, so existing
    `dependencies {}` blocks keep working for both targets. `extendsFrom` is not usable (top-level
    `implementation` extends `androidMainImplementation`, which would leak Android-only deps), and
    `dependencies.configureEach` misses version-catalog deps (they are added lazily).
    **Android-only AARs must move to `kotlin.sourceSets.androidMain.dependencies {}`** in the
    module's build file (KMK-marked) — that is the main build-file edit per converted module.
  - Only Kotlin is shared; `.java` sources would not be compiled (none exist in the candidate modules).
- **0.6 — trial (not committed).** Converted `core-metadata` (src/main/java), `domain`
  (src/main/java + src/test/java), `core:common` (src/main/kotlin) and `presentation-core` (res +
  Compose) with the plugin: all `compileDebugKotlinAndroid` passed, `:domain:testReleaseUnitTest`
  passed (tests via `jvmSharedTest`), and `:app:compileDebugKotlin` passed against them (resource
  merge / R class intact). A scratch module checked the rest: desktop + Android compile, the same
  tests on `desktopTest` and `testDebugUnitTest`, `src/main/resources` in the desktop jar and the
  AAR, manifest (with a permission) and `res` in the AAR, each class compiled once,
  `compileJvmSharedMainKotlinMetadata` OK, and a top-level OkHttp dep + `androidMain`-only
  `androidx.core` dep resolving correctly per target.
- **0.6 — plan correction (D2 option 3).** Shared code cannot call same-module declarations that
  live in `androidMain`/`desktopMain` (K2 source set visibility; confirmed in the trial: "Unresolved
  reference"). Same-package `androidMain` + `desktopMain` files only work when the callers are in
  *other* modules (verified: a second module's `jvmSharedMain` called them fine) or are themselves
  platform code. Within one module a split symbol used by shared code needs `expect`/`actual` (or an
  interface + DI). This matters for Phase 1.3 (`ImageUtil`, `WebViewUtil`, … used from shared
  `core:common` code).
- **0.6 — not done yet.** The "BOM wins" Compose constraint (D6) is not in the plugin; it belongs
  with the first Compose module (Phase 4). `kotlin.mpp.androidSourceSetLayoutVersion=2` in
  `gradle.properties` now triggers a KGP deprecation warning (pre-existing property). AGP 9 drops
  `com.android.library` + `kotlin("multiplatform")` in favour of
  `com.android.kotlin.multiplatform.library`; the plugin will need a rewrite at that point.
