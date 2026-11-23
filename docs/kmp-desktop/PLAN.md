# Komikku Desktop (Compose Multiplatform) — Revised Plan

Status: living document. Supersedes the "Phases 0-2" outline in PR #1671.
Progress is tracked in [`PROGRESS.md`](PROGRESS.md); update it after every step.

## 1. Goal and non-goals

**Goal:** a real, usable Komikku desktop app (Windows / macOS / Linux, JVM) — library, browse
sources, extensions, reader, downloads, trackers, backup — that shares as much code as possible
with the Android app, while the Android app and the extension ABI stay unchanged.

**Non-goals (for now):**

- **iOS / Kotlin-Native.** Extensions are JVM bytecode, and the network, source and database
  stacks are JVM libraries (OkHttp, jsoup, RxJava 1, Injekt). A Native target would need a
  rewrite of exactly the parts we want to share. Sections 1–7 target *Android + desktop JVM*.
  iOS is planned separately in [section 8](#8-ios-javascript-sources-mangayomi-extensions), on a
  different source model (JavaScript extensions), and does not change the desktop work.
- Android-only surfaces on desktop: Glance widget, Shizuku installer, biometric lock, Firebase,
  LeakCanary, macrobenchmark.
- Refactoring upstream-owned code for elegance. Every change is judged by "does it make the next
  Mihon / SY cherry-pick harder?".

## 2. What changes compared with PR #1671

| PR #1671 | Problem | Revised approach |
|----------|---------|------------------|
| New `NetworkClient` / `NetworkResponse` abstraction in `:core:network` | Unused by the app; string bodies only; no interceptors, cookies or Cloudflare. Extensions call `eu.kanade.tachiyomi.network.*` directly, so that package itself has to run on desktop. | **Delete it.** Compile the existing network package for desktop (D2, D3). |
| `commonMain` API with `expect`/`actual`, prepared for iOS | iOS is a non-goal; `expect`/`actual` forces file moves and signature churn. | One JVM-shared source set per module; `expect`/`actual` only where the two platforms truly differ. |
| Files moved into new modules (`:core:preference`) | Moved files conflict with every upstream change to them. | **Path-preserving conversion** (D1): files stay where upstream has them. `:core:preference` is kept because it is already done and its moves are pure renames, but no further extraction modules are created. |
| Host-specific `desktop-jvm-<os>` Compose artifact picked at configure time | No cross-OS packaging, no `nativeDistributions`. | Fix the `compose` catalog / extension name clash properly and apply `org.jetbrains.compose` (step 0.2). |
| `java.util.prefs` preference store | 80-char keys, 8 KB values, Windows registry, untyped strings; cannot back extension `SharedPreferences`. | File-backed store in the app data dir, one file per store name; also backs the `SharedPreferences` shim (step 0.4). |
| Material 2 demo UI | App is Material 3. | Material 3 + the app's theme. |
| Android types treated as blockers (`Application`, `SharedPreferences`, `Uri`, `PreferenceScreen`) | They are part of the extension ABI; extensions need the same FQNs at runtime anyway. | **`:desktop:android-compat`** provides those classes on desktop (D3). |

## 3. Key decisions

### D1 — Path-preserving KMP conversion

A module gets a desktop target **without moving its files**. A convention plugin
(`mihon.library.kmp`, KMK-owned, in `buildSrc`) configures:

- targets: `androidTarget()` + `jvm("desktop")`;
- an intermediate source set **`jvmShared`** (parent of `androidMain` and `desktopMain`) whose
  Kotlin source dir is the module's existing `src/main/java` (or `src/main/kotlin`,
  `src/commonMain/kotlin` for modules that are already KMP);
- `androidMain` → `src/androidMain/kotlin`, `desktopMain` → `src/desktopMain/kotlin`;
- `commonMain` stays empty (JDK APIs are not visible there, and we have no non-JVM target).

Only files that cannot compile on desktop even with the compat layer move to `src/androidMain`
(same package, so no import churn), paired with a `desktopMain` counterpart when the symbol is
needed. Every such move is listed in `PROGRESS.md` so cherry-pick conflicts can be mapped quickly.

Why this matters: Komikku is ~650 Mihon and ~590 SY commits behind, and upstream has since turned
`source-api` / `source-local` back into plain Android libraries, moved to AndroidX ViewModel,
Metro DI and a `build-logic` included build. A plan that relocates upstream files would turn that
backlog into a wall of conflicts. With D1 an upstream edit to `src/main/java/.../Foo.kt` still
applies at the same path.

### D2 — JVM-shared first, `expect`/`actual` last

Android and desktop are both JVM. OkHttp, Okio, jsoup, RxJava 1, Injekt, kotlinx-serialization,
SQLDelight, Coil 3, Voyager, paging-common all run unchanged on desktop. Differences are handled
in this order of preference:

1. Compile unchanged against the compat layer (D3).
2. Small interface in `jvmShared` + per-platform implementation registered in DI.
3. Same-package file in `androidMain` + `desktopMain` — only for symbols whose callers are in
   *other* modules. Shared code cannot see its own module's `androidMain` / `desktopMain`
   declarations (verified in step 0.6).
4. `expect` in the shared source set + `actual` per platform, for symbols called from shared code
   in the same module. Keep the `expect` file at the original path.

The shared source sets are named `jvmSharedMain` / `jvmSharedTest` (hierarchy-template group
`jvmShared`); "`jvmShared`" in this document refers to them. Android-only AAR dependencies of a
converted module move to `androidMain.dependencies {}`.

### D3 — `:desktop:android-compat`: Android API surface for desktop

A desktop-only JVM module that provides, under the **real Android FQNs**, the subset of the
Android SDK that shared code and extensions touch: `android.app.Application`,
`android.content.Context` / `SharedPreferences`, `android.net.Uri`, `android.util.Log` / `Base64`,
`android.os.SystemClock` / `Build` / `Handler` / `Looper`, `android.text.TextUtils` / `Html`,
`android.webkit.CookieManager` / `MimeTypeMap`, `androidx.preference.*` data holders,
`android.graphics.Bitmap` / `BitmapFactory` / `Canvas` / `Rect` (Skia-backed), and so on.

