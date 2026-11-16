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

## Deviations from the plan and open issues

- **3.4 — DI duplication (open, for `:app-shared`).** `DomainModule` / `SYDomainModule` /
  `KMKDomainModule` / `PreferenceModule` stay in `app` and unchanged, so the bindings in the shared
  `registerDatabaseRepositories` / `registerDomainPreferences` / `registerLibraryInteractors` exist
  twice (Android modules + shared functions; Android does not call the shared ones yet). When
  `:app-shared` lands, the Android modules can call the shared functions in a KMK block and drop
  their copies (or the Metro DI migration replaces both). Until then, a new interactor in `:domain`
  must be added to both places to reach desktop. `DesktopDatabase` duplicates `AppModule`'s
  `Database(...)` adapter wiring (3.2).
- **3.4 — what the desktop graph lacks:** `SourceManager` (so `SourceRepository`,
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
- **Open — desktop `Dispatchers.Main`.** Shared code that uses `launchUI` / `withUIContext` needs
  `kotlinx-coroutines-swing` on the desktop runtime classpath (Compose Desktop brings it in
  `:desktopApp`); plain JVM tests that hit such code must provide a main dispatcher.
- **Open — Android toasts from shared code on desktop** go to `Toast.presenter` (logs). The desktop
  UI should replace it with a snackbar host in Phase 4.
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
  with the first Compose module (Phase 4). `kotlin.mpp.androidSourceSetLayoutVersion=2` in
  `gradle.properties` now triggers a KGP deprecation warning (pre-existing property). AGP 9 drops
  `com.android.library` + `kotlin("multiplatform")` in favour of
  `com.android.kotlin.multiplatform.library`; the plugin will need a rewrite at that point.
