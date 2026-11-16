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
| 0.6 `mihon.library.kmp` convention plugin | done | `d4ffd9fb45` | `buildSrc/src/main/kotlin/mihon.library.kmp.gradle.kts` + `mihon/buildlogic/KmpLibrary.kt`; `:core:preference` converted (sources stay in `src/commonMain/kotlin`, compiled through `jvmSharedMain`). Adoption mechanism verified with a throwaway (uncommitted) trial, see below |
| 1.1 `:desktop:android-compat` | done | `beac8001db` | Desktop-only `kotlin("jvm")` module, written from scratch (Apache-2.0). First batch of Android / AndroidX / UniFile FQNs for `core:common` (table below). 24 unit tests (`:desktop:android-compat:test`): Uri, Base64, SharedPreferences, CookieManager, Html, UniFile |
| 1.2 Convert `:core:common` | done | `58203c5f63` | `mihon.library.kmp`; Android-only AARs (preference-ktx, image-decoder, UniFile, libarchive, quickjs-android, xlog, exifinterface) → `androidMain.dependencies`; desktop: `api(:desktop:android-compat)`. Includes the file moves listed below (needed for the desktop target to compile at all) and the minimal `expect`/`actual` pairs (`CloudflareInterceptor`, `StringDesc.toString(Context)`). Logging: `logcat` is multiplatform (0.4, `logcat-jvm`), desktop backend `DesktopLogcatLogger`; `exh.log` unchanged on both platforms through an xLog shim in compat. Android ABI of `core:common` unchanged (javap diff, see below). Catalog: `serialization-xml-core-jvm` |
| 1.3 Desktop counterparts in `core:common` | done | `d7d402a411` | `desktopMain`: `ImageUtil` subset (same signatures for `isImage`, `findImageType`, `getExtensionFromMimeType`, `isAnimatedAndSupported`, `ImageType`; magic-byte detection for JPEG/PNG/GIF/WebP(+animated)/JXL/AVIF/HEIF in pure Kotlin, no native decoder), `DesktopStorageFolderProvider` (`<dataDir>/storage`), pass-through `CloudflareInterceptor` (1.2). `NetworkHelper`, `AndroidCookieJar`, `Requests`, `OkHttpExtensions`, rate-limit / user-agent / uncaught-exception interceptors and DoH stay shared, unchanged. `:core:common:desktopTest` (ImageUtil) |
| 1.4 Desktop JavaScript engine | done | `d41ac22579` | `app.cash.quickjs.QuickJs` in compat backed by QuickJS through `io.github.dokar3:quickjs-kt` 1.0.15 (Maven Central, bundled natives); `JavaScriptEngine` unchanged. Tests: `QuickJsTest` (compat) and `JavaScriptEngineTest` (`:core:common:desktopTest`) |
| 1.5 Desktop string helpers | done | `459f032743` | `Localize.kt` unchanged and working on desktop (1.2 `StringDesc.toString(Context)` expect → moko `localized()`); new context-free `stringResource` / `pluralStringResource` (same package, `desktopMain/…/DesktopLocalize.kt`) for desktop code. `LocalizeTest` (`:core:common:desktopTest`): plain, formatted (`\"` workaround), plurals, locale switch through `StringDesc.localeType` |
| 1.x `:desktopApp` on the real stack | done | see log | `:desktopApp` depends on `:core:common`; `DesktopServices` builds the compat `Application`, the main preference store (via `DesktopPreferenceStores`, shared with `SharedPreferences`) and `NetworkHelper`; `main()` installs `DesktopLogcatLogger` and shows the default user agent / HTTP cache dir. `DesktopNetworkTest` (`:desktopApp:test`, OkHttp `mockwebserver3`, no real network): `GET` + `awaitSuccess` through `NetworkHelper.client`, cookie round trip through `AndroidCookieJar` → desktop `CookieManager`, default user agent, cache dir, `HttpException`, Cloudflare pass-through. Test JVMs get `komikku.dataDir` = `desktopApp/build/tmp/test-data` |

## Files moved out of upstream paths

Record every file moved to `src/androidMain` (old path → new path) so upstream cherry-picks can be
remapped. All moves are pure `git mv` (same package, same file name, so the same JVM class names on
Android); content edits are listed in the last column.