- Desktop targets of shared modules get it through `core:common`'s `desktopMain`
  `api(projects.desktop.androidCompat)` (the shared public API exposes its types, so a
  `compileOnly` + runtime-from-`:desktopApp` split would only add boilerplate; decided in 1.2).
- It grows on demand: add a class when a shared module or a real extension needs it. Unsupported
  members throw `UnsupportedOperationException("android-compat: …")` so gaps are loud.
- Written fresh for Komikku (Apache-2.0). Suwayomi-Server's `AndroidCompat` (MPL-2.0) is the
  reference for *what* is needed; copying files from it needs a maintainer decision on licensing
  (see §7).
- The module never ships in the Android app.

This is the same model Suwayomi-Server uses to run Tachiyomi extensions on a JVM, and it is the
only model that keeps the extension ABI intact.

### D4 — Extensions on desktop

Extension APK → download from the same repos → **dex2jar** → jar cached per APK hash → manifest
metadata parsed from the APK's binary `AndroidManifest.xml` → child-first `URLClassLoader` over
the jar with `:desktop:android-compat` + `source-api` as parent. Same `tachiyomi.extension.*`
metadata keys and lib-version check as `ExtensionLoader`. Signature trust uses the APK signing
certificate hash, as on Android.

JS engine: a desktop class with the FQN `app.cash.quickjs.QuickJs` backed by a JVM QuickJS
binding. WebView-dependent features (Cloudflare bypass, WebView login) are stubbed at first and
later backed by KCEF/JCEF.

### D5 — UI sharing strategy

The `app` module has ~940 files, ~78 screens, a View-based reader and 13 activities; it will not
become multiplatform in one move, and moving its files would wreck upstream sync.

1. `:presentation-core` gets a desktop target (D1) on Compose Multiplatform coordinates.
2. A new KMK-owned module **`:app-shared`** (`mihon.library.kmp` + Compose) is the home for
   platform-neutral services and screens. New Komikku code goes there directly.
3. Existing `app` presentation code is shared by **source borrowing**: `:app-shared` adds an
   explicit allowlist of files/directories under `app/src/main/java` (starting with
   `eu/kanade/presentation/**` leaf components that have no `Context`, `R` or `Activity` use) to
   its `jvmShared` source set, and `app` excludes exactly that allowlist and depends on
   `:app-shared`. Files stay at their upstream paths; the allowlist lives in one Gradle file.
4. Screens that need platform services get them through small interfaces resolved from DI.
5. The reader is rewritten in Compose for desktop (Skia decoding). Upstream is moving its own
   reader to Compose; re-evaluate sharing it when that lands here.

### D6 — Compose and versions

