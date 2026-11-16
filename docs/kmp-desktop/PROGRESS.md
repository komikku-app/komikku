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

| 8.2 Backup | done | see git log (`feat(kmp): … backup`) | **Borrowed as `shared`** (one copy for Android and desktop; `app` excludes them): `data/backup/models/**` (16 files), `exh/EXHMigrations.kt`, `BackupDecoder`, `BackupOptions`, `RestoreOptions`, creators `Categories` / `ExtensionStores` / `Feed` / `Preference` / `SavedSearch` / `Sources`, restorers `Categories` / `ExtensionStore` / `Feed` / `Manga` / `SavedSearch`. `:app-shared` now depends on `:data` and `kotlinx-serialization-protobuf` on both targets (both already `app` dependencies, so Android's resolved set is unchanged). **Borrowed as `desktopCopy`:** `BackupCreator`, `BackupFileValidator`, `BackupRestorer`, `MangaBackupCreator` (`ReadingMode`), `PreferenceRestorer`. Desktop counterparts (`app-shared/src/desktopMain`): `eu.kanade.tachiyomi.BuildConfig` (`APPLICATION_ID = "app.komikku"` so file names match Android, version from `komikku.version` / manifest), `BackupNotifier` (progress / completion / errors → KMK `BackupProgressListener`), `Notifications` (ids), `BackupCreateJob` / `LibraryUpdateJob` / `AppUpdateJob` (`object`s whose `setupTask` does nothing), `AndroidPreferenceStore(context, sharedPreferences)` (a function returning the compat `SharedPreferences`' `DesktopPreferenceStore`, so restored source preferences are what extensions read), `Context.createFileInCacheDir` (external cache, else cache dir), temporary `TrackerManager` stand-in (no trackers; replaced in 8.3). **Desktop service** `DesktopBackupService`: `create` (temp file next to the target, atomic move on success, so a failed run keeps the old backup; listener gets progress / complete / error), `validate` (missing sources / trackers), `restore` (cancellation reported as "Canceled restore" like `BackupRestoreJob`, then rethrown). Private preferences / app-state keys handled by the borrowed creator exactly as on Android. **UI:** More → Data and storage: storage location (folder chooser; `StoragePreferences.baseStorageDirectory`), create backup (Android option list incl. private settings → save dialog → path shown), restore (open dialog → missing sources / trackers + Android restore options → progress bar with the current item → done dialog with duration, error count, error log path and lines). File dialogs behind `DesktopFileDialogs` (AWT `FileDialog` for files, `JFileChooser` for folders; tests inject paths). Tests: `BackupRestoreTest` (backup assembled with the Android models, protobuf + gzip as Android writes it → manga, chapters, categories (by order, as Android), history, tracking, app prefs incl. category-id remapping, source prefs, extension store, saved search, feed restored; round trip create → wipe → restore → identical snapshot, private key excluded, old target replaced, no temp left), `BackupUiTest` (headless: storage location, create, wipe, restore through the dialogs) |
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
| `android.webkit.CookieManager`, `ValueCallback` | 1.1 | `AndroidCookieJar` (`NetworkHelper.cookieJar`) | `DesktopCookieManager`: RFC 6265 store with OkHttp parsing/matching; persistent cookies in `<dataDir>/cookies.json` (debounced write, `flush()`, shutdown hook) |
| `android.webkit.MimeTypeMap` | 1.1 | UniFile `type`, `ContentResolver.getType`, extensions | Built-in table (images, comic archives, documents, web, media) |
| `android.widget.Toast` | 1.1 | `ToastExtensions` (`context.toast`) | `show()` → `Toast.presenter` (logs by default; the desktop UI can replace it) |
| `android.text.Html`, `Spanned`, `androidx.core.text.parseAsHtml` | 1.1 | `StringExtensions.htmlDecode`, extensions | jsoup-based HTML → plain text (legacy/compact block separation), no spans |
| `androidx.core.content.ContextCompat` | 1.1 | `DiskUtil` | `getExternalFilesDirs`, `getExternalCacheDirs`, `getDataDir`, `getMainExecutor` |
| `androidx.preference.PreferenceManager` | 1.1 | `EHLogLevel` | `getDefaultSharedPreferences` = the main store (`preferences.json`) |
| `com.elvishew.xlog.XLog`, `Logger`, `LogLevel` | 1.2 | `exh.log` (`Logging.kt`, `ResettableLogger`, `EHNetworkLogging`) — public API exposes `Logger` | Prints through `DesktopLog` with the logger tag; no `init()` needed; borders / JSON formatting not reproduced |
| `app.cash.quickjs.QuickJs`, `QuickJsException` | 1.2, 1.4 | `JavaScriptEngine`, extensions | QuickJS via quickjs-kt: `create`, `evaluate(script[, fileName])`, `compile` / `execute`, `close`; results converted like quickjs-android (`Int` / `Double` / `String` / `Boolean` / `null` / `Array`); engine errors → `QuickJsException`. `set` / `get` (Java interface bindings) unsupported |
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

- **8.2 — backup, deviations / gaps.** No automatic (scheduled) backups on desktop (`BackupCreateJob.setupTask`
  is a no-op; `backupInterval` is restored but unused), no sync (`isSync` is always false), no share
  action for a created backup. `BackupCreator` runs in the caller's coroutine (the screen model), not a
  background job: leaving the screen cancels it. Restore cannot be cancelled from the UI yet (the
  service supports cancellation). Restoring does not trigger a library or tracker refresh (Android
  doesn't either). Missing-tracker validation reports nothing until 8.3 registers trackers. SY
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
  needs them.
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