| Old path (upstream) | New path | Step | Edits / desktop counterpart |
|---------------------|----------|------|-----------------------------|
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt` | 1.2 | `actual class … actual constructor` (KMK-marked). `expect` in `src/jvmSharedMain/kotlin/…/CloudflareInterceptor.kt`; desktop `actual` (pass-through, logs challenges) in `src/desktopMain` |
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt` | 1.2 | none; no desktop counterpart (only used by the Android `CloudflareInterceptor`) |
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt` | 1.2 | none; callers are Android-only (`app`, `WebViewInterceptor`) |
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/DeviceUtil.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/util/system/DeviceUtil.kt` | 1.2 | none; callers are Android-only |
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/GLUtil.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/util/system/GLUtil.kt` | 1.2 | none (EGL is part of `android.jar`); callers are `ImageUtil` (Android) and `app` |
| `core/common/src/main/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt` | `core/common/src/androidMain/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt` | 1.2 | none; desktop subset (`src/desktopMain/…/ImageUtil.kt`, 1.3) for the members other modules' shared code calls (`core:archive` `CbzCrypto` uses `isImage`) |
| `core/common/src/main/kotlin/tachiyomi/core/common/preference/AndroidPreference.kt` | `core/common/src/androidMain/kotlin/tachiyomi/core/common/preference/AndroidPreference.kt` | 1.2 | none; desktop uses `DesktopPreferenceStore` (`:core:preference`) |
| `core/common/src/main/kotlin/tachiyomi/core/common/preference/AndroidPreferenceStore.kt` | `core/common/src/androidMain/kotlin/tachiyomi/core/common/preference/AndroidPreferenceStore.kt` | 1.2 | none; same |
| `core/common/src/main/kotlin/tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt` | `core/common/src/androidMain/kotlin/tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt` | 1.2 | none; `DesktopStorageFolderProvider` (`src/desktopMain`, 1.3) |

New KMK-owned shared files (no upstream counterpart) live in `core/common/src/jvmSharedMain/kotlin`
so `src/main/kotlin` keeps upstream's file list.

## Android compat classes

List classes added to `:desktop:android-compat` and which module/extension needed them.
Desktop behaviour is documented in each class's KDoc; unsupported members throw
`UnsupportedOperationException("android-compat: …")`. KMK helpers live in `app.komikku.desktop.compat`.

| Class (FQN) | Step | Needed by | Desktop behaviour |
|-------------|------|-----------|-------------------|
| `android.content.Context`, `ContextWrapper`, `android.app.Application` | 1.1 | `core:common` (`NetworkHelper`, `DiskUtil`, `Localize`, …), extensions | `Application(dataDir)` → `DesktopContext`: `files/`, `cache/` (also external cache), `databases/`, `app_<name>/`, `preferences/` under the desktop data dir |
| `android.content.ContentResolver` | 1.1 | `UniFileTempFileManager` | `file:` URIs only (`openInputStream` / `openOutputStream` / `getType`) |
| `android.content.SharedPreferences` | 1.1 | `core:common` (`EHLogLevel`), extensions | `DesktopSharedPreferences` over `DesktopPreferenceStore`; one store per file via `DesktopPreferenceStores` (shared with `PreferenceStore` users); `commit`/`apply` = one batch `edit {}`; listeners for changes made through it |
| `android.content.res.Resources`, `android.util.DisplayMetrics` | 1.1 | `DensityExtensions` (`dpToPx`) | `getSystem().displayMetrics`, density 1 (160 dpi) |
| `android.net.Uri` | 1.1 | `DiskUtil`, UniFile, extensions | Lenient parser (hierarchical / opaque / relative), Builder, encode/decode, query parameters (`+` → space in `getQueryParameter`), `fromFile`, `fromParts`, `withAppendedPath` |
| `android.util.Log` | 1.1 | `exh.log.Logging`, extensions | Prints through `DesktopLog` (stderr, `komikku.log.level` system property) |
| `android.util.Base64` | 1.1 | extensions | All flags (`NO_PADDING`, `NO_WRAP`, `CRLF`, `URL_SAFE`); lenient decoder like Android's |
| `android.os.Build` | 1.1 | `UniFileTempFileManager`, extensions | `SDK_INT` = 34, generic device fields |
| `android.os.SystemClock` | 1.1 | `RateLimitInterceptor` | JVM monotonic clock |
| `android.os.Looper`, `Handler` | 1.1 | `Context.mainExecutor`, extensions | One main looper: a daemon thread "android-compat-main" (not the AWT thread); `Runnable` posts only, no `Message` |
| `android.os.Environment`, `StatFs`, `FileUtils` | 1.1 | `DiskUtil`, `UniFileTempFileManager` | External storage = user home, always mounted; `StatFs` from `java.io.File` space queries |
| `android.media.MediaScannerConnection` | 1.1 | `DiskUtil` | No-op (callback gets a `null` uri) |
| `android.webkit.CookieManager`, `ValueCallback` | 1.1 | `AndroidCookieJar` (`NetworkHelper.cookieJar`) | `DesktopCookieManager`: RFC 6265 store with OkHttp parsing/matching; persistent cookies in `<dataDir>/cookies.json` (debounced write, `flush()`, shutdown hook) |
| `android.webkit.MimeTypeMap` | 1.1 | UniFile `type`, `ContentResolver.getType`, extensions | Built-in table (images, comic archives, documents, web, media) |
| `android.widget.Toast` | 1.1 | `ToastExtensions` (`context.toast`) | `show()` → `Toast.presenter` (logs by default; the desktop UI can replace it) |
| `android.text.Html`, `Spanned`, `androidx.core.text.parseAsHtml` | 1.1 | `StringExtensions.htmlDecode`, extensions | jsoup-based HTML → plain text (legacy/compact block separation), no spans |
| `androidx.core.content.ContextCompat` | 1.1 | `DiskUtil` | `getExternalFilesDirs`, `getExternalCacheDirs`, `getDataDir`, `getMainExecutor` |
| `androidx.preference.PreferenceManager` | 1.1 | `EHLogLevel` | `getDefaultSharedPreferences` = the main store (`preferences.json`) |
| `com.elvishew.xlog.XLog`, `Logger`, `LogLevel` | 1.2 | `exh.log` (`Logging.kt`, `ResettableLogger`, `EHNetworkLogging`) — public API exposes `Logger` | Prints through `DesktopLog` with the logger tag; no `init()` needed; borders / JSON formatting not reproduced |
| `app.cash.quickjs.QuickJs`, `QuickJsException` | 1.2, 1.4 | `JavaScriptEngine`, extensions | QuickJS via quickjs-kt: `create`, `evaluate(script[, fileName])`, `compile` / `execute`, `close`; results converted like quickjs-android (`Int` / `Double` / `String` / `Boolean` / `null` / `Array`); engine errors → `QuickJsException`. `set` / `get` (Java interface bindings) unsupported |
| `com.hippo.unifile.UniFile`, `FilenameFilter`, `UniRandomAccessFile`, `UriHandler` | 1.1 | `core:common` storage helpers, later `source-local`, downloads, backups | `java.io.File`-backed (`FileUniFile`); same nullability as the komikku-app fork; `fromUri` handles `file:` (+ registered handlers) |

## Deviations from the plan and open issues

- **1.1 — Kotlin, not Java, for the shims.** Classes are Kotlin with `@JvmStatic` / `@JvmField` /
  `const` so the JVM shape matches the Android SDK (static methods and fields, `getX()` getters
  from Kotlin properties, nested `Build.VERSION` etc.). Nullability follows the SDK / library
  annotations (checked with `javap` on `android-36/android.jar` and the UniFile AAR); members the
  SDK leaves unannotated are non-null returns and nullable parameters, the most lenient choice for
  Kotlin callers. Kotlin callers that used Java-style `getFoo()` calls on these types would not
  compile on desktop (none in `core:common`).
- **1.1 — extra dependencies of the compat module.** OkHttp (cookie parsing/matching), jsoup
  (`Html`), kotlinx-serialization-json (cookie file) and `:core:preference` (`api`, the
  `SharedPreferences` backing store). All of them are on the desktop runtime classpath anyway.
- **1.1 — UniFile `parentFile`.** Mirrors the library: only files reached through a parent
  (`createFile`, `listFiles`, `findFile`, …) know their parent; `fromFile` / `fromUri` give `null`.
- **1.2 — moves done in 1.2, not 1.3.** The desktop target cannot compile while the WebView /
  Bitmap / EGL / AndroidX-preference files are shared, so the moves (and the two `expect`/`actual`
  pairs shared code needs) landed with the conversion to keep every commit green on both targets.
  1.3 adds the functional desktop counterparts.
- **1.2 — `Localize.kt` unchanged.** Instead of splitting it, `src/jvmSharedMain` declares
  `internal expect fun StringDesc.toString(context: Context)`. On Android moko's `StringDesc` member
  of the same signature always wins, so `Localize.kt` compiles to exactly the same bytecode as
  before; on desktop the extension resolves to `localized()`.
- **1.2 — `api` instead of `compileOnly` for the compat module.** D3 suggested `compileOnly` +
  runtime from `:desktopApp`, like `android.jar`. `core:common`'s public API exposes compat types
  (`Context` in `NetworkHelper`, `UniFile`, xLog `Logger`), so every desktop consumer needs them at
  compile time anyway; `desktopMain` uses `api(projects.desktop.androidCompat)`, which never reaches
  Android.
- **1.2 — Android ABI check.** `javap -protected` of every class in
  `core/common/build/tmp/kotlin-classes/debug` before and after the conversion: identical except
  one added class, `tachiyomi.core.common.i18n.StringDescContext_androidKt` (the unused Android
  `actual` of the internal helper). No class was renamed or lost; moved files keep their file
  names, so file facades (`WebViewUtilKt`, …) keep their JVM names and no `@file:JvmName` was needed.
- **1.2 — `-Xexpect-actual-classes`** is enabled for `core:common` (expect/actual classes are Beta).
- **1.2 — xmlutil.** `xmlutil:core-android` only has an Android variant (it adds Android's parser on
  top of `core`), so switching the existing alias to `core` would silently drop Android's parser.
  The alias is kept; `serialization-xml-core-jvm` (`xmlutil:core`, JVM variant `core-jvmcommon`) is
  added for desktop source sets. Modules that use `kotlinx.bundles.serialization` (domain,
  core-metadata, source-local, app) must put `serialization-xml-core` in `androidMain` and
  `serialization-xml-core-jvm` in `desktopMain` when they are converted.
- **1.3 — desktop `ImageUtil` is a subset.** Same object / package, so `core:archive` and later
  modules compile against it on desktop, but only type detection is implemented. Bitmap work
  (split / merge / rotate / background colour) waits for the desktop reader (Phase 7, Skia);
  calling code that needs it must stay Android-only until then. Kept as a separate desktop object
  rather than an `expect` because no shared code inside `core:common` calls `ImageUtil`.
- **1.3 — default desktop storage folder** is `<dataDir>/storage` (not the home directory) so a
  portable install keeps everything together; users will be able to change it like on Android.
- **1.3 — files left shared on purpose** (compiled against the compat layer instead of moved):
  `DiskUtil`, `ToastExtensions`, `DensityExtensions`, `StringExtensions` (`htmlDecode`),
  `UniFileTempFileManager`, `UniFileExtensions`, `EHLogLevel`, `exh.log.*`, `JavaScriptEngine`.
- **1.4 — JavaScript engine choice.** `quickjs-kt` (Apache-2.0, Maven Central) runs the same engine
  as Android (QuickJS, ES2023), so extension scripts behave the same; it bundles natives for
  Windows x64, Linux x64/arm64 and macOS x64/arm64 (no Windows arm64) and loads them from the jar.
  Its API is suspend-based, so the shim blocks with `runBlocking` (callers already run it on IO).
  1.0.15 is used because 1.0.16+ require Kotlin stdlib 2.4.21 (the build is on 2.4.20). Fallback if
  natives become a problem: Mozilla Rhino (pure JVM, any OS, but incomplete ES2015+ support and
  slower). `QuickJs.set` / `get` (binding Java interfaces) are not implemented; no known extension
  needs them.
- **1.5 — context-free helpers are desktop-only.** Shared code keeps using
  `Context.stringResource(...)` (a compat `Context` is always available on desktop). The top-level
  `stringResource(...)` functions exist only in `desktopMain`; making them shared would need an
  Android implementation that finds a `Context` on its own (e.g. through Injekt), which is not
  needed yet. Desktop UI language = `StringDesc.localeType` (system locale by default).
- **1.x — `DesktopDirs` stays in `:core:preference`.** `DesktopPreferenceStore` (same module) and
  the compat layer need it, and `:core:preference` cannot depend on `:core:common`, so moving it
  would not simplify anything.
- **Open — desktop `Dispatchers.Main`.** Shared code that uses `launchUI` / `withUIContext` needs
  `kotlinx-coroutines-swing` on the desktop runtime classpath (Compose Desktop brings it in
  `:desktopApp`); plain JVM tests that hit such code must provide a main dispatcher.
- **Open — Android toasts from shared code on desktop** go to `Toast.presenter` (logs). The desktop
  UI should replace it with a snackbar host in Phase 4.
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