- Compose Multiplatform version follows the Kotlin version on `master` (currently CMP 1.12.2 for
  Kotlin 2.4.20). The androidx Compose BOM remains the source of truth for Android versions ("BOM
  wins"). Since CMP 1.12's Android variants require newer androidx Compose than the BOM, KMP Compose
  modules (`mihon.library.kmp.compose`, step 4.0) resolve Compose **per target**: androidx
  coordinates + BOM in `androidMain`, `org.jetbrains.compose.*` in `desktopMain`; shared sources are
  compiled once per target against either. Android-only Compose APIs used by shared files
  (`LocalContext`, `BackHandler`, …) come from `:desktop:compose-compat` on desktop.
- `material-icons-extended` is frozen in CMP; use it as-is on desktop until upstream's generated
  icon modules are cherry-picked.
- Catalog fixes needed for JVM: `xmlutil:core-android` → `xmlutil:core`; `coil-gif` Android-only.

### D7 — Storage on desktop

- App data dir: `%APPDATA%/Komikku`, `~/Library/Application Support/Komikku`,
  `$XDG_DATA_HOME/komikku` (fallback `~/.local/share/komikku`).
- Database: same SQLDelight schema and migrations, same `sqldelight-androidx-driver` over
  `androidx.sqlite:sqlite-bundled` (both have JVM artifacts). No SQLCipher on desktop initially.
- Files: `UniFile` is provided on desktop by a `java.io.File`-backed class with the same FQN in
  `:desktop:android-compat`, so `source-local`, downloads and backup code compile unchanged.
- Archives: `core:archive` desktop implementation on Apache commons-compress + junrar (the
  Android libarchive JNI AAR has no desktop build).

### D8 — Relationship with the upstream sync

The upstream backlog is **not** a prerequisite, but the plan is ordered so that the phases that
touch upstream-heavy areas come last:

- Phases 0–4 only add build configuration, KMK-owned modules and a handful of same-package file
  moves. They are safe before or after the sync.
- **Gate before Phase 6** (shared screens / ScreenModels): check whether the ViewModel + Metro DI
  migrations have been cherry-picked. If not, shared screens in `:app-shared` are written against
  small KMK interfaces instead of Voyager `ScreenModel` + Injekt directly, to avoid a second
  rewrite.
- When upstream's "source-api back to Android library" commit is picked, `mihon.library.kmp`
  keeps working: the source dir becomes `src/main/...` again and the plugin maps it to `jvmShared`.

## 4. Module map (target state)

```
i18n, i18n-sy, i18n-kmk      android + desktop   (moko-resources)
core:preference              android + desktop   (exists; file-backed desktop store)
core:common                  android + desktop   (D1; network, logging, i18n helpers)
core:archive                 android + desktop   (commons-compress on desktop)
source-api                   android + desktop   (D1 + D3; ABI untouched)
core-metadata                android + desktop
source-local                 android + desktop
domain                       android + desktop
data                         android + desktop   (JVM sqlite driver)
presentation-core            android + desktop   (Compose Multiplatform)
app-shared        (new, KMK) android + desktop   (services, borrowed + new screens)
desktop:android-compat (new) desktop only        (Android FQN shim)
desktop:compose-compat (new) desktop only        (Android-only Compose FQN shim, 4.1)
desktop:extension-runtime (new) desktop only     (dex2jar, manifest parsing, loader)
desktopApp                   desktop only        (entry point, DI bootstrap, packaging)
app                          android only
presentation-widget, telemetry, flagkit, macrobenchmark   android only (flagkit assets copied for desktop later)
```

## 5. Phases and steps

Rules for every step:

- One step = one commit (or a few small ones), pushed. Android must still compile.
- Verification is **compile-only** during development: `./gradlew spotlessApply spotlessCheck`
  plus the listed compile tasks. No emulator, no `assemble*`, no packaging until the user asks.
- `// KMK -->` / `// KMK <--` markers on all edits to existing files; new strings in `i18n-kmk`.
- Update `PROGRESS.md` (status, files moved to `androidMain`, compat classes added, open issues).

### Phase 0 — Foundation cleanup (on top of the rebased PR)

| Step | Work | Verify |
|------|------|--------|
| 0.1 | Rebase PR #1671 on `master`; bump Compose Multiplatform for current Kotlin. **Done.** | — |
| 0.2 | Resolve the `compose` catalog vs `org.jetbrains.compose` extension clash (access the JetBrains extension by type / rename the `Project.compose` accessor in `buildSrc`); apply the Compose plugin normally in `:desktopApp`; remove the host-specific artifact workaround; switch demo UI to Material 3. | `:desktopApp:compileKotlin`, `:app:compileDebugKotlin` |
| 0.3 | Remove the `NetworkClient` abstraction and the `:core:network` module (the real network stack is handled in Phase 1). | same |
| 0.4 | Replace `DesktopPreferenceStore` (`java.util.prefs`) with a file-backed store (JSON file per store under the app data dir, typed values, `changes()` flow, atomic writes). Add `DesktopDirs` helper (D7). Unit tests. | `:core:preference:desktopTest` |
| 0.5 | Add `jvm("desktop")` to `:i18n-sy` and `:i18n-kmk`. | `:i18n-sy:compileKotlinDesktop`, `:i18n-kmk:compileKotlinDesktop` |
| 0.6 | Add the `mihon.library.kmp` convention plugin (D1) and convert `:core:preference` to it as the first consumer. | `:core:preference:compileKotlinDesktop`, `:core:common:compileDebugKotlin` |

### Phase 1 — `core:common` on desktop

| Step | Work | Verify |
|------|------|--------|
| 1.1 | Create `:desktop:android-compat` with the first batch of classes needed by `core:common` (`Context`, `Application`, `SharedPreferences`, `Uri`, `Log`, `Base64`, `SystemClock`, `Build`, `CookieManager`, `MimeTypeMap`, `Handler`/`Looper`, UniFile shim). | `:desktop:android-compat:compileKotlin` |
| 1.2 | Convert `:core:common` with `mihon.library.kmp`. Fix catalog entries (`xmlutil` core, logcat/xlog availability). Logging: `logcat` and `xLog*` keep their signatures; desktop backend prints through `java.util.logging`/stdout. | `:core:common:compileKotlinDesktop`, `:core:common:compileDebugKotlin` |
| 1.3 | Move to `androidMain` only what cannot be shimmed sensibly: WebView code (`WebViewUtil`, `WebViewInterceptor`, `CloudflareInterceptor`), `ImageUtil` (Bitmap/decoder heavy), `DeviceUtil`, `AndroidPreference*`, `AndroidStorageFolderProvider`. Desktop counterparts: no-op Cloudflare interceptor, Skia-based `ImageUtil` subset, `DesktopStorageFolderProvider`. `NetworkHelper`, `AndroidCookieJar`, `Requests`, `OkHttpExtensions`, rate-limit interceptors stay shared. | same |
| 1.4 | `JavaScriptEngine`: desktop `app.cash.quickjs.QuickJs` shim over a JVM QuickJS binding. | same |
| 1.5 | `Localize.kt`: context-free string resolution on desktop via moko `StringDesc`. | same + `:app:compileDebugKotlin` |

### Phase 2 — Source layer

| Step | Work | Verify |
|------|------|--------|
| 2.1 | `:source-api` desktop target: move `core:common` to a shared dependency, add compat classes it needs (`androidx.preference.PreferenceScreen` and widgets as plain data holders). ABI on Android must be byte-identical: compare `javap` signatures of the public classes before/after. | `:source-api:compileKotlinDesktop`, `:source-api:compileDebugKotlinAndroid`, ABI diff empty |
| 2.2 | `:core-metadata` desktop target. | compile both targets |
| 2.3 | `:core:archive` desktop target; commons-compress/junrar reader; `CbzCrypto` keystore replaced by a file-stored key on desktop. | compile both targets |

### Phase 3 — Domain and data

| Step | Work | Verify |
|------|------|--------|
| 3.1 | `:domain` desktop target. `StorageManager` → interface in shared + Android/desktop impls; `Extension.icon: Drawable` handled through the compat layer or an `Any?`-typed loader key (prefer cherry-picking upstream "Load extension icons through Coil" if it applies cleanly). | `:domain:compileKotlinDesktop`, `:domain:testReleaseUnitTest` (existing tests only) |
| 3.2 | `:data` desktop target: SQLDelight for both targets, desktop driver factory + migration runner, `ReleaseServiceImpl`/`CustomMangaRepositoryImpl` platform bits. | `:data:compileKotlinDesktop`, `:data:generateSqlDelightInterface` |
| 3.3 | `:source-local` desktop target. | compile both targets |
| 3.4 | `:desktopApp` DI bootstrap (Injekt): preferences, network, database, domain interactors (reuse `DomainModule` bindings via a shared registration function where possible). Smoke screen: categories + library count from the real DB. | `:desktopApp:compileKotlin`, a JVM unit test that opens an in-memory DB and runs one interactor |

**Milestone A:** the whole non-UI stack compiles and runs on a desktop JVM.

### Phase 4 — Presentation foundation

| Step | Work | Verify |
|------|------|--------|
| 4.1 | `:presentation-core` desktop target on CMP coordinates; platform bits (`ViewConfiguration` in scrollbars, `AdaptiveSheet` back handling, `Localize`) split. | compile both targets + `:app:compileDebugKotlin` |
| 4.2 | Create `:app-shared` (D5). Theme (`TachiyomiTheme`, color schemes, material-kolor), Coil 3 setup with cover fetchers, Voyager navigator host. | compile both targets |
| 4.3 | Desktop shell in `:desktopApp`: window, navigation rail (Library / Updates / History / Browse / More), window state persistence, keyboard shortcuts scaffold. | `:desktopApp:compileKotlin` |

### Phase 5 — Extensions on desktop

| Step | Work | Verify |
|------|------|--------|
| 5.1 | `:desktop:extension-runtime`: APK manifest parser, dex2jar conversion + cache, class loader, trust store. Unit test with a tiny fixture APK if one can be built offline, otherwise with a synthetic jar. | module tests |
| 5.2 | Desktop `ExtensionManager` / installer behind the same domain interfaces (repo index fetch, download, install = convert + register, uninstall, update). | compile |
| 5.3 | Compat growth pass driven by real extensions: load a sample of popular extensions' classes and report missing `android.*` symbols with a link-check tool (no network scraping of sources at build time). | tool report |
| 5.4 | Extension preferences screen rendered in Compose from the compat `PreferenceScreen` model. | compile |

**Milestone B:** install an extension, browse its catalogue, open a manga's details on desktop.

### Phase 6 — Shared screens (gate: D8)

Order follows user value and increasing Android entanglement. Each screen: borrow or write the
composable in `:app-shared`, move its ScreenModel logic behind platform interfaces, wire both apps.

6.1 Browse sources / source catalogue / global search → 6.2 Manga details + chapters →
6.3 Library (categories, filters, sort, search) → 6.4 Updates, History →
6.5 Settings (data-driven preference screens) → 6.6 Extensions screens → 6.7 Feed, recommendations,
merged manga and other SY/KMK screens.

Shared services needed along the way, each behind an interface with Android + desktop
implementations: `CoverCache`, `ChapterCache`, `DownloadManager`/`DownloadProvider`,
`SourceManager`, library update runner (WorkManager on Android, coroutine scheduler on desktop),
notifications (system tray on desktop).

**Gate outcome (after Phase 5):** the upstream ViewModel and Metro DI migrations are not in this
repo. Sharing the Android screens now would mean reworking ~49 Voyager `ScreenModel`s that the
upstream sync will rewrite again. Decision: **Phase 6 is deferred until after that sync.** Until
then the desktop app gets its own compact screens in `:desktopApp`, built on the shared
interactors and the `:app-shared` services, and work continues with:

- **Phase 6-lite** — desktop manga details that stores chapters (`SyncChaptersWithSource`),
  read/bookmark state, Updates and History tabs, library refresh for one manga / all.
- **Phase 7** — reader.
- **Phase 8** — parity features, in the order downloads → backup restore/create → trackers.

Android's `ExtensionManager` should implement `PlatformExtensionManager` when Phase 6 resumes.

### Phase 7 — Reader

7.1 Page loaders shared (HTTP, download, archive, directory) → 7.2 Skia image decoding, split of
tall images, colour filters → 7.3 Paged viewer (LTR/RTL/vertical) → 7.4 Webtoon / continuous
viewer → 7.5 Keyboard / mouse navigation, zoom, fullscreen, reader settings → 7.6 Chapter
transitions, progress, history and tracker updates.

**Milestone C:** read a chapter end to end from an online source and from a local archive.

### Phase 8 — Parity features

Downloads queue UI, library update scheduling, backup create/restore (same protobuf format, so
Android backups import on desktop), trackers (OAuth through the system browser + loopback
redirect), app updater, E-Hentai / MangaDex delegated sources, sync, WebDAV, Discord RPC.

### Phase 9 — Packaging and CI

`compose.desktop.nativeDistributions` (msi/exe, dmg, deb/rpm/AppImage), ProGuard off at first,
app icons, a `build_desktop.yml` workflow (compile + package per OS), a desktop job in the PR
workflow limited to `:desktopApp:compileKotlin` so Android PRs get early warning of desktop
breakage. Kotlin-Native / iOS stays out of scope. Configured in 9.1 (not executed yet); how to run,
test and package: [`BUILDING.md`](BUILDING.md).

## 6. Risks

| Risk | Mitigation |
|------|------------|
| Upstream sync conflicts | D1 path preservation; KMK-owned modules; moves logged in `PROGRESS.md`; gate before Phase 6. |
| Compat shim grows without bound | On-demand growth, loud failures, link-check tool (5.3); Bitmap/Canvas scoped to what image-descrambling extensions use. |
| Compose artifact mismatch on Android (CMP vs androidx BOM) | BOM-wins constraint in the convention plugin; `:app:compileDebugKotlin` in every UI step. |
| `jvmShared` IDE support (source set shared by Android + JVM) | Known Kotlin limitation: Gradle compiles correctly; IDE resolves through the Android target. Documented in the plugin. |
| Source borrowing is unusual Gradle | Allowlist in one file, a check task that fails if a file is compiled by both modules or by neither. |
| Cloudflare-protected sources | Stub first; KCEF-backed WebView later (large download, so optional component). |
| moko-resources maintenance | Keep for now (it is what upstream uses); isolated behind `Localize.kt`. |

## 7. Decisions that need the maintainer (not blocking current work)

1. **PR branch:** the rebased work lives on `claude/laughing-thompson-i2rd2h`; PR #1671's own
   branch was not force-pushed.
2. **Licensing of the compat layer:** write-from-scratch (current choice) vs vendoring parts of
   Suwayomi's MPL-2.0 `AndroidCompat`.
3. **Upstream sync timing:** doing the Mihon build-logic / AGP 9 / Metro / ViewModel sync before
   Phase 6 would remove the need for the interim interfaces described in D8.
4. **Database encryption on desktop:** none initially (SQLCipher Android library has no JVM build).

## 8. iOS: JavaScript sources (Mangayomi extensions)

Status: I0 done (8.9); I1 and I2 done on desktop (`:source-js`, see PROGRESS.md); Android approved
(8.7 item 5) and switched in I2b (`:core:quickjs`; `:source-js` has an Android target); I3 onward not started. Facts about Mangayomi below come from reading its source
(`kodjodevf/mangayomi` at `bc0b410`, 2026-11-16); the items once marked *(verify)* were resolved in I0
(8.9). The full contract is [`MANGAYOMI_CONTRACT.md`](MANGAYOMI_CONTRACT.md), the usage scan
[`MANGAYOMI_SURVEY.md`](MANGAYOMI_SURVEY.md).

### 8.1 Why a different source model

Mihon extensions are Android APKs with JVM bytecode. iOS has no JVM and forbids JIT, so they can
only run there inside an embedded no-JIT JVM interpreter (what Mangayomi's `m_extension_server`
does): slow, memory-hungry, sideload-only. That route stays a possible later add-on (8.8).

Mangayomi's own extensions are **source text** evaluated on the device by an interpreter, which
iOS allows. The JavaScript ones run in QuickJS. Komikku on iOS will host that same extension
format, so it gets an existing, maintained catalogue without a JVM.

The JavaScript host is also useful on Android and desktop, and it can be built and tested there
first, on Linux, before any iOS code exists. That is how the work is ordered.

### 8.2 The Mangayomi extension contract (what the host must implement)

- **Index:** `index.json` in `kodjodevf/mangayomi-extensions`, a JSON array. Per entry: `id`,
  `name`, `baseUrl`, `lang`, `sourceCodeUrl` (raw `.js` or `.dart` file), `version`, `itemType`
  (0 manga / 1 anime / 2 novel), `sourceCodeLanguage` (0 Dart, 1 JavaScript, 2 Mihon, 3 LNReader,
  4 Aidoku), `appMinVerReq`, plus `iconUrl`, `apiUrl`, `typeSource`, `isNsfw`, `hasCloudflare`,
  `isFullData`, `dateFormat`, `dateFormatLocale`, `additionalParams`, `notes`, `isManga`. No hash.
- **Extension shape:** a global class `DefaultExtension` extending a host-defined `MProvider` base
  class: async `getPopular(page)`, `getLatestUpdates(page)`, `search(query, page, filters)`,
  `getDetail(url)`, `getPageList(url)`; sync `getHeaders(url)`, `getFilterList()`,
  `getSourcePreferences()` and the getter `supportsLatest` (shapes in the contract, section 3).
- **Host bridge** (extensions call it through `sendMessage`): HTTP (get/post/put/delete/patch/
  head with headers and body), CSS and XPath selectors over HTML, crypto helpers (AES, CryptoJS
  subset, AES-GCM), a JS unpacker, string utilities, `evaluateJavascriptViaWebview`, and
  video extractors (not needed for manga).

**Scope:** JavaScript sources with `itemType` manga only. Out of scope: Dart sources (they need
the `d4rt` Dart interpreter), anime and novel sources, LNReader plugins, Aidoku WASM sources.
Of the 363 index entries 114 are JavaScript manga, served by 18 files (86 of them are MangaDex and
Comick language variants); the other 249 are Dart and will not load.

### 8.3 Design decisions

**I-D1 — One host, three platforms.** A new KMK-owned module `:source-js` holds the host. It is a
real multiplatform module (code in `commonMain`), unlike the path-preserving JVM modules of D1,
because it must compile for Kotlin/Native. Targets: Android and desktop from the start; `iosArm64`
and `iosSimulatorArm64` are declared only when the build host is macOS.

**I-D2 — Engine behind an interface.** `JsEngine` (evaluate, call function, register host
callback, close) with one implementation per platform: the existing QuickJS bindings on Android
and desktop, and on iOS either the same QuickJS library's Native target (`quickjs-kt` 1.0.15
publishes `iosArm64`, `iosSimulatorArm64` and `iosX64`) or the system JavaScriptCore, which runs
without JIT in third-party apps. Chosen in 8.9: `quickjs-kt` on all three. One engine instance per
source, created lazily, closed on idle.

