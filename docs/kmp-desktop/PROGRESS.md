# Komikku Desktop — Progress Log

See [`PLAN.md`](PLAN.md). Update this file in the same commit as each step. Building, running and
packaging the desktop app: [`BUILDING.md`](BUILDING.md).

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
| 1.x `:desktopApp` on the real stack | done | `de72348514` | `:desktopApp` depends on `:core:common`; `DesktopServices` builds the compat `Application`, the main preference store (via `DesktopPreferenceStores`, shared with `SharedPreferences`) and `NetworkHelper`; `main()` installs `DesktopLogcatLogger` and shows the default user agent / HTTP cache dir. `DesktopNetworkTest` (`:desktopApp:test`, OkHttp `mockwebserver3`, no real network): `GET` + `awaitSuccess` through `NetworkHelper.client`, cookie round trip through `AndroidCookieJar` → desktop `CookieManager`, default user agent, cache dir, `HttpException`, Cloudflare pass-through. Test JVMs get `komikku.dataDir` = `desktopApp/build/tmp/test-data` |
| 2.1 `:source-api` desktop target | done | `0573391da4` | `mihon.library.kmp` (sources stay in `src/commonMain/kotlin`, compiled through `jvmSharedMain`; the existing `androidMain` actuals stay). `core:common` is now a shared `api` dependency (was Android `implementation`). Desktop `actual`s in `src/desktopMain`: `PreferenceScreen` = compat `androidx.preference.PreferenceScreen`, `awaitSingle` = same body as Android. Compat: `androidx.preference` data holders, `androidx.core.net.toUri`, minimal `View`/`TextView`/`EditText`/`TextWatcher`/`Editable`/`InputType` (table below); 8 new tests in `PreferenceTest`. **Android ABI: `javap -protected` of all 199 classes in `source-api/build/tmp/kotlin-classes/debug` before/after: identical (empty diff)** |
| 2.2 `:core-metadata` desktop target | done | `73055b2d91` | `mihon.library.kmp` (sources stay in `src/main/java`). `kotlinx.bundles.serialization` replaced by its members (KMK block) because the bundle contains the Android-only `serialization-xml-core` (`xmlutil:core-android`): Android keeps it in `androidMain`, desktop gets `serialization-xml-core-jvm` (`xmlutil:core` → `core-jvmcommon`). No file moves, no compat classes. Android ABI (`javap -protected`, 66 classes in `core-metadata/build/tmp/kotlin-classes/debug`): identical. `ComicInfoDesktopTest` (`:core-metadata:desktopTest`): ComicInfo.xml round trip with the app's `XML {}` configuration + unknown elements ignored |
| 2.3 `:core:archive` desktop target | done | `ab4960b11e` | `mihon.library.kmp`; libarchive + UniFile → `androidMain` (Android unchanged). `ArchiveReader` is an `expect class` (shared `EpubReader` / `CbzCrypto` use it): Android `actual` = the upstream class (moved, `actual` modifiers only), desktop `actual` = `ArchiveReader(File)`. Desktop counterparts with the same public API (no `expect`, no shared callers): `ArchiveInputStream(File, encrypted)`, `ZipWriter(Context, UniFile, encrypt)`, `UniFile.archiveReader` / `epubReader`. Backends (`DesktopArchiveCursor`, format from magic bytes): zip/cbz/epub on zip4j (AES-256/128 + ZipCrypto read and write), RAR 4 on junrar, 7z on commons-compress + xz, tar/cpio/ar (also gz/bz2/xz-compressed) on commons-compress. `CbzCrypto` key handling → `internal expect object CbzKeyStore` (Android: the former Keystore code; desktop: random 256-bit key in `<dataDir>/keys/<alias>.key`, owner-only POSIX permissions / ACL). Catalog (KMK block): `zip4j` 2.11.6, `junrar` 8.1.2, `commons-compress` 1.28.0, `xz` 1.12, bundle `archive-desktop`. Android ABI (`javap -protected`, `core/archive/build/tmp/kotlin-classes/debug`): additions only (one class, `CbzKeyStore`, the `internal` object). `DesktopArchiveTest` (`:core:archive:desktopTest`, 9 tests): cbz from `java.util.zip` (incl. a directory entry), cbz from the desktop `ZipWriter`, password-protected cbz for all three encryption types (correct / wrong / missing password), gzipped tar, 7z, epub image list, key file (32 bytes, `rw-------`, regenerated by `deleteKeyCbz`) |
| 3.1 `:domain` desktop target | done | `2288f1e803` | `mihon.library.kmp` (sources stay in `src/main/java`, tests in `src/test/java` run on both targets through `jvmSharedTest`). Build file (KMK blocks): UniFile, `coroutines-android` and `serialization-xml-core` → `androidMain`; `kotlinx.bundles.coroutines` / `.serialization` replaced by their members; desktop `xmlutil:core` + `compileOnly(platform(compose.bom))` for the version of the compileOnly `runtime-annotation` (`@Immutable`). `androidx.paging.PagingSource` (`SourceRepository`) resolves from paging-common's JVM variant through `sqldelight-android-paging` unchanged; logcat / Injekt are JVM already. `StorageManager` stays shared: the Android-only body of the KMK `allowAccessStorage` moved to `internal expect fun allowAccessStoragePlatform` (Android `actual` = the moved code, desktop = folder picker callback). Compat: `android.annotation.SuppressLint`, `android.graphics.drawable.Drawable` (type only). Tests: `:domain:testReleaseUnitTest` 75/75, `:domain:desktopTest` 75/75; `:domain:compileKotlinDesktop`, `:domain:compileDebugKotlinAndroid`, `:app:compileDebugKotlin` pass |
| 3.2 `:data` desktop target | done | `06330245bb` | `mihon.library.kmp` (sources stay in `src/main/java`, `.sq` / `.sqm` stay in `src/main/sqldelight`, set through `srcDirs(...)` because KMP SQLDelight defaults to `src/commonMain/sqldelight`). SQLDelight now generates once into `commonMain` (`generateCommonMainDatabaseInterface`), compiled by both targets (metadata compilations are skipped for this JVM-only hierarchy, so the generated code's JDK / `source-api` types are fine); CI's `verifySqlDelightMigration` still works (`verifyCommonMainDatabaseMigration` instead of `verify{Debug,Release}DatabaseMigration`). **Driver: the same `sqldelight-androidx-driver` 0.0.17 (its `-jvm` variant) over `androidx.sqlite:sqlite-bundled` 2.6.2 (`-jvm`, natives in the jar)**. `desktopMain/…/DesktopDatabase.kt`: `<dataDir>/databases/tachiyomi.db`, schema create / migrate by the driver from `user_version`, foreign keys on, statement cache 25, `busy_timeout = 3000`, same column adapters as `AppModule`; handler = the existing `AndroidDatabaseHandler` (plain JVM). No source changes: `ReleaseServiceImpl` (`Build.SUPPORTED_ABIS`, compat = `os.arch`) and `CustomMangaRepositoryImpl` (`getExternalFilesDir(null)` → `<dataDir>/files/edits.json`) compile against the compat layer; `@SuppressLint` from 3.1. `DesktopDatabaseTest` (`:data:desktopTest`, 4 tests): new DB at `Schema.version` with foreign keys on; manga + 2 chapters + category through `MangaRepositoryImpl` / `ChapterRepositoryImpl` / `CategoryRepositoryImpl` and read back (library, favorites, categories by manga); data survives reopen; **migration smoke test**: `src/main/sqldelight/28.db` marked `user_version = 28`, opened → migrated to the current version with exactly the same tables / views / indexes / triggers as a fresh DB. `:data:compileKotlinDesktop`, `:data:compileDebugKotlinAndroid`, `:data:generateSqlDelightInterface`, `:data:verifySqlDelightMigration`, `:app:compileDebugKotlin` pass |
| 3.3 `:source-local` desktop target | done | `2b574a02ce` | `mihon.library.kmp` instead of `mihon.library` + `kotlin("multiplatform")`: `src/commonMain/kotlin` (the `expect` classes, `Archive`, `Format`) compiles through `jvmSharedMain`, `src/androidMain/kotlin` stays the Android source set. **No file moved, no source edits:** every file in `src/androidMain/kotlin` (`LocalSource`, the `actual` `LocalCoverManager` / `LocalSourceFileSystem`, `OrderBy`, epub metadata) compiles for desktop as well — `desktopMain` adds that directory as a second source dir (build file, KMK block) — against the compat `Context` / `UniFile` / `DiskUtil`, the desktop `ImageUtil` subset (`isImage`), core:archive's desktop `ArchiveReader` / `ZipWriter` / `archiveReader` / `epubReader`, and a new compat `nl.adaptivity.xmlutil.core.AndroidXmlReader` (ComicInfo parsing). UniFile → `androidMain`; desktop gets the serialization members + JVM xmlutil core. `DesktopLocalSourceTest` (`:source-local:desktopTest`, 2 tests): storage dir through `StoragePreferences` → `StorageManager` → `LocalSourceFileSystem`; popular listing; details from a CBZ's `ComicInfo.xml` (copied to the manga folder); chapters from a CBZ and an image directory (name from ComicInfo, sort order, non-chapter files ignored); cover extracted from the first chapter into `cover.jpg`; `getFormat`. `:source-local:compileKotlinDesktop`, `:source-local:compileDebugKotlinAndroid`, `:desktop:android-compat:test` pass |
| 3.4 Desktop DI bootstrap | done | `a992700189` | `app.komikku.desktop.di.DesktopInjekt.init(dataDir)` imports three Injekt modules: `DesktopPreferenceModule` (main `PreferenceStore` = the process-wide `DesktopPreferenceStores` instance under `<dataDir>/preferences`, `NetworkPreferences`, `SecurityPreferences`, `PrivacyPreferences`, `DelegateSourcePreferences`, `StoragePreferences` over `DesktopStorageFolderProvider`, + shared `registerDomainPreferences()`), `DesktopAppModule` (compat `Application`, `SqlDriver` / `Database` / `DatabaseHandler` from `DesktopDatabase` at `<dataDir>/databases/tachiyomi.db`, `Json` / `XML` / `ProtoBuf` with the `AppModule` configuration, `NetworkHelper`, `JavaScriptEngine`, `StorageManager`, `LocalSourceFileSystem`, `LocalCoverManager`) and `DesktopDomainModule` (shared `registerDatabaseRepositories(app)` + `registerLibraryInteractors()`). **Shared registration functions (new KMK files in `jvmSharedMain`, no upstream file touched):** `data/…/tachiyomi/data/di/DatabaseRepositories.kt` (16 DB-only repositories incl. SY custom info / merged / metadata / favorites / saved searches / feeds and KMK library-update errors), `domain/…/tachiyomi/domain/di/LibraryInteractors.kt` (6 `:domain` preference classes; 89 interactors: category, manga, chapter, history, track, updates, saved searches / feeds, library-update errors). Smoke screen (`Main.kt`, Material 3): library manga count and category names from `GetLibraryManga.subscribe()` / `GetCategories.subscribe()` (existing MR strings only); not launched. `DesktopInjektTest` (`:desktopApp:test`, 3 tests): graph boots against a `@TempDir` data dir; singletons, network cache dir and one interactor per family resolve; `GetLibraryManga` empty, `GetCategories` = system category only, `CreateCategoryWithName` → visible through `GetCategories`, DB file in `<dataDir>/databases`; a library preference persists to `<dataDir>/preferences`. Final run: `spotlessCheck`, `:domain:{compileKotlinDesktop,compileDebugKotlinAndroid,testReleaseUnitTest,desktopTest}`, `:data:{compileKotlinDesktop,compileDebugKotlinAndroid,generateSqlDelightInterface,verifySqlDelightMigration,desktopTest}`, `:source-local:{compileKotlinDesktop,compileDebugKotlinAndroid,desktopTest}`, `:desktopApp:{compileKotlin,test}`, `:app:compileDebugKotlin` all pass. **Milestone A reached** (non-UI stack — preferences, network, JS engine, archives, metadata, source API, domain, database, local source — compiles for desktop and runs in desktop JVM tests; the desktop app itself was compile-checked only) |
| 4.0 "BOM wins" for KMP Compose modules | done | `2f6e2a22c5` | New convention plugin `mihon.library.kmp.compose` (`buildSrc/…/mihon.library.kmp.compose.gradle.kts` + `mihon/buildlogic/KmpCompose.kt`): `mihon.library.kmp` + compose compiler + Android `buildFeatures.compose` + compiler metrics/reports flags; **Compose is resolved per target**: `androidMain` gets `platform(compose.bom)` and the module's androidx `compose.*` coordinates, `desktopMain` gets JetBrains `libs.compose.multiplatform.*` (runtime by default). JetBrains coordinates are *not* used on Android (see deviations: their CMP 1.12 Android variants require androidx Compose 1.12.1 while the BOM pins 1.11.4). Catalog (KMK blocks): `compose-multiplatform-{runtime,foundation,animation,animation-graphics,ui,ui-backhandler,ui-util,ui-tooling-preview,ui-test-junit4,material-icons}` + bundle `compose-multiplatform`; desktop material3 `1.9.0` → `1.11.0-alpha03` (the JetBrains build of androidx material3 `1.5.0-alpha14`, the BOM's version, so shared code sees the same Material 3 API); `kotlinx.coroutines-swing`. Evidence: see 4.1 |
| 4.1 `:presentation-core` desktop target | done | `2f6e2a22c5` | `mihon.library.kmp.compose`; sources stay in `src/main/java`, `res/` stays Android. Build file (KMK blocks): androidx Compose + `activity-compose` + `paging-runtime` → `androidMain`; desktop: `libs.bundles.compose.multiplatform` + `:desktop:compose-compat`; `paging-compose` stays top-level (it has a desktop variant). **No source file moved or edited**: the Android-only APIs presentation-core uses resolve on desktop through compat FQNs — new desktop-only module **`:desktop:compose-compat`** (`LocalContext` (default: lazily created compat `Application`), `androidx.activity.compose.BackHandler` → CMP back handler (Escape), `Modifier.systemGestureExclusion` (no-op), `WindowInsets.isImeVisible` (false)) and `android.view.ViewConfiguration` in `:desktop:android-compat` (AOSP default timeouts / sizes). `Localize.kt` (`LocalContext.current.stringResource`), `Scrollbar.kt` / `VerticalFastScroller.kt` (`ViewConfiguration`), `AdaptiveSheet.kt` (`BackHandler`), `Modifier.kt` (`isImeVisible`), `PagingDataUtil.kt` (paging-compose desktop) compile unchanged. **Android resolved versions unchanged:** `:app:dependencies` for `debugRuntimeClasspath` and `debugCompileClasspath`, every `group:artifact:resolvedVersion` before vs after: identical sets (383 coordinates at runtime; e.g. `androidx.compose.ui:ui:1.11.4`, `androidx.compose.material3:material3:1.5.0-alpha14`, `androidx.compose.foundation:foundation:1.11.4`, `androidx.compose.runtime:runtime:1.11.4`, `material-icons-extended:1.7.8`); only the tree order changed. `:presentation-core:compileKotlinDesktop`, `:presentation-core:compileDebugKotlinAndroid`, `:presentation-widget:compileDebugKotlin`, `:app:compileDebugKotlin` pass |
| 4.2 `:app-shared` | done | `ceb7099908` | New KMK module `:app-shared` (`mihon.library.kmp.compose`, namespace / package root `app.komikku.shared`; own code in `src/main/kotlin` (shared), `src/desktopMain/kotlin`). `app` depends on it (KMK block). **Source borrowing (D5) works:** allowlist `app-shared/borrowed-app-sources.gradle.kts` (the only list; kinds `shared` → `jvmSharedMain`, `androidOnly` → `androidMain`, `perPlatform` → `androidMain` + `desktopMain`), mechanism `buildSrc/…/mihon/buildlogic/SourceBorrowing.kt` (`applyAppSourceBorrowing()` called by both build files): `:app-shared` `Sync` tasks copy the listed files from `app/src/main/java` to `build/generated/borrowed/<kind>/kotlin` (source dirs of the matching source sets; a missing file fails the sync), `app` excludes exactly those paths from every `KotlinCompile` task, and **`:app:verifyBorrowedSources`** (wired into `:app:check`, runnable standalone) inspects the real inputs of `:app:compileDebugKotlin`, `:app-shared:compileDebugKotlinAndroid` and `:app-shared:compileKotlinDesktop` and fails if a listed file is missing, compiled by `app`, or not compiled by `:app-shared` for the platforms its kind says (verified: passes with 28 files; fails on a missing file). **Borrowed (28 files):** theme — `TachiyomiTheme.kt` (perPlatform), 21 colour schemes incl. `CustomColorScheme.kt` (material-kolor), `MonetColorScheme.kt` (androidOnly; desktop counterpart = Tachiyomi colours), `UiPreferences.kt`, `ui/model/{AppTheme,TabletUiMode,ThemeMode}.kt`; navigation — `presentation/util/Navigator.kt` (Voyager `Screen` / `Tab` base types, `DefaultNavigatorScreenTransition`, `ScreenTransition`). Only source edit: `AndroidViewColorScheme` (Android views) moved out of the KMK-owned `CustomColorScheme.kt` into a new `AndroidViewColorScheme.kt` (same package, app-only). New shared code: `AppNavigatorHost` (root Voyager navigator + default transition). Desktop: `DesktopImageLoader` (Coil 3: OkHttp fetcher over `NetworkHelper.client`, disk cache `<cache>/image_cache` 2 %, memory cache 25 %, IO-limited fetcher/decoder dispatchers) + `DesktopMangaCoverFetcher` / `DesktopMangaCoverKeyer` (`MangaCover`: http(s) with the source's client + headers when it is an `HttpSource`, else the app client, stored in Coil's disk cache; `file:` URIs / absolute paths from disk). Compat: `androidx.appcompat.app.AppCompatDelegate` (static night mode + `defaultNightModeFlow`). Android resolved versions unchanged (`debugRuntimeClasspath` / `debugCompileClasspath` coordinate sets identical to the pre-4.0 baseline). `:app-shared:compileKotlinDesktop`, `:app-shared:compileDebugKotlinAndroid`, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources` pass |
| 4.3 Desktop shell | done | `f9059e55e9` | `:desktopApp` now depends on `:presentation-core` + `:app-shared` and declares `kotlinx-coroutines-swing` (Swing `Dispatchers.Main`). `main()` → `KomikkuWindow` (title `MR.strings.app_name`, minimum 480×360) → `KomikkuApp(context, shortcuts)`: compat `LocalContext` = the DI `Application`, Coil singleton = `DesktopImageLoader` (cache in `<dataDir>/cache/image_cache`), `DesktopTheme` (shared `TachiyomiTheme` from `UiPreferences` app theme / AMOLED; `ThemeMode` light / dark / system through Compose's `LocalSystemTheme`), `HomeShell`: presentation-core `NavigationRail` + M3 `NavigationRailItem`s for **Library / Updates / History / Browse / More** as Voyager `Tab`s (borrowed `eu.kanade.presentation.util.Tab`) in a `TabNavigator`; labels `MR.strings.label_library` / `label_recent_updates` / `label_recent_manga` / `browse` / `label_more`, material icons. **Library:** `LibraryScreenModel` (`StateScreenModel`, `GetCategories.subscribe()` + `GetLibraryManga.subscribe()` → `libraryPages()`: visible categories in user order, default category only when it has manga or is the only one, manga sorted by title), categories as `PrimaryScrollableTabRow` tabs, `LazyVerticalGrid` (adaptive 140 dp) of covers (`AsyncImage(manga.asMangaCover())`) + titles; `LoadingScreen` / `EmptyScreen(information_empty_library)` / `information_empty_category`. **Browse:** `SourceRepository.getSources()` grouped by language (`ListGroupHeader` + `ListItem`), `EmptyScreen(KMR.desktop_no_sources)`. **Updates / History / More:** `PlaceholderTab` → `EmptyScreen(KMR.desktop_not_available_yet)`. **Window state:** `DesktopWindowPreferences` (`desktop_window_{x,y,width,height,maximized}` in the main store) restored by `rememberPersistedWindowState` (off-screen positions → platform default, size clamped) and saved 500 ms after the last move / resize / maximize (floating bounds kept while maximized). **Shortcuts:** `DesktopShortcuts` on the window's `onPreviewKeyEvent`: Ctrl+1…5 (Cmd on macOS, digits and numpad) → `Action.SelectTab`, handled by `HomeShell`. **DI (`DesktopModules`):** `UiPreferences`, `DesktopWindowPreferences`; `SourceManager` = new `DesktopSourceManager` (local source only — `LocalSource(app, fileSystem, coverManager, allow_local_source_hidden_folders)`; unknown ids → DB `StubSource`s; merged sources through `GetMergedReferencesById`), `SourceRepository` = `SourceRepositoryImpl`, `GetRemoteManga`, `GetSourcesWithNonLibraryManga`. New KMR strings (base only): `desktop_not_available_yet`, `desktop_no_sources`. Tests (`:desktopApp:test`, 14, each class in its own JVM via `forkEvery = 1` because Injekt is global): `DesktopInjektTest` + source manager / repository / `UiPreferences`; `LibraryPagesTest`; `DesktopShortcutsTest`; `WindowPlacementTest`; **`DesktopShellTest`** — headless `runDesktopComposeUiTest` (offscreen scene, no display): real graph + DB with one category / manga, rail labels present, library shows the manga, rail click → Updates placeholder, shortcut action → Browse lists the local source. `:desktopApp:compileKotlin`, `:desktopApp:test` pass; the app was not launched (`:desktopApp:run` not used) |
| 5.1 `:desktop:extension-runtime` | done | `e2dc7463fc` | New desktop-only KMK module (`kotlin("jvm")`, package `app.komikku.desktop.extension`, written from scratch). **APK reader:** `BinaryXml` (compiled XML: UTF-8 / UTF-16 string pools, resource map, element tree; `android:*` attributes matched by framework resource id like the platform), `ApkManifest` (package, `versionCode` + `versionCodeMajor`, `versionName`, literal or `@string` label, icon reference, `<uses-feature>`, `<meta-data>` typed like `ApplicationInfo.metaData` with `Bundle` getter semantics), `ResourceTable` (minimal `resources.arsc`: simple entries per configuration incl. sparse / offset16 / compact entries → `@string` label and highest-density bitmap launcher icon), `ApkInspector` (manifest + resources + signatures + file SHA-256). **Extension contract:** `ExtensionContract` duplicates `ExtensionLoader`'s private constants (feature, metadata keys, `SUPPORTED_LIB_VERSIONS = [1.4, 1.6]`; comment: keep in sync); `ExtensionPackage.from` applies the same checks and derivations (name from `tachiyomix.name` or label without `Tachiyomi: `, lib version from the float meta-data else the version name, NSFW from content warning / legacy flag, `.`-relative class names, signature list). **Signatures:** `ApkSignatures` — APK Signature Scheme v3.1 → v3 → v2 (signing block before the central directory; every signer's signature over its signed data verified with the certificate's key, RSA-PSS / PKCS#1 / ECDSA / DSA with SHA-256/512, and the 1 MiB chunked content digests of entries + central directory + patched EOCD), else v1 through the JDK's verifying `JarFile`; result = SHA-256 of the signers' DER certificates (Android's `Signature.toByteArray()` hash). **dex → jar:** `DexJarConverter` on `de.femtopedia.dex2jar:dex-translator:2.4.39` (Maven Central, **Apache-2.0**; maintained fork of dex2jar; brings ASM 9.11.1 + `d2j-external` = patched ASM core + AOSP dx, Apache-2.0 / BSD-3), multi-dex, untranslatable methods reported (dex2jar's throwing stub), cached as `<dataDir>/extensions/jars/<apk sha256>.jar` (temp file + atomic move, per-hash lock, `prune`). **Class loading:** `ExtensionClassLoader` (desktop `ChildFirstPathClassLoader`: JDK platform loader → host for shared package prefixes (`kotlin(x)`, `android(x)`, `eu.kanade.tachiyomi.{source,network,util}`, `tachiyomi`, `okhttp3`, `okio`, `org.jsoup`, `rx`, `uy.kohesive.injekt`, `app.cash.quickjs`, UniFile, …; falls through to the jar when the host lacks the class) → jar → host); `ExtensionRuntime` (inspect → convert → load; `instantiateSources` = `ExtensionLoader`'s `Source` / `SourceFactory` instantiation). Catalog (KMK blocks): `dex2jar-translator`, test-only `bouncycastle-pkix` (libs) and `apksig` (androidx, AGP's version). **Tests (`:desktop:extension-runtime:test`, 19, no network, fixtures generated in `src/testFixtures` via `java-test-fixtures`):** `AxmlWriter` / `ArscWriter` (test-side binary manifest / resource table writers), `TestSigningKey` (BouncyCastle self-signed RSA / EC certs), `TestApkSigner` (**apksig** signs v1 / v2 / v3, an implementation independent of the verifier), `FixtureApk`, `FixtureClasses` (ASM Java 8 classes, jar, **AOSP dx → `classes.dex`**). `ApkManifestTest` (UTF-8 + UTF-16 pools, id-only attributes, `versionCodeMajor`, arsc label + xxxhdpi icon), `ApkSignaturesTest` (v2 RSA, v2 EC, v3 preferred over v2 / v1, v1-only, unsigned, tampered entry fails v2 and v1), `ExtensionPackageTest` (valid package, name / lib-version derivations, NSFW flags, every rejection), `ExtensionClassLoaderTest` (Kotlin `Source` + `SourceFactory` from a jar: child-first copies, host `Source` identity, a suspend call through host coroutines; shadowed `okhttp3.OkHttpClient` → host's; extension-only class in a shared package → jar; non-source / missing class), `DexPipelineTest` (**full pipeline**: ASM class → dx → signed APK → inspect → dex2jar → class loader → `Source` with the expected id / name; cache hit on reload; prune) |
| 5.2 Desktop extension manager | done | `ecde14836c` | **Borrowed into `:app-shared` (shared, 9 files):** `SourcePreferences.kt`, `SetMigrateSorting.kt`, `mihon/domain/migration/models/MigrationFlag.kt`, `extension/model/InstallStep.kt`, `extension/model/LoadResult.kt`, `domain/extension/model/Extensions.kt`, `GetExtensionSources.kt`, `RefreshExtensionStores.kt`, `exh/source/BlacklistedSources.kt`. Upstream edits for that (KMK-marked): `SourcePreferences.enabledLanguages()` default inlined (`setOf("all", "en", Locale.getDefault().language)`, the value of the Android-only `LocaleHelper.getDefaultEnabledLanguages()`), `enum class SourceFilter` moved from `SearchScreenModel.kt` to `app-shared/src/main/kotlin/…/globalsearch/SourceFilter.kt` (same package / name). **Shared KMK interface** `app.komikku.shared.extension.PlatformExtensionManager` (the public surface of Android's `ExtensionManager`: installed / available / untrusted flows, `findAvailableExtensions`, install / update / cancel / uninstall, trust, `getExtensionPackage`, `getSourceData`) + `GetPlatformExtensionsByType` (Android's `GetExtensionsByType` over the interface). Android's `ExtensionManager` is untouched (package manager, receivers, `R`). **`DesktopExtensionManager`** (`:desktopApp`, `app.komikku.desktop.extensions`): installed extensions = `<dataDir>/extensions/apks/<package>.apk`, loaded asynchronously at start (`isInitialized`); loading = `ExtensionLoader` order (runtime checks → trust like `TrustExtension` minus the debug shortcut: Komikku key / saved store keys / `trustedExtensions` entry → NSFW policy → convert + class-load); untrusted → `LoadResult.Untrusted`; SY blacklist; icons as `ExtensionIconDrawable` (compat `Drawable` with the APK's icon bytes). Available = `ExtensionStoreRepository.fetchExtensions(disabledRepos)` (same JSON / protobuf / legacy index parsing in `:data`), newest per `identityKey`, changed-store toasts, first-run sub-languages, update / obsolete / redundant statuses, `extensionUpdatesCount`, stub data. Install = `Pending → Downloading` (`NetworkHelper.client`) `→ Installing → Installed / Error` flow, cancellable; then `installPrivateExtensionFile`'s rules (extension, expected package, no downgrade, same signer) → atomic copy → load → register. Uninstall deletes the APK and prunes converted jars. **`DesktopSourceManager`** now mirrors `AndroidSourceManager`: local source + installed extensions' sources (rebuilt on every change, SY blacklist), stub sources upserted, unknown ids → DB stub → available-extension metadata, `HIDDEN_SOURCES` filtered from visible lists. **DI (`DesktopModules`):** `SourcePreferences` (replaces the hand-written hidden-folders key), extension store service / repository / 6 interactors (incl. `RefreshExtensionStores`), `ExtensionRuntime(<dataDir>/extensions)`, `DesktopExtensionManager` (also bound as `PlatformExtensionManager`), `GetPlatformExtensionsByType`, `GetExtensionSources`. **Test `DesktopExtensionManagerTest`** (`:desktopApp:test`, 7 ordered steps, `MockWebServer` + generated fixtures from `:desktop:extension-runtime` test fixtures): add store (JSON index signed-key recorded) → find available → install (exact step sequence, store label, APK on disk) → source appears in `SourceManager` → update from a v4 index (`hasUpdate`, update count 1 → 0, new source instance) → downgrade and foreign-signer APKs refused → APK signed by an unknown key is untrusted until `trust` (pref entry written) → a second manager reloads the extension from disk (one cached jar) → uninstall removes sources / APK, `getOrStub` still names the source. `:desktopApp:compileKotlin`, `:desktopApp:test` (21 tests), `:app-shared:compileKotlinDesktop`, `:app-shared:compileDebugKotlinAndroid`, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources` pass |
| 5.3 Link-check tool | done | `31523428e6` | `app.komikku.desktop.extension.linkcheck.LinkChecker` (`:desktop:extension-runtime`, ASM): reads every class of a jar, resolves each referenced class / method / field the jar does not define (super types, descriptors, field / method / `invokedynamic` / `ldc` handles, `new` / casts, catch types) against the host class path (JVM rules: owner, superclasses, superinterfaces; class files read as resources, never loaded), skipping signature-polymorphic `MethodHandle` / `VarHandle` calls and array members; report = missing classes / methods / fields with the referencing classes. Gradle `JavaExec` task **`:desktop:extension-runtime:linkCheck`** (runtime class path of the runtime module = compat + `source-api` + `core:common` + network stack + JDK; APKs converted with dex2jar into a temp dir; directories scanned for `.apk` / `.jar`; `--output=`, `--strict`), usage in the "Link check" section below. `LinkCheckerTest` (2): generated jar referencing compat symbols that exist (`Base64.encodeToString`, `Build.VERSION.SDK_INT`, `Source.getLang`, an inherited member through the jar's own class), a missing class (`android.graphics.BitmapFactory` + `Bitmap` from its descriptor), a missing method and field on existing compat classes, an internal class and the JDK → exactly the three missing kinds reported; clean jar. Smoke-run of the task on a local jar. **Not run on real extensions** (no extension APKs downloaded, per scope) |
| 5.4 Desktop browse / extensions UI | done | `dc262308b1` | **Browse** (`BrowseTab`) now hosts its own Voyager navigator (`AppNavigatorHost`, rail stays visible, Escape / back arrows pop) starting at `BrowseHomeScreen`: **Sources / Extensions** tabs like Android. **Sources:** `SourceRepository.getSources()` by language; click → catalogue; settings icon for `ConfigurableSource`s. **Extensions** (`ExtensionsScreenModel` over `PlatformExtensionManager` + `GetPlatformExtensionsByType` + store interactors; refresh on open): updates pending (+ update all) / installed / untrusted / available (per source, like Android) with icons (installed: APK icon bytes, available: index icon URL, through Coil), version · language · store · 18+ / orphaned / redundant, actions install / update / uninstall / trust (confirmation dialog with Android's warning) / cancel, progress label + spinner per entry, install failures as snackbars; **extension stores dialog** (list, remove, add by index URL with processing / error state). **Source preferences** (`SourcePreferencesScreen` → `SourcePreferencesContent` → `PreferenceScreenContent`): built like Android's `SourcePreferencesScreen` (`PreferenceManager` over `source_<id>`, `setupPreferenceScreen`), rendered in Compose: categories (headers + indented children), `SwitchPreferenceCompat` / `SwitchPreference` (switch), `CheckBoxPreference` (check box), `ListPreference` / `DropDownPreference` (radio dialog), `MultiSelectListPreference` (check box dialog), `EditTextPreference` (text dialog honouring the extension's `OnBindEditTextListener`: number keyboard / password masking from `inputType`, `error` from text watchers blocks OK), plain clickable `Preference`s; values through `callChangeListener` (veto), summaries incl. `%s` / summary providers / `summaryOn` / `summaryOff`, compat "Not set" localized, visibility and dependency-disabled state, recomposition on `PreferenceManager` change events. **Catalogue** (`CatalogueScreen`): popular / latest (if supported) / search, entries stored through `NetworkToLocalManga` (as Android's browse paging), cover grid (`MangaCover` → desktop Coil fetcher with the source's headers), load more, error / empty states. **Details** (`MangaDetailsScreen`): cover, title, author / artist, genres, description and chapter list from `Source.getMangaUpdate` (details written back with `UpdateManga`, chapters shown, not stored), **add to / remove from library** via `UpdateManga.awaitUpdateFavorite` (borrowed `eu/kanade/domain/manga/interactor/UpdateManga.kt`, registered in desktop DI). **Snackbar host** (`DesktopToastHost`) replaces `Toast.presenter` while the window is composed. New KMR strings (base): `desktop_ext_available`, `desktop_ext_refresh`, `desktop_ext_install_failed`, `desktop_ext_store_add_failed`, `desktop_load_more`, `desktop_pref_not_set`, `desktop_source_error`, `desktop_source_not_installed`; everything else reuses MR / SYMR. **Headless UI tests** (`runDesktopComposeUiTest`, real graph + DB): `BrowseCatalogueUiTest` (in-test `CatalogueSource` + `ConfigurableSource` through a test `SourceManager`: toast → snackbar; sources → popular → latest → search; details with description + chapters; add to library verified in the DB; source settings: switch, list dialog, edit text (number input), category + check box, each value verified in `source_<id>` preferences), `ExtensionsUiTest` (`MockWebServer` store index + generated signed APK: empty state → add store in the dialog → available → install → installed section → source listed in Sources). `:desktopApp:test` 23 tests pass; `:app-shared` both targets, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources`, `spotlessCheck` pass. The GUI was not launched |
| 6L.1 Manga details that persists | done | `c091f41499`, `4380ff2e03` | **New borrowing kind `desktopCopy`** (`SourceBorrowing.kt` + allowlist): the file is still compiled by `app` for Android, unchanged, and a copy is compiled by `:app-shared` `desktopMain`; `:app:verifyBorrowedSources` checks both (also that a copy is *not* compiled by `:app-shared` for Android). Used for Android-entangled files the desktop needs; desktop counterparts of what they reference live in `app-shared/src/desktopMain` (table below). Borrowed for 6L.1 (`desktopCopy`): `BasePreferences`, `ExtensionInstallerPreference`, `SetReadStatus`, `SyncChaptersWithSource`, `domain/chapter/model/Chapter.kt`, `DeleteDownload`, `GetExcludedScanlators`, `SetMangaViewerFlags`, `domain/manga/model/Manga.kt`, `GetIncognitoState`, `CoverCache`, `data/database/models/Chapter.kt` + `ChapterImpl.kt`, `DownloadProvider`, `UpdateMangaFromRemote`, `RemoteMangaUpdate`; (`shared`): `ChapterRemoveDuplicates.kt`, `DataSaver.kt`, `OkioExtensions.kt`. Only upstream edit: `SetReadStatus.kt` KDoc links fully qualified and their 5 imports removed (the screen models they named do not exist on desktop). **Desktop details screen** (`ui/manga/MangaDetailsScreen` + `MangaDetailsScreenModel`): `GetMangaWithChapters.subscribe(applyFilter = true)`, the manga's unread / bookmarked / downloaded filters (Android `applyFilters` rules) and sort (`getChapterSort`), first open fetches when uninitialized / no chapters, refresh = `UpdateMangaFromRemote` (→ `SyncChaptersWithSource`), sort menu (`SetMangaChapterFlags.awaitSetSortingModeOrFlipOrder`), selection bar (mark read / unread via `SetReadStatus`, bookmark / unbookmark via `UpdateChapter`, mark previous as read), per-chapter read alpha, bookmark icon, page progress, upload date, scanlator; library add / remove with Android's default-category rules + categories dialog (`SetMangaCategories`); continue reading (`GetNextChapters`) → reader. Library grid opens details (library tab has its own navigator). DI: the borrowed interactors / preferences + `ChapterCache`, `CoverCache`, `DownloadProvider`, desktop `DownloadManager`. New KMR string: `desktop_refresh`. Tests: `ReaderLogicTest` (mark-previous order), `ReaderUiTest` (details fetch + store, mark read / unread from the selection, continue reading) |
| 6L.2 Updates / History tabs, library refresh | done | `89652e058d` | **Shared runner** (`app-shared` `jvmSharedMain`, `app.komikku.shared.library`): `LibraryUpdateRunner` interface (targets single / category / all, `StateFlow` progress: total, done, current titles, new chapters, failures, skipped, finished / cancelled, `cancel()`), `LibraryUpdateSelection` (Android `LibraryUpdateJob.addMangaToQueue` default-grouping path: category or included / excluded update categories, "only fetch once", smart-update restrictions, job order) and `CoroutineLibraryUpdateRunner` (3 in parallel). Desktop wiring `desktopLibraryUpdateRunner`: `UpdateMangaFromRemote` per entry like the job's `updateManga` (metadata per `autoUpdateMetadata`, `FetchInterval` window), failures stored with `InsertLibraryUpdateErrors` / `…Messages`. Android's WorkManager job untouched. UI: library tab "Update category" / "Update library" + progress banner with cancel; **Updates tab** (`GetUpdates.subscribe`, last 90 days by upload date like the query, grouped by fetch day, read alpha, page progress; row → reader, cover → details, "Update library" action + banner); **History tab** (`GetHistory.subscribe`, latest chapter per manga, grouped by day; row → details, resume = `GetNextChapters.await(manga, chapter, onlyUnread = false)` like `HistoryScreenModel`, remove entry (`RemoveHistory.await`), clear all with confirmation). Tests: `LibraryUpdateRunnerTest` (selection rules / order; progress, new chapters, failures), `RecentTabsUiTest` (refresh all → chapters stored; Updates → reader; History → resume, remove, clear) |
| 7.1 Page loading shared layer | done | `c091f41499`, `4380ff2e03` | Borrowed (`desktopCopy`): `ui/reader/model/*` (6 files), `loader/{PageLoader,ChapterLoader,HttpPageLoader,ArchivePageLoader,DirectoryPageLoader,EpubPageLoader,DownloadPageLoader}.kt`, `ChapterCache.kt` (same DiskLruCache key scheme, `<dataDir>/cache/chapter_disk_cache`, size from `ReaderPreferences.cacheSize()`), `ReaderPreferences.kt`, `ReaderOrientation.kt`, `ReaderBottomButton.kt`, `viewer/MissingChapters.kt`. The Android `ChapterLoader` picks loaders exactly as on Android (download → local directory / archive / epub → HTTP). `ReaderLoadersTest`: in-test `HttpSource` over `MockWebServer` (page list request, images downloaded into the chapter cache, page list cached on recycle and reused without a request), generated CBZ (non-image entry ignored, natural order) and image folder of the local source |
| 7.2 Image decoding | done | `4c3696b1d5`, `4380ff2e03` | Shared (`app-shared` `jvmSharedMain`, `app.komikku.shared.reader.image`): `PageFit` (values of `ReaderPreferences.imageScaleType()`), `PageScaling` (display size per fit, decode size: no upscaling + pixel budget, strips ≤ 4096 rows, strip → source rows), `ByteSizedLruCache`, `PageImageDecoder<T>` interface, `UnsupportedPageFormatException`. Desktop: `SkiaPageDecoder` (decode once, draw into mipmapped strips at the target width, release the full-size image; `Dispatchers.IO.limitedParallelism(2)`; cancellation checks between steps) + `DecodedPageCache` (256 MB by decoded bytes). JPEG / PNG / WebP / GIF / BMP decode; **animated GIF / WebP show the first frame only** (`animated` flag set); **AVIF / JPEG XL / HEIF are not decodable by Skia on desktop** → error placeholder naming the format. `ReaderImageTest` (fit math, decode size, tiles, LRU, PNG / JPEG / GIF / BMP decode + downsample, 5000 px strip → 3 tiles, AVIF + junk errors) |
| 7.3 Paged viewer | done (double pages basic) | `4c3696b1d5` | `ReaderViewers.kt` `PagedViewer`: LTR / RTL / vertical, fit screen / width / height / original / stretch / smart, zoom (Ctrl/Cmd + wheel, +/-/0, double-click) + pan (drag, wheel scrolls tall pages before turning), click zones (thirds, direction-aware; vertical: top / bottom), neighbours ±1/+2 loaded and decoded ahead, page indicator, per-page loading (download %), error + retry (`PageLoader.retryPage`), chapter transition items (`ChapterTransitionContent`: current / next names, missing-chapter warning via borrowed `calculateChapterGap`, next chapter's loading / error state). Double pages (`ReaderPreferences.pageLayout()` = double): current + next page side by side in reading order, steps by 2; no automatic / wide-page detection |
| 7.4 Webtoon viewer | done (basic) | `4c3696b1d5`, `b5ed1cf4d5` | `WebtoonViewer`: `LazyColumn` of the chapter's items, side padding from `webtoonSidePadding()`, strips stacked, stable item heights once a page's aspect is known (kept after cache eviction), native wheel scrolling, keyboard scroll (`animateScrollBy`), Ctrl+wheel zoom, reaching the next-chapter item opens the next chapter. `ReaderUiTest` (settings → webtoon, keyboard scroll to the end saves progress) |
| 7.5 Reader chrome and input | done | `4c3696b1d5`, `c766fdd206`, `b5ed1cf4d5` | **Full-window screen, not a second window** (`ReaderHost` above the shell in the main window, opened through `ReaderLauncher`): one window to manage, shared fullscreen state (`WindowPlacement.Fullscreen` via F11 / F / toolbar), works with the single-window headless test harness, any tab can open it. Top bar (manga + chapter, bookmark, fullscreen, settings), bottom bar (previous / next chapter, page slider), page number, menu toggle (middle click zone, M, Enter), `ReaderKeyMap` (arrows by direction, PgUp/PgDn, Space/Shift+Space, Home/End, N/P, +/-/0, F11/F, Esc closes settings → menu → reader). Settings panel bound to the borrowed `ReaderPreferences` (same keys as Android): per-manga reading mode and orientation through `SetMangaViewerFlags` (orientation stored only), scale type, double pages, webtoon side padding, background (white / black / gray / auto), page number, custom brightness (dim overlay), custom colour filter (ARGB + blend mode from `ColorFilterMode`), grayscale, invert, skip read / filtered / duplicates. `ReaderKeyMapTest` |
| 7.6 Progress and side effects | done | `4c3696b1d5`, `4380ff2e03` | Shared `ReaderChapterList` (Android `ReaderViewModel.chapterList`: skip read / filtered (selected kept), ascending in the manga's sort, skip duplicates, downloaded only) and `ReaderProgressSaver` (`updateChapterProgress` / `…OnComplete` / `updateHistory` / bookmark: incognito and failed pages skipped, read at the last page (or second-to-last with an extra page), E-Hentai "later chapters read", duplicate-read marking, history with session duration). **Tracker hook:** `ReaderTrackerHook` (desktop registers `None`; Phase 8). Delete-after-reading / download-ahead skipped (no downloads). Incognito = borrowed `GetIncognitoState`. Writes are serialized and flushed before the history write on close. `ReaderLogicTest`, `ReaderUiTest` (page turns → `last_page_read`, last page → read, bookmark, history on close) |
| 8.1 Downloads | done | `d6bb83e415` | **Android download stack borrowed (`desktopCopy`, unchanged):** `DownloadManager`, `Downloader`, `DownloadCache`, `DownloadStore`, `DownloadPendingDeleter`, `model/Download.kt` (the 6L stand-ins `DownloadManager` / `Downloader` are gone). Desktop counterparts (same FQNs, `app-shared/src/desktopMain`): `DownloadJob` (WorkManager worker → coroutine runner with the same companion API; `start` = REPLACE, running while the downloader runs, `isRunningFlow`), `DownloadNotifier` (notifications → `DownloadEvent`s to the KMK `DownloadNotificationSink` in DI; desktop: `DesktopDownloadEvents`, a `SharedFlow` + log), `NotificationHandler.openUrl` (returns the URL), `LibraryUpdateNotifier.HELP_WARNING_URL`, `eu.kanade.tachiyomi.extension.ExtensionManager` = `typealias` of `PlatformExtensionManager` (`DownloadCache` waits for `isInitialized`). `core:common` desktop `ImageUtil.splitTallImage` (javax.imageio region decoding, same naming / 3:1 rule / JPEG parts as Android; optimal height = 2 × tallest screen, headless 2 × 2160; WebP / AVIF / JXL / HEIF / animated left unsplit). Compat: `androidx.core.content.edit` (SharedPreferences ktx). `:app-shared`: serialization plugin + protobuf / xml deps (desktop). Shared borrowing: `presentation/manga/MangaScreenConstants.kt` (`DownloadAction`); `ChapterListActions.chaptersToDownload` (Android `runDownloadAction` rules). Same folder layout / names as Android (`<storage>/downloads/<source>/<manga>/<chapter>[.cbz]`), ComicInfo.xml, CBZ option (incl. SY password-protected CBZ via the desktop `ZipWriter`), queue persisted across restarts (`active_downloads` store), "delete after reading" / pending deleter (`chapters_to_delete` store). **UI:** details — per-chapter indicator (download / queued / progress / downloaded / error; menu: start now, cancel, delete), selection download + delete, download menu (next 1 / 5 / 10 / 25, unread, bookmarked); **More tab** (replaces the placeholder): downloaded-only + incognito toggles (`BasePreferences`), download queue entry with count; **Download queue screen** (status, page progress, start / pause, move to top / bottom, cancel one, cancel all); library honours "downloaded only" (local + manga with downloads). Reader: download-ahead (`autoDownloadWhileReading`, Android rules; current chapter checked with `isChapterDownloaded` because `DownloadPageLoader` is file-internal) and delete-after-reading (`removeAfterReadSlots` → `enqueueChaptersToDelete`, `deletePendingChapters` on close). Tests: `DownloadManagerTest` (fake `HttpSource` over `MockWebServer`: folder download with layout + ComicInfo + cache, CBZ download, reader `ChapterLoader` reads both offline, queue reorder / cancel / clear, pending delete + delete incl. empty manga folder), `DownloadUiTest` (headless: indicator download → check mark, "download unread", delete from selection, queue screen cancel one / all, downloaded-only filter), `ImageUtilTest` split case. `ReaderLoadersTest` now expects `DownloadPageLoader` for local chapters, as on Android |

| 8.2 Backup | done | `d9cf461822` | **Borrowed as `shared`** (one copy for Android and desktop; `app` excludes them): `data/backup/models/**` (16 files), `exh/EXHMigrations.kt`, `BackupDecoder`, `BackupOptions`, `RestoreOptions`, creators `Categories` / `ExtensionStores` / `Feed` / `Preference` / `SavedSearch` / `Sources`, restorers `Categories` / `ExtensionStore` / `Feed` / `Manga` / `SavedSearch`. `:app-shared` now depends on `:data` and `kotlinx-serialization-protobuf` on both targets (both already `app` dependencies, so Android's resolved set is unchanged). **Borrowed as `desktopCopy`:** `BackupCreator`, `BackupFileValidator`, `BackupRestorer`, `MangaBackupCreator` (`ReadingMode`), `PreferenceRestorer`. Desktop counterparts (`app-shared/src/desktopMain`): `eu.kanade.tachiyomi.BuildConfig` (`APPLICATION_ID = "app.komikku"` so file names match Android, version from `komikku.version` / manifest), `BackupNotifier` (progress / completion / errors → KMK `BackupProgressListener`), `Notifications` (ids), `BackupCreateJob` / `LibraryUpdateJob` / `AppUpdateJob` (`object`s whose `setupTask` does nothing), `AndroidPreferenceStore(context, sharedPreferences)` (a function returning the compat `SharedPreferences`' `DesktopPreferenceStore`, so restored source preferences are what extensions read), `Context.createFileInCacheDir` (external cache, else cache dir), temporary `TrackerManager` stand-in (no trackers; replaced in 8.3). **Desktop service** `DesktopBackupService`: `create` (temp file next to the target, atomic move on success, so a failed run keeps the old backup; listener gets progress / complete / error), `validate` (missing sources / trackers), `restore` (cancellation reported as "Canceled restore" like `BackupRestoreJob`, then rethrown). Private preferences / app-state keys handled by the borrowed creator exactly as on Android. **UI:** More → Data and storage: storage location (folder chooser; `StoragePreferences.baseStorageDirectory`), create backup (Android option list incl. private settings → save dialog → path shown), restore (open dialog → missing sources / trackers + Android restore options → progress bar with the current item → done dialog with duration, error count, error log path and lines). File dialogs behind `DesktopFileDialogs` (AWT `FileDialog` for files, `JFileChooser` for folders; tests inject paths). Tests: `BackupRestoreTest` (backup assembled with the Android models, protobuf + gzip as Android writes it → manga, chapters, categories (by order, as Android), history, tracking, app prefs incl. category-id remapping, source prefs, extension store, saved search, feed restored; round trip create → wipe → restore → identical snapshot, private key excluded, old target replaced, no temp left), `BackupUiTest` (headless: storage location, create, wipe, restore through the dialogs) |
| 8.3 Trackers | done (OAuth by paste) | `677dd0bbf8` | **Borrowed (`desktopCopy`, unchanged for Android):** all of `data/track/**` except `TrackerManager.kt` and `mdlist/**` (84 files: the 9 tracker implementations with their APIs, interceptors, DTOs and models, `BaseTracker`, `Tracker`, `EnhancedTracker`, `DeletableTracker`, `TrackStatus`), `data/database/models/Track.kt` + `TrackImpl.kt`, `domain/track/interactor/{AddTracks,RefreshTracks,SyncChapterProgressWithTrack,TrackChapter}`, `domain/track/model/{AutoTrackState,Track}`, `TrackPreferences`, `DelayedTrackingStore`. **`shared`:** `exh/md/utils/FollowStatus.kt`, `util/PkceUtil.kt`, `util/lang/DateExtensions.kt`. Desktop counterparts: `TrackerManager` (same members / ids / order; constructor `extraTrackers` so tests can add an in-test tracker the borrowed interactors find), `mdlist.MdList` (stub: same id / name / statuses, never logged in, remote calls throw "needs the MangaDex source"), `DelayedTrackingUpdateJob` (`object`; coroutine replaying the persisted store with Android's body after 5 / 10 / 20 / 40 min, `runOnce` for tests), `eu.kanade.tachiyomi.R` (`drawable.brand_*` ids only; the 8.2 `TrackerManager` stand-in is gone). DI: `TrackPreferences`, `TrackerManager`, `DelayedTrackingStore`, `TrackChapter`, `AddTracks`, `RefreshTracks`, `SyncChapterProgressWithTrack`, `ReaderTrackerHook` = `DesktopReaderTrackerHook` (Android `updateTrackChapterRead`: `autoUpdateTrack` on → `TrackChapter` in a background scope, failures → delayed store + retry coroutine; incognito skipped by `ReaderProgressSaver`). **Login** (`DesktopTrackerLogin`, More → Tracking): credentials dialog; OAuth = system browser (`java.awt.Desktop.browse`) + "paste the redirected address or the code" dialog parsed by the shared `OAuthRedirect` (Android `TrackLoginActivity` rules: query else fragment, decoded, host checked); enhanced trackers via `loginNoop` when an accepted source is installed; logout with confirmation; preferences "update progress after reading" and "when marked as read". **Tracking sheet** (details → Tracking): logged-in trackers (enhanced only when they accept the source), refresh on open (`RefreshTracks`), add (search dialog → `register`), status / score menus, chapter field, start / finish dates (YYYY-MM-DD) when supported, open in browser, remove (`DeleteTrack`). Tests: `OAuthRedirectTest` (AniList fragment token, MAL query code, bare code / parameter list, wrong host / error / empty rejected, query over fragment), `TrackingTest` (real MAL / AniList authorize URLs build on desktop, login method per tracker, backup validator lists a logged-out tracker, credentials login, search + bind pushes local progress, reader hook → remote + DB, preference off → no update, offline → delayed store → `runOnce` retry, status / score, delete), `TrackingUiTest` (headless: OAuth paste rejection, wrong / right password, details → add → bind → status / chapter / start date → remove) |
| 8.4 More / Settings | done | `00e271aad0` | **Borrowed (`desktopCopy`):** the data-driven settings model `presentation/more/settings/Preference.kt` + `PreferenceItem.kt` and the widgets `AppThemeMode`, `Base`, `Connection`, `EditText`, `Info`, `List`, `MultiSelectList`, `PreferenceGroupHeader`, `Switch`, `Text`, `Tracking`, `TriStateListDialog`. Desktop counterparts: `ConnectionsService` (type only), `ConnectionLogoIcon` / `TrackLogoIcon` (initials tile, no Android drawables). Shared renderer `app.komikku.shared.settings.PreferenceList` (Android `PreferenceScreen` without the search highlight; in `:app-shared` because `PreferenceItem` is `internal`). **More tab:** downloaded only, incognito, download queue, data and storage, tracking, categories, settings, about (version). **Settings** (Android keys / classes): Appearance (theme mode, app theme list, pure black, relative timestamps), Library (edit categories, default category, update categories, smart-update restrictions, refresh metadata), Reader (default reading mode, scale type, background, page number, skip read / filtered / duplicates), Downloads (location, CBZ, split tall images, delete after marked read / after reading slots, delete bookmarked, download ahead), Tracking (8.3 screen), Browse (hide entries in library, incognito, show NSFW sources), Data and storage (8.2), About. **Categories screen:** add, rename (duplicate names rejected like Android), move up / down (`ReorderCategory`), hide / show, delete with confirmation. Test: `SettingsUiTest` (headless: incognito toggle, theme mode list dialog, pure black, default reading mode, CBZ switch, about, categories add / duplicate rename rejected / rename / reorder / hide / delete — each verified in the preference store / database) |
| 8.5 Library extras | partial | `c58a6fe65f`, see git log (`fix(kmp): … library search`) | **Search = Android's matcher, extracted (review fixes of `c58a6fe65f`).** The library filter / sort / text-search code of Android's `LibraryScreenModel` moved, bodies unchanged, into the new KMK file `ui/library/LibraryFilterSort.kt` (`applyLibraryFilters`, `applyLibrarySort`, `filterLibraryByQuery` with the file-private `filterManga` / `filterTracks`, `groupByDefaultCategory`, `hasActiveLibraryFilters`, `libraryItemPreferencesFlow`, `libraryTrackingFiltersFlow`, `LibraryManga.toLibraryItem`, `LibraryItemPreferences` (was the private `ItemPreferences`)); Android's screen model calls them (KMK blocks), desktop borrows the file as `desktopCopy` with `LibraryItem.kt`, the SY query parser `exh/search/*` (8 files), `exh/util/LewdMangaChecker.kt`, `exh/util/CoroutineUtil.kt`, `eu/kanade/core/util/CollectionUtils.kt`. Desktop search therefore has Android's exact semantics: space / comma separated terms, quoted phrases, `-term` exclusion, `namespace:tag` and wildcards (SY `SearchEngine`), `id:<manga id>`, `src:<source id or local>`, matched (case-insensitive `contains`) against title, author, artist, description, source name, source id, genres (partial), logged-in tracker names / statuses and SY search tags / titles of metadata sources. Only the MangaDex DMCA easter-egg query (`mangadex-dmca`, Android raw resource) stays Android-only. Desktop `LibraryScreenModel` keeps query and selected category in its state (survive the pages leaving composition), debounces the query (250 ms, Android's `SEARCH_DEBOUNCE_MILLIS`) and filters on `Dispatchers.Default`; empty categories are hidden while searching / filtering unless `showEmptyCategoriesSearch` (Android); empty states as Android's `LibraryPagerEmptyScreen` (`no_results_found` / `error_no_match` / `information_no_manga_category`); tab counts when `categoryNumberOfItems` is on or a search is active (Android); unread badge from `LibraryItem.unreadCount` (0 when `unreadBadge` is off, as on Android). Category tab item counts, unread badge on covers (`c58a6fe65f`). Tests: `LibraryPagesTest` (grouping, hidden categories; search by title / author / artist / description / partial genre / source id, trimming, AND terms, exclusion, quoted phrase, `id:` / `src:`), `LibraryUiTest` (headless: `library-search` debounced + trimmed, other categories hidden, "No results found", tab counts follow the preference, `unread-badge-*` hidden at 0 and when the preference is off). **Not done:** see the 8.5b row |
| 8.5b Library filters / sort / display | done | see git log (`feat(kmp): … library filters`) | **Settings dialog** (`LibrarySettingsDialog`, filter icon `library-settings` next to the search field, highlighted while a filter is active as on Android) with **Filter / Sort / Display** tabs over the borrowed Android `LibrarySettingsScreenModel` (`desktopCopy`, with `PreferenceMutableState.kt`): tri-state downloaded (locked to "include" by "downloaded only"), unread, started, bookmarked, completed, custom interval (when the smart-update restriction is on), lewd (SY), KMK category include / exclude (borrowed `TriStateListDialog`), one tri-state per logged-in tracker; sort alphabetical / total chapters / last read / last update / unread count / latest chapter / chapter fetch date / date added / tracker mean (when logged in) / tag list (SY, when tags are set) / random (reseeded on each click), ascending / descending with Android's toggle rules, written through `SetSortModeForCategory` (per-category flags when `categorizedDisplaySettings`, else global + all categories; the global sort when the Android grouping is not "by category"); display modes compact grid (title over the cover), comfortable grid (title below; also used for the KMK panorama mode), cover-only grid, list; columns slider (0 = auto) bound to the landscape or portrait preference depending on the window's aspect; badge switches download / unread / local / language; show hidden categories; number of items. **Everything is the Android preference or category flag**, so it round-trips through backups. Filtering / sorting run the borrowed Android code (step above). **Badges** (`LibraryItems.kt`): downloads (`download-badge-*`), unread, local folder icon or source language code (text: no flag icons on desktop). **Not on desktop:** grouping by source / status / tracker / ungrouped (SY "Group" tab; done in P2), source-icon badge and language flag icons, "continue reading" button, category-tabs switch (tabs always shown), selection / batch actions (done in P2), global search from the library. Tests: `LibraryFilterSortTest` (each tri-state filter both ways, downloaded via local + global "downloaded only", combined filters, `hasActiveFilters`, tracker include / exclude / not logged in, category include / exclude / disabled; alphabetical (both directions), total chapters, last read, date added, latest chapter, unread count (both directions, Android's "unread first" rule), seeded random, per-category flags vs. global sort), `LibraryUiTest` (headless: dialog → sort by total chapters, flags + global sort written, grid order follows and flips; unread filter hides the read entry; list display mode) |
| 9.1 Packaging and CI configuration | configured, not executed | see git log (`ci(kmp): …`) | `:desktopApp` `nativeDistributions`: Msi / Exe / Dmg / Deb / Rpm / AppImage, package name, version read from `app/build.gradle.kts` (`versionName` / `versionCode`, not duplicated; also `-Dkomikku.version*` for the desktop `BuildConfig`), description / vendor / copyright / `LICENSE`, jlink `modules(...)` (jdeps over the runtime class path + `jdk.crypto.ec`, `jdk.charsets`, `jdk.localedata`, `jdk.accessibility`, `java.net.http`; `includeAllModules` documented as fallback), icons generated offline from the Android launcher vector layers (`desktopApp/icons/generate_icons.py` → png / ico / icns), Windows (fixed upgrade UUID, menu group, shortcut, per-user, dir chooser), macOS (bundle ID `app.komikku`, category, signing / notarization TODO), Linux (package `komikku`, categories, shortcut); release ProGuard disabled (reflective extension loading). `.github/workflows/build_desktop.yml` (manual + `desktop-v*` tags; Ubuntu / Windows / macOS matrix, JDK from `.github/.java-version`, same pinned actions as the other workflows, tests on Linux, `packageDistributionForCurrentOS`, artifacts; no secrets). PR workflow: separate `desktop` job (KMK markers) running `:desktopApp:compileKotlin` + `:desktopApp:test --max-workers=2`, `continue-on-error: true` while the port is experimental, independent of the APK job. Validated: `:desktopApp:tasks --all` lists the package tasks, `-m` dry run of `packageDistributionForCurrentOS`, `checkRuntime` + `createRuntimeImage` (jlink of the chosen modules, 116 MB image), YAML parsed with PyYAML. **Not validated:** any installer build, the icons inside installers, the workflows on GitHub runners. `BUILDING.md` documents run / test / package / data dirs / `linkCheck` / limitations |
| P1 Global search and source toggles | done | `5137a56` … `2e39d59`, review fixes `03642a4` … `3240a78` | Android's global search model and source filter model borrowed, sources tab on Android's `GetEnabledSources` with pins / disable; see "P1 — global search and source toggles" and "Review fixes (P1)" |
| P2 Library grouping and batch actions | done | `a60bb21` … `59d7aab` + docs | Android's grouping, selection and batch-action code moved into shared KMK files and borrowed; desktop Group tab, selection toolbar / bar, dialogs; see "P2 — library grouping and batch actions" |
**Milestone B** (install an extension, browse its catalogue, open a manga's details on desktop):
**compiles and passes fixture tests; not exercised with real extensions.** Every stage runs in tests on
generated fixtures — store index over `MockWebServer`, signed APK (apksig) with dx-produced dex,
signature verification, dex2jar, class loading, source registration, install / update / trust /
uninstall, and the browse → catalogue → details → add-to-library and source-settings UI with an in-test
source — but no real extension APK, real repository or real website was used, and the desktop app was
not launched.

**Milestone C** (read a chapter end to end from an online source and from a local archive):
**reader passes headless tests with fake sources; not exercised with a real display or real sources.**
Covered in JVM tests: an in-test `HttpSource` over `MockWebServer` (page list request, image download into
the chapter cache, page list cache), the local source's CBZ and image-folder chapters through the borrowed
Android loaders, Skia decoding of generated JPEG / PNG / GIF / BMP pages incl. tiling, and headless
Compose UI runs (offscreen Skia, `runDesktopComposeUiTest`) of library → details → reader → keyboard page
turns → progress / read flag / bookmark / history in the database, the webtoon viewer and the settings
panel. Not verified: a real window / GPU, real extensions or websites, local archives opened from the UI
(the loader path is tested without the UI), EPUB chapters, RAR / 7z chapters in the reader, large real-world
images, mouse gestures (only keys and clicks are driven in tests).

## iOS / JavaScript sources

Plan: [`PLAN.md`](PLAN.md) section 8 (findings of I0 in 8.9). Contract:
[`MANGAYOMI_CONTRACT.md`](MANGAYOMI_CONTRACT.md); usage scan: [`MANGAYOMI_SURVEY.md`](MANGAYOMI_SURVEY.md).

| Step | Status | Commit | Notes |
|------|--------|--------|-------|
| I0 Contract and feasibility | done | see git log (`docs(kmp): … Mangayomi …`) | Docs only. 114 JS manga entries from 18 files in the kodjodevf index (86 are MangaDex / Comick variants; files hosted by the `entityJY` fork; 2 files 404). Bridge used: `Client.get`, CSS DOM, `SharedPreferences`, filters, preferences, one `cryptoHandler`; no XPath, WebView, `parseDates` or unpacker. Engine: `quickjs-kt` 1.0.15 (iOS targets verified, QuickJS 2026-06-04 like Mangayomi); on Android it clashes with the zhanghai AAR (`libquickjs.so`), maintainer decision 8.7 item 5. Licences: Apache-2.0 (app, index, fork), MIT (`flutter_qjs`). Go for I1–I4 with conditions (8.9). |
| I1 Engine and host core | done | `86c99aefac`, `49da887244`, `0ec0f1193f` | New KMK module `:source-js` (`kotlin("multiplatform")`, code in `commonMain`, package `app.komikku.sourcejs`); target `jvm("desktop")` only, `iosArm64` / `iosSimulatorArm64` declared only on macOS hosts, no Android target yet (8.7 item 5). `JsEngine` (define sync / suspend host functions, evaluate, `callFunction` with JSON args, close) on `io.github.dokar3:quickjs-kt` 1.0.15 (`QuickJsEngine`), one dedicated thread per context. `JsExtensionHost(services, engineFactory, limits).extension(source, code)` gives a `JsExtension` with `getPopular`, `getLatestUpdates`, `search`, `getDetail`, `getPageList`, `getFilterList`, `getSourcePreferences`, `getHeaders`, `supportsLatest` and `metadata()` (API as reshaped by the review fixes below); a fresh context per call; `JsHostLimits` (180 s wall clock per call, 64 MiB heap, 4 MiB JS stack, 50 MiB response bodies); own prelude (`JsPrelude`). Typed `@Serializable` models: `JsMangaPage`, `JsManga` + `JsMangaStatus`, `JsChapter`, `JsPage`, `JsFilter` tree (8 kinds, raw JSON kept), `JsPreference` (5 kinds); lenient parser. Errors: sealed `JsSourceException` (`ScriptError` with JS stack, `Timeout`, `OutOfMemory`, `Unsupported`, `BridgeError`, `MalformedResult`). Tests (`:source-js:desktopTest`): `QuickJsEngineTest` 7, `HostCoreTest` 15, `ResultModelsTest` 11. Android unit tests: not applicable (no Android target). |
| I2 Bridge | done | `49da887244` (code), `f57cacd5a3` (conformance tests) | Bridge interfaces in `commonMain`: `HostHttp` (`HostHttpRequest` / `HostHttpResponse`), `HostPreferences` + `HostPreferencesProvider` (`KeyValueHostPreferences` over a `JsKeyValueStore`), `HostCrypto` (`CommonHostCrypto`, pure Kotlin AES-CBC / AES-GCM / MD5), `HostLog`, `HostWebView` (`UnsupportedHostWebView`), bundled in `JsHostServices`. `Client` (all verbs, Mangayomi body encoding and response object), DOM on Ksoup `com.fleeksoft.ksoup:ksoup` 0.2.6 with Mangayomi's quirks, `SharedPreferences` (default materialisation, `getString` / `setString`), `cryptoHandler`, CryptoJS AES, `decryptAESGCM`, `deobfuscateJsPassword`, string helpers, `console`, `setTimeout`; clear "unsupported" errors for XPath, `parseDates`, unpackers, WebView, extractors, HLS, EPUB. Desktop: `OkHttpHostHttp(OkHttpClient)`. Tests: conformance suite in `commonTest` (`ClientConformanceTest` 2, `DomConformanceTest` 1, `PreferencesConformanceTest` 1, `UtilsConformanceTest` 2; fake `HostHttp`, fixed HTML, vectors from Python `cryptography`), `desktopTest`: `OkHttpHostHttpTest` 7 (`MockWebServer`), `CommonHostCryptoJdkTest` 3. 49 tests in total, all pass. Local smoke run (not committed) over the real files: see below. |
| I2b Android engine switch | done | `6485fbe496`, `21f30d5dbe`, `935c6c9710`, `8cf7a35ebd`, `f2b94425c6` | New KMK module `:core:quickjs` (`mihon.library.kmp`, namespace `app.komikku.core.quickjs`): `app.cash.quickjs.QuickJs` / `QuickJsException` on `quickjs-kt` 1.0.15 for Android and desktop, binary compatible with quickjs-android (ABI check below), rebuilt to its behaviour (parity note below; `set` / `get` implemented although no extension uses them). Moved with `git mv` from `:desktop:android-compat` (`src/main/kotlin/app/cash/quickjs/{QuickJs,QuickJsException}.kt`, test `QuickJsTest.kt`); internals in `app.komikku.quickjs` (`QuickJsRuntime`, `EngineThread`, `Marshalling`, `JsStackTrace`). `core:common` depends on it for both targets (desktop no longer gets it from the compat module; `:desktop:extension-runtime` still exposes `app.cash.quickjs.` as a host package). Catalog: `quickjs-android` + bundle `js-engine` commented out (KMK), `quickjs-kt-jvm` added for host tests. Android: `:app:dependencyInsight` finds no `quickjs-android` in `debugRuntimeClasspath`, `quickjs-kt-android` comes through `:core:quickjs`; its AAR has `jni/{arm64-v8a,armeabi-v7a,x86,x86_64}/libquickjs.so` (all four split ABIs; `keepDebugSymbols` `**/libquickjs.so` still matches) and `:app:mergeDebugNativeLibs` packages only its copy (JNI symbols `Java_com_dokar_quickjs_*`). ProGuard: the AAR's consumer rules keep `QuickJs`, `QuickJsException`, `JsProperty`, `JsFunction`, `JsObject`, `UByteArray` (name only); KMK rules added for `MemoryUsage` (JNI constructor) and `UByteArray`'s `(byte[])` constructor and `storage` field; `app.cash.quickjs.** { public protected *; }` still matches. `:source-js` gained `androidTarget()` (namespace `app.komikku.sourcejs`, `mihon.library`) and a `jvmShared` source set (`OkHttpHostHttp`, `JsEngineThread.jvm.kt`, moved from `desktopMain`). Android host unit tests of both modules run the engine: their `*UnitTestRuntimeClasspath` excludes `quickjs-kt-android` and adds `quickjs-kt-jvm` (Linux / macOS / Windows x64 natives). Tests: `:core:quickjs` 16 (13 `QuickJsTest`, 3 `QuickJsErrorClassTest`, 2 of them `@Disabled`), on desktop and the Android host; `:source-js:testReleaseUnitTest` 67 (all of `commonTest`), `desktopTest` 87 (1 skipped). Not verified: R8 / release builds, a device or emulator. After the review fixes (section "Review fixes (I2b)"): `:core:quickjs` 38 (2 `@Disabled`), `:source-js` `desktopTest` 110 (1 skipped), `testReleaseUnitTest` 83. |
| I3 Source adapter | done | `c259852190`, `08091d4ed4`, `0d67724c45`, `9381c340e6`, `bbd19b16bf`, `6c45bdf10a` | `app.komikku.sourcejs.mihon` in `:source-js`: `MangayomiJsSource` (`HttpSource` + `ConfigurableSource` over a `JsExtension`, cached metadata refreshed after preference changes, filters / preferences mapped both ways, page headers in `Page.url`, details merge, readable failures) and `MangayomiJsRuntime` (one host, the app's client and preference files); pure helpers in `commonMain`. `:app` / `:desktopApp` depend on `:source-js`; not yet in the source managers (I4). Details: section "I3 — source adapter". Tests: `:source-js` `desktopTest` 136 (1 skipped), `testReleaseUnitTest` 95; `:desktopApp` `JsSourceBrowseUiTest` 2. After the review fixes (section "Review fixes (I3)", `6bdcd94` … `9efd135`): `desktopTest` 145 (1 skipped), `testReleaseUnitTest` 96, `JsSourceBrowseUiTest` 3. |
| I4 Store and UI | done | `5780d0e` … (see section "I4 — repository store and UI") | Index / `repo.json` / version logic in `:source-js` (`app.komikku.sourcejs.repo`), `MangayomiSourceStore` + shared screen model and UI in `:app-shared` (`app.komikku.shared.jssource`), sources in `AndroidSourceManager` / `DesktopSourceManager`, Android screen `mihon.feature.jssources.JsSourcesScreen` (Extensions tab overflow, Browse settings), desktop third browse tab. Tests: `:source-js` `desktopTest` 153 (1 skipped), `testReleaseUnitTest` 104; `:desktopApp` `MangayomiSourceStoreTest` 12, `JsSourceManagerTest` 2, `JsSourcesUiTest` 1. Reviewed (medium, 9 findings), all fixed in `6337d12` … `a1ef663` (section "Review fixes (I4)"): `desktopTest` 154 (1 skipped), `testReleaseUnitTest` 105, `MangayomiSourceStoreTest` 16, `JsSourceManagerTest` 5. |
| I5 Common core for iOS | todo | | Needs macOS. |
| I6 iOS app | todo | | Needs macOS. |
| I7 Distribution and CI | todo | | Needs macOS. |

### I1 / I2 decisions and deviations

Specification: [`MANGAYOMI_CONTRACT.md`](MANGAYOMI_CONTRACT.md) (corrected where the implementation showed it
vague or wrong). Decisions taken without the maintainer, as asked:

- **Targets (deviation from I-D1):** desktop only (I2b later added Android, see its row). Android waits for the QuickJS AAR decision (8.7 item 5); adding
  it is `id("mihon.library")` + `androidTarget()` + a namespace in `source-js/build.gradle.kts` (comment there).
  iOS targets are declared on macOS hosts only and have **not been compiled** (no macOS here); the iOS
  `newJsEngineThread` clamps the JS stack to 256 KiB for Darwin's 512 KiB secondary-thread stacks (unverified).
- **DOM on Ksoup everywhere (deviation from I-D3, which planned jsoup on Android / desktop):** one implementation
  with identical behaviour on every platform; no XPath (Ksoup has none).
- **Crypto in pure Kotlin** instead of `cryptography-kotlin`: AES-CBC (PKCS#7), AES-GCM decryption and MD5 are
  small, need no provider registration and behave the same on JVM and iOS; checked against the JDK and Python
  `cryptography` vectors. The CryptoJS salt comes from `kotlin.random` (a salt need not be secret).
- **Engine threads and limits:** each context has its own thread (desktop: 4 MiB JS stack + 4 MiB headroom) and
  every call runs on it, because QuickJS checks its stack limit against the native stack of the thread it runs on
  (quickjs-kt re-bases the limit at every entry into the engine, so the thread may also be replaced: since the I2b
  review an idle engine thread ends after 30 s). Defaults per call: 180 s wall clock (including network waits;
  enforced on `Dispatchers.Default` so test dispatchers do not fake it), 64 MiB heap, 4 MiB stack; cancelling the
  caller interrupts the script.
  Out-of-memory: see the review fixes below.
- **Fresh context for every call**, metadata calls included (Mangayomi keeps up to 6 metadata services alive);
  extensions cannot observe the difference. `JsExtension.metadata()` reads the four sync members in one context.
- **HTTP (deviations, as decided):** the app supplies the client (cookies from responses are stored by the app's
  jar, rate limits and Cloudflare handling apply); TLS certificates are always verified (`verifyCertificates` is
  ignored; Mangayomi defaults to not verifying); `reqcopyWith.timeout` and `followRedirects` are honoured, the
  other options ignored. Body without a charset is decoded as UTF-8 (Mangayomi: Latin-1); Latin-1 / ASCII when
  declared; other charsets as UTF-8. Repeated response headers are joined with `", "`; `reasonPhrase` is always
  `null` and `request.maxRedirects` 5, as with Mangayomi's default client. The JSON double encoding of string
  bodies with a JSON content type is kept.
- **Bridge transport:** two host functions (`__kmk_host_sync` / `__kmk_host_async`, strings in and out, hidden
  from extension code) instead of a global `sendMessage`, which no source calls. Host errors reach the script
  as errors branded with the id the host recorded; see the review fixes below.
- **Prelude:** written for Komikku from the contract (no Mangayomi text). Helper functions are configurable
  `globalThis` properties, so an extension that declares its own `atob` / `unpackJs` / … still loads (a test
  covers it); `Client`, `Document`, `Element`, `SharedPreferences` and `MProvider` are classes as in Mangayomi.
  `Document` parses once on first use (Mangayomi re-parses per call; same results).
- **Small leniencies:** `console.log(undefined)` logs `undefined` (Mangayomi throws); `hasAttr` works (always
  `false` in Mangayomi); `:contains("x")` accepts quotes. (Sync members returning a promise are no longer
  awaited: like Mangayomi they get their default.)
- **DOM choices for unverified points:** `Element.select` / `selectFirst` match descendants only (DOM
  `querySelectorAll`; jsoup alone would include the element); `Document.text` is the `textContent` of the whole
  document; `children` is a getter; `:contains(x)` is rewritten to `:is(:contains(x), :containsData(x))` so script
  text matches (jsoup still normalises whitespace on the text side: edge case accepted).
- **Unsupported, with an error naming the function:** XPath (`Document` / `Element` `xpath`, `xpathFirst`),
  `parseDates`, `unpackJs`, `unpackJsAndCombine`, `evaluateJavascriptViaWebview` (`HostWebView` stub), video
  extractors, `createHlsProxyUrl`, `parseEpub*`. None is used by a JavaScript manga source (survey).
- **Results:** parsed leniently (unknown keys ignored, numbers / booleans accepted as strings and numeric strings
  as numbers, `null` entries skipped, missing `hasNextPage` = `false`, empty optional texts = `null`, chapters with
  an empty URL dropped, `dateUpload` `"NaN"` / invalid = `null`, pages trimmed and de-duplicated); wrong container
  types raise `MalformedResult` naming the method and field. Filters keep the source's raw JSON (extra keys
  round-trip, Mangayomi drops them); `search(filters = null)` sends the default list with states filled in, an
  explicit list is sent as given. Any `getFilterList` failure becomes `[]` with a warning (Mangayomi's default).
- **Preferences:** stored typed (`Text`, `Bool`, `TextSet`); a list preference stores its entry value (not the
  index); the declared default is stored on first read; `get` called from the provider's constructor evaluates
  `getSourcePreferences()` on an uninitialised instance (`extention` does not exist yet).
- **Fixtures:** the conformance fixtures are Kotlin string constants, not `.js` resources, so the suite runs on
  iOS without resource loading.

### Review fixes (I1 / I2)

Code review of `:source-js` (four commits after `b019b0e402`: error protocol, Mangayomi parity, efficiency / API,
CI and docs). Decisions taken without the maintainer, as asked.

**Security: `quickjs-kt` class instantiation (residual risk, maintainer decision pending).** `quickjs-kt` 1.0.15
turns a JS `Error` that reaches the JVM into `new <error.name>(error.message)` for any class
(`js_error_to_java_error`, no `Throwable` check), so an extension could run a `(String)` constructor such as
`java.io.FileOutputStream` (creates / truncates a file) or forge `CancellationException`s. The real fix is a
patched `quickjs-kt`; it is **not** done here. Closed in Komikku's own code paths (defense in depth):

- the evaluation never rejects with an extension's value: one entry (`__kmk_entry`) loads the extension and calls
  the member inside `try` / `catch` and reports a string envelope through the bindings; the extension text is
  compiled inside a function (after a standalone syntax check, so it cannot close the function early);
  `Object.prototype` is made non-extensible (QuickJS resolves an evaluation's completion object, so an
  inherited `then` / `value` accessor could otherwise reject it), and `Promise.prototype.constructor` / `then`
  and `Promise[Symbol.species]` are locked (QuickJS rejects an async function's promise directly when an
  `await` fails, bypassing its `catch`); the entry attaches its own rejection handler before any extension code
  runs;
- host bindings are captured in the prelude's closure and deleted from `globalThis`; every call passes strings
  checked with `typeof`; bindings never throw into the engine (host failures are recorded under an id);
- a `CancellationException` while the caller is still active is an error of the call; only the host's own limit
  (`withTimeoutOrNull`) is a `Timeout`.

**Not closable from JavaScript:** the native rejection tracker converts the reason of every rejection that has no
handler *at the moment it happens* — a stray `Promise.reject(e)`, and also an `async` function that throws before
its first `await` (its promise is already rejected when the caller gets it). An extension can therefore still
trigger the class instantiation deliberately. `ErrorClassInjectionTest.unhandledRejectionsDoNotReachTheJvm` covers
these cases and is `@Ignore`d; enable it once `quickjs-kt` is patched (it was run once with the annotation removed
and fails today: the payload file is created, inside its temporary directory). The other tests of that class (error
from sync members and after an `await`, at load, passed to every bridge function, raw binding lookup, patched
intrinsics) pass.

**Error protocol.** Classification by markers only: the `MProvider` defaults throw errors branded "not
implemented" (defaults apply to those only, not to any message containing the words — deviation from Mangayomi's
substring test); the stubs (`unpackJs`, `unpackJsAndCombine`, `parseDates`, XPath, extractors, HLS, EPUB) throw
errors branded "unsupported" naming the feature, so a sync member hitting one is `Unsupported`, not a default;
host failures carry their id. `SharedPreferences.get` without declared preferences fails with "Error when getting
source preference …" (`BridgeError`, channel `pref_default`). Load failures (syntax error, top-level throw,
constructor throw, no `DefaultExtension`) are `ScriptError`s "while loading the source"; a promise that never
settles is a `ScriptError` instead of a hang until the timeout.

**Out of memory.** QuickJS's own `InternalError: out of memory`, or the context still within 1/16 of its limit
after the failure, is `OutOfMemory`. `throw null` / `throw undefined` / `Promise.reject()` are `ScriptError`s.
Limitation: when QuickJS could only throw `null` and the memory was already released during unwinding (garbage in
a frame that unwound), the failure is indistinguishable from `throw null` and reported as `ScriptError("null")`;
`quickjs-kt` exposes no peak usage or allocation-failure counter.

**Limits.** `quickjs-kt`'s `evaluationTimeoutMillis` is a deadline from the start of the evaluation that is only
checked while JavaScript runs, i.e. still wall clock (a call that waited for the network would be interrupted at
its next JS step), so there is no JS-time-only limit: the wall-clock call limit is now 180 s (the app's HTTP client
allows 120 s per request). Response bodies are capped at 50 MiB (`JsHostLimits.maxResponseBodyBytes`, enforced
while reading by `OkHttpHostHttp` and checked again by the bridge; a `BridgeError` the script can catch; since the
I2b review the default is an eighth of the heap, see "Review fixes (I2b)"). A runtime
created while the caller was cancelled is closed (it used to leak).

**Mangayomi parity.** Source-supplied `Accept-Encoding` is dropped so OkHttp negotiates and decompresses; `getSrc`
/ `getImg` / `getHref` / `getDataSrc` use `src="([^"]+)"` etc.; `getFilterList` turns every failure into `[]`;
sync members of the wrong type (and promises, no longer awaited) fall back to their defaults with a warning;
`setTimeout` waits end unrun when the call settles, callback errors are logged, `clearTimeout` added (Komikku
extra); crypto helpers follow the Dart code (`deobfuscateJsPassword` paren / `+[]` rules with errors instead of
Mangayomi's `RangeError` / endless loop on unbalanced input, no `Salted__` check, low-byte passphrases, Dart
base64, replacement characters, stdlib hex); form bodies use `Uri.encodeQueryComponent`'s character set.

**Efficiency and API.** The prelude is compiled once per process and each extension text once per SHA-256 (LRU of
32, `BytecodeCache`); fresh contexts load the bytecode (fallback to the source text if loading fails). DOM
`selectFirst` stops at the first descendant match and parsed CSS queries are cached per context; `this.source`
is a fresh object from a JSON literal; HTML, response bodies and DOM strings cross the boundary raw. Measured with a
throwaway benchmark (not committed; ~400 KB page, 1529 items, `getPopular` reading three values per item, JVM,
average of 20 calls after 5 warm-ups): 72 ms → 61–65 ms per call; parsing alone (fetch + `new Document` + one
lookup) 23 ms → 14–15 ms; an empty call 3 ms → 2 ms. The per-element bridge calls dominate what is left. API: one
call surface (`JsExtensionHost.extension(source, code)` → `JsExtension`, plus `metadata()` → `JsSourceMetadata`
read in one context for the adapter to cache); `EnginePolicy` removed — a pooling seam would have to reset the
per-context bridge state, the frozen intrinsics and the one-shot entry, so it must be redesigned with I3 if
pooling is ever wanted; dead surface removed (`HostHttpResponse.finalUrl` / `reasonPhrase`,
`InMemoryKeyValueStore.snapshot()`, `HostLog.Level.DEBUG`, `-Xexpect-actual-classes`).

**Tests and CI.** `ErrorProtocolTest`, `MangayomiParityTest`, `HostEfficiencyTest` (common),
`ErrorClassInjectionTest` (desktop, temporary directory only), new cases in the engine, conformance and
`OkHttpHostHttpTest` suites (gzip, body cap, an `EventListener` proving the OkHttp call is cancelled, directly and
through an extension call). `:source-js:desktopTest` now runs in the PR workflow's `desktop` job and in
`build_desktop.yml` (Linux). 87 tests, 1 skipped.

**Real-extension smoke check** (local, not committed; offline `HostHttp` that always fails; files from the I0
clones): 21 files (the 18 of the kodjodevf index incl. the two dead entries' copies, plus `batoto`, `baozimh`,
`rumanhua1` from the entityJY index), 117 index entries. Every entry evaluates, instantiates and returns
`supportsLatest`, `getFilterList`, `getSourcePreferences` and `getHeaders` without error; `getPopular` and
`search("one piece", 1)` get as far as the HTTP bridge (offline error) for all of them, except Oduto, whose
`getPopular` needs no network and which does not implement `search`. No host bug was found by it. Not verified:
any real website, `getDetail` / `getPageList` against real pages, iOS.

### QuickJS parity (I2b)

What the zhanghai `quickjs-android` AAR (`547f5b1597`, QuickJS `2021-03-27`, Square's `quickjs-java` code) does,
read from its source (`quickjs/common/java` + `native`), and how Mihon extensions use it. This is the behaviour
the `quickjs-kt`-backed `app.cash.quickjs.QuickJs` of `:core:quickjs` has to keep.

- **Results of `evaluate` / `execute`** (`Context::toJavaObject`, unsupported types not thrown at top level):
  `undefined` / `null` → `null`; booleans → `Boolean`; QuickJS int-tagged numbers → `Integer`, float-tagged →
  `Double` (an integral double such as `2 ** 40` or `0.5 * 2` stays `Double`; `NaN` → `Double.NaN`); strings →
  `String`; arrays → `Object[]` whose elements are converted *strictly*: a nested object / function makes the call
  throw `QuickJsException("Cannot marshal value [object Object] to Java")`; any other object, function, symbol,
  promise or error at top level → `null`. No pending jobs are run (promise callbacks never execute).
- **Errors:** a JavaScript exception becomes `QuickJsException(message, stack)`: the message is the error's
  `message` property (no `Name:` prefix, e.g. `'nope' is not defined`), or `String(value)` when it has none
  (`throw 2` → `"2"`); the JS `stack` lines (`    at f (file.js:10)`) are parsed with a regex and spliced into the
  Java stack trace as `JavaScript.f(file.js:10)` frames above the `QuickJs` frame. An exception thrown by a Java
  method bound with `set` is rethrown as that same Java exception (not wrapped) with the JS frames spliced in;
  inside JavaScript it is an `InternalError("Java Exception")`. A closed instance throws
  `NullPointerException("Null QuickJs context - did you close your QuickJs?")`.
- **`set(name, type, object)`:** `type` must be an interface without super-interfaces and without overloads
  (`UnsupportedOperationException`), `object` an instance (`IllegalArgumentException`), `name` must not exist on
  the global object (`IllegalArgumentException`). Supported parameter / return types: `void`, `boolean`, `int`,
  `double`, their boxes, `String`, `Object` (runtime type) and arrays of these (others:
  `IllegalArgumentException("Unsupported Java type …")` at `set` time). Arguments: exact count unless varargs
  (`RangeError("Wrong number of arguments")`); `String` must be a string or null / undefined; primitives must be
  of the JS type (`int` int-tagged); boxed `Double` / `Integer` / `Boolean` coerce (`ToNumber` / `ToInt32` /
  `ToBoolean`); mismatches throw `IllegalArgumentException("Cannot convert value … to …")` into the script.
- **`get(name, type)`:** same interface rules; at call time the global must be an object
  (`IllegalArgumentException`) whose methods are functions (`QuickJsException`); returns a `java.lang.reflect.Proxy`
  that looks the global up again and invokes the method on each call, converting arguments like `set`'s returns and
  results like `set`'s arguments in boxed (coercing) mode; `Object` methods go to the invocation handler
  (`toString()` = `QuickJsProxy{name=…, type=…}`).
- **`compile` / `execute`:** `JS_EVAL_FLAG_COMPILE_ONLY` + `JS_WriteObject`; bytecode is only valid for the same
  QuickJS build, so an instance created later may execute it (one extension does exactly that).
- **Threading and limits:** "not thread safe", everything runs on the calling thread; QuickJS's default JS stack
  limit **256 KiB**; no memory limit, no time limit, no interrupt.
- **`close()` / finalizer:** `close()` is idempotent; `finalize()` only logs "QuickJs instance leaked!" (it does
  not free the native context).

**Extension usage** (shallow clone of `keiyoushi/extensions-source` at `f1d882a93e`, 2026-11-18, static grep):
14 files import `app.cash.quickjs` (13 sources + `lib/synchrony`). Calls: `evaluate` 23, `compile` 1, `execute` 1
(Mangago compiles a helper once into a lazily created byte array and executes it in later instances), `set` 0,
`get` 0, `QuickJsException` caught by name 0. Results are used as `as String`, `as? String`, `?.toString()` /
`.toString()`, and one `is Array<*>` (Comicabc maps the array elements). Typical scripts: deobfuscating `eval`
packers, `JSON.stringify(...)` of a page's literal, small key / challenge solvers, the synchrony deobfuscator
library. No extension uses `JavaScriptEngine` directly.

**Komikku's implementation (`:core:quickjs`).** Same as above, checked by `QuickJsTest`: result types (int-tagged
→ `Integer`, float-tagged → `Double`, arrays → `Object[]`, objects / functions / maps / errors / promises / symbols
/ BigInts → `null`, nested objects → "Cannot marshal value [object Object] to Java"), messages without the
`Name:` prefix (quickjs-kt's `Name: message\n<stack>` is cut back), `JavaScript.f(file.js:6:…)` frames spliced
above `QuickJs.evaluate` (the runtime's own frames above it are dropped), the `set` / `get` rules and messages
(wrong count, conversions, existing name, missing global / method, `QuickJsProxy{…}`), a Java exception from a
binding rethrown as itself, the closed-instance `NullPointerException`, globals (`var`, `function`, `let`,
`const`) kept across calls, bytecode from one instance executed in another, no memory or time limit.
Threading: one engine thread per instance with a 4 MiB native stack and a **1 MiB** JS stack limit (4× the old
256 KiB, so recursion goes about four times deeper — a simple one reaches ~2000 frames; deeper recursion is a
JavaScript error, never a crash); calls block the caller (waiting is not interruptible, the interrupt flag is restored) and are serialized,
so the instance is now safe to share between threads. `close()` cancels the call in progress and fails the queued
ones with the closed-instance exception, frees the runtime and ends the thread; `finalize()` frees a leaked instance
(the old one only logged) once no call and no `get` proxy uses it (details in "Review fixes (I2b)").

**Deviations that cannot be avoided without changing scripts** (quickjs-kt evaluates every script with
`JS_EVAL_FLAG_ASYNC` and awaits it):
- pending jobs run: promise callbacks (`then`, `async` continuations) execute before `evaluate` returns
  (quickjs-android never ran them);
- an unhandled promise rejection fails the call with its message (`Promise.reject(e); 5` threw nothing before);
- `await` is reserved at the top level (`var await = 1` is a syntax error);
- the completion value is taken from a `{ value }` object that quickjs-kt resolves as a promise; the completion
  helper of the I2b review converts it there (see "Review fixes (I2b)"), which also keeps an `Object.prototype.then`
  of the script from intercepting it;
- newer QuickJS (`2026-06-04` vs `2021-03-27`): error texts differ in places (`nope is not defined` instead of
  `'nope' is not defined`) and stack lines carry a column (`file.js:6:5`);
- `throw {message: 'm'}` gives "[object Object]" (was "m"), `throw null` / `throw undefined` give "null";
- for `int` / `int[]` binding parameters an integral double such as `1.0` is accepted (JavaScript cannot see
  QuickJS's int / float tag); a promise nested in an array is reported as "[object Promise]";
- quickjs-kt cannot evaluate from inside a binding callback, so a Java method bound with `set` that calls back
  into the same instance (`evaluate`, `execute`, `compile`, a `get` proxy) gets `UnsupportedOperationException`
  instead of re-entering (`compile` too since the I2b review: quickjs-kt re-based the stack limit at the binding's
  depth); `close()` from inside a binding throws `IllegalStateException`.

**Hardening and residual risk (PLAN.md 8.7 item 5).** Values a script passes to a `set` binding never reach
quickjs-kt as JavaScript objects: generated script-side code converts them by the Java parameter type and encodes
them into one string (operators and `typeof` only), and the raw quickjs-kt bindings (`set` and the `get` argument
pull) live under random names that the generated code captures and deletes from the global object.
`QuickJsErrorClassTest` passes an `Error` named `java.io.FileOutputStream` to every parameter kind of a bound
interface and to every function reachable from the global object (the hidden `get` invoker included): no file is
created. Evaluation semantics are untouched, so a script's own errors still reach quickjs-kt's native conversion:
an unhandled rejection or an error thrown out of the script creates the file (one returned as, or inside, the
completion value no longer does since the I2b review's completion helper, unless the helper cannot run). Two `@Disabled` tests in `QuickJsErrorClassTest` document these paths (run once without the
annotation: both fail, the files appear in the temporary directory); enable them once quickjs-kt checks
`Throwable` in `js_error_to_java_error`. Any `Throwable` quickjs-kt produces that way is reported as a
`QuickJsException`, so an instantiated `Error` or `CancellationException` class does not escape as itself.

**ABI check (I2b step 6).** `javap -protected` of the Android compile output of `:core:quickjs`
(`build/tmp/kotlin-classes/debug/app/cash/quickjs`) against the old AAR's `classes.jar`; also compared with
`javap -s` descriptors. Every public / protected member of the old `QuickJs` and `QuickJsException` exists with
the same descriptor and generic signature (`<T> void set(String, Class<T>, T)`, `<T> T get(String, Class<T>)`,
`static QuickJs create()`, both `QuickJsException` constructors, `protected void finalize()`, `Closeable`, both
classes `public final`). Differences: methods are now also flagged `final` (Kotlin; no effect on linking, the
class was already final); **added** the `Companion` field and class `QuickJs$Companion` (Kotlin's holder of the
`@JvmStatic create()`) and an `ACC_SYNTHETIC` constructor `QuickJs(QuickJsRuntime, DefaultConstructorMarker)`
(Kotlin's accessor for the private constructor; Java source cannot call synthetic members); **removed** only the
package-private `QuickJs$1` (quickjs-java's anonymous `get` invocation handler) and `QuickJsNativeLoader`
(`System.loadLibrary("quickjs")`), which code outside the library could never reference. The raw and normalized
diffs were kept in the session scratchpad (`abi.diff`, `abi.normalized.diff`), not committed.

### Review fixes (I2b)

Code review of `b019b0e402..712fa9294c` (the I1 / I2 review fixes and I2b) at max level; the ids in brackets are the
review's finding ids. Decisions taken without the maintainer, as asked; runtime claims below were checked with
probes (not committed) unless a test is named.

**`:core:quickjs`: completion values [K1, B4, A1-5, B7, A1-1 in part].** quickjs-kt evaluates every script as an
async global script and converts the `value` of the `{ value }` object its promise resolves to natively: objects
through `JSON.stringify` (getters and `toJSON` ran, cyclic and BigInt-holding objects threw, a throwing getter with
an error named after a non-`Throwable` class crashed the JVM), symbols and BigInts as "Unsupported js value type".
The script is still evaluated as a global script (top-level `let` / `const` persist, file names and lines are the
script's own), with one statement appended: `let {} = helper();`, a lexical declaration that binds nothing and so
keeps the completion value, and that is a syntax error where a statement is expected (an incomplete `if (x)` /
`while (x)` / `label:` stays an error instead of getting it as its body). The helper (`Completion.kt`, installed once
per instance as a non-enumerable, non-writable, non-configurable global with a random per-process name) defines an
`Object.prototype.then` getter that QuickJS reads on the `{ value }` object right after that statement; the getter
removes itself (a microtask removes it at the latest), replaces `value` by `[token, value]` for primitives (number
tags survive: `0.5 * 2` stays `Double`), `[token, copy]` for arrays (elements converted by quickjs-android's strict
rules, nested values copied), `[token, undefined, message]` for "Cannot marshal value … to Java" and `[token]` for
anything else (`null`), and returns `undefined`, so the object is never a thenable — an `Object.prototype.then` of
the script no longer hangs the evaluation either. Result: objects, functions, Maps, Sets, promises, errors, cyclic
and BigInt-holding values are `null` without running getters or `toJSON`; nested symbols / BigInts / objects /
promises throw "Cannot marshal value … to Java" (`Symbol()`, `1`, `[object Object]`, `[object Promise]`); a getter
throwing while an array is converted is a `QuickJsException` with its message (quickjs-android's behaviour); strings
equal to quickjs-kt's promise placeholders and a thrown `'Unsupported js value type: x'` are kept; an `Error`
returned as (or inside) the completion value no longer reaches quickjs-kt (no class instantiation,
`QuickJsErrorClassTest`). Syntax errors are reported for the text alone: on a `SyntaxError` the text is compiled
again without the statement (compiling runs nothing; a valid script stays valid with it). Tests:
`QuickJsCompletionTest`. What a script can see: the hidden global function (`Object.getOwnPropertyNames`), the
transient `then` getter between its last statement and the end of the evaluation, the appended statement in
`compile()`'s bytecode. When the helper cannot run (the script froze or sealed `Object.prototype` or made its `then`
non-configurable) quickjs-kt converts the raw value as before, except that its conversion failures give `null`
(recognised by the frame that created the exception, `getEvaluateResult`, not by message), and nested objects still
throw; getters and `toJSON` then run and the crash path below applies. Kuaikanmanhua (`window.__NUXT__ = …`, an
object completion) and damconuong (its shims end with an object, the page bundle's completion can be a class or
hold a throwing getter) get `null` again. Not restored, already listed above: promise jobs run, and an unhandled
rejection fails the call — damconuong's `__noImport` returns `Promise.reject(…)`, so its key capture can now fail and
return `null` instead of the key.

**Crash paths left [A1-1] (PLAN.md 8.7 item 5).** Confirmed in child JVMs: a script that *throws* such a value, an
unhandled *rejection* with it as reason, or a completion value while the helper cannot run, whose conversion meets
a getter that throws an object named after a non-`Throwable` class (`{ get a() { throw { name:
'java.lang.StringBuilder' }; } }`), makes quickjs-kt `Throw` a non-`Throwable` through JNI: the JVM dies (SIGSEGV).
So a page script evaluated by an extension can crash the app — stronger than the accepted class instantiation, and
fixed only by the same quickjs-kt native patch (`IsAssignableFrom(cls, Throwable)` in `js_error_to_java_error`).

**Engine lifecycle race [B1, C3].** quickjs-kt's JNI layer keeps an instance counter, the cached `JavaVM`, about 30
cached global class references (deleted by the last close) and the `Unit` / `UByteArray` references (replaced on every
create) process-wide without a lock; creates / closes on different engine threads crashed the JVM. Both modules now
create and close engines under the monitor of the `com.dokar.quickjs.QuickJs` class (shared without a module
dependency; `:source-js` behind `expect fun quickJsLifecycle`, iOS: no lock) and keep a sentinel engine, created under
that lock and never closed, so the counter never reaches zero. Deviation from the plan's "one process-wide sentinel":
one per module (two engines), since the modules cannot share an object; the counter only has to stay above zero.
Stress probe (8 threads × 15000 create / evaluate `[1, 2.5, 'x']` / close): 120000 correct, no failure, both without
and with `-Xcheck:jni` (before: CheckJNI "Bad global or local ref" FATAL within 3 s, product-mode SIGSEGV in
`getEvaluateResult` after ~26k instances); CheckJNI still prints quickjs-kt's general "JNI call made without checking
exceptions" warnings (720000 in that run). Residual, not serialised (that would serialise every evaluation): the
per-create replacement of the `Unit` / `UByteArray` references can race with an evaluation on another thread that
converts such a value, and a lazily cached class reference can be created twice (a leaked global reference). Tests:
`EngineLifecycleTest`, `QuickJsLifecycleTest` (the lock is held while creating / closing).

**`close()` and hangs [A1-3, B3, D4, C6].** Every evaluation is a coroutine that `close()` cancels: quickjs-kt
interrupts busy JavaScript and stops waiting for pending jobs, so the call ends with the closed-instance
`NullPointerException` instead of blocking its caller and every later `close()` forever. Calls queued before
`close()` fail the same way (C6: the KDoc claimed they ran first; quickjs-android had no queue, it ran on the calling
thread). Hangs that stay until `close()` (deviations, quickjs-android ran no jobs): a promise job that keeps
rescheduling itself, a top-level `await` of a promise that never settles. `Object.prototype.then` no longer hangs
(completion helper). Test: `QuickJsCloseTest`.

**Bindings [A1-4, A1-6, C4, A1-2, N2]** (`set` / `get`, which no extension uses; kept for ABI parity). `compile()`
from inside a bound Java method is refused like `evaluate()` (quickjs-kt re-based the stack limit at the binding's
depth, a native stack overflow; and the refused call cannot report a stale binding failure any more). The generated
code takes the raw quickjs-kt binding first, through a bare identifier and `delete` in sloppy code, before reading any
built-in, and reaches the global object as a sloppy function's `this` instead of through `globalThis` (a replaced
`globalThis` Proxy or a patched `Array.isArray` getter could take the raw binding and call it with an `Error`);
`set()` checks the name before defining the raw binding (which quickjs-kt kept registered with the bound object).
Documented, not changed: bound methods run on the engine thread, not on the caller's (thread locals and locks of the
calling thread are not visible; a method that waits for the calling thread deadlocks) [B6]; `throw { name: 'X',
message: 'm' }` reports "[object Object]" and an error whose name contains a space keeps its prefix ("My Error: m";
quickjs-android: "m" for both) [D6]; for `Object`-typed values of a binding or proxy an integral double arrives as
`Integer` (JavaScript cannot see QuickJS's int / float tag) [D7].

**Finalizer [K2, A1-7, D3].** The runtime references its `QuickJs` wrapper, so whatever reaches the runtime (a call
in flight, held by the engine thread's queue; a `get` proxy's invocation handler) keeps the wrapper reachable and
its finalizer cannot close the engine under them; the pair is still collected together (no `reachabilityFence`,
which needs API 28). A proxy of an otherwise unreferenced instance no longer fails after a GC
(`QuickJsFinalizerTest`); the review's stress probe (never-closed instances evaluating under GC pressure) gave 0
failures in 9637 (before: 108 in 14690).

**Threads [E2].** The engine thread of a `QuickJs` and of a `:source-js` context ends after 30 s without calls; the
next call starts one of the same size (quickjs-kt re-bases the stack limit at every entry, so state and the JS stack
limit carry over: `EngineThreadTest`, `JsEngineThreadTest`). The claim that QuickJS measures its stack limit from the
creating thread (decisions above, `QuickJsEngine` KDoc) was wrong and is corrected.

**Small [A1-8, Rec-4, Rec-5, Rec-8].** One `[versions]` entry for both quickjs-kt artifacts; the Android host test
swap is one `buildSrc` function (`useQuickJsKtJvmInAndroidHostTests`); `QuickJsErrorClassTest` uses the module's
`jsString`; number narrowing is one function; U+2028 / U+2029 are written as `\u` escapes in both JS string escapers.

**`:source-js`: extension text [C1, A2-1, G1].** The text was syntax-checked as an async script (quickjs-kt's only
grammar: `await` a keyword) but run as the body of a sync function (`await` an identifier): a regular expression in
one was a division in the other, so a text could close the wrapper and run code at the top level, outside the entry
(an error thrown there reached quickjs-kt, ran twice through the bytecode fallback and wiped the shared cache). The
wrapper is now an `async function` (same grammar as the check), which the entry calls with `this` = the global object
and awaits. Its body starts with `await undefined;` so a throw before the text's first `await` rejects a promise
that already has a handler (an async function rejecting at once would be converted natively by quickjs-kt's
rejection tracker). Deviations from Mangayomi's global script: top-level `var` / `function` declarations are the
factory's own, not properties of `globalThis`; a leading `"use strict"` is not a directive (the text runs in sloppy
mode); the text runs one microtask later; top-level `await` works. Tests: `ExtensionTextTest` (both escape texts, no
code outside the entry, cache not wiped, `this`, declarations), `ErrorClassInjectionTest` (file payload).

**Failures are `JsSourceException`s [C2, B5, A2-2, D5].** quickjs-kt throws an instance of whatever class an
unhandled rejection names, so `getPopular()` could throw `java.lang.Error`, `kotlin.NotImplementedError` or
`java.lang.StackOverflowError` as themselves, or be reported as `OutOfMemory` through a forged
`JsEngineException$OutOfMemory`. `QuickJsEngine` turns every non-cancellation `Throwable` of the engine into a script
error, the host catches `Throwable`, and anything else that fails a call (creating the engine, the host's own
decoding) is a `BridgeError` on channel `engine`. The host bindings and the DOM's selector parser catch `Throwable`, so
an `Error` of a service is a host failure the script can catch. Tests: `ForgedThrowableTest` (checks first that
quickjs-kt really instantiates the classes, so it cannot pass vacuously), `forgedCancellationsAreScriptErrors` (now
checks the script's message), `errorsOfHostServicesAreBridgeErrors`.

**Bytecode [Rec-9, N1, E3, Rec-2].** Only a script error while loading cached bytecode falls back to the source;
running out of memory or being interrupted is the call's failure and keeps the cache. The cache key covers the file
name (it is in the bytecode's stack traces) and is computed once per `JsExtension`. `Sha256` stays; public
`sha256Hex(text)` for I4.

**How a call ends [A2-5, A2-4, A2-3, G2].** A call whose entry has reported its outcome returns it: the evaluation is
then cancelled (interrupting stray JavaScript such as a busy job or timer) instead of running into the call timeout.
`clearTimeout` also cancels a timer whose wait has ended but whose callback has not run. The promises of the host's
own bridge (`Client` requests, `evaluateJavascriptViaWebview`, the unsupported async stubs) get a no-op rejection
handler, so a request that fails while the source awaits something else can still be handled later and keeps its
`BridgeError` channel; their functions start with an `await`, so the source's arguments are converted only once the
handler is attached. An `async` `setTimeout` callback that rejects is logged like a throwing one, and an `async`
`getSourcePreferences` counts as no preferences without failing the call. Deviation, documented in the contract: a
promise the source creates itself that rejects while the source awaits something else, and that it handles only
afterwards, still fails the call (quickjs-kt fails it on a rejection that has no handler once it has run all pending
jobs). Test: `CallLifecycleTest`.

**HTTP [C5, D2, E1, AL4, Rec-6].** `OkHttpHostHttp` resumes the call on every `Throwable` of its response handler
(a cap of `Long.MAX_VALUE` overflowed, threw on OkHttp's thread and left the call hanging) and no longer uses the
internal `okhttp3.internal.http.HttpMethod`; the too-large message comes from `HttpBridge`. The prelude slices the
response and its body from the reply itself and the host builds the reply in one buffer of its final size: a body is
in the JavaScript heap twice (reply and body) instead of three times. The default body cap is an eighth of the heap
(8 MiB for 64 MiB; it was 50 MiB, which a body with one character above U+00FF could not reach before the heap
ran out: 12 MiB failed): each copy takes up to two bytes per character, which leaves half the heap to the source
(`JsHostLimits` KDoc; `aBodyAtTheDefaultCapFitsTheDefaultHeap` tests a worst-case body at the cap).

**API for the adapter [AL1–AL8, E4].** `JsSourceException.NotImplemented(member)` for async members the source does
not implement (the adapter no longer has to match the text); every `JsSourceException` carries `sourceId`; the
`BridgeError.channel` values are documented. `metadata()` keeps the members that worked (a failing member gets its
default with a warning; the single calls still throw; out of memory, timeouts and load failures still fail it) and
its KDoc says to read it again after a preference change (headers and `supportsLatest` may depend on preferences).
`search(filters = null)` reads the default filters with `getFilterList()` in the same context (one engine; a
script failure there gives `[]` with a warning, out of memory fails the search). An unknown host channel is a host
failure; the prelude takes the binding names from `JsSession`'s constants. `JsEngineThread` is internal;
kotlinx-serialization-json and OkHttp are `api` dependencies.

**Prototype lockdown [D1]** (kept, security). What it costs extensions: adding a property to `Object.prototype`
throws in strict code (class bodies) and is silently dropped in sloppy code (the text's top level); assigning
`constructor` or `then` on an object that inherits from `Promise.prototype` fails the same way (`defineProperty`
works); existing `Object.prototype` properties stay writable. Test: `PrototypeLockdownTest`; also in the contract.

**QuickJS out-of-memory crash (found in this round).** While testing these fixes, QuickJS (2026-06-04 through
quickjs-kt 1.0.15) crashed the JVM (SIGSEGV in `JS_DefineProperty`, a `NULL` object, inside `executePendingJob`)
whenever a source ran out of memory in an `Array.prototype.push` loop — depending only on the shape of the prelude's
code: with plain instead of async methods in the `Client` class body (at every heap limit from 8 to 48 MiB) or with
one envelope helper extracted from the entry (in `getFilterList`), and also when the prelude ran from source instead
of bytecode. The `Client` layout that shipped passed a survey of 36 out-of-memory cases (three loops, twelve heap
limits) in child JVMs, and the final prelude passes the 16 cases below; cause not found (QuickJS's out-of-memory
paths: the same JavaScript runs in both shapes). `OutOfMemoryCrashTest` runs 16 such cases in child JVMs so a prelude change
that brings the crash back fails one test with the case named. The envelope refactor [Rec-7] was therefore not done.
Any QuickJS out-of-memory may still crash the app in code shapes not covered (risk for the maintainer, PLAN 8.7).

**Not changed:** E5 (both CI test runs are deliberate), E6 (no single-flight for a cold compile: a rare duplicate
compile), Rec-10 (load phase tracked in the prelude and in `JsSession`, each for its own side), Rec-11 (the
`metadata` path and the single sync calls stay separate), Rec-13 (the `set` / `get` bridge is kept for ABI
parity), and the `quickjs-kt` class instantiation itself (accepted risk; the three `@Disabled` / `@Ignore` tests stay).

**Tests and CI.** `:core:quickjs` 38 tests (2 `@Disabled`; was 16), on desktop and the Android host;
`:source-js` `desktopTest` 110 (1 skipped; was 87), `testReleaseUnitTest` 83 (was 67). CI runs
`:core:quickjs:desktopTest` next to `:source-js:desktopTest`.

### I3 — source adapter

A Mangayomi JavaScript source as a Mihon source (PLAN.md 8.3, I-D4), usable on Android and desktop. Decisions taken
without the maintainer, as asked. Wiring into `AndroidSourceManager` / `DesktopSourceManager` is I4 (the store
provides the list); I3 only makes a source built by the runtime work when handed to the existing code paths.

**What exists** (package `app.komikku.sourcejs.mihon` in `:source-js`):

- `commonMain` (pure, no JVM types): `JsSourceIds` (`komikkuId(mangayomiId)` = first 8 bytes of
  `MD5("mangayomi-js/<id>")` big-endian with the sign bit cleared, the shape of Mihon's generated ids;
  `preferencesName(id)` = `source_<id>`, the same name `ConfigurableSource.preferenceKey()` gives; `normalizeLang`),
  `JsSourceUrls` (`normalizeBaseUrl`, `resolveCoverUrl`, `resolveWebUrl`), `MihonMangaStatus` + `toMihonStatus()`
  (0 ongoing → 1, 1 completed → 2, 2 on hiatus → 6, 3 cancelled → 5, 4 publishing finished → 4, 5 unknown → 0;
  constants repeated from `SManga` because `commonMain` cannot see `source-api`, checked by the tests), `JsPageRef`
  (a page's image URL and headers, encoded into `Page.url` as `mangayomi-page:<json>`).
- `jvmSharedMain` (Android + desktop; `api(projects.sourceApi)` on that source set resolved for both targets, so no
  separate module was needed): `JsFilters` (+ `HeaderFilter` … `GroupFilter`, `JsBackedFilter`),
  `SharedPreferencesHostPreferences`, `JsPreferenceScreens`, `MangayomiJsSource`, `MangayomiJsRuntime`.
- `:app` and `:desktopApp` depend on `:source-js` (KMK block in `app/build.gradle.kts`); `app/proguard-rules.pro`
  keeps the serializers / companions of `app.komikku.**` (KMK block next to the `exh.**` rules).

**Adapter (`MangayomiJsSource`)**: an `HttpSource` + `ConfigurableSource` (the chapter loader, downloader, download
store and cover fetchers only handle `HttpSource`s). It overrides `id`, `client`, `headers` and every request helper
it could reach, so it never touches `HttpSource`'s Injekt-provided `network` or the SY delegate and can be built
without Injekt (unit tests do).

- *Identity:* `name` from the index; `lang` normalised to Mihon's casing (`pt-br` → `pt-BR`, `zh-hans` → `zh-Hans`,
  blank / `all` / `multi` / `*` → `all`) so language groups merge with Mihon sources; `baseUrl` trimmed of trailing
  slashes; `supportsRelatedMangas = false` (related titles by search still work, through `search`). `toString()` (the
  download directory name) is `"<name> [JS] (<LANG>)"` and `stubName` = `"<name> [JS]"` (review fix 1).
- *Metadata:* `supportsLatest`, filters, preferences and `getHeaders(baseUrl)` from `JsExtension.metadata()`, cached
  (Mihon reads them synchronously). A change of the source's preference file (an `OnSharedPreferenceChangeListener`
  held by the source, since Android keeps them weakly; keys of the free string store are ignored) marks the metadata
  stale and refreshes it in the background; every call first waits for a pending or running refresh
  (`awaitMetadata`). A refresh that fails keeps the cached metadata (logged) and is retried by the first call after
  `DEFAULT_METADATA_RETRY_DELAY_MILLIS` = 60 s (review fix 10). `refreshMetadata()` forces one;
  `close()` unregisters and cancels the refresh scope. Opening the settings screen persists the defaults, which
  triggers one (harmless) refresh.
- *Calls:* at most `DEFAULT_CONCURRENT_CALLS` = 2 per source at a time (`Semaphore`; each call creates a context of up
  to 64 MiB), metadata refreshes included, and at most `MangayomiJsRuntime.DEFAULT_CONCURRENT_CALLS` = 4 across a
  runtime's sources (review fix 7).
- *Lists:* entries without a `link` are dropped (they cannot be opened again); a blank name shows the link; covers
  resolved against `baseUrl`, `http(s)` only (review fix 8).
- *`getMangaUpdate`:* one `getDetail(manga.url)` for details and chapters (none when neither is asked). Because
  `UpdateMangaFromRemote` writes author / artist / description / genre / status unconditionally, details the source
  leaves out (`null` / blank, empty genre, status unknown) keep the incoming values; a blank name keeps the title; a
  missing cover keeps the old one; `initialized = true`. Chapters: `date_upload = dateUpload ?: 0`,
  `chapter_number = -1` (recognised by the app from the name), a blank name shows the URL.
- *Pages:* relative page URLs resolved against `baseUrl`, de-duplicated; `Page.url` always carries `JsPageRef(url,
  headers)` and `Page.imageUrl` the URL. **Deviation from the design note** (which put headers in `Page.url` only when
  present and `""` otherwise): the URL is always included so `getImageUrl(page)` can recover it when
  `Page.imageUrl` was cleared (reader retry on EH-based sources, downloader paths). `imageRequest` uses
  `Page.imageUrl` (so the data saver's replacement is honoured) with the source headers plus the page headers (page
  headers win).
- *Headers:* invalid header names / values are skipped (non-ASCII values without control characters through
  `addUnsafeNonAscii`, review fix 9);
  `Accept-Encoding` from the source is dropped so OkHttp decompresses, as for the script's own requests.
- *WebView / share:* `getMangaUrl` / `getChapterUrl` (and `mangaDetailsRequest` / `chapterListRequest` /
  `pageListRequest`) use the link when it is an absolute or protocol-relative URL or a path starting with `/`;
  any other link is opaque (an id) and opens the home page `baseUrl`.
- *Failures:* `getLatestUpdates` not implemented → an empty page, and `supportsLatest` turns false until the next
  metadata refresh (Mangayomi's `supportsLatest` defaults to `true`, so many sources claim latest without it);
  `search` not implemented → an empty page (global search shows nothing instead of an error; only a `NotImplemented`
  of those two members themselves, review fix 6); filter / preference /
  header failures already fall back to defaults in `metadata()`. A `BridgeError` on channel `http` whose cause is an
  `IOException` (a network failure the script did not handle) is rethrown as that `IOException`, so the app's
  formatter shows "offline" / "unknown host" as for any source. Everything else (including `NotImplemented` of
  `getPopular` / `getDetail` / `getPageList`, timeouts, out of memory) stays the `JsSourceException`, whose message
  is readable (`Extension "X" failed while getPopular(1): TypeError: …`); no new user-visible strings were needed.

**Filters (`JsFilters`)**: each Mihon filter (`Filter.Header` … `Filter.Group`) keeps its `JsFilter`;
`getFilterList()` builds fresh objects from the cached metadata on every call (Mihon mutates states). Back: every
`JsBackedFilter` becomes `js.copy(state = …)` in order (groups recursively; a `Sort` with no selection keeps the
source's own state; select / sort indexes and tri-states clamped); other filters are skipped; an empty or foreign
list (global search and related-titles search pass `FilterList()`) becomes the cached default list. The adapter never
passes `null` to `search` (which would re-read the defaults in the call's context): the cached list is the same
thing.

**Preferences**: `SharedPreferencesHostPreferences` backs the script's `SharedPreferences` with the source's
`source_<komikkuId>` file (the file the settings screen writes and backups include) using Android types: check box /
switch `Boolean`, list / edit text `String` (a list stores its entry value), multi-select `Set<String>` (returned in
the declared `entryValues` order, unknown values after them sorted). A stored value whose type does not fit the
declared preference reads as missing (the script gets the declared default); before the metadata is known the stored
type decides. The free `getString` / `setString` store uses keys prefixed `mangayomi_str:` in the same file. The
existing `KeyValueHostPreferences` (tagged strings) was not usable for a file the settings screen also writes.
`JsPreferenceScreens` maps check box → `CheckBoxPreference`, switch → `SwitchPreferenceCompat`, list →
`ListPreference` (a `SummaryProvider`: the declared summary verbatim, the current entry when blank; review fix 2),
multi-select → `MultiSelectListPreference`, edit text →
`EditTextPreference` (dialog title falls back to the title); blank or repeated keys are skipped and entry lists are
cut to the shorter of `entries` / `entryValues` (AndroidX indexes one by the other); stored values of the wrong type
are removed before the widgets read them (review fix 4). The file is compiled for each
target against AndroidX (Android) and the compat model (desktop) — `PreferenceScreen` is an `expect class` in
`source-api`, but shared Android + JVM source sets are compiled per target, so no `expect` / `actual` was needed.

**Runtime (`MangayomiJsRuntime`)**: one `JsExtensionHost` for all sources; `OkHttpHostHttp` over the app's client
(read per request, `NetworkHelper.client`), preferences through `SharedPreferencesHostPreferences` typed by the
declared preferences of the source built for that Mangayomi id, console / host warnings to `exh.log`
(`XLogHostLog`, prefix `[JS <name>]`; review fix 11). `source(descriptor, code, metadata?)` reads the metadata when not
given (a load failure throws its `JsSourceException`), and once more when the new declarations type a stored value
differently from the first read (review fix 5); a source built again for the same id replaces and closes the earlier one;
`remove(descriptor)` / `close()`. The app constructor takes `(Application, NetworkHelper)`; the primary one takes a
preference-file function and a client function (tests).

**Tests** (`:source-js`): `AdapterHelpersTest` 6 (`commonTest`: ids against Python's MD5, languages, URLs, statuses,
page refs), `JsFiltersTest` 6 (`jvmSharedTest`, desktop and Android host), `JsPreferencesTest` 4 and
`MangayomiJsSourceTest` 10 (`desktopTest`; a small extension against `MockWebServer`: identity, popular with the
script's own headers and preference-dependent query, metadata / headers / filters / settings screen, a preference
change refreshing headers and `supportsLatest` (the free string store does not), search with changed and default
filters, the details merge rules and chapter mapping, pages with headers through `getImage`, the data saver and the
cleared-URL path, web URLs, missing latest / search, network failure as `IOException`, readable `NotImplemented` /
`ScriptError` messages, a rebuilt source closing the old one). `:desktopApp`: `JsSourceBrowseUiTest` 2 (the runtime on
the desktop app's network client and preference files, a Mangayomi-style extension using the DOM bridge, handed to the
existing code through `FixedSourceManager`: headless browse popular / search → details → add to library (stored
description, title, resolved cover) → the settings screen → turning latest off refreshes `supportsLatest`; and the
reader's `ChapterLoader` loading a page into the chapter cache with the source's `Referer` (the server refuses images
without it) and the page list cache keeping the image URL in `Page.url`). That test found the refresh race fixed in
`bbd19b16bf`. Totals: `:source-js` `desktopTest` 136 (1 skipped; was 110), `testReleaseUnitTest` 95 (was 83). The whole
`:desktopApp:test` suite passes with `:source-js` on its class path (30 classes, 88 tests). Also run: `spotlessCheck`,
`:app:compileDebugKotlin`, `:app:checkDebugDuplicateClasses`, `:desktopApp:compileKotlin` (all pass); `:core:quickjs`
was not touched.

**Known gaps / risks:**
- Not wired into the source managers, no store, no UI label for JavaScript sources (I4); nothing was run on a
  device, under R8, or against a real website or real extension through the adapter.
- The Android preference screen path (`JsPreferenceScreens` on real AndroidX, listener on the main thread) is only
  compiled, not run (Android host tests cannot instantiate AndroidX preferences); likewise
  `SharedPreferencesHostPreferences` on Android's `SharedPreferencesImpl`.
- `MangayomiJsSource` registers its preference listener in its constructor (the runtime's provider resolves the
  file through the `Application`), so building one touches the preference file.
- Source ids cannot collide with Mihon ids except through a 63-bit MD5 prefix collision; no reserved-id check (e.g.
  `LocalSource.ID` 0) is made.
- After any preference change `supportsLatest` is offered again until `getLatestUpdates` proves missing again.
- Per-extension incognito, `isNsfw`, `hasCloudflare` and `notes` are not used by the adapter (store / UI, I4).

### Review fixes (I3)

Max-level review of `c259852190^..f49bc4e909` (12 findings: 6 important, 6 nits; the finding list was deleted once
fixed). Each finding was first shown by a failing test where a unit test can show it (findings 2–9 failed before the
fix on the unfixed adapter; 1, 10 and 12 are new behaviour, 11 is logging). All 12 fixed:

| # | Finding | Fix | Commit |
|---|---------|-----|--------|
| 1 | `toString()` (download directory) could equal a Mihon source's for the same site; `DownloadCache` keeps one id per directory name | `toString()` = `"<name> [JS] (<LANG>)"`; `name` stays the index name; `stubName` = `"<name> [JS]"`, so a `StubSource(id, lang, stubName)` gives the same directory. Checked against the borrowed `DownloadProvider.getSourceDirName` (used by `DownloadCache` / `DownloadManager` on both platforms; the desktop has no own equivalent) in `JsSourceBrowseUiTest`. Format `[JS]` before the language (not appended at the end, as the decision's example said) so the stub's `StubSource.toString()` matches | `6bdcd94` |
| 2 | `%` in a list summary crashed settings (AndroidX `String.format`) | `SummaryProvider` (declared summary verbatim, current entry when blank); no plain summary on lists | `bf5912b` |
| 3 | A select filter without options crashed the filter sheet (`options[0]`) | `EmptySelectFilter`: a `Filter.Header` with the name, `toJs()` returns the filter unchanged | `d271060` |
| 4 | A stored value of another type crashed settings (`ClassCastException` in typed getters) | `JsPreferenceScreens.setup(screen, prefs, stored)` removes values that do not fit (`SharedPreferencesHostPreferences.fits`, the rule of `getValue`) before adding widgets; the adapter passes its own file (on Android the screen uses a data store, so `screen.sharedPreferences` is `null`) | `bf5912b` |
| 5 | The first metadata read was typed by no / the previous version's declarations | after registering, the runtime compares every stored value read under the old and the new declarations and reads the metadata once more when one differs (wrong type, or a set's order) — no extra read in the common case | `caad000` |
| 6 | Any `NotImplemented` counted as "no latest / no search" | only `member == "getLatestUpdates"` / `"search"`; others are rethrown | `67e896c` |
| 7 | No runtime-wide call limit | `MangayomiJsRuntime(concurrentCalls = 4)` shares a `Semaphore`; a source takes its own permit, then the shared one (the runtime's first metadata read takes only the shared one), so no cycle | `852b678` |
| 8 | Covers accepted `file:` / `content:`; `data:` pages failed in OkHttp | `resolveCoverUrl` returns only `http(s)` URLs (protocol-relative get `https` unless the base is `http`; paths need an `http(s)` base); a page with another URL keeps its place without `imageUrl` and fails with `Page N of X has an unsupported image URL (only http and https): …` | `af14fe9` |
| 9 | Header values with CR / LF / NUL were sent through `addUnsafeNonAscii` | that fallback only for values without control characters (tab allowed) | `88e0683` |
| 10 | A failed refresh was never retried | stays stale; retried by the first call after 60 s (`metadataRetryDelayMillis`, `clock` injectable); a preference change / `refreshMetadata()` tries at once | `81e43bd` |
| 11 | `logcat` instead of `exh.log` | `exh.log` is reachable (`source-api` → `core:common`, no new dependency): `xLogW`, `XLogHostLog` (was `LogcatHostLog`); AGENTS.md unchanged | `7eb7da5` |
| 12 | `JsSourceBrowseUiTest` never closed its runtime | field, closed in `stop()` | `9efd135` |

New tests: `JsPreferencesTest` +3 (summaries, wrong-type reset, right-type kept), `JsFiltersTest` +1 (and the empty-select
case of `indexesOutOfRangeAreClamped` now expects the header), `MangayomiJsSourceTest` +5 (typed first read, delegated
`NotImplemented`, shared limit across three sources, header control characters, retry delay with a fake clock) and the
`data:` page in `pagesCarryTheirHeadersToTheImageRequest`, `AdapterHelpersTest` (schemes), `JsSourceBrowseUiTest` +1
(download folder). Totals: `:source-js` `desktopTest` 145 (1 skipped; was 136), `testReleaseUnitTest` 96 (was 95);
`:desktopApp` `JsSourceBrowseUiTest` 3 (was 2).

**Left open / for I4:**
- The source managers must register a JavaScript source's stub as `StubSource(id, lang, source.stubName)` (not
  `StubSource.from(source)`, which takes `name`), or an uninstalled source's downloads get the Mihon-style directory
  name again. `StubSource` is in `domain`, which cannot see `:source-js`, so this could not be enforced here.
- Downloads made with a JavaScript source before this fix would sit in a directory without `[JS]`; none exist (the
  sources were never registered).
- A preference change that arrives while a refresh is failing waits for the retry delay (the failure sets it after the
  change cleared it); a later change or `refreshMetadata()` tries at once.
- Not run: the Android paths of fixes 2 and 4 (real AndroidX `ListPreference.SummaryProvider`, data store reads) are
  compiled only, like the rest of `JsPreferenceScreens` on Android; the intermediate commits were not compiled one by
  one (the final tree was).

### I4 — repository store and UI

Mangayomi repositories, installed JavaScript sources and their UI on Android and desktop (PLAN.md 8.4, I4). Decisions
taken without the maintainer, as asked.

**What exists:**

- `:source-js` `commonMain`, package `app.komikku.sourcejs.repo` (pure, no JVM types): `MangayomiIndex.parse` (legacy
  JSON array; keeps entries with `sourceCodeLanguage == 1`, `itemType == 0` and `appMinVerReq` not above
  `TARGET_APP_VERSION` = `0.9.96`, the version the contract describes), `MangayomiRepoInfo` (`repo.json`, also under
  `meta`; `infoUrl(indexUrl)`), `MangayomiVersions.compare` (contract 1.3), `JsSourceStatus` (`UP_TO_DATE`,
  `UPDATE_AVAILABLE`, `TEXT_CHANGED`, `OBSOLETE`, with `of(…)`), `JsSourceOffer` (+ `merge`). The index entries are the
  existing `JsSourceDescriptor`s.
- `:app-shared` (`api(projects.sourceJs)`), package `app.komikku.shared.jssource`: `JsSourcePreferences`
  (`mangayomi_js_repos` string set in the main preference store), `MangayomiSourceStore` (+ `forApp(…)` for DI,
  `sourcesOrEmpty(…)` for the source managers), models (`InstalledJsSource`, `JsSourceRepository`, `JsSourceItem`,
  `JsSourceDownload`, `JsSourceStoreException` with a `Reason`), `JsSourcesScreenModel` (Voyager `StateScreenModel`)
  and the Compose UI shared by both platforms (`JsSourcesContent`, `JsSourceLabel`, `JsSourceNotesBanner`).
- DI: Android `KMKDomainModule` (preferences + store); desktop `DesktopPreferenceModule` (preferences) and
  `DesktopDomainModule` (store). One `MangayomiJsRuntime` per store (on the app's client and preference files).
- Source managers: `AndroidSourceManager` (KMK blocks) and `DesktopSourceManager` (new `jsSources` parameter).
- Android UI: `app/src/main/java/mihon/feature/jssources/JsSourcesScreen.kt`; entry points in `ExtensionsTab.kt`
  (overflow action) and `SettingsBrowseScreen.kt` (Sources group); the "JS" label in `SourcesScreen.kt`; the notes
  banner in `BrowseSourceScreen.kt`; the icon branch in `ExtensionManager.getAppIconForSource` (all KMK blocks).
- Desktop UI: third tab "JavaScript sources" in `BrowseHomeScreen` (`JsSourcesPane.kt`), "JS" label in its Sources
  tab, notes banner in `CatalogueScreen`.
- 33 new strings `js_source*` in `i18n-kmk` base only.

**Store behaviour (`MangayomiSourceStore`):**

- *Repositories:* index URLs in the preference set (the app-preference backup carries them). `addRepository` trims
  the URL, refuses non-`https` ones, reads the index once (not a Mangayomi index / not readable → not added) and stores
  it. `refresh` reads every repository in parallel; a failed read keeps the entries of the last good read and records
  the error (shown on the repository row). `repo.json` only labels the repository (a missing or broken one is no
  error; `index_v2` is ignored). Offers are merged by id across repositories in URL order (the set has no order), the
  highest version winning, the first repository on a tie.
- *Files:* `<filesDir>/mangayomi-js/` (`Context.filesDir`; desktop `<dataDir>/files`): `manifest.json`
  (`{version: 1, sources: [InstalledJsSource]}`: descriptor, repository URL, SHA-256, installed / updated times, cached
  metadata), `code/<sha256>.js` (shared by entries of one file; deleted when no entry uses it), `icons/<mangayomi id>`.
  Every write goes to `<name>.tmp`, is synced, then moved over the old file (`ATOMIC_MOVE`, plain replace where the
  file system cannot). A manifest that does not decode is renamed to `manifest.json.corrupt-<time>` and the store
  starts empty with a warning. A text whose file is missing or no longer has its recorded SHA-256 is not loaded
  (reported on its row as a load error); other sources still load.
- *Start-up:* the store reads its files in the background (the source managers wait for that, bounded; see "Review
  fixes (I4)"). A source with cached metadata is
  built without running its script and then refreshes its metadata in the background (it may predate preference
  changes); a source without (a manifest written without it) runs its script once and the metadata is
  written to the manifest. Loading holds the store's lock, so an install / update / uninstall started meanwhile waits
  for it (and cannot be overwritten by the start-up state or race it in the runtime).
- *Install / update:* two steps. `download(offer)` / `downloadUpdate(id)` fetch the text and return its host,
  SHA-256 and size for the confirmation; `install(offer, confirmedSha256)` / `update(id, confirmedSha256)` fetch it
  again and install it only if it still has the confirmed SHA-256 (`SHA_MISMATCH` otherwise; nothing is cached
  between the two, so the hash check is what binds the install to the confirmation). The text must load
  (`MangayomiJsRuntime.source`, which reads the metadata); a text that does not load is not installed and an update
  keeps the old version. No auto-update.
- *Statuses:* an installed source is `UPDATE_AVAILABLE` when the best offer has a higher version; `TEXT_CHANGED` when
  the version is the same but the offered text has another SHA-256 — `refresh` downloads the offered text of every
  installed source whose version did not change to know this (one download per such source per refresh);
  `OBSOLETE` when no repository offers it and its own repository was removed or read well (not while that repository
  was not read yet).
- *Network:* only `https` URLs are fetched (repositories, `repo.json`, code, icons). Redirects are followed by the
  store itself (at most 5), each target checked before it is requested. No HTTP cache (`FORCE_NETWORK`). Size limits:
  index 8 MiB, `repo.json` 64 KiB, code 4 MiB, icon 1 MiB (`Limits`; `TOO_LARGE`). Icons are best effort (an `http`,
  missing or too large icon only means no icon). The script's own requests are not restricted (sites may be `http`).
- *Uninstall:* removes the source (runtime closes it), its record, its text (unless shared) and its icon; its
  preference file `source_<komikkuId>` stays, as for uninstalled Mihon extensions.
- *NSFW:* entries with `isNsfw` are left out of `sources` (and from the offers in the list) while
  `SourcePreferences.showNsfwSource()` is off, as the task asked — their library entries then show as stubs; installed
  NSFW sources stay in the list so they can be uninstalled.

**Source managers:** both add the store's sources after the extension sources (an extension source wins an id clash,
which needs a 63-bit MD5 prefix collision) and register their stubs as `StubSource(id, lang, source.stubName)`, so an
uninstalled source keeps its `"<name> [JS] (<LANG>)"` download folder (PROGRESS "Review fixes (I3)", left open item 1;
checked in `JsSourceManagerTest`). Android: the extension flow is combined with the store's flow and the sources are
read in the KMK block after the extension loop. Desktop: `jsSources` is the fourth `combine` input (the manager also
runs with only JavaScript sources). `isInitialized` did not wait for the store at first; since the review fixes it
waits for the store's first loaded list, at most 15 s ("Review fixes (I4)"). When the store cannot be created at all
(`sourcesOrEmpty`), the managers run without JavaScript sources (logged).

**UI:** one `JsSourcesScreenModel` + `JsSourcesContent` for both platforms: a "Repositories" header with an add
action, one row per repository (name from `repo.json`, URL, entry count or error, remove with confirmation), then
"Updates pending" / "Installed" / "Available" (MR strings) with name, the "JS" label, version (and `→ new`), language,
NSFW / obsolete / "Text changed" marks, the source's `notes`, a load error; actions install / update / uninstall
(confirmation) and settings. The install / update confirmation shows a warning that the source runs downloaded code,
the version (old → new for updates), "The text changed under the same version" in the error colour when it applies,
the host, the size and the SHA-256 (selectable; for updates also the installed one). Failures are shown in a dialog
(one message per `Reason`). The model refreshes the store when it is created (Android: opening the screen; desktop:
opening the JavaScript sources tab, since the review fixes).

- Settings of an installed JavaScript source: like any `ConfigurableSource` — Android: the settings action of
  `BrowseSourceScreen` / `MangaScreen` (`SourcePreferencesScreen`, unchanged) and the store list; desktop: the
  Sources tab, the catalogue and the store list (`SourcePreferencesScreen`).
- Icons: Android `ExtensionManager.getAppIconForSource` returns the stored icon (`Drawable.createFromPath`, cached
  per source and install time), so every place that resolves source icons shows it; the store lists load the stored
  file or the `https` icon URL through Coil. The desktop Sources tab has no source icons (unchanged), only the label.
- Notes: banner above the catalogue (Android `BrowseSourceScreen`, desktop `CatalogueScreen`) and on the store rows.

**Deviations from the design note / task:**

- Install is `download` + `install(offer, sha)` (and `downloadUpdate` + `update(id, sha)`) instead of one call, so the
  confirmed hash is an argument; the icon lookup is `iconFileForSource(komikkuSourceId)` (callers have Mihon ids).
- The shared UI and screen model live in `:app-shared` (one implementation, two hosts) rather than one screen per
  platform; the repository list is a section of the list instead of a separate screen or dialog.
- `JsSourceStatus` / `JsSourceOffer` are in `:source-js` (`repo`), the store models in `:app-shared`.
- The design note's `icons/<id>.png` became `icons/<id>` (the file is whatever the repository serves).
- `MockWebServer` serves plain HTTP, so the store takes an `allowUrl` policy (default: `https` only); the tests allow
  the server's host, and one test checks that the default refuses it.

**Tests:** `:source-js` `MangayomiIndexTest` 8 (`commonTest`: real-shaped entries with all 20 fields, filters by
language / type / app version, missing and loosely typed fields, duplicate ids, non-index documents, `repo.json`,
version comparison, offer merge, statuses). `:desktopApp` `MangayomiSourceStoreTest` 12 (store end to end in a
temporary directory against `MockWebServer`: refused `http` / non-URL / default-policy / redirect-to-refused URLs,
redirect loops, broken and missing indexes; add → list (name from `repo.json`) → install with the confirmed SHA-256 →
source browses, files and icon written → text changed under the same version → SHA-256 mismatch refused → update →
new version → reload from disk (cached metadata) → NSFW hidden and shown → obsolete → uninstall (files gone); size
limit; corrupt manifest set aside; tampered text reported while another source loads; install while a store is still
loading; JavaScript that does not load refused), `JsSourceManagerTest` 2 (desktop manager merge, stub name and download
folder after removal, DI wiring), `JsSourcesUiTest` 1 (headless: add a repository through the dialog, install after
the confirmation showing host and SHA-256, the source with its "JS" label in the Sources tab, its catalogue with the
notes banner). Totals: `:source-js` `desktopTest` 153 (1 skipped; was 145), `testReleaseUnitTest` 104 (was 96);
`:desktopApp:test` 33 classes, 104 tests (was 30 / 89).

**Known gaps / risks:**

- Android is compile-only: the screen, the entry points, the icon branch (decoded on the calling thread, the main
  thread on first use), the label and the banner were not run; nothing on a device or under R8 (the `app.komikku.**`
  keep rules from I3 cover the new serializable classes).
- Installed sources are not in backups (only the repository list is, as a preference); after a restore on another
  device their library entries show as stubs until the sources are installed again. Whether the backup includes their
  `source_<id>` preference files (as for extension sources) was not checked.
- A refresh downloads the text of each installed source whose version did not change (to detect `TEXT_CHANGED`).
- ~~`isInitialized` does not wait for the store~~: fixed in "Review fixes (I4)" (waits, at most 15 s).
- No Mangayomi deep link (`mangayomi://add-repo`), no update badge / notification for JavaScript sources, no
  per-source enable / disable beyond uninstall, no global search on desktop (unchanged gap).
- The Android source list shows the label for loaded sources only (a stub has no type).

### Review fixes (I4)

Medium review of `5780d0e^..dff17e0`: 9 findings (6 important, 3 nits), all fixed; a failing test was written first
where a unit test can show the defect (all seven new / changed store, manager and UI tests failed before the fixes).

| # | Finding | Fix | Commit |
|---|---------|-----|--------|
| 1 | A disk error during install / update / uninstall crashed the app (`busy()` caught only store exceptions) | The store's file writes report `JsSourceStoreException(STORAGE_FAILED)` (`IOException` / `SecurityException`; a failed atomic write removes its `.tmp`); the screen model's `busy()`, `addRepository` and `refresh` show any other `Exception` as `UNEXPECTED` (logged) instead of throwing. Two new strings. Test: manifest write blocked by a directory → `STORAGE_FAILED` for update and uninstall | `ddec1a6` |
| 2 | Source managers were initialized before the JavaScript sources were loaded (queued downloads dropped, download cache without them, reader stubs) | `MangayomiSourceStore.loadedSources` (`null` until loaded; the current values are read when `isLoaded` turns true, so the first list is complete) and `sourcesOrEmpty(loadTimeoutMillis = LOAD_TIMEOUT_MILLIS (15 s)) { store }`: the first list comes once loaded, or empty after the timeout (logged; the sources follow when loaded); a store that cannot be built is loaded-empty. Android (`stateIn` + `filterNotNull` in the KMK blocks) and desktop (`combine` input; `isInitialized` starts false when `jsSources` is given) wait for it. Tests: store whose scope thread is blocked → manager not initialized, then initialized with the source; timeout → initialized without it, source follows; broken store → initialized | `9a416e0` |
| 3 | `refresh()` replaced all listings with the URL set captured at its start | Merged into the current listings, filtered by the repositories added at the end; `addRepository` stores the URL before its listing. Test: refresh held on a slow index while one repository is added and another removed | `54ca78b` |
| 4 | A failed update left the old source closed | `MangayomiJsRuntime.prepare` (built, not registered) + `register` (swap, closes the old one; `source` = both); the store writes text and manifest first, then swaps (under `NonCancellable`); on a failure it closes the new source and removes its new text file. Test: manifest write fails → same source instance in `sources`, manifest record and text unchanged, new text gone, icon kept, browses, still follows its preferences (listener registered: a preference change refreshes `supportsLatest`); update succeeds once writable | `ddec1a6` |
| 5 | Opening desktop Browse refreshed every JS repository; store lookup threw when the store could not be built | The desktop model is created in the JS tab (`JsSourcesTab`); `MangayomiSourceStore.getOrNull()` guards the lookup (desktop tab and Android screen show `js_sources_unavailable`). The Android screen is reached on purpose, so its refresh on open stays. UI test: a preset repository is not requested when Browse opens, only when the tab does | `a1ef663` |
| 6 | The install dialog showed the pre-redirect host | `fetchBytes` returns the final URL; `JsSourceDownload.host` is the serving host, `requestedHost` the code URL's, the dialog shows both when they differ (new string). Test: redirect to another host name of the fixture server | `e7b8aa3` |
| 7 | Obsolete too early (only the source's own repository counted) | Known only when every added repository has entries (the item flow now follows the repository preference). Test: one repository read without the source, another unreadable → up to date; readable → obsolete | `54ca78b` |
| 8 | Stale icon after a failed icon download on update | The icon is fetched first and stored after the manifest; no icon (or a failed write) deletes the old file. Test in "a new version is an update" | `ddec1a6` |
| 9 | `infoUrl` wrong for an index URL without a path | Resolution against the index URL in common code (no path → `/repo.json` on the host; query / fragment with `/` dropped; trailing slash = directory). `commonTest` `repoInfoUrlResolvesAgainstTheIndexUrl` | `6337d12` |

New strings (`i18n-kmk` base only): `js_source_error_storage_failed`, `js_source_error_unexpected`,
`js_source_host_redirected`, `js_sources_unavailable`.

Tests: `:source-js` `desktopTest` 154 (1 skipped; was 153), `testReleaseUnitTest` 105 (was 104); `:desktopApp:test`
33 classes, 111 tests (was 104): `MangayomiSourceStoreTest` 16, `JsSourceManagerTest` 5, `JsSourcesUiTest` 1.
`spotlessCheck`, `:app:compileDebugKotlin`, `:desktopApp:compileKotlin` pass.

**Left open:**

- Android stays compile-only (the guarded screen, the manager wait). A cold start with a slow store now delays
  `isInitialized` (reader, download restore, feed) by up to 15 s; with cached metadata the wait is the file reads.
- The record written for an update carries the metadata read at install; when `register` reads it once more (the new
  declarations type stored values differently) the refreshed metadata reaches the manifest only at the next start
  (the source refreshes cached metadata in the background then).
- `TEXT_CHANGED` still costs one download per installed source per refresh; installed sources are still not in
  backups (unchanged from I4).

## Files moved out of upstream paths

Record every file moved to `src/androidMain` (old path → new path) so upstream cherry-picks can be
remapped. All moves are pure `git mv` (same package, same file name, so the same JVM class names on
Android); content edits are listed in the last column.

| Old path (upstream) | New path | Step | Edits / desktop counterpart |
|---------------------|----------|------|-----------------------------|
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt` | 1.2 | `actual class … actual constructor` (KMK-marked). `expect` in `src/jvmSharedMain/kotlin/…/CloudflareInterceptor.kt`; desktop `actual` in `src/desktopMain` (pass-through until P7; since P7 on a `BrowserBackend`). P7: detection and safe-header filter moved to `src/main/…/CloudflareChallenge.kt`, both platforms call it |
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt` | 1.2 | none; no desktop counterpart (only used by the Android `CloudflareInterceptor`) |
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt` | 1.2 | none; callers are Android-only (`app`, `WebViewInterceptor`) |
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/DeviceUtil.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/util/system/DeviceUtil.kt` | 1.2 | none; callers are Android-only |
| `core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/GLUtil.kt` | `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/util/system/GLUtil.kt` | 1.2 | none (EGL is part of `android.jar`); callers are `ImageUtil` (Android) and `app` |
| `core/common/src/main/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt` | `core/common/src/androidMain/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt` | 1.2 | none; desktop subset (`src/desktopMain/…/ImageUtil.kt`, 1.3) for the members other modules' shared code calls (`core:archive` `CbzCrypto` uses `isImage`) |
| `core/common/src/main/kotlin/tachiyomi/core/common/preference/AndroidPreference.kt` | `core/common/src/androidMain/kotlin/tachiyomi/core/common/preference/AndroidPreference.kt` | 1.2 | none; desktop uses `DesktopPreferenceStore` (`:core:preference`) |
| `core/common/src/main/kotlin/tachiyomi/core/common/preference/AndroidPreferenceStore.kt` | `core/common/src/androidMain/kotlin/tachiyomi/core/common/preference/AndroidPreferenceStore.kt` | 1.2 | none; same |
| `core/common/src/main/kotlin/tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt` | `core/common/src/androidMain/kotlin/tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt` | 1.2 | none; `DesktopStorageFolderProvider` (`src/desktopMain`, 1.3) |
| `core/archive/src/main/kotlin/mihon/core/archive/ArchiveReader.kt` | `core/archive/src/androidMain/kotlin/mihon/core/archive/ArchiveReader.kt` | 2.3 | `actual class` / `actual` members (KMK-marked). `expect` in `src/jvmSharedMain/…/ArchiveReader.kt`; desktop `actual` `ArchiveReader(File)` in `src/desktopMain` |
| `core/archive/src/main/kotlin/mihon/core/archive/ArchiveInputStream.kt` | `core/archive/src/androidMain/kotlin/mihon/core/archive/ArchiveInputStream.kt` | 2.3 | none; desktop class of the same name (`ArchiveInputStream(File, Boolean)`) in `src/desktopMain` |
| `core/archive/src/main/kotlin/mihon/core/archive/ZipWriter.kt` | `core/archive/src/androidMain/kotlin/mihon/core/archive/ZipWriter.kt` | 2.3 | none; desktop `ZipWriter` (same constructor and methods, zip4j) in `src/desktopMain` |
| `core/archive/src/main/kotlin/mihon/core/archive/UniFileExtensions.kt` | `core/archive/src/androidMain/kotlin/mihon/core/archive/UniFileExtensions.kt` | 2.3 | none; desktop `archiveReader` / `epubReader` (same signatures) in `src/desktopMain` |

**Source borrowing (4.2+):** files compiled by `:app-shared` instead of `app` are *not* moved; they
are listed in `app-shared/borrowed-app-sources.gradle.kts`. Upstream cherry-picks apply at the same
paths; a picked change that adds an Android-only API to a borrowed file breaks `:app-shared`'s
desktop compilation (fix: compat symbol, or move the file to `androidOnly` / `perPlatform`).
6L.1 edit: `app/…/domain/chapter/interactor/SetReadStatus.kt` — KDoc links fully qualified, their five
imports removed (KMK note in the KDoc). 4.2 split: `AndroidViewColorScheme` moved from `app/…/theme/colorscheme/CustomColorScheme.kt` (KMK-owned)
to `app/…/theme/colorscheme/AndroidViewColorScheme.kt` (same package and class name).

**`desktopCopy` borrowing (6L / 7):** the file stays compiled by `app` (Android unchanged) and is also
compiled by `:app-shared` `desktopMain`. Desktop counterparts (same FQN, `app-shared/src/desktopMain/kotlin`)
of what those files reference:

| Symbol (FQN) | Android original | Desktop counterpart |
|--------------|------------------|---------------------|
| `eu.kanade.tachiyomi.data.download.DownloadJob` | WorkManager worker | 8.1: coroutine runner, same companion API (`start` / `stop` / `isRunning` / `isRunningFlow`), no network checks |
| `eu.kanade.tachiyomi.data.download.DownloadNotifier` | download notifications | 8.1: `DownloadEvent`s to the KMK `DownloadNotificationSink` (desktop UI snackbars / log) |
| `eu.kanade.tachiyomi.data.notification.NotificationHandler` | notification intents | 8.1: `openUrl` returns the URL |
| `eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier` | library update notifications | 8.1: `HELP_WARNING_URL` constant only |
| `eu.kanade.tachiyomi.extension.ExtensionManager` | Android extension manager | 8.1: `typealias` of `PlatformExtensionManager` (for `DownloadCache`) |
| `eu.kanade.tachiyomi.BuildConfig` | generated build config | 8.2: `APPLICATION_ID`, `VERSION_NAME` / `VERSION_CODE` (system properties / manifest), `BUILD_TYPE`, `DEBUG`, `PREVIEW` |
| `eu.kanade.tachiyomi.data.backup.BackupNotifier` | backup notifications | 8.2: forwards to the KMK `BackupProgressListener` |
| `eu.kanade.tachiyomi.data.notification.Notifications` | notification channels / ids | 8.2: ids used by borrowed files |
| `eu.kanade.tachiyomi.data.backup.create.BackupCreateJob`, `eu.kanade.tachiyomi.data.library.LibraryUpdateJob`, `eu.kanade.tachiyomi.data.updater.AppUpdateJob` | WorkManager jobs | 8.2: `object`s, `setupTask` = no-op (no scheduling on desktop) |
| `eu.kanade.tachiyomi.data.track.TrackerManager` | tracker registry | 8.3: same trackers / API + `extraTrackers` constructor parameter (tests) |
| `eu.kanade.tachiyomi.data.track.mdlist.MdList` | SY MangaDex tracker | 8.3: stub (statuses only, never logged in); removed in P4 (the real tracker is borrowed with the MangaDex delegate) |
| `eu.kanade.domain.track.service.DelayedTrackingUpdateJob` | WorkManager retry job | 8.3: coroutine retry (5 → 40 min, 4 runs) over the persisted store |
| `eu.kanade.tachiyomi.R` | Android resources | 8.3: `drawable.brand_*` ids for `Tracker.getLogo()` |
| `tachiyomi.core.common.preference.AndroidPreferenceStore(context, prefs)` | `SharedPreferences`-backed store | 8.2: function returning the compat preferences' `DesktopPreferenceStore` |
| `Context.createFileInCacheDir` | app `ContextExtensions` | 8.2: same body, cache-dir fallback |
| `eu.kanade.tachiyomi.util.system.GLUtil` | `core:common` androidMain (EGL) | `SAFE_TEXTURE_LIMIT` / `DEVICE_TEXTURE_LIMIT` = 2048 |
| `Context.hasMiuiPackageInstaller`, `Context.isShizukuInstalled` | app `ContextExtensions` | `false` |
| `Source.isIncognitoModeEnabled`, `Source.isLocalOrStub` | app `SourceExtensions.kt` | Same bodies, extension package from `PlatformExtensionManager` |
| `eu.kanade.tachiyomi.source.online.all.EHentai`, `MergedSource` | SY built-in sources | Abstract placeholders with the called members; never instantiated (not on desktop), so `is` checks are false |
| `eu.kanade.tachiyomi.ui.reader.setting.ReadingMode` | enum with icons + `View` viewer factory | Same entries / flag values / labels / direction / type, no icons or factory |
| `eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig` | pager viewer config | `PageLayout` / `CenterMarginType` constants |
| `com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView` | image view library | `SCALE_TYPE_*` constants (library values) |

New KMK-owned shared files (no upstream counterpart) live in `<module>/src/jvmSharedMain/kotlin`
(`core/common`; 2.3: `core/archive/…/ArchiveReader.kt` (`expect`) and `CbzKeyStore.kt`; 3.1:
`domain/…/storage/service/StorageAccess.kt` (`expect`); 3.4: `domain/…/di/LibraryInteractors.kt`,
`data/…/di/DatabaseRepositories.kt`) so `src/main/kotlin` / `src/main/java` keep upstream's file list.

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
| `android.webkit.CookieManager`, `ValueCallback` | 1.1 | `AndroidCookieJar` (`NetworkHelper.cookieJar`) | `DesktopCookieManager`: RFC 6265 store with OkHttp parsing/matching; persistent cookies in `<dataDir>/cookies.json` (debounced write, `flush()`, shutdown hook). P7: `cookiesFor(url)` (full cookies) and `removeFor(url, names)` (whatever the domain / path) for browser sessions. P7 review: `setPastedCookies` / `isPasted` (pasted cookies marked, `"pasted": true` in the file, removed when the site sets one of the same name sent with it) |
| `android.webkit.MimeTypeMap` | 1.1 | UniFile `type`, `ContentResolver.getType`, extensions | Built-in table (images, comic archives, documents, web, media) |
| `android.widget.Toast` | 1.1 | `ToastExtensions` (`context.toast`) | `show()` → `Toast.presenter` (logs by default; the desktop UI can replace it) |
| `android.text.Html`, `Spanned`, `androidx.core.text.parseAsHtml` | 1.1 | `StringExtensions.htmlDecode`, extensions | jsoup-based HTML → plain text (legacy/compact block separation), no spans |
| `androidx.core.content.ContextCompat` | 1.1 | `DiskUtil` | `getExternalFilesDirs`, `getExternalCacheDirs`, `getDataDir`, `getMainExecutor` |
| `androidx.preference.PreferenceManager` | 1.1 | `EHLogLevel` | `getDefaultSharedPreferences` = the main store (`preferences.json`) |
| `com.elvishew.xlog.XLog`, `Logger`, `LogLevel` | 1.2 | `exh.log` (`Logging.kt`, `ResettableLogger`, `EHNetworkLogging`) — public API exposes `Logger` | Prints through `DesktopLog` with the logger tag; no `init()` needed; borders / JSON formatting not reproduced |
| ~~`app.cash.quickjs.QuickJs`, `QuickJsException`~~ | 1.2, 1.4 | `JavaScriptEngine`, extensions | **Moved to `:core:quickjs` in I2b** (Android + desktop, same FQNs; `set` / `get` implemented there). Was: QuickJS via quickjs-kt with `create`, `evaluate`, `compile` / `execute`, `close`, quickjs-android result types, `set` / `get` unsupported |
| `com.hippo.unifile.UniFile`, `FilenameFilter`, `UniRandomAccessFile`, `UriHandler` | 1.1 | `core:common` storage helpers, later `source-local`, downloads, backups | `java.io.File`-backed (`FileUniFile`); same nullability as the komikku-app fork; `fromUri` handles `file:` (+ registered handlers) |
| `androidx.preference.Preference` (+ `OnPreferenceChangeListener`, `OnPreferenceClickListener`, `SummaryProvider`), `PreferenceGroup`, `PreferenceScreen`, `PreferenceCategory`, `DialogPreference`, `EditTextPreference` (+ `OnBindEditTextListener`, `SimpleSummaryProvider`), `ListPreference` (+ `SimpleSummaryProvider`), `DropDownPreference`, `MultiSelectListPreference`, `TwoStatePreference`, `CheckBoxPreference`, `SwitchPreferenceCompat`, `SwitchPreference`, `PreferenceDataStore`; `PreferenceManager` instances | 2.1 | `source-api` (`expect class PreferenceScreen`, `ConfigurableSource.setupPreferenceScreen`), extensions; later the desktop source settings screen (5.4) | Plain data holders with the AndroidX public members (key, title, summary + `%s` formatting, summary providers, enabled / visible / order, `dependency`, default value, change / click listeners, `callChangeListener`, `performClick`, entries / entryValues / value / values / text / isChecked, summaryOn/Off, dialog texts, `addPreference` / `findPreference` / `removePreference`). `PreferenceManager(context)` + `sharedPreferencesName` (or `preferenceDataStore`) + `createPreferenceScreen`: attached preferences load the persisted value or persist their default, and every setter persists through the compat `SharedPreferences`. Additions for the renderer: `defaultValue` getter, public `getOnBindEditTextListener()`, `PreferenceManager.addPreferenceChangeObserver`. No resources: `setTitle(@StringRes)` / `setSummary(@StringRes)` throw |
| `androidx.preference.PreferenceGroupKt` (preference-ktx: `get`, `contains`, `plusAssign`, `minusAssign`, `size`, `isEmpty`, `forEach`, `forEachIndexed`, `iterator`, `children`) | 2.1 | extensions that call the non-inline preference-ktx helpers | Same semantics as preference-ktx |
| `android.view.View`, `android.widget.TextView`, `EditText`, `android.text.TextWatcher`, `Editable`, `SpannableStringBuilder`, `InputType` | 2.1 | `EditTextPreference.OnBindEditTextListener` (extensions set input types and validate with text watchers) | Holders only: enabled / visibility / tag, text, hint, `inputType`, `error`, single line, selection; `setText` runs the watchers (the renderer calls it as the user types). No view tree or drawing; `SpannableStringBuilder` has no spans |
| `androidx.core.net.UriKt` (`String.toUri`, `File.toUri`, `Uri.toFile`) | 2.1 | `source-api` (`EHentaiSearchMetadata`), extensions | Same as core-ktx |
| `android.security.keystore.KeyProperties` | 2.3 | `core:archive` `CbzCrypto` (cipher transformation from `KEY_ALGORITHM_AES` / `BLOCK_MODE_CBC` / `ENCRYPTION_PADDING_PKCS7`) | Constants only, Android values except `ENCRYPTION_PADDING_PKCS7 = "PKCS5Padding"` (SunJCE's name for the same AES padding), so `AES/CBC/PKCS7Padding`-style transformations built from the constants work with the JDK providers |
| `android.annotation.SuppressLint` | 3.1 | `domain` (`Manga`), extensions | Source-retention annotation, no effect |
| `android.graphics.drawable.Drawable` | 3.1 | `domain` (`Extension.Installed.icon`), later the desktop extension runtime | Abstract type only (`intrinsicWidth` / `intrinsicHeight` = -1); desktop subclasses will carry icon bytes for Compose. No drawing |
| `nl.adaptivity.xmlutil.core.AndroidXmlReader` | 3.3 | `source-local` (`LocalSource` ComicInfo parsing); xmlutil's `core-android` artifact | `XmlReader` delegating to xmlutil's pure-Kotlin `KtXmlReader`; `(InputStream, encoding[, relaxed])` and `(Reader[, relaxed])` constructors; no `XmlPullParser` constructors. The compat module now has `api(xmlutil core-jvm)` |
| `android.view.ViewConfiguration` | 4.1 | `presentation-core` (`Scrollbar`, `VerticalFastScroller`) | AOSP defaults: scrollbar fade 250 ms, scroll default delay 300 ms, tap / double-tap / long-press timeouts, scaled scrollbar size 4 dp × compat density, touch slop, fling velocities |
| `androidx.appcompat.app.AppCompatDelegate` (static night-mode API) | 4.2 | borrowed `ThemeMode.kt` (`setAppCompatDelegateThemeMode`) | `setDefaultNightMode` / `getDefaultNightMode` + desktop addition `defaultNightModeFlow` (the desktop UI maps it to light / dark); constants as AndroidX |
| `android.content.pm.ActivityInfo` | 7.1 | borrowed `ReaderOrientation` | `SCREEN_ORIENTATION_*` constants (Android values) |
| `android.text.format.Formatter` | 7.1 | borrowed `ChapterCache.readableSize` | `formatFileSize` / `formatShortFileSize`, SI units, English unit names |
| `androidx.core.content.SharedPreferencesKt` (`edit { }`) | 8.1 | borrowed `DownloadStore`, `DownloadPendingDeleter` | Same as core-ktx (`commit` or `apply`) |

### `:desktop:compose-compat` (4.1)

Desktop-only Compose library for Android-only Compose / AndroidX Compose APIs used by shared UI
code (same FQNs, so upstream files compile unchanged). Depends on `:desktop:android-compat` (api)
and the JetBrains Compose runtime / ui / foundation / ui-backhandler.

| Symbol (FQN) | Step | Needed by | Desktop behaviour |
|--------------|------|-----------|-------------------|
| `androidx.compose.ui.platform.LocalContext` | 4.1 | `presentation-core` (`Localize.kt`, `Scrollbar.kt`), later borrowed `app` files | `staticCompositionLocalOf<Context>`; the desktop shell provides the DI `Application`; default = a lazily created compat `Application()` (previews / UI tests) |
| `androidx.activity.compose.BackHandler(enabled, onBack)` | 4.1 | `presentation-core` (`AdaptiveSheet`) | Delegates to CMP's `androidx.compose.ui.backhandler.BackHandler` (Escape key in desktop windows); deprecated in CMP 1.12 in favour of navigation events (suppressed) |
| `androidx.compose.foundation.systemGestureExclusion()` (+ rect overload) | 4.1 | `presentation-core` (`VerticalFastScroller`) | No-op |
| `androidx.compose.foundation.layout.isImeVisible` (`WindowInsets.Companion`) | 4.1 | `presentation-core` (`Modifier.clearFocusOnSoftKeyboardHide`) | Always `false` (no soft keyboard) |

## Link check (5.3)

Lists what extension code references but the desktop host lacks (the compat layer's to-do list for
that extension). Inputs are local files only — e.g. APKs copied from a device's installed extensions
or downloaded by hand from a repo you trust:

```bash
./gradlew :desktop:extension-runtime:linkCheck --args="path/to/ext.apk path/to/dir-with-apks --output=build/linkcheck.txt"
```

- Accepts `.apk` (converted with dex2jar first), already converted `.jar` (e.g. from
  `<dataDir>/extensions/jars/`), and directories (scanned recursively for both).
- `--output=<file>` also writes the report to a file (relative paths resolve against the repository
  root); `--strict` exits with code 1 when anything is missing.
- The host class path is the runtime class path of `:desktop:extension-runtime`: `:desktop:android-compat`,
  `source-api`, `core:common`, OkHttp / okio / jsoup / RxJava / Injekt / kotlinx and the JDK — what the
  extension class loader shares with extensions. Report lines read
  `missing.Symbol  <- referencing.Class, …`; fix by adding the symbol to the compat layer (or decide
  the extension is unsupported on desktop).

## Deviations from the plan and open issues

- **8.5 — library logic extracted from an upstream file.** To share (not re-implement) Android's library
  filter / sort / search, these parts of `LibraryScreenModel` moved to the KMK file
  `ui/library/LibraryFilterSort.kt`: the bodies of `applyFilters`, `applySort`, `filterManga`, `filterTracks`,
  the query part of `filterLibrary`, `getLibraryItemPreferencesFlow`, `getTrackingFiltersFlow`, the
  `LibraryItem` mapping of `getFavoritesFlow`, the `LibraryGroup.BY_DEFAULT` branch of `applyGrouping`
  (`groupByDefaultCategory`), the inline active-filter check (`hasActiveLibraryFilters`) and the
  `ItemPreferences` class (now `LibraryItemPreferences`). The moved lines are the original ones **with their
  SY / KMK markers** (generated from the pre-move file and compared line by line). The only changes to the
  moved code: new signatures (screen-model members became parameters, inside KMK markers); `return` in front
  of former expression bodies; the `trackerManager` argument to `filterManga` / `filterTracks`; `filterManga`
  and `filterTracks` are file-private; `applyLibrarySort`'s SY `groupSort` parameter lost its `= null` default
  (callers pass it; KMK note inside the SY block); the original's unclosed KMK block in the `ItemPreferences`
  mapping is closed. Code written for the move uses KMK markers only (explained in the file header). The
  Android functions are one-line delegations inside KMK markers, so Android behaviour is unchanged;
  `applyGrouping` keeps `master`'s marker lines. Upstream syncs that touch those bodies must be applied to
  `LibraryFilterSort.kt`. **Could it be smaller?** Not usefully: desktop needs every moved piece (item
  mapping, filters, grouping, sort, search), the rest of the screen model (selection, dialogs, downloads,
  sync, merge, grouping by source / status / tracker) is Android-bound and stays, and a `desktopCopy` of the
  whole screen model is impossible (Android resources, `FavoritesSyncHelper`, recommendations,
  `LibraryUpdateJob`). Keeping desktop copies instead would drift (it already had: the review found a
  different system-category rule in the desktop grouping copy, now removed). Desktop inherits Android's quirks
  on purpose, e.g. a local-source entry never passes a `-term` exclusion (its source-id string is null);
  `LibraryPagesTest` pins that. Desktop deviations: `isLibraryEmpty` is the unfiltered library (no
  empty-library flash while a query is cleared) and the global "downloaded only" counts as an active filter
  for the empty-state message (`error_no_match` with the search / filter controls instead of "Your library is
  empty").
- **Phase 8 review fixes.** Backup create / restore moved from the screen model's scope to the
  app-lifetime `DesktopBackupJobs` (DI singleton; the screen observes its status, cancel is reported as
  cancelled). "Add tracking" with an enhanced tracker (Komga / Kavita / Suwayomi) binds `match(manga)` +
  `register` like Android instead of calling their `TODO()` search; tracker searches cancel the previous
  one and only apply results to the same tracker + query. `DownloadJob` delegates to `DownloaderRunner`,
  where only the current run writes "running" (a replaced run ending late no longer clears it).
  `OAuthRedirect.parse` never throws (lenient percent-decoding) and treats base64-padded bare codes
  (`abc==`) as bare values. Details screen download indicators: per-chapter updates from the status /
  progress flows, sampled (100 ms) instead of debounced. Reader: "delete after reading" runs every time the
  last page is reached (`ReaderProgressSaver.onPageSelected` returns `true` then, as Android's
  `updateChapterProgressOnComplete`), and download-ahead is latched only after the request was made.
  A login that ends logged out shows `KMR.login_failed`; the download settings read the storage folder
  through `Preference.collectAsState()`. Tests: `DesktopBackupJobsTest`, `DownloaderRunnerTest`,
  `ReaderDownloadsTest`, new cases in `OAuthRedirectTest`, `TrackingTest`, `ReaderLogicTest`.
- **8.4 — settings scope.** Desktop settings are compact lists built from the borrowed `Preference` model,
  not the Android `Settings*Screen` objects (those are bound to activities, navigators, WorkManager and
  Android-only options). Not on desktop: theme previews (`AppThemePreferenceWidget`, list instead), custom
  colour theme picker, tablet UI / navigation options, library display / badges settings (in the library
  settings dialog since 8.5b), device restrictions for updates and scheduled updates, reader tap-zone / volume-key /
  orientation options, download-new-chapters, extension / repository settings beyond Browse, security,
  advanced (clear caches, logs), settings search. App theme changes apply immediately (shell theme reads
  `UiPreferences`).
- **8.3 — tracker login support on desktop** (no new OAuth clients registered, client ids unchanged):

  | Tracker | Android login | Desktop login | Status |
  |---------|---------------|---------------|--------|
  | MyAnimeList | OAuth code + PKCE, redirect `komikku://myanimelist-auth` | Browser + paste redirected URL / code (`MyAnimeList.login(code)` with the verifier of the same process) | Works if the user can copy the `komikku://…?code=…` address (browsers show an "open app" prompt or an error page; the code is in the address / devtools). A loopback redirect is not possible: the registered redirect URI is the app scheme |
  | AniList | OAuth implicit token, `komikku://anilist-auth#access_token=…` | Browser + paste (token from the fragment) | Same caveat; tokens are long, pasting the whole address is easiest |
  | Shikimori | OAuth code, `komikku://shikimori-auth` | Browser + paste code | Same caveat |
  | Bangumi | OAuth code, `komikku://bangumi-auth` | Browser + paste code | Same caveat |
  | Kitsu | Username / password | Credentials dialog | Works as on Android (not exercised against the real service) |
  | MangaUpdates | Username / password | Credentials dialog | Works as on Android (not exercised against the real service) |
  | Komga / Kavita / Suwayomi (enhanced) | Enabled when the matching extension is installed | Same rule (`loginNoop`), enabled when an accepted source class is loaded | Compiles and is wired; depends on those extensions running on desktop (untested) |
  | MDList (MangaDex) | SY MangaDex delegated source login | OAuth by paste through the MangaDex source since P4 (was: not available, stub) | Needs the MangaDex extension installed; unverified against the server |

  No request was made to any tracker service; tests use an in-test tracker. Tracker logos are not shown on
  desktop (names only; `R.drawable` ids have no images). Not ported: Android's "track on adding to
  library" prompt, auto-binding enhanced trackers when adding to the library, private tracking toggle,
  AniList score-type setting, tracker-metadata ("resolve using source metadata"), delete-from-service for
  `DeletableTracker`s (local unbind only), the tracking-status badge / filter in the library.
- **8.3 — delayed tracking.** A retry is scheduled by the failure that queued the item (5, 10, 20, 40 minutes,
  in-process); items still in the persisted store after a restart wait for the next failure or chapter read
  (Android: WorkManager survives restarts). No network-availability constraint.
- **8.2 — backup, deviations / gaps.** No automatic (scheduled) backups on desktop (`BackupCreateJob.setupTask`
  is a no-op; `backupInterval` is restored but unused), no sync (`isSync` is always false), no share
  action for a created backup. Create / restore run in the app-lifetime `DesktopBackupJobs` (not a WorkManager job, no
  notification): leaving the screen does not stop them, and both can be cancelled from the screen. Restoring does not trigger a library or tracker refresh (Android
  doesn't either). Missing-tracker validation works since 8.3 (logged-out trackers are listed). SY
  `MergedSource` / E-Hentai specific data in backups (merged references, flat metadata) is restored into
  the database but those sources do not exist on desktop.
- **8.1 — downloads, deviations / gaps.** No system notifications or tray: progress is shown in the
  details screen and the queue screen; warnings / errors are `DownloadEvent`s (logged and shown as snackbars through
  `Context.toast` → `Toast.presenter`). `downloadOnlyOverWifi` and network-state stops are ignored (no metered
  network notion on desktop). `DownloadJob.start` monitors an already running downloader instead of
  failing like the WorkManager job would. No "download new chapters" after library updates (Android's
  `LibraryUpdateJob` does it; the desktop runner does not yet), no download cache "indexing"
  indicator, no per-source grouping / sorting in the queue screen (move to top / bottom only), no
  download-related settings UI beyond what 8.4 adds. Split tall images: JPEG / PNG / BMP only. Local
  chapters now go through `DownloadPageLoader` in the reader, exactly as on Android.
- **6L / 7 — `desktopCopy` instead of `shared` for the reader and chapter stack.** The reader models,
  loaders, `ChapterCache`, `ReaderPreferences` and the manga / chapter interactors reference Android-only
  classes (download manager, `ReaderActivity` viewers, SY built-in sources, `R`), so moving them to
  `shared` would mean editing upstream files or borrowing large Android-only trees. With the new kind they
  stay exactly as Android compiles them and the desktop gets a copy plus small same-FQN counterparts.
  Android can adopt the shared versions later by switching entries to `shared` once their references are
  shareable. New platform-neutral code (chapter list, progress, scaling, decoder interface, update runner)
  is in `app-shared` `jvmSharedMain`, so it is compiled for Android too (unused there yet).
- **6L / 7 — Kotlin incremental compilation after moving a file between kinds:** moving
  `GetExcludedScanlators.kt` from `shared` to `desktopCopy` left stale IC state (unresolved reference in a
  file that was correct); deleting `app-shared/build/kotlin` fixed it. Expect the same when reshuffling.
- **6L.1 — details scope:** no duplicate detection on add, no tracker binding, no download actions or
  delete-downloads prompt, no filter / display menus (stored filters are honoured), no edit info / cover,
  no merge, no WebView / share. Library refresh of one entry uses `UpdateMangaFromRemote` directly.
- **6L.2 — scope:** no scheduling (Phase 8), no notifications / tray, no update-errors screen (errors are
  stored), no Updates filters / selection actions, history search is not exposed, Updates list = Android's
  query (last 90 days by upload date, 500 rows).
- **7 — reader scope / gaps:** one chapter at a time (Android viewers also append neighbouring chapters'
  pages); double pages = fixed pairs (no automatic layout, wide-page or center-margin handling, no
  "shifted" pages); no crop borders, dual-page split / rotate, auto-scroll, long-tap page actions (save /
  share / set as cover), tap-zone layouts (thirds only), flash on page change, page transition animations,
  keep-screen-on, custom brightness above 0 (no screen brightness API; only dimming); orientation is stored
  but has no effect; animated GIF / WebP show the first frame; AVIF / JPEG XL / HEIF show an error
  placeholder (Skia on desktop has no decoders for them; a JNI or pure-JVM decoder would be needed).
  Decoding reads the encoded page fully and decodes it once at full size before downsampling (Skia's
  desktop codec API has no sampled decode); at most two such decodes run at a time.
- **7.6 — tracker updates:** `ReaderTrackerHook` is registered as `None` on desktop; Phase 8 should
  register a hook that calls the borrowed `TrackChapter` when `TrackPreferences.autoUpdateTrack` is on
  (Android `ReaderViewModel.updateTrackChapterRead`). Sync triggers (`SyncDataJob`) are not called.
- **7 — reader as a full-window screen** (not a separate window): see the 7.5 row. A second window
  would need its own focus / placement handling and is not supported by the single-window test harness.
- **Fixed in 7.1 — desktop storage folder on a fresh data dir:** `DesktopStorageFolderProvider` now
  creates `<dataDir>/storage`; before, `StorageManager` rejected the missing directory, so the local
  source and downloads folder were unavailable until the user picked a folder.
- **Phase 8 hand-off (done in 8.1):** the Android `DownloadCache` / `Downloader` / `DownloadManager` are
  borrowed; only their WorkManager / notification dependencies have desktop counterparts.

- **5.4 — scope of the desktop browse screens (deliberately minimal, Phase 6 replaces them):** no
  source filters (`getFilterList()` is passed as is), no global search, no pinned / last-used sources,
  no language filter or NSFW toggle UI, no extension details screen (settings are reached from the
  extension row or the source row), no per-source enable / disable, no migration; the details screen
  does not store chapters (no `SyncChaptersWithSource` on desktop yet), has no reader, categories
  dialog, tracking or downloads; adding to the library sets `favorite` / `dateAdded` only (default
  category). `SourceFilter`-based global search state is unused on desktop.
- **5.4 — preferences renderer gaps:** no `SeekBarPreference` (not in compat), no icons, no nested
  `PreferenceScreen` navigation (nested screens are flattened like categories), `isSingleLineTitle` /
  `isIconSpaceReserved` / layout resources ignored; `OnBindEditTextListener` sees a compat `EditText`
  holder (input type, text watchers, error), not a real view.
- **5.4 — UI tests replace the `SourceManager`** (`Injekt.addSingletonFactory` before first use) with
  a fixed one holding the in-test source; the extension → source manager path is covered by
  `ExtensionsUiTest` and `DesktopExtensionManagerTest` instead.
- **Phase 6 gate (D8) — checked in 5.4:** the upstream Metro DI and AndroidX ViewModel migrations are
  **not** in this repository (no Metro, screens still use Voyager `ScreenModel`s; only the pre-existing
  `ReaderViewModel`), and there is no `build-logic` included build. Per D8, shared screens in Phase 6
  should be written against small KMK interfaces (like `PlatformExtensionManager`) rather than
  Voyager + Injekt directly, or the sync should happen first.

- **5.3 — plan step reduced to the tool.** The plan's 5.3 is a "compat growth pass driven by real
  extensions"; downloading extension APKs is out of scope for this task, so only the tool, its test and
  its documentation exist. Running it on a sample of popular extensions and adding the reported symbols
  is left for the maintainer (expected gaps: `android.graphics.Bitmap*` / `Canvas` for image
  descramblers, `eu.kanade.tachiyomi.AppInfo`, `org.json`, `android.webkit.WebView` for WebView-based
  sources, preference widgets not covered yet such as `SeekBarPreference`).
- **5.2 — separate desktop manager, KMK interface.** Android's `ExtensionManager` / `ExtensionLoader` /
  `ExtensionInstaller` / `TrustExtension` / `GetExtensionsByType` are not borrowed (PackageManager,
  `PackageInfo`, broadcast receivers, `R`, notifications). Their logic is re-implemented in
  `DesktopExtensionManager` and `GetPlatformExtensionsByType` behind `PlatformExtensionManager`
  (duplication: trust rules, update / obsolete status, sub-language enabling, blacklist). Unification
  path: Android `ExtensionManager : PlatformExtensionManager` (KMK one-liner) when the extension
  screens are shared (6.6). `TrustExtension`'s debug-build "trust everything" shortcut is not copied.
- **5.2 — desktop install semantics.** An APK signed by a key that is not a saved store's key is
  installed and listed as untrusted (same as Android, where the system installs it and the app refuses
  to load it). The SY built-in E-Hentai / merged sources and delegated (enhanced) sources are not on
  desktop, so `DesktopSourceManager` has only local + extension sources. The extension update
  notifier / periodic update check (`ExtensionApi.checkForUpdates`) is not ported.
- **5.2 — borrowing edits to upstream files:** `SourcePreferences.kt` (default languages inlined,
  `LocaleHelper` import removed — ktlint forbids comments in the import list) and `SearchScreenModel.kt`
  (`SourceFilter` commented out, now in `:app-shared`). A cherry-pick touching `SourceFilter` must be
  applied to `app-shared/src/main/kotlin/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SourceFilter.kt`.

- **5.1 — what the runtime does not do (yet).** v3 proof-of-rotation lineages are not parsed: only the
  current signer's certificate is reported, where Android's `signingCertificateHistory` would also list
  past signers (accepting an *unverified* lineage would let any APK claim a trusted past key, so it is
  left out rather than half-done). Verity-only signature algorithms (0x0421…) and ZIP64 are not
  supported. v1-only APKs are verified by the JDK, which treats **SHA-1-only JAR signatures as unsigned**
  (`jdk.jar.disabledAlgorithms`) — old v1-only extensions would be rejected as unsigned; every extension
  built with AGP since 2016 has a v2 signature. The class loader cannot be unloaded explicitly (a
  replaced / uninstalled extension's classes go away with GC).
- **5.1 — test coverage of the pipeline.** Covered: AXML / ARSC parsing (test-written fixtures in
  aapt2's layout, not real aapt2 output), v1 / v2 / v3 verification against apksig-signed fixtures
  (RSA + EC) incl. tamper detection, extension validation, class loading / instantiation, and dex2jar on
  dx-produced dex (Java 8 classes). **Not covered:** real extension APKs (aapt2 manifests, R8 / D8 dex
  with invokedynamic, Kotlin-compiled extension code, multi-dex), v3.1 blocks, sparse / compact
  resource entries (implemented from the format description only).
- **5.1 — duplicated constants.** `ExtensionContract` copies `ExtensionLoader`'s private metadata keys
  and `SUPPORTED_LIB_VERSIONS` (referencing them would mean editing the upstream file to make them
  public). A lib-version bump must touch both files.

- **4.3 — `LocalSystemTheme` is deprecated in CMP 1.12** ("made public by mistake, will be
  removed"). It is the only way to make `isSystemInDarkTheme()` — called by the shared
  `TachiyomiTheme` — honour a forced light / dark `ThemeMode` on desktop. Used with
  `@Suppress("DEPRECATION")`; when it disappears, give the desktop a theme entry point that takes
  `isDark` (e.g. a KMK overload next to `TachiyomiTheme`, or a `perPlatform` split).
- **4.3 — shell scope.** Read-only library (no manga screen, filters, sort, search, selection,
  badges, display modes; Phase 6.3), sources listed but not browsable (6.1; a minimal catalogue came
  with 5.4), no settings screen to change theme / window prefs (6.5), no tray / notifications, no
  snackbar host yet for `Toast.presenter` (open since 1.x; added in 5.4). Library covers use `MangaCover` → `DesktopMangaCoverFetcher`;
  local-source covers load from disk, online covers need online sources (Phase 5).
- **4.3 — `Window(onPreviewKeyEvent)` shortcuts only fire while the window has focus** (AWT key
  dispatch); there are no global hotkeys. Shortcut actions are only handled while `HomeShell` is
  composed.
- **4.3 — `:desktopApp:test` forks one JVM per test class** (`forkEvery = 1`) so each class can boot
  its own `DesktopInjekt` graph; slower, but isolates the process-global Injekt registry.
- **4.3 — desktop `SourceManager` lives in `:desktopApp`,** not `:app-shared`: it will grow into the
  extension-backed manager of Phase 5 (desktop-only loading), and `AndroidSourceManager` stays in
  `app`. Move the platform-neutral parts (stub handling, merged sources) to `:app-shared` if a
  shared base class becomes useful.

- **4.2 — borrowing copies instead of filtered source dirs.** `:app-shared` compiles *copies*
  (`build/generated/borrowed/…`, refreshed by `Sync` before every compile) rather than pointing a
  source set at `app/src/main/java` with include filters: an include-filtered root would make the
  IDE import the whole app source root into `:app-shared`. Consequences: compiler errors for
  borrowed files point at the copy (same relative path under `app-shared/build/generated/borrowed/<kind>/kotlin`);
  always edit the original in `app`. In the IDE, the `app` module still shows the borrowed files as
  its own sources (AGP has no per-file exclude that the IDE honours); Gradle compiles them only in
  `:app-shared`. The allowlist is a script plugin that only fills `extra` lists, because script
  plugins cannot see buildSrc classes; changing it does not rebuild buildSrc.
- **4.2 — `perPlatform` kind.** `jvmSharedMain` cannot see `androidMain` / `desktopMain`
  declarations of the same module (0.6), and `TachiyomiTheme.kt` calls `MonetColorScheme`, which is
  Android-only (wallpaper / `dynamicLightColorScheme(context)`). Rather than editing either upstream
  file into `expect` / `actual`, `TachiyomiTheme.kt` is compiled once per platform and
  `MonetColorScheme.kt` is borrowed for Android with a desktop counterpart in
  `app-shared/src/desktopMain`. Use `perPlatform` sparingly: such a file cannot be called from
  `jvmSharedMain` code of `:app-shared`.
- **4.2 — DI wiring of Android to the shared registration functions: not done (open, from 3.4).**
  Replacing the bindings in `DomainModule` / `SYDomainModule` / `KMKDomainModule` /
  `PreferenceModule` with `registerDatabaseRepositories` / `registerDomainPreferences` /
  `registerLibraryInteractors` would delete ~150 upstream-owned lines (conflicts on every upstream
  DI change) and would need a binding-by-binding audit of scopes (`addSingletonFactory` vs
  `addFactory`) and registration order; not the "small, safe, behaviour-preserving" edit the step
  allows. The duplication stays until the upstream Metro DI migration is picked or Phase 6 needs it.
  `UiPreferences` (now compiled in `:app-shared`) is still registered by Android's `PreferenceModule`.
- **4.2 — desktop theme limitations.** "Dynamic" (Monet) = default Tachiyomi colours on desktop
  (no wallpaper colour API); `ThemeMode` (light / dark / system) is applied on desktop by the shell
  (4.3), not by `AppCompatDelegate`. Cover-based dynamic theming works (material-kolor is
  multiplatform) but nothing on desktop uses it yet.
- **4.2 — desktop Coil is minimal.** No `CoverCache` (library / custom covers are not copied out of
  Coil's disk cache), no `TachiyomiImageDecoder` (AVIF / JXL / HEIF need a Skia-capable decoder;
  Skia decodes JPEG / PNG / WebP / GIF), no page-preview fetcher, no cover colour extraction, no
  `Manga` (as opposed to `MangaCover`) fetcher. The Android Coil setup in `App` is unchanged.

- **4.0 — "BOM wins" by construction instead of a strict constraint (D6).** PLAN.md D6 assumed
  KMP modules would use `org.jetbrains.compose.*` coordinates everywhere, resolving to androidx on
  Android, with a strict constraint so the BOM wins. With CMP 1.12.2 that does not hold: the
  JetBrains modules' Android variants are now separate artifacts (`org.jetbrains.compose.ui:ui-android:1.12.2`,
  with its own AAR) that require `androidx.compose.ui:ui:1.12.1` / `runtime-retain:1.12.1`, while the
  BOM (2026.06.01) pins 1.11.4. Without constraints the app's Compose would be upgraded; with a
  strict constraint the app would carry JetBrains 1.12.2 wrapper AARs compiled against a newer
  androidx than the one resolved. So `mihon.library.kmp.compose` keeps **androidx coordinates +
  BOM in `androidMain` and JetBrains coordinates in `desktopMain`**; `jvmSharedMain` is compiled
  per target (no shared metadata compilation in this JVM-only hierarchy), so shared sources compile
  against androidx on Android and CMP on desktop (same `androidx.compose.*` packages). The app's
  resolved versions are unchanged by construction (evidence in the 4.1 row). The JetBrains
  artifacts that were already on the app's classpath (`org.jetbrains.compose.*:1.11.1`, through
  Voyager / markdown / material-kolor) are unaffected.
- **4.0 — desktop Compose API level.** Desktop compiles shared UI against CMP 1.12.2 (≈ androidx
  1.12.1) + material3 1.11.0-alpha03 (= androidx material3 1.5.0-alpha14), Android against androidx
  1.11.4 + material3 1.5.0-alpha14. Shared code must stick to APIs present in both; the desktop
  side is newer, so the practical risk is an API removed/deprecated in 1.12 (shows up in
  `compileKotlinDesktop`). `material-icons-extended` on desktop is CMP's frozen 1.7.3 (Android:
  1.7.8, same icon set).
- **4.1 — compat FQNs instead of platform splits.** The plan expected splits for `Localize.kt`,
  `ViewConfiguration` and `AdaptiveSheet`; providing the few Android-only Compose symbols under
  their own FQNs (`:desktop:compose-compat`) and `ViewConfiguration` in `:desktop:android-compat`
  leaves every presentation-core file unchanged and unmoved (D2 option 1). These symbols are
  source-compatible only (JVM file-facade names differ from Android's), which is irrelevant
  because only Komikku code compiled for desktop uses them.
- **4.1 — no `javap` diff for `presentation-core`.** Sources and Android dependencies are the same
  as before (only the plugin wiring changed), and `:app:compileDebugKotlin` passes; the Android
  ABI was not compared class by class.

- **3.4 — DI duplication (open, for `:app-shared`).** `DomainModule` / `SYDomainModule` /
  `KMKDomainModule` / `PreferenceModule` stay in `app` and unchanged, so the bindings in the shared
  `registerDatabaseRepositories` / `registerDomainPreferences` / `registerLibraryInteractors` exist
  twice (Android modules + shared functions; Android does not call the shared ones yet). When
  `:app-shared` lands, the Android modules can call the shared functions in a KMK block and drop
  their copies (or the Metro DI migration replaces both). Until then, a new interactor in `:domain`
  must be added to both places to reach desktop. `DesktopDatabase` duplicates `AppModule`'s
  `Database(...)` adapter wiring (3.2).
- **3.4 — what the desktop graph lacks** (4.3 added `SourceManager` / `SourceRepository`, 5.2 the
  extension manager, stores and `SourcePreferences`, 5.4 `UpdateManga`; 6L / 7 added `BasePreferences`,
  `ReaderPreferences`, `ChapterCache`, `CoverCache`, `DownloadProvider`, the desktop `DownloadManager`,
  `SyncChaptersWithSource`, `SetReadStatus`, `UpdateMangaFromRemote`, `GetIncognitoState`,
  `SetMangaViewerFlags`, `GetExcludedScanlators`, `DeleteDownload`, the library update runner and the
  reader services): `SourceManager` (so `SourceRepository`,
  `GetRemoteManga`, `GetSourcesWithNonLibraryManga`, `GetEnabledSources`, …), extension manager and
  stores (Phase 5), download manager / cache, cover and chapter caches, trackers + `TrackerManager`,
  backup, sync, library update runner, and every `app`-module preference class (`SourcePreferences`,
  `UiPreferences`, `ReaderPreferences`, `TrackPreferences`, `BasePreferences`, `ConnectionsPreferences`,
  `SyncPreferences`) and `app` interactors (`SetReadStatus`, `SyncChaptersWithSource`, `UpdateManga`,
  `GetIncognitoState`, …). Those come with `:app-shared` (Phase 4+) and the feature phases.
- **3.4 — Injekt is process-global.** `DesktopInjekt.init` is meant to be called once; tests that
  boot it share the graph within one test JVM (`DesktopInjektTest` boots it in `@BeforeAll`).
  `DesktopServices` (hand-wired network services from 1.x) is kept only for `DesktopNetworkTest`.
- **3.4 — the smoke screen is compile-checked only** (no `:desktopApp:run`); `Dispatchers.Main` for
  `collectAsState` comes from Compose Desktop's coroutines-swing.
- **3.3 — `src/androidMain/kotlin` of `source-local` is compiled by both targets.** All of its
  files run on desktop over the compat layer, so instead of moving `LocalSource` & co. to a shared
  directory (an upstream path change) or duplicating them in `desktopMain`, the desktop source set
  lists `src/androidMain/kotlin` as an extra source dir. The directory name is now misleading
  ("platform files, compiled for Android and desktop"); the `expect`/`actual` split stays as
  upstream wrote it. If a file there ever needs Android-only APIs, give `desktopMain` an
  exclude for it plus a desktop counterpart in `src/desktopMain/kotlin`. This is a small instance
  of D5's source borrowing.
- **3.3 — desktop `LocalSource` limits** come from the layers below: cover detection and page
  images use the desktop `ImageUtil` subset (magic bytes only), RAR 5 is unsupported (2.3), and
  `.nomedia` files are still written (harmless).
- **3.2 — no platform splits in `data`.** The compat layer covers `Build`, `Context` and
  `SuppressLint`, so `ReleaseServiceImpl` and `CustomMangaRepositoryImpl` are unchanged. On desktop
  `ReleaseServiceImpl.getDownloadLink` sees `Build.SUPPORTED_ABIS = [os.arch]`, matches no APK ABI
  and falls back to the universal APK link — wrong for desktop but harmless (no desktop updater
  yet). **Open:** the desktop updater (Phase 8) should register its own `ReleaseService`
  implementation that picks the msi / dmg / deb asset; `ReleaseService` is already an interface.
- **3.2 — `DesktopDatabase` duplicates the `Database(...)` adapter wiring** of `AppModule` (app
  module, upstream-owned, not touched). If `AppModule` gains an adapter, desktop must follow; the
  migration test catches schema drift but not a missing adapter (that fails at compile time).
- **3.2 — desktop driver configuration.** One `BundledSQLiteDriver` per driver instance; the
  `sqldelight-androidx-driver` default concurrency model (WAL, multiple readers / one writer) is
  used as on Android. `AndroidxSqliteDatabaseType.Memory` works for tests but the test suite uses
  temp files (the driver's reader pool and `:memory:` connections are separate databases).
- **3.2 — `28.db` snapshot** has `user_version = 0`; the migration test sets 28 before opening. It is
  the only schema snapshot in the repo (`schemaOutputDirectory`), so older starting points are not
  tested.
- **3.1 — no file moved in `domain`.** Only one Android-only piece existed: the KMK-owned storage
  permission flow in `StorageManager`'s companion (`allowAccessStorage` + private helpers using
  `Intent`, `Settings`, `ActivityCompat`, `Environment.isExternalStorageManager`). It moved verbatim
  to `domain/src/androidMain/kotlin/tachiyomi/domain/storage/service/StorageAccess.android.kt`
  behind `internal expect fun allowAccessStoragePlatform` (D2 option 4: called from shared code in
  the same module). `allowAccessStorage` / `directoryAccessible` keep their signatures, so `app`
  is unchanged. The 14 imports only that code used were deleted from `StorageManager.kt` (ktlint
  rejects comments inside the import list). The rest of `StorageManager` (UniFile base dir, change
  flow) runs on desktop over the compat `UniFile` / `Context`.
- **3.1 — `Extension.icon` stays `Drawable?`.** Upstream's "Load extension icons through Coil"
  was not cherry-picked: it changes `app` extension screens and the loader, far beyond `domain`.
  The compat `Drawable` type keeps the model unchanged; the desktop extension runtime (Phase 5)
  can subclass it (or the upstream change can be picked later, removing the need).
- **3.1 — `coroutines-android` split.** The coroutines bundle includes `kotlinx-coroutines-android`,
  which registers the Android `Dispatchers.Main` factory through `ServiceLoader`; on a desktop
  classpath it would compete with `kotlinx-coroutines-swing`, so it is Android-only now.
- **3.1 — Android ABI.** Public API unchanged by construction (only `private` helpers of
  `StorageManager.Companion` moved, plus the new internal `StorageAccess_androidKt` facade);
  no `javap` diff was run for `domain`.
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
  needs them. (I2b: the class moved to `:core:quickjs`, runs on Android too and implements `set` / `get`.)
- **1.5 — context-free helpers are desktop-only.** Shared code keeps using
  `Context.stringResource(...)` (a compat `Context` is always available on desktop). The top-level
  `stringResource(...)` functions exist only in `desktopMain`; making them shared would need an
  Android implementation that finds a `Context` on its own (e.g. through Injekt), which is not
  needed yet. Desktop UI language = `StringDesc.localeType` (system locale by default).
- **1.x — `DesktopDirs` stays in `:core:preference`.** `DesktopPreferenceStore` (same module) and
  the compat layer need it, and `:core:preference` cannot depend on `:core:common`, so moving it
  would not simplify anything.
- **Desktop `Dispatchers.Main`** (resolved in 4.3). Shared code that uses `launchUI` / `withUIContext`
  needs `kotlinx-coroutines-swing` on the desktop runtime classpath; `:desktopApp` declares it
  explicitly (`kotlinx.coroutines.swing`). Plain JVM tests of other modules that hit such code must
  still provide a main dispatcher.
- **Resolved in 5.4 — Android toasts from shared code on desktop** go to `Toast.presenter`, which the
  window's `DesktopToastHost` points at a Material 3 snackbar host (logs again when no window is
  composed, e.g. in non-UI tests).
- **2.1 — `expect class PreferenceScreen` kept.** `source-api` already had `expect`/`actual` for
  `PreferenceScreen` and `awaitSingle`; the files stay where they are (`src/commonMain` is compiled
  through `jvmSharedMain`, `src/androidMain` unchanged) and only desktop `actual`s were added, rather
  than replacing the `expect` with a direct `androidx.preference` import (which now would compile on
  both targets, but would touch an upstream file).
- **2.1 — compat preferences are Kotlin classes.** JVM shapes match AndroidX for extension
  bytecode (`getTitle`/`setTitle`, `isChecked`/`setChecked`, `getEntries(): CharSequence[]`,
  `setOnPreferenceChangeListener(...)`, nested listener interfaces, `SimpleSummaryProvider.getInstance()`).
  Kotlin *source* written against AndroidX may need small changes on desktop where AndroidX has a
  Java getter/setter pair that is a function here (listeners) or a property here (`title`); no
  shared Komikku code builds preferences, so nothing is affected today.
- **2.1 — lenient group attachment.** A `PreferenceCategory` filled before it is added to the screen
  attaches (and persists) its children when it is added; AndroidX would leave them unpersisted.
  Dependency state (`dependency`, parent groups) is computed on read instead of AndroidX's
  listener bookkeeping. Sorting: children keep insertion order (stable by `order`), not by title.
- **2.1 — "Not set" summary** of the simple summary providers is an English constant
  (`app.komikku.desktop.compat.NOT_SET`); the compat module has no string resources. The desktop
  renderer (5.4) should show a localized text instead.
- **2.1 — not provided yet:** `SeekBarPreference`, icons, intents / extras, `Preference(Context, AttributeSet)`
  constructors, `TypedArray`-based `onGetDefaultValue`. Add them in 5.3 if real extensions need them.
- **2.2 — bundle split.** `kotlinx.bundles.serialization` cannot be used by a converted module
  (it contains the Android-only `xmlutil:core-android`), so `core-metadata` lists the members it
  needs; the same applies to `domain`, `source-local` and `app-shared` later.
- **2.3 — `CbzCrypto` edits.** The Keystore code moved verbatim into the Android `CbzKeyStore`
  (same aliases and key spec, so existing Android keys keep working); `CbzCrypto` now calls it
  (KMK-marked, old code kept as comments). The three unused imports (`KeyGenParameterSpec`,
  `KeyStore`, `KeyGenerator`) were deleted instead of commented out: ktlint rejects comments inside
  the import list. `KeyStore` was created in `CbzCrypto`'s static initializer before; it is now
  created on first key access (no behaviour change).
- **2.3 — desktop key file is weaker than the Android Keystore** (documented in `CbzKeyStore`):
  the key is readable by anything running as the user and is included in file backups of the data
  dir. Options for later: OS keychains (Windows DPAPI / macOS Keychain / libsecret) via a small
  JNA binding. Deleting `keys/` makes stored CBZ / database passwords undecryptable (same as
  clearing the Android Keystore).
- **2.3 — library choice.** commons-compress cannot read or write encrypted zip entries, which
  Komikku's password-protected CBZ downloads need, so zip-based formats use zip4j (Apache-2.0)
  instead; commons-compress handles 7z (with xz for LZMA) and the stream formats; junrar reads
  RAR. All from Maven Central, Java 8 bytecode, pure JVM (no natives).
- **2.3 — format gaps vs. libarchive:** RAR 5 (junrar reads RAR 4 only; libarchive reads both),
  encrypted RAR, ISO / CAB / LHA / XAR and other exotic libarchive formats. RAR entries are
  extracted to memory when read (fine for comic pages). Encrypted 7z: readable with the CBZ
  password, but 7z has no password check value, so a wrong password surfaces as
  `wrongPassword = true` only through the decode failure of the probed entry. ZipCrypto's one-byte
  check lets a wrong password pass with probability 1/256 (also true for libarchive); the data
  then fails its CRC.
- **2.3 — no RAR test fixture:** there is no JVM RAR writer, so RAR reading is compile-checked
  only; add a small fixture file to `src/desktopTest/resources` when one with a clear licence is
  available.
- **2.3 — desktop `ArchiveReader` holds no open handle** between calls (every `useEntries` /
  `getInputStream` opens its own cursor, like Android opens a new libarchive reader over the
  mmap), so `close()` is a no-op and `archiveHashCode` is the identity hash.
- **Open — compat test noise.** `:desktop:android-compat:test` prints a `NoSuchFileException` for
  `cookies.json.tmp` from the cookie store's shutdown-hook flush after JUnit deleted the temp dir;
  tests pass. Pre-existing (1.1).
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
  with the first Compose module (Phase 4) — done in 4.0 as a separate `mihon.library.kmp.compose` plugin. `kotlin.mpp.androidSourceSetLayoutVersion=2` in
  `gradle.properties` now triggers a KGP deprecation warning (pre-existing property). AGP 9 drops
  `com.android.library` + `kotlin("multiplatform")` in favour of
  `com.android.kotlin.multiplatform.library`; the plugin will need a rewrite at that point.

## P1 — global search and source toggles

The first two desktop parity gaps (commits `5137a56` … `2e39d59`).

**Borrowed** (`app-shared/borrowed-app-sources.gradle.kts`, block "P1"):

- `shared` (one copy for both targets, `app` gets them from `:app-shared`, code unchanged): `ToggleLanguage`,
  `ToggleSource`, `ToggleSourcePin`, `presentation/browse/components/GlobalSearchResultItems.kt` (row header,
  loading and error items).
- `desktopCopy` (Android unchanged): `GetEnabledSources` (uses `tachiyomi.source.local.isLocal`; `:source-local` is a
  desktop-only dependency of `:app-shared`, so it cannot be `shared` without changing the Android dependency graph),
  `GetLanguagesWithSources` (uses `LocaleHelper`), `SourcesFilterScreenModel`, `SearchScreenModel`,
  `GlobalSearchScreenModel` (use the per-platform `ExtensionManager`; desktop = the `PlatformExtensionManager`
  typealias from 8.1).
- New desktop counterpart `app-shared/src/desktopMain/.../util/system/LocaleHelper.kt`: Android's `LocaleHelper`
  without `LocaleListCompat` / `SourcesScreenModel` (same comparator, names, `last_used` / `pinned` keys, default
  languages; system language = JVM default locale; no app language switching).
- Registered in `DesktopDomainModule`: `GetEnabledSources`, `GetLanguagesWithSources`, `ToggleLanguage`,
  `ToggleSource`, `ToggleSourcePin` (same constructors as Android's `DomainModule`).

**Sources tab** (`desktopApp/.../ui/browse/SourcesPane.kt`): `SourcesPaneModel` reads Android's `GetEnabledSources`
(enabled languages, disabled sources, pins, last used source, SY source categories from a restored backup) and groups
with the comparator of Android's `SourcesScreenModel.collectLatestSources` (last used, pinned, categories, languages,
no language last). Android's model itself is not borrowed: it needs `SourcesScreen.SmartSearchConfig` (nested in the
Android Voyager screen), `SourceUiModel` (in a presentation file full of Android-only UI) and the SY category /
data-saver / show-latest interactors and screens. Rows: click → catalogue, pin button (Android SY `showPin`), long
press → Android's options dialog (pin / unpin, disable; not for the local source), right click → the same two actions
as a context menu (desktop addition), settings button for configurable sources, "JS" label. Toolbar actions above the
list: global search and the source filter (Android's sources tab toolbar).

- **Behaviour change on desktop:** before P1 the sources tab listed every source; now, like Android, only sources of
  enabled languages (default `all`, `en` and the system language) that are not disabled.
- The catalogue (`CatalogueScreen`) sets `lastUsedSource` when opened outside incognito mode, as Android's
  `BrowseSourceScreenModel` does, so the "Last used" group works.
- Not on desktop: SY source categories (set / filter), data-saver exclusion, "extension info" in the dialog, the
  "Latest" button in the row (the catalogue has a Latest chip), Komikku's source search box and NSFW-only filter.

**Source filter** (`SourcesFilterScreen.kt`): the borrowed `SourcesFilterScreenModel` with a desktop layout: a switch
per language (`LocaleHelper` names, sticky), SY's "All sources" switch and a check box per source of an enabled
language. Like Android it lists only online (`HttpSource`) sources, so the local source is not in it. No flag emoji
(`FlagEmoji` is Android-only).

**Global search** (`GlobalSearchScreen.kt`): the borrowed `GlobalSearchScreenModel` / `SearchScreenModel` unchanged:
enabled languages, disabled and pinned sources read when the screen opens, at most five searches at a time, results
stored with `NetworkToLocalManga`, rows sorted like Android (with results first, pinned, name), "pinned only" / "all"
and "has results" persisted in the same `SourcePreferences`; no pinned source → "all" and no pinned chip (KMK).
JavaScript sources are in the source manager and take part like any other source (tested). Desktop UI: toolbar
(search field, progress bar while rows load, chips), one borrowed `GlobalSearchResultItem` per source with the loading
/ error items, a desktop card row (cover item, library entries dimmed with Android's alpha and an "In library" label;
Android's `GlobalSearchCardRow` needs the library `MangaComfortableGridItem` / `MangaCover` chain). The row header
opens the source's own search with the query (`CatalogueScreen(sourceId, initialQuery)`, Android's
`BrowseSourceScreen(id, query)`), a result opens the manga details. Entry points: the sources tab action and a click on
the title in the manga details (Android's manga info header).

- Not on desktop: bulk favourite selection (`BulkFavoriteScreenModel`), the extension filter and its single-result
  shortcut (deep links), the extension-name suffix of row titles (`Source.installedExtension` is Android-only; JS rows
  show the "JS" label instead), "search globally" from the library search, related mangas and deep links.
- Same as Android (its `GlobalSearchToolbar` TODO): the model reads the source filter from its preference flow, so a
  chip change can apply only to the next search. Fixed on desktop in the P1 review (see "Review fixes (P1)").

**Build note:** moving a file from `shared` to `desktopCopy` left `:app-shared:compileKotlinDesktop` up to date
without the moved class (Kotlin incremental compilation across the two generated source roots);
`./gradlew :app-shared:compileKotlinDesktop --rerun` fixed it. Repeat that after moving borrowed files between kinds.

No new strings (all from `MR` / `SYMR` / `KMR`).

Tests (`desktopApp/src/test/.../ui/browse/`): `SourcesUiTest` (pin / unpin moves a source to the pinned group,
long-press disable hides it, the filter screen enables a language, re-enables a source and toggles all sources of a
language, all through `SourcePreferences`; the grouping order), `GlobalSearchUiTest` (a Mihon-style source, a
JavaScript source, one without results and a failing one are searched, a disabled-language and a disabled source are
not; "has results", "pinned only" / "all"; row → the source's search with the query; result → details; details title →
new global search). `FakeSources.kt` gains `SimpleSearchSource` (an `HttpSource` without network).

Verification: `spotlessCheck`, `:app:verifyBorrowedSources`, `:app:compileDebugKotlin`, `:desktopApp:compileKotlin`
pass; `:desktopApp:test` 35 classes, 115 tests, 0 failures (was 33 / 111: `SourcesUiTest` 2, `GlobalSearchUiTest` 2).
One full run before `2e39d59` failed `GlobalSearchUiTest` on the filter-chip race above (passed alone); fixed in the
test. Android UI unchanged and compile-only (the moved interactors and result items are the same code from
`:app-shared`); the desktop screens were not run in a real window.

### Review fixes (P1)

Review of `1ff8d31..7f4e22f` (medium, 6 findings), fixed in `03642a4` and `7d5b984`. Each fix has a
headless test where the defect can be shown; the failing runs were made against the unfixed code.

| # | Finding | Outcome | Commit |
|---|---------|---------|--------|
| 1 | Global search opened with a query (details title) could show no rows and never search: the borrowed model's first `search()` runs with the state's default `PinnedOnly` before the preference flow (and its own "no pinned sources → all") reaches the state | fixed: `GlobalSearchScreen` re-runs `model.search()` from `LaunchedEffect(state.sourceFilter)`; `search()` skips an already searched query + filter, so nothing runs twice. The borrowed model is unchanged. Test `a search opened with a query runs on all sources while the stored filter is pinned only` (failed before: no rows) | `03642a4` |
| 2 | The Pinned / All chip did not re-run the search (`setSourceFilter` searches before the preference reaches the state) | fixed by the same effect; the extra search click is gone from `only pinned sources are searched with the pinned filter`. That test passed on the unfixed code too in this environment (the preference collector happened to update the state first), so it guards the fix but did not show the race | `03642a4` |
| 3 | One failure in the enabled-sources flow left the sources tab on "no sources" for good (`.catch` ends the collection) | fixed: `SourcesPaneModel` keeps an `error` state (shown with `KMR.desktop_source_error`), and `retryWhen` subscribes again on the next change of anything `GetEnabledSources` reads (its seven source preferences or the source manager's sources), so no busy retry loop on a persistent error. Test `a failure reading the sources shows an error and a later preference change recovers` (malformed `sourcesTabSourcesInCategories` entry; failed before) | `7d5b984` |
| 4 | `remember(source.id) { sourceManager.get(source.id) }` kept a stale source instance | fixed: keyed on the manager's `sources` flow as well (an updated / reinstalled source is looked up again) | `7d5b984` |
| 5 | A catalogue of a source that is not installed recorded `lastUsedSource` | fixed: only a `CatalogueSource` is recorded (and loaded). Test `only an installed source becomes the last used source` (failed before) | `7d5b984` |
| 6 | `model.hasPinnedSources()` ran on every recomposition | fixed: `remember(state.sourceFilter)` | `03642a4` |

No new strings. Verification after the fixes: `spotlessCheck` passes; `:desktopApp:test` 35 classes, 118 tests, 0
failures (was 115: `GlobalSearchUiTest` +1, `SourcesUiTest` +2). Android code is unchanged by these fixes.

## P2 — library grouping and batch actions

Commits `a60bb21` (refactor), `d93a54c`, `4eef128` (features), `59d7aab` (tests) and this section's docs commit.

**Shared instead of rewritten** (same pattern as 8.5's `LibraryFilterSort.kt`; Android behaviour unchanged, the Android
model calls the moved code from KMK blocks marked "Body moved to …"):
- `LibraryFilterSort.kt` gains `List<LibraryItem>.groupLibraryItems(groupType, context, getTracks, trackerManager,
  sourceManager)`: the `UNGROUPED` branch of `LibraryScreenModel.applyGrouping` and the whole of
  `getGroupedMangaItems` + `statusMap` (by tracking status, source, status), bodies and markers unchanged.
- New KMK file `app/…/ui/library/LibraryActions.kt`: the selection bodies (`toggleLibrarySelection`,
  `toggleLibraryRangeSelection`, `selectAllLibraryItems`, `invertLibrarySelection` on the selection set) and
  `LibraryBatchActions` (common / mixed categories and the change-category dialog's initial selection, download next
  N / unread / bookmarked chapters including the SY merged-entry branch, mark read / unread, remove from library with
  covers and / or downloaded chapters, set categories). Android's `LibraryScreenModel` builds one `LibraryBatchActions`
  from its injected dependencies and keeps its own scopes (`launchNonCancellable`), state and `lastSelectionCategory`.
- Borrowed (`desktopCopy`, block "P2"): `LibraryActions.kt`, `util/MangaExtensions.kt` (`removeCovers`),
  `core/preference/CheckboxState.kt`, `presentation/category/components/CategoryDialogs.kt` (`ChangeCategoryDialog`),
  `presentation/library/DeleteLibraryMangaDialog.kt`, `presentation/components/DropdownMenu.kt` and
  `DownloadDropdownMenu.kt`. Desktop counterpart: `app-shared/src/desktopMain/…/presentation/category/CategoryExtensions.kt`
  (same `visualName` API; the Android file's two `stringResource` imports are ambiguous on desktop, where
  `tachiyomi.core.common.i18n` also has a context-free overload).
- `LibraryUpdateRunner.Target.Entries(mangaIds)` (`:app-shared`): Android's targeted `LibraryUpdateJob.startNow(mangaIds)`
  (only the "fetch once" restriction applies).

**Desktop library** (`desktopApp/…/ui/tabs/`):
- Grouping from `LibraryPreferences.groupLibraryBy()` (the preference Android uses, so it round-trips through backups):
  pages are categories, sources, statuses, tracking statuses or one ungrouped page; an active category filter with
  included categories shows the result ungrouped (Android rule); the global sort applies when not grouped by category
  (already so). Settings dialog: fourth tab "Group" (SY `GroupPage`: category, source, status, tracking status only with
  a logged-in tracker, ungrouped only with user categories or when already chosen; Material icons instead of Android's
  drawables). "Update category" on a non-category page updates that page's entries (`Target.Entries`; Android SY updates
  the group).
- Selection: long press (Android) selects a range from the last selection in the same page (or starts one), a click
  toggles while selecting; desktop also: Shift-click = range, Ctrl / Cmd-click = toggle without selecting first.
  Selected entries are tinted and carry the `selected` semantics. Selection toolbar (count, unselect, select all,
  invert — on the shown page, like Android) replaces the search row; the selection bar under the grid offers set
  categories (borrowed `ChangeCategoryDialog`, edit → desktop categories screen), mark read / unread, download (borrowed
  `DownloadDropdownMenu`: next 1 / 5 / 10 / 25, unread, bookmarked; hidden when a local entry is selected, like Android),
  update (KMK `action_update`, `Target.Entries`) and remove (borrowed `DeleteLibraryMangaDialog`: from library and / or
  downloaded chapters, the latter not offered for local entries).

**Decisions / deviations:**
- Selection actions without desktop plumbing are left out: migrate, smart-search merge, recommendations, SY clean titles
  (EH / nHentai only), MangaDex follow, reset custom info (no metadata editing on desktop yet).
- Page titles: a non-category group always shows its name (Android shows "Default" for a group with id 0, e.g. the local
  source when grouped by source).
- The selection survives grouping / filter changes like on Android (`selectedManga` only resolves entries still shown).
- Escape does not clear the selection (the close button does); no right-click menu on entries.

No new strings (`MR` / `SYMR` / `KMR` reused: `group`, `ungrouped`, `tracking_status`, `action_update`, …).

Tests: `LibraryGroupSelectionUiTest` (headless: Group tab → by source, pages per source, the tracking-status option
hidden without a tracker; by status with Android's names; ungrouped; long press, Shift-click range, click toggle,
invert, select all, cancel; mark read / unread on the chapters; set categories adds "Later"; download unread chapters
through `FakeMangaServer`; remove from library with downloaded chapters), `LibraryUpdateRunnerTest` (`Target.Entries`
skips only "fetch once" entries).

Verification (after `59d7aab`): `spotlessCheck`, `:app:verifyBorrowedSources`, `:app:compileDebugKotlin` (the Android
`LibraryScreenModel` refactor compiles; Android UI not run), `:desktopApp:compileKotlin` pass; `:desktopApp:test` 36
classes, 120 tests, 0 failures (was 35 / 118: `LibraryGroupSelectionUiTest` 2). The desktop library was not run in a
real window; Shift-click is proven with the test input's key state only.

### Review fixes (P2)

Review of `3240a78..c060104` (medium, 8 findings), fixed in `2d720a7`, `d8a8329`, `e9b4313` (test tidy-up `84508c4`).
Failing runs were made against the unfixed code for findings 1–5 (each fix reverted on its own; the UI test then failed
at that finding's assertion).

| # | Finding | Outcome | Commit |
|---|---------|---------|--------|
| 1 | Range selection crashed (`categoryItems[-1]`) when the anchor (`lastOrNull()`) is not on the page: select A on page 1, select + deselect B on page 2, range-select C on page 2 (Android too) | fixed in the shared `toggleLibraryRangeSelection`: when either index is -1 only the clicked entry is added; otherwise unchanged on both platforms. Desktop `toggleSelection` still sets `lastSelectionCategory` on a deselect, like Android (`category.id.takeIf { newSelection.isNotEmpty() }`): with the guard that is harmless, and a different rule would make the platforms differ. Test `LibraryActionsTest` (failed before: `ArrayIndexOutOfBoundsException`) | `2d720a7` |
| 2 | With a category filter that has included categories the page is ungrouped but the state kept the preference's group type, so "Update category" ran `Target.Category(0)` | fixed: `State.groupType` is the effective grouping (`UNGROUPED` under the filter); page titles and the update button use it. Android's `LibraryScreenModel` keeps the preference value in its state (same latent issue, Android code not changed). Test step in `LibraryGroupSelectionUiTest` (failed before: `Category(0)`) | `e9b4313` |
| 3 | A group page (source / status / tracking status / ungrouped) updated through `Target.Entries`, skipping the smart-update restrictions and `groupLibraryUpdateType` | fixed: new `LibraryUpdateRunner.Target.Group(groupType, mangaIds)`; `LibraryUpdateSelection` takes the whole library (update categories, like "all") for `GLOBAL` (Android's default) and for `ALL_BUT_UNGROUPED` on the ungrouped page, else the page's entries, then all `autoUpdateMangaRestrictions` and the update order, like Android SY's group branch. `Target.Entries` (restriction-free except "fetch once") stays for an explicit selection only. Deviation: the page's shown entries (after library filters / search) are the group, Android re-derives the group from the whole library through `groupExtra`. Tests `LibraryUpdateRunnerTest` (`a group page goes through the restrictions and groupLibraryUpdateType`) and the UI step (failed before: `Entries`) | `e9b4313` |
| 4 | The selection bar was pushed out when the shown page is empty (`EmptyScreen` without a weight) | fixed: `Modifier.weight(1f)` on the empty branch. UI step (failed before: bar not displayed) | `e9b4313` |
| 5 | "Update" on a selection ignored `runner.start(...) == false` and cleared the selection | fixed: the selection stays and `MR.strings.update_already_running` is shown (compat toast → snackbar), Android's library snackbar message; "Update category" / "Update library" show it too. UI step (failed before: no message) | `e9b4313` |
| 6 | Press modifiers were reset only in `onClick` | fixed: `onLongClick` resets them too | `e9b4313` |
| 7 | `runBlocking { getTracks.await() }` in `groupLibraryItems` | fixed: `groupLibraryItems` and Android's private `applyGrouping` are `suspend` (both callers already run in a coroutine: Android's `combine` transform, desktop's `buildResult`), the tracks are awaited. Same data at the same point, Android behaviour unchanged | `d8a8329` |
| 8 | New SY blocks in Komikku-owned code | rule applied: a file new in Komikku (`LibraryActions.kt`) has no SY markers; its KDoc names the functions moved from `LibraryScreenModel` and the parts that come from TachiyomiSY (merged-entry download branches, `ogTitle` in the download checks). Code moved into an existing file (`groupLibraryItems` in `LibraryFilterSort.kt`, KMK-owned since 8.5) is wrapped in one KMK block with a "moved from …" comment; the inner `// SY` block is `getGroupedMangaItems`'s own SY block moved verbatim with its body, so it stays (said in the KDoc); the KMK block around `libraryStatusGroups` merged into the outer one | `2d720a7`, `d8a8329` |

No new strings. Verification after the fixes: `spotlessCheck`, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources` pass;
`:desktopApp:test` 37 classes, 124 tests, 0 failures (was 36 / 120: `LibraryActionsTest` +2, `LibraryUpdateRunnerTest`
+1, `LibraryGroupSelectionUiTest` +1). Android UI compile-only.

## P3 — scheduled updates and backups

Commits `80c64c6` (scheduler), `3f50fd0` (scheduled work), `f5c934a` (settings / last runs) and this section's docs
commit.

**Scheduler** (`desktopApp/…/scheduler/DesktopScheduler.kt`, new, desktop-only): the counterpart of Android's periodic
WorkManager jobs, owned by the app's lifetime (`main()` starts it after `DesktopInjekt.init()` and stops it when the
window closes; tests never start it). Every minute, and at once when an interval preference changes, it starts the due
tasks in its own scope. A task is due when its interval is on and has passed since the later of its persisted last run
(so a run missed while the app was closed is caught up once on the first check) and its last attempt in this process.
A task never runs twice at once. Outcomes follow WorkManager: done / failed wait a whole interval from the attempt (a
failing task is not retried every minute), busy (the same work already runs) retries after 10 minutes (Android's
back-off). The clock is injected (`now: () -> Long`).

**Scheduled work** (`scheduler/DesktopScheduledJobs.kt`), on Android's preferences and keys (a restored backup carries
them; `lastUpdatedTimestamp` / `lastAutoBackupTimestamp` are app-state keys, not in backups, as on Android):
- Library update: every `autoUpdateInterval()` hours (Android's entries: never, 12, 24, 48, 72 h, weekly) a
  `LibraryUpdateRunner.Target.All` through the existing desktop runner (update categories and smart-update restrictions,
  like Android's periodic job). Due from `lastUpdatedTimestamp()`, which every desktop library update (all targets but
  `Single`) now stores when it starts, as Android's `LibraryUpdateJob` does (`CoroutineLibraryUpdateRunner` gains an
  `onStarted` hook); so a manual update postpones the next scheduled one (Android's periodic work runs on its own
  period instead). A running update makes it "busy" (Android: `Result.retry()` while the manual worker runs).
- Automatic backup: every `backupInterval()` hours (off, 1, 3, 6, 12, 24, 48 h, weekly) the borrowed `BackupCreator` in
  automatic mode (`DesktopBackupService.createAutomatic`: the storage location's `autobackup` folder through
  `StorageManager`, newest four kept, sets `lastAutoBackupTimestamp()`), started through the backup jobs' single slot
  (`DesktopBackupJobs.createAutomatic`), so it never runs next to a manual backup or a restore ("busy"; Android retries
  while a restore runs). Like Android it reports no result; while it runs the data screen shows the progress row (with
  cancel) and disables its buttons, afterwards the previous status is back.

**Settings / last run:**
- Settings → Library, "Library update" group: "Automatic updates" (Android's interval list) and SY's "Library dynamic
  category updates" (`groupLibraryUpdateType`, used by the group-page update since the P2 review).
- Settings → Data and storage: "Automatic backup frequency" and Android's info text with "Last automatically backed up:
  …" (borrowed `ListPreference` / `InfoPreference` rendered through the new `PreferenceRow` of `:app-shared`'s
  `PreferenceList.kt`).
- Updates tab: Android's "Library last updated: …" line at the top of the list (Android's `updatesLastUpdatedItem`).
- Desktop `relativeTimeSpanString` (`ui/RelativeTime.kt`): Android's never / just now, otherwise the localized date and
  time (Android's `TimeUtils` needs `android.text.format.DateUtils` for "5 minutes ago"; not borrowed).

**Decisions / deviations:**
- Device restrictions: none applies on desktop. Wi-Fi / unmetered / charging (and Android's implicit "battery not low"
  and "network connected") have no reliable JVM API across Windows / macOS / Linux, so the restriction list is not shown
  and the preference is left untouched (it still round-trips through backups). Offline runs fail per entry and are
  stored as update errors, like a manual update.
- Only while the app is open (no tray / background service, no OS-level scheduling); notifications are not shown (the
  library update banner shows the run like a manual one).
- The schedule is measured from the last run, not from when the interval was set (WorkManager); the first run after
  enabling is immediate when nothing ran yet, as WorkManager's first periodic run.
- No change to Android code or behaviour (the runner hook is in `:app-shared`'s desktop runner path only; Android keeps
  WorkManager).

No new strings (`MR` / `SYMR` reused: `pref_library_update_interval`, `update_*hour`, `update_never`, `update_weekly`,
`library_group_updates*`, `pref_backup_interval`, `backup_info`, `last_auto_backup_info`, `updates_last_update_info`,
`relative_time_span_never`, `updates_last_update_info_just_now`, `off`).

Tests: `DesktopSchedulerTest` (7, injected clock: due / not due, catch-up once, disabled, no overlap, busy retry /
failure wait, exceptions, start / wake-ups / stop), `DesktopScheduledJobsTest` (2: the library task on the real desktop
runner stores the last update, a manual update postpones it, a running update makes it busy; the automatic backup writes
one `.tachibk` into `autobackup` through the borrowed creator and stores its time, a running restore makes it busy),
`ScheduleSettingsUiTest` (1: the settings entries set Android's preferences, the last automatic backup text follows the
preference).

Verification (after `f5c934a`): `spotlessCheck`, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources`,
`:desktopApp:compileKotlin` pass; `:desktopApp:test` 40 classes, 134 tests, 0 failures (was 37 / 124 after the P2
review: `DesktopSchedulerTest` 7, `DesktopScheduledJobsTest` 2, `ScheduleSettingsUiTest` 1). Not run in a real window;
the scheduler was never left running for real hours (only the injected clock and the wake-up path are tested); the
"Library last updated" line in the updates list has no test of its own.

### Review fixes (P3)

Review of `80160e3..c7a2daa` (medium, 8 findings), fixed in `d6110e7`, `3121d4e`, `560bd49`, `8f936c1`. New tests for every
finding; they were written against the fixed code and not run against the unfixed one (most need the new parameters /
preferences to compile), so "would fail before" is by reading for findings 1, 4, 6 (the scheduler's old bookkeeping /
due time give the opposite result at the asserted step), 7 (old order published the progress first) and 8.

| # | Finding | Outcome | Commit |
|---|---------|---------|--------|
| 1 | Cancelling an automatic backup from the data screen made it restart within a minute, forever (the cancelled `await()` rethrew through `checkNow`, skipping the bookkeeping) | fixed in the scheduler: a `CancellationException` while the scheduler's own task coroutine is still active (`ensureActive()`) is a `Failed` outcome, so the task waits a whole interval from the attempt. Decision: that is Android's behaviour too (its backup has no cancel action; a stopped periodic work runs again on its next period). Test `DesktopScheduledJobsTest` (`a cancelled automatic backup waits a whole interval`) | `d6110e7` |
| 2 | A scheduled update due at launch ran before extension / JavaScript sources were loaded (stubs, failures, `lastUpdatedTimestamp` set) | fixed: `CoroutineLibraryUpdateRunner` gains `awaitReady`, run inside the job before the entries are selected (the progress shows the run while it waits; cancel ends the wait); the desktop runner waits for `sourceManager.isInitialized.first { it }`, bounded by 2 minutes (then logs and updates anyway, so a source that never loads cannot block updates forever). Applies to every update, also an early manual one. Tests `LibraryUpdateRunnerTest` (`waits until the sources are ready`), `DesktopScheduledJobsTest` (`a library update waits for the sources to load`) | `3121d4e` |
| 3 | `backupJobs.create(...)` / `restore(...)` returning `false` (slot taken, e.g. by the automatic backup) was ignored | fixed: Android's toasts `MR.strings.backup_in_progress` / `restore_in_progress` (compat toast → the window's snackbar). Test `DesktopScheduledJobsTest` (`a manual backup or restore while the slot is taken shows Android's message`) | `560bd49` |
| 4 | `lastUpdatedTimestamp` (written by partial updates too) was the due time of `Target.All`, so a daily partial refresh postponed the scheduled update forever | fixed: the scheduler owns the due time, `DesktopSchedulerPreferences.lastScheduledLibraryUpdate()` (app-state key `desktop_last_scheduled_library_update`, not in backups), set when the scheduled task starts its update; `lastUpdatedTimestamp` stays for the updates tab as on Android. Manual "Update library" does not move it either (Android's periodic work keeps its own period; P3's "a manual update postpones the next scheduled one" is reversed). Test step in `DesktopScheduledJobsTest` (a partial update 11 h later, the scheduled one still runs at 12 h) | `560bd49` |
| 5 | `scheduler.stop()` did not stop the work it started; closing the window during an automatic backup could leave a truncated newest file after rotation already deleted the oldest | fixed on both sides: on exit `main()` runs `stopDesktopWork` (new `DesktopShutdown.kt`): the scheduler's `stop()` (now `suspend`) cancels its tasks and joins them, each task cancels and joins the work it started (backup `Deferred`, library update), then the backup jobs (`cancelAndJoin`, manual backup / restore) and the library runner (manual update) are cancelled and awaited, all within 10 s. The borrowed `BackupCreator`'s automatic mode deletes the oldest files first and writes in place, so `DesktopBackupService.createAutomatic` no longer uses it: it writes through the manual path (temporary `.<name>.tmp` file, validated, atomically renamed to `BackupCreator.getFilename()` in `autobackup`), then rotates to the newest four (Android's `FILENAME_REGEX` / `MAX_AUTO_BACKUPS`, private there, mirrored) and sets `lastAutoBackupTimestamp`. Android unchanged. The runner also ends a run cancelled before its body started (its progress stayed unfinished). Tests `DesktopScheduledJobsTest` (`exit stops the scheduled and the manual work and waits for it to clean up`, `the automatic backup rotates old files only after the new one is complete`: five old files + one unrelated → new + newest three + unrelated; a failing run keeps every old file and leaves no temporary file) | `560bd49`, `3121d4e` |
| 6 | After the clock moved back, in-memory `lastAttempt` / `retryAt` stalled tasks | fixed: `lastAttempt` is clamped to now, `retryAt` to now + retry delay; and the persisted last run in the future now counts from the first check that saw it (the old clamp to "now" at every check stalled too, until the clock caught up). Tests `DesktopSchedulerTest` (2) | `d6110e7` |
| 7 | A throwing `onStarted` left unfinished progress published | fixed: `onStarted` runs before the progress is replaced; when it throws, `start` throws and nothing is published or started. Test `LibraryUpdateRunnerTest` | `3121d4e` |
| 8 | "Just now" never refreshed | fixed: `relativeTimeSpanString`'s `now` defaults to `rememberCurrentTimeMillis()`, re-read every minute (`produceState` loop, injectable clock). Test `RelativeTimeUiTest` | `8f936c1` |

No new strings (`backup_in_progress`, `restore_in_progress` reused). Verification after the fixes: `spotlessCheck`,
`:app:compileDebugKotlin`, `:app:verifyBorrowedSources` pass; `:desktopApp:test` 41 classes, 144 tests, 0 failures (was 40 /
134: `DesktopSchedulerTest` +2, `DesktopScheduledJobsTest` +5, `LibraryUpdateRunnerTest` +2, `RelativeTimeUiTest` 1). Still
not run in a real window; the exit path (`stopDesktopWork` after `application {}` returns) is tested headless only.

## P4 — delegated sources

Commits `678778d` (borrowing), `8bdca18` (delegation in the source manager), `60bb46c` (settings toggle), `0c2270b`
(MangaDex + MDList), `2dc771d` (MDList login), `02c1181` (SY id lookups), `a204b58` (update exclusion) and this section's
docs commit. Chosen from "Remaining parity gaps" over AVIF / JXL because the SY delegates turned out to be desktop-safe:
their Android dependencies (`Context`, `SharedPreferences`, `Uri`, `androidx.core.net.toUri`, `android.util.Base64`) are
all covered by `:desktop:android-compat`, and the base classes (`DelegatedHttpSource`, `EnhancedHttpSource`,
`MetadataSource`, `UrlImportableSource`, `NamespaceSource`, `PagePreviewSource`, the `exh.metadata` models) already live in
`:source-api`, so no blocker was found and AVIF / JXL stays open.

**What Android does:** `AndroidSourceManager.toInternalSource` wraps an installed extension source whose class name is in
`DELEGATED_SOURCES` (Pururin, MangaDex, 8Muses, NHentai, LANraragi) in an `EnhancedHttpSource(original, delegate)`;
`EnhancedHttpSource.source()` picks the delegate while `DelegateSourcePreferences.delegateSources()` is on (default), else
the extension. The delegates add SY metadata (stored through `MetadataSource` into the metadata tables), namespace tags,
URL import (a gallery URL typed into the source's search goes through `GalleryAdder`), page previews and, for MangaDex,
the MDList tracker / follows. `handleSourceLibrary` publishes the delegated ids to `exh.source` lookups (metadata sources
for the library search by tags, NHentai / MangaDex / LANraragi ids, `LIBRARY_UPDATE_EXCLUDED_SOURCES`).

**Desktop:**
- Borrowed unchanged (`desktopCopy`, Android still compiles its own copy): the five delegates
  (`source/online/all/{Pururin,MangaDex,NHentai,Lanraragi}.kt`, `source/online/english/EightMuses.kt`), the URL import
  (`exh/util/SearchOverride.kt`, `exh/GalleryAdder.kt`), MangaDex's API layer (`exh/md/{dto,handlers,network,service}`,
  `exh/md/utils/{MdApi,MdConstants,MdExtensions,MdLang,MdUtil}.kt`), the real MDList tracker
  (`data/track/mdlist/MdList.kt`, replacing the desktop stub) and two pure helpers (`exh/util/{Boolean,Math}.kt`). Desktop
  counterpart added: `EHentai.GalleryNotFoundException` in the EHentai placeholder (`AbsentSources.kt`), which
  `GalleryAdder` tests for.
- `app.komikku.desktop.source.DesktopDelegatedSources`: Android's list (same class names and order, explicit
  constructors instead of reflection), `wrap(source, context)` used by `DesktopSourceManager` for every extension source
  (after the SY blacklist, like Android), `publishSourceIds` (Android's `handleSourceLibrary`, run on every rebuild).
- DI: the `MetadataSource.GetMangaId` / `GetFlatMetadataById` / `InsertFlatMetadata` bindings (Android: `SYDomainModule`).
- Settings → Browse → "Enable delegated sources" (SY's switch and strings; Android has it in Settings → Advanced, which the
  desktop has no screen for).
- MDList login: Settings → Tracking offers MangaDex's OAuth like the other OAuth trackers (authorize page in the browser,
  paste the `tachiyomisy://mangadex-auth?code=…` redirect); the code goes to the enabled MangaDex source's `login(code)`
  (Android's `MangaDexLoginActivity`), so it needs the MangaDex extension installed.
- Library updates skip `LIBRARY_UPDATE_EXCLUDED_SOURCES` (Pururin, NHentai delegates, E-Hentai ids) for every target but
  a single entry's refresh, like Android SY's `LibraryUpdateJob.updateChapterList` (not counted as skipped; explicitly
  selected entries are, since the P4 review).

**Decisions / deviations:**
- A delegate that throws while being created leaves the plain extension source in place (logged); Android does not guard.
- The id lookups are recomputed from the current sources on every rebuild; Android's `currentDelegatedSources` only grows
  during a run (an uninstalled delegated source keeps its ids there). `AndroidSourceManager.currentDelegatedSources` itself
  stays Android-only.
- The `DELEGATED_SOURCES` list is mirrored, not shared: it lives in the Android source manager's companion (with
  reflection over Android-only classes); moving it would change Android code for no behaviour gain.
- MDList logout goes through the enabled MangaDex source's logout like Android's MangaDex settings (revokes the session
  on the server) and always resets the tracker's interceptor (P4 review); without a MangaDex source it is local only.

**Known gaps:** no SY metadata / namespace-tag view and no page previews on the desktop details screen (the metadata is
stored and searchable); no batch add / "open URL" import screen (URL import works through a source's search box); no
MangaDex settings screen (preferred MangaDex, data saver, blocked groups — the source reads its preferences, which only
Android can edit for now), follows / similar screens or MangaDex follow from the library selection; SY's built-in
E-Hentai / ExHentai and merged sources are still absent. Not run against real extension APKs (dex2jar keeps class names,
so the class-name match is expected to work) or the real MangaDex / NHentai servers; the MDList login is unverified
against MangaDex.

No new strings (`SYMR.strings.toggle_delegated_sources`, `toggle_delegated_sources_summary` reused).

Tests: `DelegatedSourcesTest` (5: wrapping by class name and the id lookups; every delegate starts over an extension
source; MangaDex delegate URL mapping and not logged in; NHentai details through the delegate's parser into the metadata
tables and the library search by `artist:` tag; plain search to the extension, URL import of a gallery URL, unknown URL,
toggle off), offline test doubles of the NHentai / MangaDex extensions (`desktopApp/src/test/kotlin/eu/kanade/tachiyomi/
extension/all/…`), `TrackingTest` (MDList login method, no login without a MangaDex source), `LibraryUpdateRunnerTest`
(excluded sources), `SettingsUiTest` (the toggle).

Verification (after `a204b58`): `spotlessCheck`, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources`,
`:desktopApp:compileKotlin` pass; `:desktopApp:test` 42 classes, 150 tests, 0 failures (was 41 / 144 after the P3 review:
`DelegatedSourcesTest` 5, `LibraryUpdateRunnerTest` +1; `TrackingTest` / `SettingsUiTest` steps added to existing tests).
Android code unchanged (all borrowed files are `desktopCopy`); Android UI not run.

### Review fixes (P4)

Review of `ddfd839..642de81` (medium, 8 findings), fixed in `9734788`, `7aee688`, `9c1a2d2`. Tests were written against
the fixed code; by reading, they fail on the unfixed one for findings 1 (the interceptor still holds the token), 2 (no
availability state, the code exchange is attempted), 3 (the `NoClassDefFoundError` escapes `wrap` / the collector), 5
(the entry is dropped, `skipped` 0) and 6 (the cached `null` throws `MangaDexNotFoundException` on the second search).

| # | Finding | Outcome | Commit |
|---|---------|---------|--------|
| 1 | MDList logout only deleted the token preference; `MangaDexAuthInterceptor` kept `token` / `oauth` in memory and a refresh wrote it back | fixed (desktop only): `DesktopTrackerLogin.logout` logs MDList out through `MdUtil.getEnabledMangaDex()?.logout()` (Android's `MangaDexLoginHelper.logout`, revokes the session), errors logged, then always `interceptor.setAuth(null)` and the tracker's `logout()`; the settings model runs it off the UI thread. Test `TrackingTest` (`MDList logout forgets the session …`) | `9734788` |
| 2 | MDList login offered without a usable MangaDex source | fixed: `DesktopTrackerLogin.sourceAvailable` (MDList: `MdUtil.getEnabledMangaDex` — installed, language and source enabled, delegated sources on; enhanced trackers: as before). The settings model recomputes it on sources / enabled languages / disabled sources / delegated-sources changes; the row is disabled with a specific reason (new `KMR.strings.desktop_mdlist_needs_source`) until a source exists, also before the first check; `startLogin` and the paste step refuse with that reason before the browser / code exchange. Test `TrackingTest` (`MDList login needs an enabled MangaDex source …`) | `9734788` |
| 3 | `DesktopDelegatedSources.wrap` caught only `Exception`; an `Error` from borrowed Android code ended `DesktopSourceManager`'s collector | fixed: `wrap` catches `Throwable` (rethrows `CancellationException`), the class-name match inside the guard; the manager guards each extension's source list, each source (blacklist check, wrap, stub) and each JavaScript source separately, and the id publishing, so one failing source is left out (logged) and the manager still initializes. Test `DelegatedSourcesTest` (`a delegate or a source failing with an Error …`) | `7aee688` |
| 4 | `LIBRARY_UPDATE_EXCLUDED_SOURCES` holds the NHentai ids only after the source manager published them | already ordered at `642de81` (the selection ran after `awaitReady` and read the global at call time); made explicit: `CoroutineLibraryUpdateRunner(excludedSources = …)` is a provider read after `awaitReady`, passed into `LibraryUpdateSelection.select`, for every target. Test `LibraryUpdateRunnerTest` (`the excluded sources are read once the sources are ready, for every target`: All, Category, Entries, Group). Left: when `awaitReady` times out (2 min) the ids may still be missing, as before | `9c1a2d2` |
| 5 | Explicitly selected excluded entries vanished without feedback | fixed: `Target.Entries` partitions them into `Result.excluded`; the progress counts them in `skipped` and `excluded`; the banner shows the skipped count (`MR.plurals.migrationListScreen_migrateDialog_skipText`) and the reason (`MR.strings.skipped_reason_not_always_update`), and stays visible for a run that skipped everything. Other targets drop them silently like Android SY. Tests `LibraryUpdateRunnerTest` | `9c1a2d2` |
| 6 | Borrowed `MdList` cached `MdUtil.getEnabledMangaDex()` with `by lazy`, `null` included | fixed in `app` (KMK-marked, so both platforms): a found source is cached as before (Android's non-null behaviour unchanged), a `null` is not and the lookup is retried on the next use. Test `DelegatedSourcesTest` (`MDList finds a MangaDex source that loads after its first use`) | `7aee688` |
| 7 | The cause of a failed MDList login was swallowed | partly: the cause is caught and only logged inside the borrowed `MangaDexLoginHelper.login` (returns `false`), so the desktop cannot surface it without changing Android code; the desktop says that an authorization works once and the login must be restarted from the browser step (new `KMR.strings.desktop_mdlist_login_failed`); a missing source has its own message (finding 2) | `9734788` |
| 8 | Stale "MDList login not on desktop" comments | fixed: desktop `TrackerManager`, `DesktopDelegatedSources` KDoc, `borrowed-app-sources.gradle.kts` (two places), `DelegatedSourcesTest` | `9734788`, `7aee688` |

New strings: `KMR.strings.desktop_mdlist_needs_source`, `desktop_mdlist_login_failed` (base only). Android behaviour
changed only where intended: `MdList` no longer caches a missing MangaDex source (finding 6).

Verification after the fixes: `spotlessApply`, `spotlessCheck`, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources`,
`:desktopApp:compileKotlin` pass; `:desktopApp:test` 42 classes, 156 tests, 0 failures (was 42 / 150: `TrackingTest` +2,
`DelegatedSourcesTest` +2, `LibraryUpdateRunnerTest` +2). Not run: the Android UI, a real MangaDex login / logout, a real
window.

## P5 — AVIF / JPEG XL images (evaluation, unsupported-format state)

Chosen from "Remaining parity gaps" (reader item 4 and covers). **Outcome: no acceptable decoder; the desktop detects
AVIF / JPEG XL / HEIF and shows a clear "unsupported image format" state** for reader pages and manga covers.

**What Android does:** covers and pages in AVIF / JPEG XL / HEIF go through `TachiyomiImageDecoder` (Coil) and the
reader's decoder, both on `tachiyomiorg/image-decoder`, a JNI library with libavif / libjxl / libheif built for the four
Android ABIs only, on Android's `Bitmap`; not usable on a desktop JVM.

**Desktop pipeline today:** covers through Coil 3 (`DesktopImageLoader`, Coil's Skia decoder), reader pages through
`SkiaPageDecoder` (Skiko 0.150.2 `Image.makeFromEncoded` / `Codec`). The reader already gated AVIF / JXL / HEIF by their
magic bytes (desktop `ImageUtil`) into `UnsupportedPageFormatException`; covers failed with Skia's generic error and
stayed blank.

**Decoders evaluated (none added):**

| Candidate | Licence / version | Natives (OS / CPU) | Verdict |
|-----------|-------------------|--------------------|---------|
| Skia in Skiko 0.150.2 (already on the classpath) | Apache-2.0 / BSD | Linux / macOS / Windows x64 + arm64 (Skiko runtime) | built without Skia's AVIF / JPEG XL / HEIF codecs: real AVIF and JXL samples fail in `Image.makeFromEncoded` and `Codec.makeFromData` (test below) |
| NightMonkeys `com.github.gotson.nightmonkeys:imageio-jxl` / `imageio-heif` (ImageIO plugins, libjxl / libheif) | MIT; 1.1.0 (Aug 2025), maintained | none bundled: needs the system's libjxl / libheif (the `imageio-heif` jar, 390 KB, has no `.so` / `.dylib` / `.dll`) | blocked twice: its decoder classes are Java 22 FFM classes (`META-INF/versions/22/…` in a multi-release jar) while the desktop builds and ships JDK 21 (`BUILDING.md`, jlink runtime), and the native libraries would have to be installed by the user on every OS |
| Android's `tachiyomiorg/image-decoder` | Apache-2.0 | Android ABIs only | Android-only (JNI + `android.graphics.Bitmap`) |
| Maven Central search for AVIF / JPEG XL artifacts (`search.maven.org`: "avif", "jxl", "jpegxl", "libheif") | — | — | only Android AARs (`org.aomedia.avif.android:avif`, Glide / penfeizhou AVIF integrations, vendor SDKs) and the NightMonkeys plugins; no pure-Java JPEG XL decoder (jxlatte is not on Maven Central) |
| FFmpeg through bytedeco JavaCV | LGPL / GPL builds | Linux / macOS / Windows x64 (+ arm64 on Linux / macOS) | not pursued: tens of MB of natives per platform for still images, AVIF / JPEG XL support of its build unverified (JPEG XL needs libjxl) |

**Unblocking options for the maintainer:** a Skiko build with Skia's AVIF / JPEG XL codecs enabled (since the P5 review
Skia always gets the first try, so such an update needs no code change; see "Review fixes (P5)"); or
moving the desktop runtime to JDK 22+ and bundling libjxl / libheif per OS for NightMonkeys (licences: libjxl BSD-3,
libheif LGPL-3, libde265 LGPL-3 for HEIF).

**Desktop changes** (`ccd825a`):
- `app.komikku.shared.coil.UnsupportedImageFormatDecoder` (Coil `Decoder.Factory`, registered in `DesktopImageLoader`):
  an AVIF / JPEG XL / HEIF image (magic bytes, `SkiaPageDecoder.UNSUPPORTED`) fails with `UnsupportedPageFormatException`
  (format name) instead of Skia's generic decode error; other formats go to Coil's Skia decoder as before.
  (Since the review fixes: Skia tries first, the format error only replaces its failure; see "Review fixes (P5)".)
- `app.komikku.desktop.ui.CoverImage`: the library, catalogue, global search, manga details and updates covers show the format's name
  ("AVIF") on such a cover instead of staying blank; other failures keep the plain background.
- Reader page placeholder: "AVIF images can't be shown on desktop yet" (new `KMR.strings.desktop_unsupported_image_format`)
  instead of "The image couldn't be loaded (AVIF)"; still no retry button for it.
- Not covered: other images (extension / JavaScript source icons, which are PNG in practice), animated AVIF, HEIF samples (no encoder available here; HEIF is detected by its `ftyp` brand like before).

Tests: `UnsupportedImageFormatTest` (5): real 8×8 AVIF (Pillow / libavif) and JPEG XL (ffmpeg / libjxl) samples embedded
as Base64 (314 and 64 bytes): detection, Skia's missing codecs, the reader decoder's `UnsupportedPageFormatException` with
the format (`decode` and `readSize`), the desktop image loader's error result for both (a PNG still decodes), and the
cover label in a headless Compose test.

Verification (after `ccd825a`): `spotlessApply`, `spotlessCheck`, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources`,
`:desktopApp:compileKotlin` pass; `:desktopApp:test` 43 classes, 161 tests, 0 failures (was 42 / 156 after the P4 review).
Not run in a real window; no real AVIF / JPEG XL cover from a source was loaded.

### Review fixes (P5)

Review of `18a7cbc..5b0d41e` (medium, 4 findings), fixed in `26ad164`, `eb6c2d9`, `ac074ee`. The new `CoverImage`
sizing test was run against the unfixed `CoverImage` and fails there; the other tests were written against the fixed
code.

| # | Finding | Outcome | Commit |
|---|---------|---------|--------|
| 1 | `UnsupportedImageFormatDecoder` rejected every AVIF / JXL / HEIF image by its magic bytes before Coil's Skia decoder, although Skia on macOS / Windows might decode some through OS codecs | fixed: Skia gets the first try everywhere. The Coil factory wraps Coil's `SkiaImageDecoder` (injectable `delegate`) for these types only and maps its failure (any `Exception`, not cancellation) to `UnsupportedPageFormatException(format, cause)`; other formats take Coil's normal chain. `SkiaPageDecoder` no longer gates by type: `readSize` / `decode` run Skia (`openCodec` / `decodeImage`, injectable) and only an `IllegalArgumentException` for an image detected as AVIF / JXL / HEIF becomes the format error (`SkiaPageDecoder.unsupportedFormat`); others keep "Could not decode the image". `UnsupportedPageFormatException` carries the cause | `26ad164` |
| 2 | `Skia on desktop cannot decode AVIF or JPEG XL` would fail on a runner whose Skia decodes them | fixed: the platform tests accept "decodes to 8×8" or "`UnsupportedPageFormatException` naming the format" for the reader (`decode`, `readSize`) and the desktop Coil loader, and print the outcome per OS; the mapping is tested with an injected failing Skia step (reader decoder and Coil factory; a PNG stays a plain decode error / goes to Coil's own decoder); the cover label test uses the failing loader | `26ad164` |
| 3 | `CoverImage`'s `Box` + `matchParentSize()` collapsed to zero when the caller did not fix both dimensions | fixed: `Box(propagateMinConstraints = true)` with the image as the sizing child, the label `wrapContentSize`-centred; call sites unchanged. Test `a cover sized by one dimension takes the image's height` (fails on the old code) | `eb6c2d9` |
| 4 | Desktop detection read 32 bytes, so an AVIF with a late `avif` brand (major `mif1`, compatible `mif1 heic miaf MA1B avif`) was labelled HEIF | fixed (desktop): up to 256 bytes read; the `ftyp` box's own size bounds the brand scan (64-bit size and size 0 handled), AVIF brands win over HEIF ones, brands of later boxes no longer count. Android unchanged: its `ImageUtil.getImageType` passes 32 bytes to the native `tachiyomi.decoder.ImageDecoder.findType`, so it likely has the same limit (not verified; native code). Test `ImageUtilTest` (`scans every compatible brand of the ftyp box …`) | `ac074ee` |

**macOS / Windows behaviour is unverified and decided at run time:** whether Skiko's Skia decodes AVIF / JPEG XL / HEIF
there (through its own codecs or the OS's) is not known; only Linux was checked (it cannot). The app no longer
assumes either way: a decodable image is shown, an undecodable one shows the format. The test output
(`Skia on <OS>: AVIF decoded / unsupported`) of the Windows / macOS CI runners will tell. P5's "Unblocking options"
above still apply to Linux; the test that was to fail on a Skiko with these codecs is gone (it was OS-dependent).

Verification after the fixes: `spotlessApply`, `spotlessCheck`, `:app:compileDebugKotlin`, `:desktopApp:compileKotlin`
pass; `:desktopApp:test` 43 classes, 163 tests, 0 failures (was 43 / 161: `UnsupportedImageFormatTest` 5 → 7);
`:core:common:desktopTest` 3 classes, 9 tests, 0 failures (`ImageUtilTest` +1). Not run: a real window, real AVIF /
JPEG XL covers from a source, macOS / Windows.

## P6 — app update check

Commits `9f4a7ec` (release service download-link selector), `0dbc9a1` (desktop check, dialog, About entry, schedule,
build flag), `6c6d0a5` (tests) and this section's docs commit. Chosen from "Remaining parity gaps" (distribution item 6).

**What Android does:** `AppUpdateChecker` → `GetApplicationRelease.await` (domain) → `ReleaseServiceImpl.releaseNotes`
(data, GitHub `GET /repos/<repo>/releases`), only with `BuildConfig.UPDATER_ENABLED` (Gradle `-Penable-updater`, off by
default). Channel: preview builds read `komikku-app/komikku-preview` and compare the tag `r<n>` with `COMMIT_COUNT`,
the others read `komikku-app/komikku` and compare `v<x.y.z>` with `VERSION_NAME`; pre-releases are skipped, the notes of
every newer release are joined. Automatic checks at start (`MainActivity.CheckForUpdates`) and every 3 days
(`AppUpdateJob`) are throttled to one per two days (`last_app_check` app-state key); the About screen's "Check for
updates" forces one. A found update opens `NewUpdateScreen` (notes, "Download" with the in-app downloader / installer,
"Open on GitHub").

**What the release side has for the desktop today:** nothing. `build_desktop.yml` (manual or a `desktop-v*` tag) packages
Msi / Exe (Windows), Dmg (macOS), Deb / Rpm / an app-image directory (Linux) and uploads them as **workflow artifacts
only**; no GitHub release is created and no desktop installer is attached to any Komikku release. The desktop version
is the Android `versionName` (single source of truth, `-Dkomikku.version`). So the check is built against the existing
GitHub releases API shape and the existing Android release repositories / tags, and finds an update only once a release
carries a desktop installer (see "What the release pipeline still needs").

**Desktop changes:**
- `ReleaseServiceImpl` (data, KMK block, Android unchanged: the new constructor parameter defaults to `null`, which keeps
  the APK selection) takes an optional `downloadLinkSelector`; the desktop passes its installer selection, so the notes
  clean-up (mentions, download badges, compare links) is shared, not copied.
- `app.komikku.desktop.updater` (new, desktop-only):
  - `DesktopUpdaterConfig` — from system properties set by `desktopApp/build.gradle.kts`: `komikku.updater=true` only
    with Android's own Gradle flag `-Penable-updater` (off by default, as on Android; CI's desktop workflow does not pass
    it), `-Pdesktop-preview` → `komikku.preview=true` + `komikku.commitCount` (`getCommitCount()`, the Android preview
    rule). Repository and tag rule as on Android. A build whose version cannot be compared (`dev` without
    `komikku.version`, a preview without commit count) does not check. `installerLink` keeps a release only when it has
    an asset for this OS (`.msi` / `.exe`, `.dmg`, `.deb` / `.rpm` / `.AppImage`); Android's APK-only releases are skipped.
  - `DesktopAppUpdateChecker` — Android's `GetApplicationRelease` (borrowed through the shared domain module, same
    throttle key) with the desktop's own version; outcomes new update / no update / disabled / failed (exceptions are
    logged with `xLogE` and returned, never thrown into the UI); `available` holds the found release for the dialog.
    `checkOnStart` = Android's start-up check; `appUpdateCheckTask` = `AppUpdateJob` as a P3 `DesktopScheduler` task
    (every 3 days, interval 0 when disabled). The start-up check stores the task's last run
    (`DesktopSchedulerPreferences.lastAppUpdateCheck`, app-state key), so the scheduler's catch-up does not check twice.
  - `AppUpdateDialog` / `AppUpdateDialogHost` (in `KomikkuApp`, over every screen) — Android's `NewUpdateScreen` texts:
    "New version available!", "Latest: …", the release notes, "Open on GitHub" (opens the release page through
    `DesktopBrowser`), "Not now".
- Settings → About: the version line now names the preview commit count on a preview build; "Check for updates" (only
  with the updater flag, as on Android) runs a forced check with a progress indicator; no update → Android's "No new
  updates available" toast, a failure → its message.
- DI: `DesktopUpdaterConfig`, `ReleaseService` (`ReleaseServiceImpl` with the desktop selector), `GetApplicationRelease`,
  `DesktopAppUpdateChecker`; the scheduler gets the update task.

**Decisions / deviations:**
- **No in-app download / install on desktop** (Android: `AppUpdateDownloadJob` + package installer, and the
  `appShouldAutoUpdate` auto-download preference): installers are per OS and per package format, need the OS installer
  (MSI / DMG / package manager, often with elevation), and are unsigned so far; the dialog opens the release page
  instead. "Download" is therefore not shown, and the auto-update preference has no desktop entry (it stays in backups).
- Same flag as Android (`-Penable-updater`) instead of a desktop-only name: one switch for "this build may contact GitHub
  for updates"; the preview channel needs the extra `-Pdesktop-preview` because the desktop has no preview build type.
- Same repositories and tags as Android: the desktop shares Android's version line, so a release that carries both APKs
  and desktop installers serves both; a desktop-only release line would need its own repository or tag prefix.
- Release notes are plain selectable text (Android renders Markdown with `MarkdownRender`; the Markdown renderer is not on
  the desktop's classpath); the interactor's `\r` separators become line breaks.
- ~~A failed scheduled check waits the whole 3 days~~ (changed in the review fixes: a failed check is not recorded and
  retries after 10 minutes; Android's job waits for a network connection instead, which has no reliable JVM signal).
- Android's "What's new" / "What's coming" entries are not ported (release notes of the running version / a peek into
  the preview channel); without desktop releases they would show Android notes.
- No new strings (`MR`: `check_for_updates`, `update_check_notification_update_available`, `update_check_open`,
  `update_check_no_new_updates`, `action_not_now`, `version`, `unknown_error`; `SYMR`: `latest_`).
- Android behaviour unchanged (the only shared change is the defaulted constructor parameter in a KMK block).

**What the release pipeline still needs** (for the check to ever find a desktop update):
1. Publish the desktop installers as GitHub release assets: on a stable release attach the `build_desktop.yml` outputs to
   the `v<versionName>` release of `komikku-app/komikku` (preview: the `r<commit count>` release of
   `komikku-app/komikku-preview`, built with `-Pdesktop-preview`), with file names ending in `.msi` / `.exe`, `.dmg`,
   `.deb` / `.rpm` / `.AppImage` (the Linux app image is a directory today; it needs `appimagetool` or should be
   left out).
2. Build those installers with `-Penable-updater` (the workflow does not pass it yet).
3. Per-architecture assets need the architecture in the file name (`x64` / `amd64` / `x86_64`, `arm64` / `aarch64`;
   matched since the P6 review fixes), like Android's ABI suffixes; a name without one counts for every architecture.
4. Signing / notarization (separate parity step) so that the downloaded installers open without OS warnings.

**Tests:** `DesktopAppUpdateCheckerTest` (9; fake release source in GitHub's newest-first shape filtered through the
desktop installer selection, real interactor and desktop preference store): newer stable release (notes, release page,
installer link, dismiss), same / older version, Android-only and pre-releases skipped, preview channel (repository,
commit counts, no commit count = disabled), network failure (reported, no throttle, the next check retries), throttle vs
forced check, flag off / `dev` version (nothing contacted, default build has the flag off), scheduled task (3 days,
start-up check counts, throttled run, failure), installer per OS / OS detection / release URLs. `AppUpdateUiTest` (2,
headless, real DI with the checker replaced): About → "Check for updates" with no update (toast), failure (message), a
newer release (the window's dialog with the notes, "Not now"); "Open on GitHub" opens the release page and closes the
dialog.

Verification (after `6c6d0a5`, `--max-workers=2`): `spotlessApply`, `spotlessCheck`, `:app:compileDebugKotlin`,
`:app:verifyBorrowedSources`, `:desktopApp:compileKotlin` pass; `:desktopApp:test` 45 classes, 174 tests, 0 failures
(was 43 / 163 after the P5 review: `DesktopAppUpdateCheckerTest` 9, `AppUpdateUiTest` 2).

**Not verified:** a real request against `api.github.com` (no network calls in tests; the API shape is the one Android
already parses); the dialog in a real window; a build with `-Penable-updater` / `-Pdesktop-preview` was only compiled
through the property reading, not run.

### Review fixes (P6)

Review `/code-review medium 9f4a7ec^..ff465c0`, 8 findings (4 important, 1 pre-existing, 3 nits), all fixed in
`c0ad31b` (preview build / workflow), `3a9a712` (dialog), `921518b` (checker, config, About, `main`) and this docs commit.

1. **"Open on GitHub" closed the dialog although the browser did not open** (`AppUpdateDialog`): `DesktopBrowser.open`
   returns `false` without AWT browse support or when `browse` throws. The dialog now closes only on success; otherwise it
   stays and shows "Could not open the browser. Open this address yourself:" (new `KMR.strings.desktop_browser_open_failed`;
   no existing string said this) and the release page address as selectable text (`app-update-release-link`). Test:
   `AppUpdateUiTest` "when the browser cannot be opened …" (failed before: the dialog was gone).
2. **A preview build on a shallow clone compared release tags with a commit count of 1** (`desktopApp/build.gradle.kts`):
   `build_desktop.yml` checked out with the default depth. The checkout now has `fetch-depth: 0`, and with
   `-Pdesktop-preview` the build fails at configuration with a clear message when `git rev-parse --is-shallow-repository`
   says `true`, or the commit count is missing / not a number / 0 (`previewCommitCount()`). Checked by hand on this
   (shallow) clone: `./gradlew :desktopApp:help -Pdesktop-preview` fails with "-Pdesktop-preview needs the full Git
   history …"; without the flag nothing changes. No Gradle test (build logic has no test harness here).
3. **An offline start blocked automatic checks for 3 days** (`DesktopAppUpdateChecker`): `checkOnStart` and the task
   wrote `lastAppUpdateCheck` before the check, whatever its result. Now the checker writes it only after a check that got
   an answer (update or not, forced or not), never after a failure; the scheduled task maps a failure to
   `Outcome.Busy` (retry after its `retryDelay`, set to `FAILURE_RETRY_DELAY` = 10 minutes, like the other tasks' busy
   retry). Checks are serialized with a `Mutex`; an automatic check within 10 minutes of a failure of this process returns
   that failure without contacting GitHub, so the start-up check and the scheduler's catch-up right after it make one
   request, not two. Tests: "a network failure is reported, not recorded …", "an offline start does not block the
   scheduled check for days" (real `DesktopScheduler`: one request, retry not before 10 minutes, then the update), the
   reworked scheduled-task test (failure = `Busy`, last run unchanged).
4. **Installers were matched by OS only** (`DesktopUpdaterConfig.installerLink`): new `DesktopArch` (`X64`: `x64`,
   `amd64`, `x86_64`, `x86-64`; `Arm64`: `arm64`, `aarch64`) from `os.arch`, matched in asset names as separate words
   (between non-alphanumeric characters, ignoring case). An asset that states no architecture counts for any, one that
   states another architecture does not; an unknown JVM architecture (`null`) matches by OS only. **The Linux package
   format is not distinguished** (`.deb` / `.rpm` / `.AppImage`: the first matching asset of the release wins, as before;
   the link is only shown via the release page anyway). Test: "the installer matches the CPU architecture …".
5. **`isNewVersion` threw on an odd tag** (pre-existing, Android `GetApplicationRelease`): fixed on the desktop side
   without touching the domain. The desktop `ReleaseService` selector is now `DesktopUpdaterConfig.downloadLink`, which
   drops a release whose tag `isComparableTag` rejects (the interactor's own rule: digits and dots only; preview = an
   `Int`; stable = all parts numbers and at least as many as the app's version), so one odd tag is skipped instead of
   failing the whole check. Android is unchanged (it still throws on such a tag; out of scope). Test: "a release with a
   tag that cannot be compared is skipped …" (`desktop-v2`, `v1.15`, `nightly`, `r12.4`).
6. **The KDoc claimed a two-day throttle that only applied after a found update**: an up-to-date desktop asked GitHub at
   every start. Automatic checks (start-up and scheduled) now return the new `Check.Throttled` without contacting GitHub
   within `AUTOMATIC_CHECK_INTERVAL` (2 days, Android's value) of the desktop's last answered check
   (`desktop_last_app_update_check`, also the task's last run); a forced check (About) is never throttled. KDoc
   rewritten (class, `checkOnStart`, `appUpdateCheckTask`). Test: "an up-to-date app does not ask GitHub at every start".
7. **About showed "(r0)"** for a preview build without a commit count: the suffix is added only when the count is above 0.
   Not covered by a test (the About UI test builds a stable config; a preview build without a commit count cannot pass
   the Gradle check from item 2 anyway).
8. **The P3 scheduler comment headed the update check in `main`**: each comment is next to its statement now, and the
   update check's comment says it must run before `scheduler.start()`.

Tests after the fixes: `DesktopAppUpdateCheckerTest` 13 (was 9), `AppUpdateUiTest` 3 (was 2).

Verification (after `921518b`, `--max-workers=2 --console=plain`): `spotlessApply`, `spotlessCheck`,
`:app:compileDebugKotlin`, `:desktopApp:compileKotlin` pass; `:desktopApp:test` 45 classes, 179 tests, 0 failures (was
174). `:domain` untouched, so its tests were not run. Not verified: a real CI run of `build_desktop.yml` with
`fetch-depth: 0`, a full (non-shallow) clone building with `-Pdesktop-preview`.

## P7 — WebView and Cloudflare

Commits `2093b68` (browser abstraction, desktop interceptor, shared detection), `244325d` (site-wide cookies in browser
sessions), `d692d02` ("Open in WebView" and the cookie dialog), `8587728` / `6eb4b18` / `e3c18cd` (tests) and this
section's docs commit. Next in HANDOVER's order ("Left to do", item 4). **Outcome: no embedded browser in this step**; the desktop has a
browser-backend abstraction, a Cloudflare interceptor on it that reuses Android's detection, a manual "Open in WebView"
(system browser + pasted cookies and User-Agent) and a fake backend for tests. What an embedded backend still needs is
listed below.

**What Android does:** `CloudflareInterceptor` (a `WebViewInterceptor`, on every `NetworkHelper` client) detects a
challenge (403 / 503, `Server: cloudflare(-nginx)`, `challenge-error-title` / `-text` in the page), removes the old
`cf_clearance`, loads the page in a hidden `WebView` with the request's headers and User-Agent for up to 30 s until a new
`cf_clearance` appears in the shared `android.webkit.CookieManager` (the same store `AndroidCookieJar` reads), then sends
the request again; without a WebView it toasts "WebView is required" and returns the challenge page. `WebViewScreen` /
`WebViewActivity` (source home page from browse / feed / extensions, an entry's `getMangaUrl` from details) let the user
log in or pass a check by hand with the source's headers; the cookies land in the same store. The default User-Agent is
the `default_user_agent` preference (Settings → Advanced, "requires app restart"; sources build their `headers` once).
Extensions may use `android.webkit.WebView` directly (JavaScript evaluation, hidden logins) and read
`WebSettings.getDefaultUserAgent(context)`.

**Desktop before P7:** the `actual` `CloudflareInterceptor` passed every response through and logged a warning, so a
Cloudflare-protected source failed with `HTTP 403` (`HttpException`, or a parse error of the challenge page) and nothing
told the user what to do. No "Open in WebView" anywhere. The cookie jar was already shared (`AndroidCookieJar` over the
compat `DesktopCookieManager`, persisted in `<dataDir>/cookies.json`); the User-Agent was the same preference with
Android's default (a mobile Chrome string), with no desktop setting to change it. `android.webkit` had only
`CookieManager`, `ValueCallback` and `MimeTypeMap`.

### Embedded browser evaluation

Sizes and versions from Maven Central on 2026-11-24 (compressed jars as downloaded; unpacked sizes not measured).

| Option | Licence | Size per platform | OS / CPU | Maintenance | Packaging / fit | Verdict |
|--------|---------|-------------------|----------|-------------|-----------------|---------|
| JCEF via jcefmaven (`me.friwi:jcefmaven` 152.0.6, 2026-09-30) | wrapper Apache-2.0; JCEF / CEF BSD-3; Chromium BSD-3 plus its third-party licences | natives as separate artifacts: 132 MB (`jcef-natives-linux-amd64`), 118 MB (`macosx-arm64`), 138 MB (`windows-amd64`) for the build the metadata lists as `release` (Chromium 132) | Linux amd64 / arm64 / arm / i386, macOS amd64 / arm64, Windows amd64 / arm64 / i386 | active (follows CEF), one maintainer | natives either downloaded at first use (its `CefAppBuilder` from Maven Central into an install dir: fits "optional at run time") or bundled per OS (installer +120–140 MB). Needs JVM `--add-opens` / `--add-exports` for `java.desktop` internals, macOS helper app bundles that must be signed / notarized with the app (and hardened-runtime library validation relaxed for downloaded natives), and a heavyweight AWT component in Compose (`SwingPanel`, z-order problems with Compose popups) or off-screen rendering | best candidate for a later step; real Chromium, so the best Cloudflare compatibility of the embedded options |
| KCEF (`dev.datlag:kcef` 2025.03.23, last release 2025-03-23) | Apache-2.0 (over JetBrains' JCEF fork) | downloads a JCEF / CEF bundle from its GitHub releases at first start (same order of size as above) | Linux / macOS / Windows, x64 and arm64 | no release for 20 months | same JVM flags and macOS signing questions as jcefmaven; adds Ktor client dependencies; used by compose-webview-multiplatform and Suwayomi | not chosen: stalled maintenance, extra dependency tree, no advantage over jcefmaven for a hidden solver |
| JavaFX WebView (`org.openjfx:javafx-web` 21.0.x) | GPL-2.0 with Classpath Exception | `javafx-web-21.0.8-linux.jar` 43 MB, plus `javafx-graphics` / `-base` / `-controls` / `-media` | classifiers `linux`, `mac`, `mac-aarch64`, `win` only: no Linux arm64 and no Windows arm64 build of the web module | maintained (OpenJFX); the 21 line is the one for the bundled JDK 21 (newer JavaFX needs newer JDKs) | per-OS jars resolved on each OS's build runner (fits `build_desktop.yml`), `JFXPanel` inside `SwingPanel`; its cookies go through `java.net.CookieHandler` (could be bridged to the jar) | not chosen: WebKit, not Chromium, so Cloudflare's checks are less likely to pass; missing arm64 platforms |
| No embedded browser (system browser + manual cookies / User-Agent) | — | 0 | every OS | — | nothing to bundle | **chosen for P7**: the user's own browser passes the check (a real, up-to-date browser); costs a manual step and, for Cloudflare, the browser's User-Agent (or setting the browser's to the app's) |
| External solver (FlareSolverr, MIT; the user runs it) | MIT | none in the app | wherever the user runs it (Docker image) | active | an HTTP API (`POST /v1`, `request.get`) returning cookies and its User-Agent: a `BrowserBackend` over it is buildable and testable with `MockWebServer` only | not built in P7 (needs a setting for its address); the cheapest automatic backend to add next, the interceptor already retries with the solver's User-Agent |

Why no embedded backend now: it cannot be verified here (no display, no launch allowed in this task, so not even a
smoke test of the native loading); the 120–140 MB native download per platform and the macOS helper-bundle signing
belong with the signing / notarization step; the JVM flags and the jlink runtime would change for every user without a
run on any OS. **What the embedded backend still needs:** a `BrowserBackend` over jcefmaven (`availability()` =
installed natives for this OS / CPU and a display, `NOT_INSTALLED` before the download); a download / install flow with
progress and a "not installed" state in settings (natives under `<dataDir>/jcef`, checksum check); the JVM flags in
`desktopApp/build.gradle.kts` (`jvmArgs`, also for `run`) and a check of the jlink module list; a hidden window or
off-screen browser for the interceptor and a visible one for "Open in WebView" (the cookie hand-over and conditions
are already defined by `BrowserSession` / `BrowserCondition`); macOS helper bundles and entitlements in the signed DMG;
CI must not download it (tests keep the fake backend); BUILDING.md: where the natives come from and their licences.

### Built

- **Shared detection** (`core/common/src/main/…/network/interceptor/CloudflareChallenge.kt`, new KMK file): Android's
  `shouldIntercept` body and `WebViewInterceptor.parseHeaders`' safe-header filter moved there unchanged; Android's
  `CloudflareInterceptor.shouldIntercept` / `parseHeaders` delegate to it (KMK-marked). Android behaviour unchanged.
- **Browser abstraction** (`core/common/src/desktopMain/kotlin/app/komikku/network/browser/BrowserBackend.kt`):
  `BrowserBackend` (`availability()`, blocking `run(BrowserSession)`), `BrowserSession` (URL, browser-safe headers,
  User-Agent, the jar's cookies to start with, a `BrowserCondition`, timeout, interactive or hidden),
  `BrowserCondition` (`cookieSet(url, name, notValue)`, `urlReached`, `UntilClosed`), `BrowserSessionResult` (condition
  met / timed out / closed, final URL, the browser's cookies, the User-Agent it really sent), `NoBrowserBackend` and the
  `BrowserBackends.current` registry (default: none). Cookie helpers (`BrowserCookies.kt`): `storeBrowserCookies` (each
  cookie under its own domain and path), `browserCookies` / `removeBrowserCookies` through the compat
  `DesktopCookieManager.cookiesFor` / `removeFor` (new: full cookies, removal whatever the domain), because
  `AndroidCookieJar.remove` only reaches a host-only cookie of the address's own path.
- **Desktop `CloudflareInterceptor`** (`actual`, same constructor as before plus an internal one with a backend provider
  and timeout): Android's detection; no backend → `CloudflareBypassException` (`IOException`, reason `NO_BROWSER`,
  `KMR.strings.desktop_cloudflare_no_browser`: what to do); otherwise the old `cf_clearance` is removed, the backend runs
  with the request's headers and User-Agent until a new `cf_clearance` for the URL (not the refused one) or 30 s, its
  cookies go into the jar and the request is sent once more (that response is returned as is); not solved / timed out /
  backend failure → `CloudflareBypassException` (`NOT_SOLVED` / `TIMED_OUT` / `BROWSER_FAILED`,
  `KMR.strings.desktop_cloudflare_failed`). `NetworkHelper` is unchanged (it builds the interceptor through the `expect`
  constructor).
- **"Open in WebView"** (`desktopApp/…/webview/DesktopWebView.kt`, Injekt singleton): with an available backend an
  interactive session (source headers and User-Agent, until closed, 30 min) whose cookies are stored; otherwise (today
  always) the system browser opens the page and a dialog (`WebViewDialog.kt`) takes pasted cookies (a `Cookie` header,
  `Set-Cookie`-like lines or developer-tools table rows, `ManualCookies`) and the User-Agent (pre-filled with the
  source's), with "Open in browser" again, "Clear cookies" (this site) and the address as selectable text when no
  browser opened. Entry points: the catalogue's top bar (Android's `BrowseSourceScreen` WebView action, source home
  page) and its error screen (Android's retry / WebView actions), and the details top bar (`getMangaUrl`).

### Decisions / deviations

- Without a backend a challenge **fails with an actionable error** instead of returning the challenge page (Android
  returns it after a toast): on desktop that is the normal case, and the message says what to do.
- One challenge per host at a time: a request that waited is sent again with the cookie the other one got (Android
  opens one WebView per request).
- When the backend reports another User-Agent than the request's, the retry uses the backend's (the clearance is bound
  to it). The next requests of that source still send the source's own User-Agent and may be challenged again; an
  embedded backend should be given (and honour) the request's User-Agent, as `BrowserSession.userAgent` asks.
- Pasted cookies are stored for the site's registrable domain (`www.example.com` → `example.com`, where Cloudflare sets
  `cf_clearance`; host-only for IPs / hosts without a public suffix), path `/`, `Secure` on `https`, for one year (the
  browser does not say; the site's next `Set-Cookie` replaces them), replacing stored cookies of the same names.
- A pasted User-Agent is saved to Android's `default_user_agent` preference (Android's check: not blank, a valid header
  value). Requests without their own User-Agent use it at once (`UserAgentInterceptor` reads it per request), but
  extension sources build their `headers` once, so it applies to them after a restart: the dialog shows Android's
  "Requires app restart" then. Alternative offered in the help text: set the browser's User-Agent to the app's (shown
  in the field) before passing the check, which needs no restart.
- Removing cookies on desktop (`removeBrowserCookies`, "Clear cookies") removes every cookie sent to the URL whatever its
  domain / path; Android's `AndroidCookieJar.remove` is unchanged.
- No desktop Settings → Advanced: no global "Clear cookies", "Clear WebView data", User-Agent edit / reset there (the
  dialog edits the User-Agent; "Clear cookies" is per site).
- The progress dialog of an embedded session cannot be cancelled (no backend exists yet to cancel).

### `android.webkit.WebView` used by extensions

Today: the compat layer has no `WebView`, `WebViewClient`, `WebChromeClient`, `WebSettings`, `WebResourceRequest` /
`WebResourceResponse` or `JavascriptInterface`. Class resolution is lazy, so an extension that references them loads
and its other code works; the first call of a method that touches them (or loading a class that extends
`WebViewClient`) throws `NoClassDefFoundError`, which the source manager and the screens treat like any source failure
(P4 review guards; `DelegatedSourcesTest` models exactly this error). `:desktop:extension-runtime:linkCheck` lists such
symbols per APK. What would be needed: compat classes for that surface (`loadUrl` with headers, `loadDataWithBaseURL`,
`evaluateJavascript`, `addJavascriptInterface`, the client callbacks, `settings.userAgentString`,
`WebSettings.getDefaultUserAgent`) mapped onto an embedded `BrowserBackend` that can also evaluate JavaScript and call
back into the JVM, with Android's main-thread semantics (`Handler` / `Looper` exist in compat). Without an embedded
browser the honest behaviour is a clear "needs WebView" error. Not attempted here. The smallest useful piece would be a
static `WebSettings.getDefaultUserAgent(Context)` returning the app's default User-Agent (not added).

### Tests

- `DesktopCloudflareInterceptorTest` (11, `MockWebServer` + `FakeBrowserBackend`): challenge → backend → `cf_clearance`
  stored → retried with the cookie and the same User-Agent → 200 (session headers, no `User-Agent` header in them,
  timeout); `NetworkHelper.client` uses `BrowserBackends.current`; no backend → `NO_BROWSER` with the host and "Open in
  WebView" in the message, backend not run, one request; the desktop default (no backend); timeout → `TIMED_OUT`, no
  retry, the browser's other cookies kept; closed → `NOT_SOLVED`, throwing backend → `BROWSER_FAILED`; a stale site-wide
  clearance removed before the session and not accepted as solved, then a fresh one works; retry with the browser's
  User-Agent; a non-challenge 403 (Cloudflare without markers, markers from another server) and a 429 untouched;
  three parallel challenges of one host → one session; the conditions.
- `DesktopWebViewTest` (7): no backend → system browser, the source's User-Agent, stored cookie names, invalid URL; the
  manual flow end to end (challenge → `NO_BROWSER` → paste `Cookie: cf_clearance=…; theme=…` and the browser's
  User-Agent → the next request carries both and passes; pasting again replaces); invalid cookie / blank / invalid
  User-Agent store nothing; "Clear cookies" removes site-wide cookies and keeps other sites'; an available backend runs
  an interactive session with the source's headers and its cookies are stored; unavailable / failing backend → manual;
  `ManualCookies` forms and the stored cookie attributes.
- `WebViewUiTest` (1, headless window): catalogue of a protected `HttpSource` → the interceptor's message with retry /
  WebView actions → "Open in WebView" (browser cannot open: address shown) → invalid paste shows the reason → valid
  `cf_clearance` saved (snackbar) → Retry loads the listing.
- `DesktopNetworkTest`: the Cloudflare case now expects the `NO_BROWSER` error (through `await`'s wrapping) instead of
  the pass-through 403.

New strings (KMR, base only): `desktop_cloudflare_no_browser`, `desktop_cloudflare_failed`,
`desktop_webview_manual_title`, `desktop_webview_manual_help`, `desktop_webview_cookies_label`,
`desktop_webview_stored_cookies`, `desktop_webview_cookie_invalid`, `desktop_webview_cookies_saved`; reused MR:
`action_open_in_web_view`, `action_open_in_browser`, `pref_clear_cookies`, `cookies_cleared`, `pref_user_agent_string`,
`error_user_agent_string_blank` / `_invalid`, `requires_app_restart`, `action_retry`, `action_save`, `action_cancel`,
`loading`. `core:common`'s desktop source set now depends on `:i18n-kmk` (Android's does not).

**Known gaps:** no embedded browser (above); automatic flows (library updates, global search, downloads) hitting a
challenge fail with the message and need the manual step; the User-Agent change needs a restart for extension sources;
no Settings → Advanced network group; extensions using `android.webkit.WebView` fail at that call; not run in a real
window or against a real Cloudflare site (the cookie / User-Agent binding to the IP and browser is Cloudflare's, so a
pasted clearance may still be refused, e.g. behind another IP or after it expires).

The section's first draft went into `e3c18cd` by mistake (a `test(kmp)` commit that also carries
`PROGRESS.md`); the docs commit after it completes it.

Verification (after the cookie-manager test commit, `--max-workers=2 --console=plain`, `./gradlew --stop` between heavy
runs): `spotlessApply`, `spotlessCheck`, `:app:compileDebugKotlin` (with `:core:common:compileDebugKotlinAndroid`),
`:app:verifyBorrowedSources`, `:desktopApp:compileKotlin` pass; `:desktopApp:test` 48 classes, 198 tests, 0 failures
(was 45 / 179 after the P6 review: `DesktopCloudflareInterceptorTest` 11, `DesktopWebViewTest` 7, `WebViewUiTest` 1;
`DesktopNetworkTest` changed, still 2); `:desktop:android-compat:test` 33 tests, 0 failures (`CookieManagerTest` +1);
`:core:common:desktopTest` 3 classes, 9 tests, 0 failures (unchanged). Not run: a real window, a real Cloudflare site,
any embedded browser, the Android app (compile only; Android's interceptor code is moved, not changed).

### Review fixes (P7)

Review `/code-review medium 2093b68^..efae9ab`, 10 findings (8 important, 2 nits), all fixed in `ed37378` (interceptor:
1, 2, 4), `fc72df6` (cookies, User-Agent, dialog, details: 3, 5–10) and the commit with this section (a follow-up to
4 for redirects, below, and these notes). New tests were written with the
fixes (they use the new API, so they were not run against the old code); each targets the defect as described.

1. **A failed solve was repeated by every request queued on the host's lock** (`CloudflareInterceptor`, up to 30 s
   each). New `CloudflareHostState` (`core:common` desktop, `app.komikku.network.browser`; shared by all interceptors
   and "Open in WebView" through `CloudflareHosts.state`): a failed solve (`NOT_SOLVED` / `TIMED_OUT` / `BROWSER_FAILED`)
   is remembered per host for `FAILURE_MEMORY` = 45 s (injectable clock), bound to the clearance the jar held after it.
   Waiters and requests right after it fail at once with a new `CloudflareBypassException` of the same reason and
   message (the original as cause). It ends after the 45 s, when a new `cf_clearance` is in the jar, after a successful
   solve, or by `clearFailure` (a manual save or "Clear cookies" in the dialog). `NO_BROWSER` is not remembered (it is
   immediate anyway). Test: "a failed solve is not repeated by the requests behind it and right after it" (3 parallel
   requests → 1 browser run, a follower fails without a run, cleared / new clearance / expiry each allow the next run).
2. **Waiters and later requests used their original User-Agent** although the solver switched to its own. The state
   keeps a User-Agent override per host, bound to a SHA-256 hash of the clearance it came with (no cookie values are
   stored next to it). The interceptor applies it before every request to that host while that clearance is in the jar
   (also when a source sets its own `User-Agent` header), on the waiter path and to the retry; another host whose
   current clearance is the same (a site-wide `cf_clearance`) uses it too. A solve with the source's own User-Agent
   removes the override. **Persisted** (not only in memory): the desktop app keeps the overrides in the
   `desktop_cloudflare_user_agents` preference (`DesktopCloudflare.installHostState`, run when `NetworkHelper` is
   created, in the app module and in `DesktopServices`), so they live as long as the cookies in `cookies.json`; the
   `core:common` default and the tests keep them in memory. Test: "the solver's user agent is used for the waiters and
   the host's later requests".
3. **Pasted cookies were domain cookies on the registrable domain**, so a later host-only `Set-Cookie` of the site became
   a second entry and the stale pasted one could be sent first. `ManualCookies.toCookies` now stores pasted cookies
   host-only for the entered host; only Cloudflare's own (`cf_*`, `__cf*`, `_cf*`: `cf_clearance`, `__cf_bm`, `_cfuvid`)
   go to the registrable domain (host-only for IPs / hosts without a public suffix, as before). Pasted cookies are stored
   through the new `storePastedCookies` → `DesktopCookieManager.setPastedCookies`, which marks them (persisted as
   `"pasted": true` in `cookies.json`); when the site later sets a cookie of the same name for an address the pasted one
   is sent to (parent domain, or host-only vs. domain on the same host), the pasted one is removed. Ordinary cookies of
   the same name on different domains are left alone (RFC 6265). The `LIFETIME` comment says this now. Tests:
   `CookieManagerTest` "a cookie the site sets replaces a pasted one …", `DesktopWebViewTest` "a pasted cookie gives way to
   the one the site sets later", the reworked `toCookies` checks.
4. **The clearance was compared for the original URL, not the challenged one** (after redirects). The interceptor now
   takes the challenged request from `response.request` (the last hop): its host is locked, its jar clearance compared
   with the one the network request sent, its cookies removed and the browser session runs for that URL; the retry is
   the original request (OkHttp follows the redirect again with the new cookie). Test: "a challenge on a redirect target
   is solved for that address" (localhost → 127.0.0.1, the first host holding a clearance of its own: before, no
   browser run and the 403 was returned).
5. **One cookie value with a comma, backslash or inner quote rejected the whole paste.** Values may now hold anything a
   `Cookie` header carries except `;`, control characters and non-ASCII (OkHttp refuses those in header values, which
   would fail every request to the site). `ManualCookies.parse` returns `Parsed(pairs, skipped)`: an unusable entry
   (no `=`, a bad name, a bad value) is skipped by name (entries without `=` as text, cut at 40 characters; values are
   never echoed), the rest is stored, and the dialog shows "Skipped cookies that can't be used: …"
   (`KMR.strings.desktop_webview_cookies_skipped`); only a paste with nothing usable is refused (`InvalidCookie` lists
   what was skipped; `desktop_webview_cookie_invalid` reworded to "Not a usable name=value cookie"). Tests: "unusable
   entries are skipped …", the `ManualCookies` forms.
6. **The pasted User-Agent only went to `default_user_agent`**, so sources with their own User-Agent header ignored it
   until a restart. `saveManual` stores it as the host's override (finding 2's mechanism, persisted), bound to the
   pasted clearance (or the one already in the jar; with no clearance at all it applies to the host until "Clear
   cookies" or a save with the source's own User-Agent), so the next request of that source to that host sends it at
   once. The dialog's field shows what is sent now (`ManualEntry.userAgent`; the source's own in `sourceUserAgent`).
   The default preference is written only when the new "Also use this user agent as the default for all sources"
   checkbox (`desktop_webview_user_agent_default`, default off) is ticked; only then "Requires app restart" is shown. The
   help text says the app sends the pasted User-Agent to the site at once. "Clear cookies" removes the override too.
   Test: "a pasted user agent is sent at once to a source with its own user agent header" (no restart, default
   untouched, preference without the cookie value, the dialog shows it, cleared with the cookies).
7. **Details cached a null address and called the extension during composition.** The WebView button shows whenever an
   entry is loaded (it always has a source id); on click `WebViewLauncher.open(sourceId) { model.mangaUrl() }` resolves
   the address on an IO thread (inside the cancellable progress state), so a source that loads later works; no address
   → "This entry has no web address …" (`desktop_webview_no_address`). Test: `WebViewLauncherTest` "the address is
   resolved off the calling thread …".
8. **A throwing `webView.open` left the modal progress dialog up forever.** The launcher catches `Throwable` (cancellation
   rethrown), resets to `Idle` and shows "Could not open <host>: <message>" (`desktop_webview_failed`). The progress dialog
   has a Cancel button and is dismissable: `cancel()` resets the state and cancels the job; the session runs in
   `runInterruptible(Dispatchers.IO)`, so the blocking backend call is interrupted (a backend must stop on interrupt).
   Tests: "a failing open ends the progress state with an error", "a running session can be cancelled".
9. **"Cookies saved" after any embedded session**: now only when the session returned cookies; a timeout without cookies
   says so (`desktop_webview_timed_out`), otherwise "The browser returned no cookies" (`desktop_webview_no_cookies`).
   Test: "an embedded session reports saved cookies, a timeout or nothing".
10. **A blank User-Agent field blocked saving cookies**: blank now keeps the current User-Agent (`SaveResult.UserAgentBlank`
    removed; Android's blank-check string is no longer used here).

Decisions: the failure memory is 45 s (middle of the asked 30–60 s); overrides are persisted rather than in memory only
(the clearances they belong to persist too); the override is keyed by host with a clearance-hash fallback rather than by
cookie domain. Left: a request to host A that redirects to a protected host B goes out to B with the source's User-Agent when A has no
override of its own (an application interceptor cannot change redirect hops); B's challenge is then answered by
resending with B's override when B's clearance is in the jar (one extra round trip, no browser run; test "a clearance
reached through a redirect is resent with its user agent instead of solved again").

Tests after the fixes: `DesktopCloudflareInterceptorTest` 15 (was 11), `DesktopWebViewTest` 10 (was 7), new
`WebViewLauncherTest` 5, `WebViewUiTest` 1 (unchanged, still passes with the new dialog), `CookieManagerTest` 7 (was 6).

Verification (`--max-workers=2 --console=plain`, `./gradlew --stop` between heavy runs): after `fc72df6`:
`spotlessApply`, `spotlessCheck`, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources`, `:desktopApp:compileKotlin`
pass; `:desktopApp:test` 49 classes, 209 tests, 0 failures (was 48 / 198); `:core:common:desktopTest` 3 classes, 9 tests,
0 failures; `:desktop:android-compat:test` 7 classes, 34 tests, 0 failures. After the redirect follow-up in this docs
commit: `spotlessCheck` and the `app.komikku.desktop.webview.*` tests (4 classes, 31 tests) pass. Not run: a real window,
a real Cloudflare site, the Android app (compile only; nothing Android changed).

## P8 — automatic Cloudflare solving through a FlareSolverr-compatible service

Commits `1be55e8` (backend, settings, wiring), `29f0fef` (tests) and `5fd575b` (this section). Next in HANDOVER's order
after the P7 review fixes. **Outcome:** with the address of a FlareSolverr-compatible service the user runs (Settings →
Browse → "Cloudflare solver (FlareSolverr)"), the desktop's `CloudflareInterceptor` gets past challenges without the
manual step, in every flow that uses `NetworkHelper.client` (browse, library updates, global search, downloads); empty
address (the default) = the P7 manual flow, unchanged. No embedded browser.

**Preference key:** none existed for this (checked: no `flaresolverr` key or string in Android / Mihon / SY code or
resources here), so it is a desktop preference, `desktop_flaresolverr_url` (`DesktopCloudflarePreferences`), default
empty.

### Built

- **`FlareSolverrBackend`** (`desktopApp/…/webview/FlareSolverrBackend.kt`, a `BrowserBackend`): one
  `POST <address>/v1 {"cmd": "request.get", "url", "maxTimeout", "cookies": [{name, value, domain, path}]}` per session
  (the jar's cookies for the page; FlareSolverr v2+ takes no headers / User-Agent, so the source's are not sent). The
  answer's `solution` becomes the session result: cookies with their domain (Chrome's leading dot = domain cookie, else
  host-only), path, expiry (`expires` in seconds, -1 = session cookie), secure / HttpOnly; `userAgent`; the final `url`
  and `status`; the outcome comes from testing the session's condition (a new `cf_clearance` for the URL) against that
  page, because the service answers `ok` also when the page had no challenge (not met → `CLOSED` → the interceptor's
  `NOT_SOLVED`). The interceptor then stores the cookies, keeps the solver's User-Agent as the host's override (P7 review
  finding 2) and retries.
  - **Timeouts:** `maxTimeout` = the session's timeout, at least `MIN_TIMEOUT` = 60 s (FlareSolverr's default; a solve
    takes longer than the 30 s WebView wait Android uses); the HTTP call gets that plus `ANSWER_MARGIN` = 15 s. The
    service's own timeout answer (`status` not `ok` with "timeout" in the message) and no answer in time
    (`InterruptedIOException` without an interrupt) are `TIMED_OUT`; an interrupt (cancelled dialog) is rethrown.
  - **Unavailability, clearly:** empty address → `Unavailable(NOT_INSTALLED)` (the interceptor's `NO_BROWSER` message:
    pass the check by hand); a non-`http(s)` address → `Unavailable(NOT_SUPPORTED)` (same message; the settings refuse
    to save it); unreachable → `BrowserUnavailableException("FlareSolverr is unreachable at <scheme://host:port>: …")`;
    an error answer → "FlareSolverr error (HTTP n): <its message>"; an unreadable answer / one without a solution →
    their own messages. The interceptor now quotes a `BrowserUnavailableException`'s message in its error
    (`KMR.strings.desktop_cloudflare_solver_failed`: "Failed to bypass Cloudflare on <host>: <reason>. Try again, or use
    “Open in WebView” …"), so the user sees why.
  - **Addresses:** `http(s)` only; the base address (`http://localhost:8191`, with or without `/`) gets `/v1` appended,
    an address ending in `/v1` is used as is, query / fragment dropped (`apiUrl`). User info in the address is sent as
    basic authentication (a solver behind a proxy) and never shown or logged: logs and messages carry
    `scheme://host:port` only; request URLs are logged by host only.
  - Its own `OkHttpClient` (10 s connect timeout), not the app's: the app's client has the Cloudflare interceptor and
    the DoH setting, and a solver is usually on the local network.
  - `showsPages = false` (new `BrowserBackend.showsPages`, default `true`): "Open in WebView" does not use it and keeps
    the system browser + pasted cookies dialog as the fallback.
  - `testConnection()`: `GET` of the base address; `Ok(version)` when the answer is JSON with `msg` / `version`
    (FlareSolverr: `{"msg": "FlareSolverr is ready!", "version": …}`), else `Failed("HTTP n from …")`, `Unreachable`,
    `NotConfigured`, `InvalidAddress`.
- **Wiring** (`DesktopCloudflare.installBackend`, Injekt singleton `FlareSolverrBackend`): created with the
  `NetworkHelper` and set as `BrowserBackends.current`; it reads the address per challenge, so a change in the settings
  applies at once (no restart). `DesktopServices` (tests) does not install it.
- **Settings** (Settings → Browse, new group "Cloudflare solver (FlareSolverr)"): the address (`EditTextPreference`,
  "Off" when empty; an address that is not `http(s)` is refused with "Not an http(s) address"), "Test connection"
  (progress indicator, result as a snackbar: connected + version / not set / not http(s) / unreachable + reason / no
  FlareSolverr answer + HTTP status), and the explanation with the privacy note: pages of protected sites are then
  loaded by that service, which sees their addresses and the cookies sent with them; "Open in WebView" stays available.
  Browse was chosen because the desktop has no Settings → Advanced (P7 decision); Android's "Networking" group lives
  there.

### Decisions / deviations

- **A desktop preference**, not a shared key (none exists); not in backups (Android's backup would not restore it).
- **The source's headers and User-Agent are not passed** (FlareSolverr v2+ dropped `headers` / `userAgent`); the
  clearance is bound to the solver's User-Agent, which the interceptor then sends to that host (P7 review finding 2).
  Byparr and other compatible services: only the `request.get` call and the `solution` fields above are relied on.
- **No `session` / `proxy` parameters** (FlareSolverr's browser sessions and proxies): one solve per challenge.
- **One solve per host at a time and the 45 s failure memory** (P7 review) apply as with any backend: a down solver
  fails every queued request at once instead of each waiting for the connect timeout.
- The 60 s minimum solve time means a request can now block up to ~75 s on a hard challenge, inside the app client's
  120 s call timeout; acceptable for background work, and the UI shows its normal loading state meanwhile.

### Tests

- `FlareSolverrBackendTest` (9, one `MockWebServer` as the protected site, which wants the clearance and the solver's
  User-Agent, one as the solver): solved → `POST /v1` with `request.get`, the URL and `maxTimeout` → cookies and
  User-Agent stored → the retry passes, and the host's next request goes through with the solver's User-Agent without a
  second solve; the jar's cookies are sent to the solver, domain / session cookies mapped, the 60 s minimum; an error
  answer → `BROWSER_FAILED` quoting the solver's message; unreachable (closed port, credentials in the address) →
  `BROWSER_FAILED` "unreachable", no credentials in the message; the solver's timeout answer and an answer that does not
  come in time → `TIMED_OUT` (bounded wait); empty / `ftp:` address → `NO_BROWSER` (P7 manual error), solver not called;
  the app wiring (`installBackend`: the setting turns it on at once, `NetworkHelper.client` passes) and "Open in WebView"
  staying manual with it; address normalisation and validation; `testConnection` (ready, 404, not set, invalid,
  unreachable) and its messages.
- `FlareSolverrSettingsUiTest` (1, headless window): the app's backend is the FlareSolverr one; Settings → Browse shows
  the group; "Test connection" says no address is set, then (address set to the stand-in) "Connected to FlareSolverr
  3.3.21".

New strings (KMR, base only): `desktop_cloudflare_solver_failed`, `desktop_flaresolverr_title`, `desktop_flaresolverr_url`,
`desktop_flaresolverr_info`, `desktop_flaresolverr_test`, `desktop_flaresolverr_ok`, `desktop_flaresolverr_not_configured`,
`desktop_flaresolverr_invalid`, `desktop_flaresolverr_unreachable`, `desktop_flaresolverr_failed`; reused MR: `off`.

**Known gaps:** not run against a real FlareSolverr / Byparr or a real Cloudflare site (only the documented API shape,
FlareSolverr 3.x); no per-source opt-out (every challenged host goes to the solver once it is set); the solver's
`response` HTML is not used (the request is always sent again); no Settings → Advanced network group; extensions that use
`android.webkit.WebView` themselves still fail at that call (P7).

Verification (after `29f0fef`, `--max-workers=2 --console=plain`, `./gradlew --stop` between heavy runs):
`spotlessApply`, `spotlessCheck`, `:app:compileDebugKotlin`, `:app:verifyBorrowedSources`, `:desktopApp:compileKotlin`
pass; `:desktopApp:test` 51 classes, 220 tests, 0 failures (was 49 / 209 after `fc72df6`: +1 interceptor test in
`6c8bc49`, `FlareSolverrBackendTest` 9, `FlareSolverrSettingsUiTest` 1); `:core:common:desktopTest` 3 classes, 9 tests,
0 failures; `:desktop:android-compat:test` 7 classes, 34 tests, 0 failures. Not run: a real FlareSolverr / Byparr, a
real Cloudflare site, a real window, the Android app (compile only; nothing Android changed).

### Review fixes (P8)

Review `/code-review medium 6c8bc49..65ccc46` (the P8 range), 8 findings (7 important, 1 nit), all fixed in `90ce3b8`
(backend, interceptor, strings, tests), `26c5bec` (settings) and the commits after it (settings "Disable", this docs
commit).

1. **FlareSolverr v3 cookies lost their expiry** (`FlareSolverrBackend.toCookie`): only `expires` was read, while
   FlareSolverr v3 (Selenium's cookie shape) says `expiry`, so every cookie became a session cookie and the clearance was
   gone after a restart. Now `expires` or `expiry` (the first positive one), seconds as an integer or a fraction; with
   neither (or ≤ 0) the cookie stays a session cookie. Test: "cookie expiry is read from expires or expiry …".
2. **A connect timeout counted as a timed-out solve**: OkHttp's connect timeout is a `SocketTimeoutException`
   (an `InterruptedIOException`). The call now has an `EventListener` that notes `connectionAcquired`: an
   `InterruptedIOException` before a connection (and every other `IOException`) is `UNREACHABLE`; after it (the read /
   call timeout while the service solves) it stays `TIMED_OUT`, like the service's own timeout answer. A real thread
   interrupt keeps its P8 handling (rethrown, no result) and now also cancels the call (item 4). Test: "a connect
   timeout is an unreachable solver …" (a socket factory whose sockets time out while connecting; the cause is the
   `SocketTimeoutException`).
3. **A failed solve was replayed for 45 s after the solver address was corrected** (`CloudflareInterceptor`,
   `DesktopCloudflare`): failures of the backend itself (any `BrowserUnavailableException` as the cause: not configured,
   invalid, unreachable, an unreadable answer, an error answer of the service, an answer without a solution) are no
   longer recorded in the per-host failure memory; timed-out and not-solved solves still are. And the backend reports a
   changed address (`onEndpointChanged`, seen at its next `availability()`, which the interceptor asks before it looks
   at a remembered failure); the app's wiring then calls the new `CloudflareHostState.clearFailures()`. **Decision:** a
   lazy check of the address the backend reads anyway instead of a preference listener (no scope to own, no ordering
   question; the memory is only consulted after that read). Test: "a solver that cannot be used is not remembered, and
   another address forgets the failed solves".
4. **A cancelled request left the solver call running (up to 75 s) and the host lock held**: `BrowserSession` has a new
   defaulted `isCancelled` check; the interceptor passes `chain.call().isCanceled()` and checks it once more after it got
   the host lock (a cancelled waiter starts no solve). The backend refuses a session that is already cancelled and, while
   its blocking call runs, a shared daemon watcher polls every 200 ms and cancels the HTTP call when the session was
   cancelled or the calling thread interrupted (not `call.isCanceled()` afterwards: OkHttp's own call timeout cancels
   too). The result is an `InterruptedIOException`; the interceptor turns a failure of a cancelled call into a plain
   `IOException("Canceled")`, which is not a `CloudflareBypassException` and is not remembered. Test: "a cancelled request
   cancels the solver call and frees the host" (ends within 3 s of the cancel instead of the 10 s answer delay, nothing
   remembered, the next request is solved at once; a pre-cancelled session sends nothing).
5. **`…/v1/` was accepted but never worked** (`apiUrl` kept the trailing empty segment): `apiUrl` / `baseUrl` now
   rebuild the path from the non-empty segments (encoded), `v1` appended unless last; the base address is the
   directory (`http://host:8191/`, `https://host/solver/`). Test: the address test with `…:8191`, `…:8191/`, `…/v1`,
   `…/v1/`, `//v1//`, sub-paths, an encoded segment.
6. **`.jsonObject` / `.jsonArray` / `.jsonPrimitive` threw on `null` or another type** outside any `try` (`run`,
   `testConnection`), and the settings' "Test connection" launch had only `try` / `finally`: every field is read with a
   safe cast now (a field of another type, or `null`, counts as missing; a missing / `null` cookie list is empty; a
   non-object answer is unreadable), and the settings catch any failure of the test (except cancellation) and show it as
   "No FlareSolverr answer: …". Test: "answers with null or other types are read safely" (`run` and `testConnection`).
7. **Hard-coded English in the exception messages, doubled final period** (nit): the new `FlareSolverrException`
   (a `BrowserUnavailableException`, now `open`) carries a `Reason` (`NOT_CONFIGURED`, `INVALID_ADDRESS`, `UNREACHABLE`,
   `UNREADABLE_ANSWER`, `ERROR_ANSWER`, `NO_SOLUTION`), the HTTP code and the service's own message / the network error;
   its text comes from `KMR` (reused `desktop_flaresolverr_not_configured`, `_invalid`, `_unreachable`; new
   `desktop_flaresolverr_error_answer`, `_unreadable_answer`, `_no_solution`, base locale only). The interceptor drops a
   final period of the quoted reason before `desktop_cloudflare_solver_failed` goes on with ". Try again". The log-only
   availability details stay English. Test: the error-answer test checks reason, code, detail, the string and no `..`.
8. **The address subtitle showed `user:password@` in clear**: the settings now edit the address through
   `MaskedAddressPreference` over the stored one (`FlareSolverrAddress.mask` / `unmask`, plain text work on the address
   as typed): the subtitle **and the edit field** show `user:••••@…`; saving an address that still holds the mask keeps
   the stored password (also with another host / path), typing over the mask replaces it, an address without user info
   is saved as typed. **Decision:** mask in the field instead of a password input (the shared `EditTextPreferenceWidget`
   is Android's and has no password mode; the username, host and path stay readable and editable); the subtitle shows the
   masked address rather than `safeName()` so that the path and the presence of credentials stay visible. The subtitle's
   `%` is escaped (the widget runs it through `String.format`, which threw on an address with `%2F`). Tests:
   "the settings never render the address' password" (unit) and `FlareSolverrSettingsUiTest` (subtitle and edit field
   masked, no "secret" anywhere, "Test connection" still sends the credentials, an address with `%2F`).

Found in the fix round, fixed: the shared edit dialog cannot save an empty value (its confirm button is disabled for
blank text), so the solver could not be turned off again from the settings; a "Disable" entry (`MR.strings.action_disable`,
enabled while an address is set) empties it. Covered by `FlareSolverrSettingsUiTest`.

Tests after the fixes: `FlareSolverrBackendTest` 15 (was 9), `FlareSolverrSettingsUiTest` 1 (extended),
`DesktopCloudflareInterceptorTest` 15 unchanged and passing.

Verification (`--max-workers=2 --console=plain`, `./gradlew --stop` between heavy runs): `spotlessApply`,
`spotlessCheck`, `:app:compileDebugKotlin`, `:desktopApp:compileKotlin` pass; `:desktopApp:test` 51 classes, 226 tests,
0 failures (was 220: `FlareSolverrBackendTest` +6); `:core:common:desktopTest` 3 classes, 9 tests, 0 failures. Not run:
a real FlareSolverr / Byparr (in particular the v3 `expiry` shape is taken from Selenium's cookie format, not from a
captured answer), a real window.

## Remaining parity gaps

What Android has and the desktop app does not (yet), roughly by user impact:

1. **Browse:** source feeds / saved searches UI, migration (out of scope so far). Global search, source / language
   toggles and pins are done (P1); left from them: bulk favourites from global search, "search globally" from the
   library search, SY source categories and data-saver exclusion, the extension-name suffix in global search rows.
   SY delegated sources (Pururin, MangaDex, 8Muses, NHentai, LANraragi) are done (P4); left from them: batch add / URL
   import screen, MangaDex settings / follows / similar screens, SY built-in E-Hentai / ExHentai and merged sources.
2. **Library:** grouping and selection / batch actions are done (P2); left from them: migrate, merge,
   recommendations, SY clean titles / MangaDex follow / reset info from the selection. Also: "continue reading" button, source-icon badge and language flag
   icons, random-entry shortcut. Scheduled automatic library updates are done (P3; no device restrictions on desktop).
3. **Manga details:** duplicate detection on add, merged entries, metadata editing, cover actions, sharing,
   recommendations, SY metadata / namespace-tag view and page previews (the delegated sources store the metadata, P4).
4. **Reader:** neighbouring chapters appended in viewers, animated GIF / WebP playback, AVIF / JPEG XL / HEIF
   pages and covers (P5: no acceptable decoder, shown as an unsupported format; blocker in "P5"), automatic wide-page detection for double pages, tap-zone / volume-key settings, EPUB / RAR / 7z
   chapters exercised from the UI.
5. **System integration:** notifications / tray (scheduled updates / backups run only while the app is open; P3 did
   them), OAuth via redirect (paste only), app security / lock, settings search, advanced settings (clear caches, logs,
   cookies, User-Agent). WebView / Cloudflare (P7): a browser-backend abstraction, the Cloudflare interceptor on it and
   a manual "Open in WebView" (system browser + pasted cookies / User-Agent) are done, and automatic solving through a
   FlareSolverr-compatible service the user runs (P8); left: an embedded browser backend (jcefmaven evaluated as the
   candidate; P7 "What the embedded backend still needs"), `android.webkit.WebView` for extensions.
6. **Distribution:** installers built and tried on each OS, signing / notarization. The update check is done (P6, behind
   `-Penable-updater`; opens the release page, no in-app download / install); left from it: the release pipeline does
   not publish desktop installers as GitHub release assets yet (P6 "What the release pipeline still needs"), so the
   check finds nothing until it does; no Markdown in the notes, no "What's new" / "What's coming".

