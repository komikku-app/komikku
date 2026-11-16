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
  rewrite of exactly the parts we want to share. Everything below targets *Android + desktop JVM*.
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
breakage. Kotlin-Native / iOS stays out of scope.

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