**I-D3 — Bridge services behind interfaces.** `HostHttp`, `HostDom`, `HostCrypto`,
`HostWebView`. On Android and desktop they delegate to what exists (`NetworkHelper`'s OkHttp
client with its cookie jar, rate limits and Cloudflare handling; jsoup). On iOS: Ktor with the
Darwin engine and Ksoup. `HostWebView` is a stub at first; sources that need it report a clear
error.

**I-D4 — JS sources are ordinary sources.** An adapter class implements the existing
`CatalogueSource` / `ConfigurableSource` contract and maps results to `SManga`, `SChapter`,
`Page`, `FilterList` and preference models. Library, reader, downloads, tracking and backup then
work unchanged on Android and desktop. Source ids are derived deterministically from the
Mangayomi source id plus a fixed namespace, so they are stable across devices and never collide
with Mihon source ids.

**I-D5 — Separate store type.** A Mangayomi repository is added by URL like a Mihon one, but is a
distinct store type: installing means downloading the `.js` text, recording its SHA-256 (the index
carries no hash, 8.9), and caching it; there is no APK, signature or trust-by-certificate. The UI labels these
sources as JavaScript sources. Each engine gets only the bridge API, no file or process access.

**I-D6 — Written from scratch against the contract.** The host and bridge are new Kotlin code;
nothing is copied from Mangayomi. The `MProvider` base class text the host injects must match
Mangayomi's, so its licence has to be checked before reusing that text (both repositories are
Apache-2.0, see 8.9).

**I-D7 — iOS app is a separate, smaller app at first.** The Android/desktop shared code is
JVM-only by design. iOS gets its own Compose Multiplatform app on a common core, reusing what is
portable (strings, the SQLDelight schema, backup models, `:source-js`) and not the JVM modules.

### 8.4 Phases

Phases I0–I4 run on Linux and deliver value on Android and desktop. I5 onward needs macOS.

| Step | Work | Verify |
|------|------|--------|
| **I0 Contract and feasibility** | Write `docs/kmp-desktop/MANGAYOMI_CONTRACT.md` from Mangayomi's source: index fields, `MProvider` methods and return shapes, every bridge message with arguments and results, filter and preference models. Resolve the *(verify)* items above. Count manga JS sources and list which bridge calls they use (static scan of the public extension sources), to order the bridge work. | Document reviewed; go/no-go on engine choice and licence |
| **I1 Engine and host core** | `:source-js` module; `JsEngine` on Android and desktop; load an extension, instantiate its provider, call a method, marshal JSON both ways; timeouts, memory limit, cancellation; errors mapped to Kotlin exceptions with the JS stack. | Unit tests with in-test JS providers on `desktopTest` and Android unit tests |
| **I2 Bridge** | `HostHttp` (through `NetworkHelper`), `HostDom` (CSS + XPath), `HostCrypto`, unpacker, string utilities; `HostWebView` stub. Ordered by the usage scan from I0. | Bridge conformance tests: fixture JS calling each message against `MockWebServer` and fixed HTML |
| **I2b Android engine switch** | Replace the zhanghai `quickjs-android` AAR with a `quickjs-kt`-backed `app.cash.quickjs.QuickJs` in a new KMK module `:core:quickjs` (Android + desktop), binary-compatible with the old class (same public classes and signatures; `set`/`get` implemented if extensions use them). `JavaScriptEngine` and Mihon extensions use it on both platforms; the desktop compat copy moves there. R8 keep rules and native ABIs checked against the app's splits. `:source-js` gains `androidTarget()`. **Done:** the Android host unit tests run the engine on quickjs-kt's JVM artifact, swapped in for the AAR whose native library cannot load there. | `javap` diff against the old AAR (additions only); `QuickJs` parity tests on `desktopTest`; `:app:compileDebugKotlin`; `testReleaseUnitTest` of touched modules. On-device check deferred to the first real run. |
| **I3 Source adapter** | `MangayomiJsSource` implementing the source contract: popular / latest / search with paging, details, chapters, pages, image headers, filters, preferences, stable ids. | Adapter tests with a fake provider; a headless desktop UI test browsing it |
| **I4 Store and UI** | Mangayomi repository type: index fetch, install, update, uninstall, hash check, cache under the data dir; register sources in `DesktopSourceManager` and `AndroidSourceManager`; Extensions screen shows the new type on desktop and Android. New strings in `i18n-kmk`. | Store end-to-end test against `MockWebServer`; `:app:compileDebugKotlin` |
| **I5 Common core for iOS** | New `:ios-core` multiplatform module: the portable subset needed by an app (manga / chapter / category / history models and repositories on the shared SQLDelight schema with the native driver, preferences on a multiplatform settings store, backup models moved to common code so `.tachibk` files restore). Decide per piece: move existing code to `commonMain` vs a thin common twin; record each choice. iOS implementations of `JsEngine`, `HostHttp` (Ktor Darwin), `HostDom` (Ksoup), `HostCrypto`. | `:source-js` and `:ios-core` tests on the iOS simulator; the I2 conformance suite passes on iOS |
| **I6 iOS app** | `:iosApp` (Compose Multiplatform, Xcode project): library, browse JS sources, manga details, reader (paged and webtoon, Skia decoding), downloads within iOS background limits, history, settings, backup restore. Reuse desktop screens where they are free of JVM types; otherwise write common ones and have desktop adopt them. | Simulator UI tests; manual run on a device |
| **I7 Distribution and CI** | macOS CI job building the framework and an unsigned IPA; sideload sources (AltStore / SideStore). App Store submission is a maintainer decision (8.7). | CI green; IPA installs |

**Milestone I-A (after I4):** a Mangayomi JavaScript source can be installed, browsed and read on
desktop and Android. **Milestone I-B (after I6):** the same on an iPhone.

### 8.5 What is reused and what is not

| Reused on iOS | Not reused on iOS |
|---------------|-------------------|
| `:source-js` host and bridge contract | Mihon APK extensions, `:desktop:extension-runtime`, `:desktop:android-compat` |
| `i18n*` string resources (moko-resources has iOS targets) | `core:common` network stack (OkHttp), `source-api` as compiled today |
| SQLDelight schema and migrations | Android screens in `app`, Voyager `ScreenModel`s tied to Injekt |
| Backup format (kotlinx-serialization protobuf) | Download stack, trackers, WorkManager jobs (rewritten or added later) |
| Compose UI written in common code | E-Hentai / MangaDex delegated sources |

### 8.6 Risks

| Risk | Mitigation |
|------|------------|
| Bridge drifts from Mangayomi as it evolves | `appMinVerReq` gate per source; conformance suite derived from the contract doc; refresh the contract on each Mangayomi release |
| JS catalogue is the smaller part of Mangayomi's index | Measure in I0 before committing; Dart sources stay unsupported |
| Sources that need WebView evaluation or Cloudflare solving | Stub first, report clearly; real WebView bridge per platform later |
| Two extension ecosystems confuse users | Separate store type and labels (I-D5); source ids never collide (I-D4) |
| Executing downloaded scripts | Interpreter only, bridge-only API surface, hash check, per-source engine, time and memory limits |
| iOS core duplicates JVM modules | Keep `:ios-core` small; move code to common only when both sides use it |

### 8.7 Decisions for the maintainer

1. **Go/no-go after I0**, once the manga JS source count and bridge surface are known.
2. **App Store or sideload only.** Apple restricts apps that run downloaded code; whether an
   interpreter-only JS source model passes review is not established. Sideload first.
3. **Licence** of the injected `MProvider` base class text (I-D6).
4. **Default repository:** ship with no Mangayomi repository preconfigured (user adds the URL),
   as with Mihon repositories.
5. **QuickJS on Android** (8.9): replace the zhanghai `quickjs-android` AAR by the `quickjs-kt`-backed
   `app.cash.quickjs.QuickJs` compat class, or keep JavaScript sources off Android for now.
   **Decided 2026-11-17: switch** — Android uses `quickjs-kt` for Mihon extensions and for JavaScript
   sources (step I2b).
   **Known risk, accepted 2026-11-18:** the I1/I2 review confirmed (runtime proof) that quickjs-kt
   1.0.15 — unchanged in 1.0.18 — turns a JavaScript `Error` reaching the JVM into
   `NewObject(FindClass(error.name), (String) constructor)` without a `Throwable` check, so script code
   can construct arbitrary classes (e.g. `java.io.FileOutputStream(path)` creates or truncates a file).
   `:source-js` closes the paths through its own code (errors cross as strings, raw bindings hidden),
   but an unhandled promise rejection is converted natively and cannot be blocked from JavaScript; the
   `app.cash.quickjs.QuickJs` API used by Mihon extensions keeps exact script semantics, so website
   JavaScript that an extension evaluates keeps that exposure. Told this, the maintainer chose to switch
   anyway. The fix is a one-line native change in quickjs-kt (`IsAssignableFrom(cls, Throwable)` in
   `js_error_to_java_error`); a disabled regression test marks the residual path.
   **Residual risks found by the I2b review (2026-11-23), recorded, not decided:**
   - *Crash, stronger than the instantiation:* when the class an error names is not a `Throwable`, quickjs-kt
     `Throw`s the constructed object through JNI and the JVM dies (SIGSEGV). Komikku now keeps completion values
     away from that conversion, but a script that throws such a value, an unhandled rejection with it as reason,
     or a completion value while the completion helper cannot run (frozen `Object.prototype`), whose conversion
     meets a throwing getter, still kills the process (confirmed). So **a page script evaluated by an extension
     can crash the app**; only the same quickjs-kt patch fixes it.
   - *Process-wide JNI state:* quickjs-kt's instance counter and cached class references are unsynchronised;
     concurrent creates / closes crashed the JVM. Mitigated in Komikku (one lock on the `com.dokar.quickjs.QuickJs`
     class for creating and closing in both modules, a sentinel engine that is never closed); the per-create
     replacement of two cached references can still race with evaluations on other threads (not serialised).
   - *Out of memory:* QuickJS crashed the JVM on out-of-memory depending only on the shape of the prelude's code;
     the shipped prelude passes a child-JVM guard test (`OutOfMemoryCrashTest`), cause unknown, so other code
     shapes (an extension's) may still crash on out-of-memory (PROGRESS.md, "Review fixes (I2b)").

### 8.8 Later options (not planned)

- **Mihon APK extensions on iOS** through an embedded no-JIT JVM (OpenJDK Zero) running the
  desktop extension runtime: full catalogue, but slow, foreground-only, device-only and
  sideload-only.
- **Thin client mode:** the iOS app talks to a Komikku desktop instance or a Suwayomi server.
- **Aidoku WASM sources** through a Kotlin WASM interpreter.

### 8.9 I0 findings

Done 2026-11-17 from `kodjodevf/mangayomi` `bc0b410`, `kodjodevf/mangayomi-extensions` `6004f1f`,
`entityJY/mangayomi-extensions-eJ` `f21a60b` and Maven Central metadata. Details:
[`MANGAYOMI_CONTRACT.md`](MANGAYOMI_CONTRACT.md) (what to implement) and
[`MANGAYOMI_SURVEY.md`](MANGAYOMI_SURVEY.md) (what the sources use). "Unverified" marks what could not be
confirmed from source or metadata.

**Numbers.** The kodjodevf index has 363 entries, all manga: 249 Dart, **114 JavaScript from 18 files**.
MangaDex (45 languages) and Comick (41) are 86 of the 114; 14 files serve one site each. Every JS manga
entry points at a third-party fork (`entityJY/mangayomi-extensions-eJ`, last commit 2026-01-08; the
kodjodevf extensions repository's last commit is 2025-10-01), and 2 of the 18 files are already 404 there.
The fork's own index lists 115 JS manga entries from 19 files. 1 entry is NSFW.

**Bridge actually used.** All files: `Client.get`, `async`/`await`, JSON. Then: `SharedPreferences.get`
13 files, CSS DOM (`Document`, `select`/`selectFirst`, `text`, `attr`, `getSrc`/`getHref`) 12, filters 15,
preferences 13, `cryptoHandler` 1, `Client.post` 1 (fork only). **Unused by every JS manga source:**
XPath, `parseDates`, WebView evaluation, unpackers, CryptoJS / GCM, extractors, `Client` options.
Cumulative coverage of the 114 entries: core + filter models 37%, + preferences 78%, + CSS DOM 99%,
+ `cryptoHandler` 100%.

**Contract surprises** (all in the contract): the index has **no hash**, and files change under an
unchanged version (I-D5 corrected above: record a SHA-256 at install and show changes); ids are Dart
`String.hashCode` values chosen by the index generator, so they must be taken from the index; Mangayomi
creates a fresh engine for every data call and sets no time or memory limit; `selectFirst` never returns
null and invalid selectors give empty results; `Element.text` and `:contains` include `<script>` text
(unlike jsoup); `Element.xpath`/`xpathFirst`/`hasAttr` are broken in Mangayomi; responses' cookies are not
stored and TLS certificates are not verified by default; sources read filters by position, so `search`
must always get the full default list (Mangayomi's global search passes `[]`); 13 files use `new Date(…)`,
mostly to parse chapter dates with QuickJS's own parser.

**Licences** (from the LICENSE files; the final call is the maintainer's, 8.7 item 3):

- `kodjodevf/mangayomi`: Apache-2.0 ("Copyright 2023 Moustapha Kodjo Amadou" in the README); no NOTICE
  file. Its QuickJS binding `kodjodevf/flutter_qjs` is MIT.
- `kodjodevf/mangayomi-extensions`: Apache-2.0, same copyright line; the fork that hosts the JS files,
  `entityJY/mangayomi-extensions-eJ`: Apache-2.0. The `.js` files carry no per-file headers.
- The prelude is JavaScript text inside Dart string literals in `lib/eval/javascript/`
  (`service.dart` `MProvider`, `http.dart` `Client`, `dom_selector.dart` `Document`/`Element`,
  `utils.dart` helpers, `js_common.dart` console and string helpers, `preferences.dart`,
  `extractors.dart`): about 590 lines / 16 KB, of which the extractor, HLS and EPUB parts (about 4 KB) are
  not needed.
- (a) **Prelude:** Apache-2.0 permits copying and modifying with the licence text, kept copyright
  notices and a statement of changes; Komikku is Apache-2.0, so a copied prelude would be compatible.
  The plan keeps I-D6: write our own prelude that exposes the same names (class, method, channel and
  field names are interface facts), which needs no attribution; if any text is copied, the file gets the
  Apache header and attribution.
- (b) **Installing extensions:** users add a repository URL and the app downloads the source text at run
  time; Komikku distributes none of it. The licence permits that use; nothing has to ship with the app.
  App Store rules on downloaded code are a separate, non-licence question (8.7 item 2).

**JS engine** (verified from Maven Central Gradle metadata, the published AAR/JAR contents and the
`quickjs-kt` README):

- `io.github.dokar3:quickjs-kt` 1.0.15 (the catalog's version; latest 1.0.18, which adds tvOS) publishes
  one KMP API for Android (`quickjs-kt-android` AAR), JVM (`quickjs-kt-jvm`, natives for Linux x64/arm64,
  macOS x64/arm64, Windows x64), **`iosArm64`, `iosSimulatorArm64`, `iosX64`**, macOS, Linux and MinGW;
  built against Kotlin stdlib 2.4.10 and coroutines 1.11.0; Apache-2.0. API: suspend `evaluate` (script
  or module), sync and **async function bindings** (an async binding is a suspend function on the Kotlin
  side and a promise in JS), object bindings, `memoryLimit`, `maxStackSize`, `evaluationTimeoutMillis`,
  `interruptEvaluation()`, interruption on coroutine cancellation, `gc()`, bytecode `compile`. Its native
  library embeds QuickJS **`2026-06-04`, the same version Mangayomi ships**.
- Android today uses `com.github.zhanghai.quickjs-java:quickjs-android:547f5b1597` for
  `app.cash.quickjs.QuickJs` (the API Mihon extensions use). Both AARs ship `jni/<abi>/libquickjs.so`
  (different JNI symbols, same file name; `quickjs-kt` loads it by that name), so **they cannot be packaged
  together as published** (the Gradle failure itself was not run: unverified; `pickFirst` would break one
  of them).
- Alternatives: *JavaScriptCore* on iOS through Kotlin/Native's `platform.JavaScriptCore` (availability
  of the full C API there unverified) — no binary cost and no JIT needed, but a second engine with
  different `Date` parsing and errors, no public memory or execution-time limit, separate binding code;
  not recommended. *Zipline* (`app.cash.zipline` 1.28.0, Android/JVM/iOS) — QuickJS-based but built for
  Kotlin/JS services; too heavy for third-party scripts. *zhanghai quickjs-android* — Android only,
  synchronous, no async bindings. *Our own renamed QuickJS build* — possible, adds a native build.
- **Recommendation: `quickjs-kt` on all three platforms.** Same QuickJS as Mangayomi (dates, regex,
  error behaviour), one binding and one test suite for Android, desktop and iOS, async bindings that map
  directly onto `Client` calls, built-in time and memory limits and interruption (8.6), already used and
  tested here on desktop (step 1.4). On Android this means replacing the zhanghai AAR by the
  `quickjs-kt`-backed compat `app.cash.quickjs.QuickJs` that desktop already has, moved where Android can
  use it, after Android parity tests of `JavaScriptEngine` and a check that extensions do not need
  `QuickJs.set`/`get` (not implemented by the compat class). *(I2b: no extension in
  `keiyoushi/extensions-source` calls `set` / `get`; `:core:quickjs` implements both anyway.)* Because that changes how Mihon extensions
  run on Android, it is a maintainer decision (8.7 item 5). If it is declined, `:source-js` starts on
  desktop only and Android JS sources wait.

**iOS bridge libraries** (Maven Central metadata; all publish `iosArm64`, `iosSimulatorArm64`,
`iosX64`):

| Need | Library | Notes |
|------|---------|-------|
| HTTP | `io.ktor:ktor-client-darwin` 3.6.1 | Android/desktop keep `NetworkHelper`'s OkHttp (cookie jar and Cloudflare handling are a superset of Mangayomi's). |
| HTML + CSS | `com.fleeksoft.ksoup:ksoup` 0.2.6 (MIT, jsoup port) | **No XPath** (none in the JVM jar or README). No manga source needs XPath, so it leaves I2; if needed later: jsoup `selectXpath` on JVM, a subset or libxml2 cinterop on iOS. |
| Crypto | `dev.whyoleg.cryptography` 0.6.0: `cryptography-core`, `provider-apple` / `provider-cryptokit` (iOS), `provider-jdk` (JVM) | Only AES-CBC is needed now (one source). A hand-written AES-CBC in common code is the alternative; decide in I2. MD5 support in the Apple provider (only for CryptoJS) unverified. |
| Dates | `org.jetbrains.kotlinx:kotlinx-datetime` 0.8.0 | Only if `parseDates` is ever ported (no source uses it); Dart `intl` month names would need CLDR data beyond this library. |

These were built with Kotlin 2.1–2.4.10 stdlibs. A Kotlin 2.4.20 compiler reads klibs made by older
compilers, but no iOS build was run here (unverified until I5 on macOS).

**Go / no-go: go for I1–I4**, with two conditions. (1) The Android engine decision (8.7 item 5);
without it, I1–I4 run on desktop only. (2) The case for the work is iOS: on Android and desktop the 18
sites add little (several are also Mihon extension targets — overlap not counted: unverified), and the
JS catalogue is small, concentrated and maintained in a third-party fork. Before starting I5, re-check
the catalogue size and maintenance and confirm the iOS app is still wanted.

**Refined effort and order:**

| Step | Size | Scope after I0 |
|------|------|----------------|
| I1 | small–medium | `:source-js` (KMP, `android` + `desktop` targets); `JsEngine` on `quickjs-kt`; own prelude with Mangayomi's names; call convention and JSON marshalling; `setTimeout`, single-argument `console`, string helpers; per-call timeout, memory limit, cancellation; errors with the JS stack. Desktop first if the Android decision is pending. |
| I2 | small (was medium) | `Client` (all verbs share one path; GET/POST tested), response object; `SharedPreferences` (`get` with default materialisation, `getString`/`setString`); CSS DOM with Mangayomi's semantics (never-null elements, `textContent`, `:contains` on it, regex `getSrc`/`getHref`, selector errors → empty); `cryptoHandler`; stubs that reject clearly for WebView, extractors, HLS, EPUB. XPath, `parseDates`, unpackers, CryptoJS, GCM deferred until a source needs them. Conformance fixtures follow the contract. |
| I3 | medium | Filter models (8 types, full default list always sent, original JSON round-tripped) and the 4 preference types first — they gate 78% of entries; then one `getDetail` serving details and chapters, `dateUpload` parsing, per-page headers kept with each page, `getHeaders` for images, ids from the index id. |
| I4 | medium | Index and `repo.json` parsing, `compareVersions`, SHA-256 recorded at install, one source per id across repositories, clear errors for 404 source files, NSFW flag, `notes` banner, `i18n-kmk` strings. |

Order: I1, then I2 and I3's filter/preference models in parallel, then the rest of I3, then I4. Real
websites stay out of CI; fixtures cover the contract.
