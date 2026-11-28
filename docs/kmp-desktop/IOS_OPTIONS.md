Status: iOS engineer — done: options report (a)–(d), measurements, iOS check state, open questions — left: the
maintainer's decision; until then the iOS engineer only keeps the existing iOS checks green (INSTRUCTION 001, track E).

# The iOS app: options for building it on what exists (track E)

Assessment only; no app code. Measured on `multiplatform` at `aaac9488cd` (2026-11-28). Every count is reproducible
with the commands in the last section. Effort is given in engineer-days for one engineer who knows Kotlin
Multiplatform; ranges are rough and meant for comparing options, not as a schedule.

## Summary and recommendation

Build the iOS app with **Compose Multiplatform for iOS, on a new, small, KMK-owned common core**, and do not try to
make the existing JVM stack (`:domain`, `:data`, `:source-api`, `:core:common`, `:app-shared`, `:desktopApp`) compile
for iOS. The desktop screens cannot be reused as they are: 56 of the 61 desktop UI files (15,700 of 16,246 lines)
call into the JVM stack (borrowed `app` code, Injekt, the domain layer), and that stack lives in modules `master` owns,
where making it multiplatform would mean editing roughly 120 to 150 upstream files and touching the extension ABI
(`SManga` / `SChapter` implement `java.io.Serializable`, `Source` returns RxJava `Observable`s). What iOS can reuse at
no conflict cost is already sizeable: the whole JavaScript source host (`:source-js`, 175 + 25 tests green on the
simulator), the SQLDelight schema (23 `.sq` files, 47 migrations), the pure-Kotlin preference API (`:core:preference`),
the strings (`i18n*`, moko-resources has iOS targets), 41 of the 49 `:presentation-core` files and the colour
schemes (22 files), borrowed from their paths the way desktop borrows `app` files. SwiftUI would give the best iOS feel, but it
shares no UI with desktop or Android, needs a second language and a Kotlin-to-Swift bridge (SKIE) that must keep up
with every Kotlin upgrade `master` brings, and it costs about the same for the first version (40–58 engineer-days
versus 37–54) while every later screen costs again. The first usable version should be what the Architect named
(add a JavaScript repository, browse a source, manga details, library, reader, a few settings), distributed first as
an unsigned IPA for sideloading and, once the maintainer decides to pay for the Apple Developer Program, through
TestFlight. App Store publication stays a separate maintainer decision: an app that downloads and runs third-party
scripts from user-added repositories is at real risk of rejection.

## Current state of the iOS checks

**Green.** `build_ios_check.yml` has run once on `multiplatform` (the branch was created on 2026-11-28): run
46099469392, commit `6bc87481ba`, 2026-11-28 02:43 UTC, push, conclusion `success` in 11 min 48 s on `macos-15`
(Xcode 16.4 selected; iOS 18.5 to 26.2 simulator runtimes installed). Both steps passed: "Compile and test for iOS"
ran 175 `:source-js` tests on the iOS simulator with no failure, and "WebKit tests in a simulator app" reported
`[  PASSED  ] 25 tests.` against the expected minimum of 25. The known open item is unchanged:
`WEBVIEW_DEALLOCATED=false` (a `WKWebView` is still not freed 8 s after a call; the controls are freed), as
IOS_CI.md describes. No commit since `6bc87481ba` touches the workflow's trigger paths (`source-js/**`, `gradle/**`,
`buildSrc/**`, the root build files, `.github/.java-version`, the workflow), so that run is current. The last runs on
the old branch were green too (45659640950, 45627948544); the failures before them (45624564728, 45585550783) were
fixed as IOS_CI.md records.

One thing to know for later stages: the job builds with Xcode 16.4, the image default. Uploads to TestFlight or the
App Store need the SDK Apple currently requires (the Xcode 26 / iOS 26 SDK, to be confirmed when we get there). The
image lists iOS 26 simulator runtimes, which suggests Xcode 26 is installed next to 16.4 (to be confirmed); if so, it
is an `xcode-select` line, not a new runner.

## (a) Compose Multiplatform versus SwiftUI

### The options

1. **Compose Multiplatform, reusing the desktop screens as they are.** Not possible without first making the whole
   JVM stack multiplatform (see (b), "Option L1"). `:desktopApp` is a `kotlin("jvm")` module; only 5 of its 61 UI
   files (524 lines) import nothing but Compose, kotlinx, the strings, Voyager, Coil, `:presentation-core` and the
   app's own types. The other 56 import borrowed `app` code (48 files), Injekt (33), `tachiyomi.domain` (31),
   `tachiyomi.core.common` (21), `android.*` compat types (up to 8) or `java.*` (up to 11). Estimated 60–90 engineer-days before
   the first iOS screen, plus a permanent merge cost. Rejected.
2. **Compose Multiplatform, new common screens on a common core (recommended).** A new KMK module holds iOS screens
   written in common Compose, fed by a KMK common core (`:ios-core`, PLAN.md I5). They reuse `:presentation-core`
   components, the theme and colour schemes, Material 3, the reader's Skia decoding and the desktop screens'
   structure and code where it lifts out cleanly (state hoisting), but they are laid out for a phone (bottom
   navigation, sheets, safe areas, swipe back). A small Swift entry point (`@main` App hosting a
   `ComposeUIViewController`, about 50 lines) and UIKit interop for the few places where native matters: the
   `WKWebView` challenge screen, the document picker, the share sheet.
3. **Native SwiftUI shell over shared logic.** The same common core and `:source-js` exported as an XCFramework; all
   UI in Swift (SwiftUI, with UIKit for the reader's zooming scroll view).
4. **Hybrid, SwiftUI shell with Compose screens inside.** A SwiftUI `TabView` / `NavigationStack` with Compose screens
   embedded per tab. It buys native navigation chrome at the cost of two navigation systems talking to each other.
   Worth it only if device tests of option 2 show the Compose navigation feels wrong; it can be adopted later without
   rewriting the screens.

### Trade-offs between options 2 and 3

| | Compose Multiplatform (2) | SwiftUI (3) |
|---|---|---|
| UI shared with desktop | possible later (common composables desktop can adopt) | none |
| UI shared with Android | none now (Android screens wait for the Phase 6 gate); possible after it | none, ever |
| Languages | Kotlin only (plus about 50 lines of Swift) | Kotlin and Swift |
| Kotlin-to-Swift bridge | not needed | needed (SKIE or wrappers) |
| First version, engineer-days | 37–54 | 40–58 |
| Later features | one common implementation | a Swift implementation each |
| iOS look and feel | close, not native (Material 3, own gestures) | native |
| App size | larger (Skia / Skiko in the binary) | smaller |

**Code sharing.** Option 2 is the only path on which iOS and desktop UI can converge: screens written in common code
can be adopted by desktop, and by Android once the upstream ViewModel / Metro migration has been synced and Phase 6
resumes. Option 3 shares only logic, so every screen Android adds on `master` is written once in Compose (desktop) and
once in Swift (iOS). `master` moved 98 commits in the 11 days since our merge base (`290267dd4c`, 2026-11-16, to
`15610e67e4`, 2026-11-27), changing 84 files in `app`, `domain`, `data`, `source-api`, `core` and
`presentation-core`, 12 of them in `eu/kanade/presentation`. That pace is what an iOS UI has to keep up with.

**UX fidelity.** SwiftUI wins: system navigation bars, swipe-back, context menus, pull to refresh, text fields,
scroll physics and haptics behave exactly as iOS users expect. Compose Multiplatform 1.12 (the catalog's version) is
stable on iOS and covers most of these (native-like scroll physics, the iOS back gesture, UIKit text input), but it
remains a Material app on iOS. For a reader app the main surfaces are a cover grid, lists and a full-screen image
reader, where the difference is smallest.

**App size.** The Kotlin/Native part (QuickJS, Ktor, Ksoup, serialization, SQLite) is in both options; its size is
not measured yet. Compose Multiplatform adds the Skia rendering engine on top; publicly reported numbers are around
10 to 25 MB more download size than a comparable SwiftUI app (unverified here). Stage 2 measures it with an App
Thinning size report in CI.

**Startup.** Compose on iOS initialises the Kotlin runtime and a Metal-backed Skia surface before the first frame;
SwiftUI only the Kotlin runtime (for the shared core). Both should stay well under a second on current iPhones, but
that is not measured; it needs a device (Instruments, App Launch) in stage 2.

**Accessibility.** SwiftUI gets VoiceOver, Dynamic Type, Switch Control and keyboard navigation from the system.
Compose Multiplatform maps its semantics tree to iOS accessibility (VoiceOver works for standard components) and
follows the system font scale, but custom components need explicit semantics and coverage is narrower. A VoiceOver
pass on a device is part of stage 2 either way.

**Team skills.** Forge's team writes Kotlin and Compose and reviews it; the Reviewer, Atlas's design review and the
existing CI all assume Kotlin. SwiftUI adds a language nobody on the team currently reviews, and a bridge
(SKIE from Touchlab, Apache-2.0) that maps `suspend` functions and `Flow`s to Swift `async` / `AsyncSequence`.
SKIE supports specific Kotlin versions, so every Kotlin upgrade that comes with a merge of `master` can wait on a SKIE
release; Kotlin's own Swift export is still experimental.

**Maintenance.** With option 2, a behaviour change on `master` reaches iOS through the common core and one set of
screens; with option 3 through the common core and Swift screens. Option 2 also lets the iOS engineer and the
desktop engineers review each other's UI code.

## (b) What is JVM-only today, and what each part would cost on iOS

### Measured

Files are Kotlin sources; "portable as is" means the file imports nothing from `java.*`, `javax.*`, `android.*`,
non-Compose `androidx.*`, OkHttp, RxJava, Injekt, jsoup, UniFile, logcat, `kotlin.reflect`, the Guava coroutines
bridge, `androidx.paging`, nor from `:source-api` / `:core:common` (which are JVM-only themselves). The count is
approximate: same-package references are not seen (which overstates portability), and `kotlin.reflect` imports such
as `KClass`, which exist on iOS too, count as blockers (which understates it).

| Module | On `master` | Files (main) | Portable as is | Main blockers |
|---|---|---|---|---|
| `:source-js` `commonMain` | no | 32 (4,185 lines) | 32, already compiled for iOS | none |
| `:core:preference` `commonMain` | no | 5 (305 lines) | 5 | none (desktop store uses `java.io.File`) |
| `:domain` | yes | 174 (5,884 lines) | 103 (2,438 lines) | imports of `tachiyomi.core.common` 65, logcat 30, source-api 24, Injekt 6; `java.time` / `java.util.Date` in 8 files |
| `:data` | yes | 44 (3,639 lines) + 23 `.sq` | 20 (844 lines) | imports of source-api 20, `core.common` 13, logcat 7, `androidx.paging` 5; `ThreadLocal` context in the transaction handler |
| `:source-api` | yes | 51 (4,245 lines) | 24 (687 lines) | files with RxJava 8, OkHttp 5, Injekt 5, `android.*` compat 12, jsoup 2, `kotlin-reflect` 1; `java.io.Serializable` on `SManga` / `SChapter` |
| `:core:common` | yes | 66 (5,108 lines in 4 source sets) | few | OkHttp network stack (19 files), `android.*` (28), UniFile, QuickJS compat |
| `:presentation-core` | yes | 49 (5,696 lines) | 41 (4,078 lines) | imports of `tachiyomi.core.common` 6 (the `Context`-based string lookup), `androidx.paging` 3, `ViewConfiguration` 2 |
| `:app-shared` own code | no | 58 (4,205 lines) | — | JVM by design (Injekt, OkHttp, borrowed code) |
| borrowed `app` files | yes | 339 (32,326 lines) | 38 (2,380 lines) | see below |
| `:desktop:android-compat` | no | 78 (4,637 lines) | — | it *is* the Android API on the JVM |
| `:desktopApp` | no | 100 (22,101 lines) | 5 UI files (524 lines) | JVM module; Injekt 41 files, Voyager 35, borrowed code 48 |

### Item by item

**The Android compat layer (`:desktop:android-compat`, 78 files, 4,637 lines).** It exists so that Android-flavoured
shared code and APK extensions run on a JVM. iOS runs no APK extensions, so porting it would mean re-implementing
`android.*` in Kotlin/Native only so that upstream code compiles there, a large and permanent effort for no user
value. Cost to make it available: not worth estimating; the recommendation is to never ship it on iOS (PLAN.md 8.5
already says so). The same goes for `:desktop:extension-runtime` and `:core:quickjs`.

**Borrowed `app` sources (`app-shared/borrowed-app-sources.gradle.kts`).** 94 files are borrowed as `shared`
(6,269 lines), 243 as `desktopCopy` (25,835 lines), plus 1 `androidOnly` and 1 `perPlatform`. `desktopCopy` files are
by definition Android-entangled and stay JVM. Of the 94 `shared` files, 38 (2,380 lines) have no JVM or domain
import; the useful ones for iOS are the theme models and the colour schemes (22 files, pure Compose plus material-kolor,
which publishes iOS artifacts). The backup models among the 38 refer to `BackupManga` in the same package, which
imports domain models, so they do not compile alone. Cost: about one day to borrow the theme files into an iOS
module's common source set with their own allowlist and a check like `:app:verifyBorrowedSources`.

**The SQLDelight driver, and SQLCipher.** The schema itself is portable, and so is the driver stack: the app and
desktop use `com.eygraber:sqldelight-androidx-driver` 0.0.17 over `androidx.sqlite:sqlite-bundled` 2.6.2, and both
publish `iosArm64` / `iosSimulatorArm64` artifacts (checked in their Gradle module metadata), so iOS can use the same
driver code as desktop. Two `.sq` files and one migration import JVM types (`java.util.Date` in `history.sq`,
`mangas.sq` and `14.sqm`; `eu.kanade.tachiyomi.source.model.UpdateStrategy` in `mangas.sq`). Without editing them, a
Gradle copy task can feed the iOS module's SQLDelight a copy with those imports replaced by common types; a test then
compares the generated schema with the JVM one. The database handler (`AndroidDatabaseHandler` and its transaction
context) uses a `ThreadLocal` coroutine element, which is JVM-only, so the iOS core needs its own small handler.
Cost: 3–4 days for database, handler and the schema check. **SQLCipher** (Android's optional database encryption,
`net.zetetic:sqlcipher-android`) has no iOS artifact in this stack; the bundled driver compiles its own SQLite and
cannot encrypt. On iOS the usual protection is the system's Data Protection (files encrypted while the device is
locked), which costs nothing. Real SQLCipher on iOS would mean linking Zetetic's SQLCipher library and SQLDelight's
native driver instead of the bundled one: 3–5 days plus a licence check. Recommendation: Data Protection only in
the first version, recorded as a difference from Android.

**Injekt.** JVM-only (it keys bindings on Java reflection types). It is used in 41 desktop files, 7 own `app-shared`
files, 6 in `:domain`, 3 in `:data` and 5 in `:source-api` (e.g. `ConfigurableSource` reads `Application` through
it). For an iOS core of this size, plain constructor injection with one composition root is enough and needs no
library; Koin (multiplatform) or Metro (compile-time, multiplatform, and where upstream Mihon is heading) are the
options if it grows. Cost: none beyond writing the composition root (about 1 day); replacing Injekt in the shared JVM
modules is not needed.

**Voyager** (1.1.0-beta03). Multiplatform; it publishes iOS artifacts (only the desktop variants are in the local
cache, so the iOS resolution is unverified until a build). It can be used on iOS as on desktop. Upstream Mihon is
moving to AndroidX ViewModel and navigation, which also exist for Compose Multiplatform on iOS. Choosing between them
for iOS is cheap now and expensive later; it is a question for Atlas (open questions). Cost: none to use it.

**Coil 3** (3.5.0). `coil-core` and `coil-compose` publish iOS artifacts (checked); `coil-network-okhttp`, which the
desktop setup uses, is JVM-only. iOS uses `coil-network-ktor3` (same BOM, a new catalog entry) or a custom fetcher
over the store's HTTP client, so covers get the source's headers. The desktop cover fetcher and disk cache
(`DesktopMangaCover.kt`, 254 lines with the decoder) use `java.io.File`; an iOS twin on Okio's file system is about
the same size. Cost: 1–2 days.

**OkHttp versus Ktor.** OkHttp 5 is JVM / Android only. iOS already has Ktor 3.6.1 on the Darwin engine
(`DarwinHostHttp`, with OkHttp's redirect, body and cookie semantics, I5a). Everything HTTP on iOS goes through it:
the JavaScript sources, covers and page images. Not covered on iOS today: DNS over HTTPS, rate limits per host and
the Cloudflare interceptor of `NetworkHelper` (IOS_CI.md). Cost: covered; rate limiting is about 1 day if needed.

**RxJava 1 in `:source-api`.** `Source` and `CatalogueSource` keep the RxJava `fetch*` methods that APK extensions
call, so `:source-api` cannot be compiled for iOS without changing the extension ABI. iOS does not need it: its
sources are `JsExtension`s from `:source-js`, whose results are already common models (`JsMangaPage`,
`JsMangaDetail`, filters, preferences). On the JVM, `MangayomiJsSource` adapts them to `HttpSource`; on iOS the core
maps them directly to its own models. Cost: no change to `:source-api`; the mapping is part of the iOS core.

**Preferences.** `:core:preference`'s `commonMain` (the `PreferenceStore` / `Preference` API, 5 files) is pure Kotlin
and coroutines and the module does not exist on `master`, so it can gain iOS targets with an `NSUserDefaults` store
(the desktop store is 448 lines; `:source-js` already writes per-source suites with typed values). The app's
preference classes (`UiPreferences`, `ReaderPreferences`, `SourcePreferences`, …) are borrowed JVM files; the iOS
core declares the subset it needs, with the same keys so a later backup restore maps them. The convention plugin
`mihon.library.kmp` maps `commonMain` to the JVM-only `jvmShared` source set, so this module needs an iOS-aware
variant of the plugin (a build-convention change, open question for Atlas). Cost: 2 days.

**`java.io.File` and `java.time` in `:domain` / `:data`.** `java.time` / `java.util.Date` are imported in 11 main
files of the two modules (8 in `:domain`, among them the `History` and `Manga` models and `FetchInterval`; 3 in
`:data`), `java.io` in 6 (5 and 1), and `java.util.Date` is also a column type in two `.sq` files. In place, they would be replaced by `kotlin.time.Instant` / kotlinx-datetime and Okio or
expect/actual types. That is cheap per file but each file is owned by `master` (see option L1 below).

**kotlinx libraries.** Coroutines, serialization (JSON, protobuf), `kotlinx-collections-immutable` and xmlutil are
multiplatform and fine on iOS. Two used in the shared modules are JVM-only: `kotlinx-coroutines-guava` (`:domain`)
and `kotlin-reflect` (`:source-api`, SY's filter serializer). kotlinx-datetime is not in the catalogs; the iOS core
can use the standard library's `kotlin.time.Instant` and only needs kotlinx-datetime for calendar dates (a new
dependency, an Atlas question if needed). logcat 0.4 resolves as `logcat-jvm`; whether it publishes iOS artifacts is
unverified, and the iOS code logs through its own small logger anyway (`NSLog`, as `:source-js` does).

**Strings.** `i18n`, `i18n-sy`, `i18n-kmk` are real multiplatform modules (Android + desktop) on moko-resources
0.26.4, which publishes iOS artifacts. They need two lines each (`iosArm64()`, `iosSimulatorArm64()`) inside
`// KMK` markers, in three build files `master` owns, exactly as the desktop target was added. The Compose lookup
(`stringResource` in `:presentation-core`) goes through Android's `Context`; iOS needs its own two-function lookup.
Bundling moko resources into a Compose iOS app is a known rough edge and is verified first in stage 0. Cost: 1–2
days.

**`:presentation-core`.** 41 of 49 files (4,078 lines) are pure Compose. Rather than adding an iOS target to this
`master`-owned module (which would require splitting the 8 Android-touching files), an iOS module can borrow the 41
files from their paths, as `:app-shared` borrows `app` files, with its own allowlist and a check that fails when
`master` changes one so it no longer compiles there. Cost: 1–2 days.

### Three ways to get a logic layer on iOS

- **L1: make the existing modules multiplatform in place.** Move `:domain`, `:data`, the models of `:source-api`
  and parts of `:core:common` to `commonMain` with iOS targets. Measured: 71 `:domain` files (3,446 lines), 24
  `:data` files (2,795 lines) and 27 `:source-api` files (3,558 lines) import something that does not exist on iOS,
  plus most of `:core:common`; together roughly 120 to 150 files `master` owns, every one a future merge conflict.
  `SManga` / `SChapter` implementing `java.io.Serializable` and RxJava in `Source` are extension ABI, so `:source-api`
  would need expect/actual tricks that keep the JVM bytecode identical. Upstream is moving the other way (it has
  turned `source-api` back into a plain Android library, PLAN.md D1). Estimate: 25–40 engineer-days, plus a
  permanent merge tax. Not now; reconsider after the upstream ViewModel / Metro sync.
- **L2: a KMK common core next to them (recommended; PLAN.md I5 and I-D7).** A new module `:ios-core` (name open)
  with `commonMain` + `iosMain`, and a JVM target used only for tests so most of its suite runs on Linux in seconds.
  It holds the models and repositories the first version needs (manga, chapter, category, history; tracks later) on
  the shared schema, the preferences subset, the mapping from `JsExtension` results to its models, chapter syncing
  (a subset of `SyncChaptersWithSource`), and cover / page fetching through `DarwinHostHttp`. It edits no
  `master`-owned file except the three `i18n` build files. Duplication is the cost: about 3,000 to 5,000 lines that
  mirror `:domain` / `:data`, held together by the shared schema and by tests that compare behaviour with the JVM
  implementation where both exist. Estimate: 10–14 engineer-days for the first version's scope.
- **L3: thin client.** The iOS app talks to a running Komikku desktop or a Suwayomi server (PLAN.md 8.8). Cheapest
  in logic, but it does not give a standalone app; listed only for completeness.

## (c) What is impossible or restricted on iOS, and what the first version should contain

### Impossible or restricted

- **APK / dex extensions: impossible.** There is no JVM, no dex and no dynamic code loading for native code on iOS.
  The only route would be an embedded no-JIT JVM interpreter (PLAN.md 8.8): slow, foreground-only, sideload-only.
  So the iOS catalogue is the Mangayomi JavaScript manga sources: 114 index entries from 18 files, 86 of them
  MangaDex and Comick language variants, hosted in a third-party fork, 2 files already 404 (PLAN.md 8.9). The SY
  delegated sources (E-Hentai, NHentai, the MangaDex delegate) are JVM `app` code and are not in the first version.
- **JIT: not needed.** QuickJS is an interpreter and Kotlin/Native is compiled ahead of time; neither needs the JIT
  entitlement iOS withholds from third-party apps.
- **Downloaded executable code and JavaScript: allowed technically, risky in review.** App Review Guideline 2.5.2
  forbids downloading code that "introduces or changes features or functionality", and the developer agreement
  (section 3.3.1(B)) allows interpreted code only if it does not change the app's primary purpose, does not create a
  store or storefront for other code, and does not bypass security features. Guideline 4.7 covers HTML5 mini apps and
  plug-ins run in WebKit, which QuickJS with native bridges is not. A "repository" of sources to install reads like a
  storefront, and guideline 5.2 (third-party content and intellectual property) is a second, independent risk for
  any manga aggregator. Comparable readers with downloadable source plug-ins are, as far as I know, distributed
  mostly through TestFlight or sideloading (unverified). Hence: sideload first, App Store only by the maintainer's
  decision (PLAN.md 8.7 item 2). External TestFlight builds also pass a (lighter) Beta App Review under the same
  guidelines; internal TestFlight testers (members of the App Store Connect team, up to 100) need no review.
- **Background work: limited.** There is no WorkManager equivalent. `BGAppRefreshTask` gives about 30 seconds at
  times the system chooses, `BGProcessingTask` minutes when the device is idle and charging; iOS 26 adds a
  continued-processing task for work the user started (to be verified). A background `URLSession` can download files
  while the app is suspended, but only URLs known in advance: page lists come from running a source's script, which
  only happens while the app runs. So library updates are best effort, and downloads resolve page URLs in the
  foreground and hand the files to a background session. None of this is in the first version.
- **Web view and Cloudflare.** `WKWebView` works, and `evaluateJavascriptViaWebview` runs on it (I5c). An
  interactive challenge needs a visible web view screen that copies the clearance cookie to the shared cookie storage
  `DarwinHostHttp` uses. Risk: Cloudflare binds the clearance to the User-Agent, and today the web view and
  NSURLSession send different ones. No KCEF or FlareSolverr on iOS. No JavaScript manga source uses web view
  evaluation (PLAN.md 8.9), so this is a later stage.
- **File access.** The app sees only its own container. A local source means a folder in the app's Documents,
  visible in the Files app (`UIFileSharingEnabled`, `LSSupportsOpeningDocumentsInPlace`), or folders picked with the
  document picker and kept as security-scoped bookmarks. Backups go in and out through the share sheet or the
  document picker; there is no free choice of a download folder.
- **Memory.** iOS ends apps that exceed their memory limit without warning. The reader has to downsample large pages
  and split tall webtoon images, and QuickJS keeps its per-source memory limit.
- **Not on iOS at all:** the in-app updater (forbidden in App Store builds; AltStore / TestFlight handle updates),
  APK installers and Shizuku, Discord rich presence, notifications from a server (local notifications are fine).
  Database encryption becomes Data Protection (see (b)); the app lock uses LocalAuthentication (Face ID / passcode).

### The first usable version

Following the Architect's priority list (browse a JavaScript source, library, reader):

1. **JavaScript sources:** add a repository URL, list offers, install / update / uninstall with the SHA-256
   confirmation, source preferences. `IosJsSourceStore` already does the logic (I5b).
2. **Browse a source:** popular, latest, search with the source's filters, paging, NSFW hidden unless enabled.
3. **Manga details:** information, chapter list stored in the database, add to / remove from the library, one
   category level.
4. **Library:** cover grid and list, unread counts, sort and a text filter, categories as tabs, continue reading.
5. **Reader:** paged (left-to-right, right-to-left) and vertical webtoon, pinch zoom, tap zones, page prefetch, the
   source's image headers, progress and history saved.
6. **Settings:** theme and colour scheme, reader defaults, NSFW, about and licences.

Not in it: downloads and offline reading, backup restore, trackers, global search, the updates tab, migration, the
local source, interactive Cloudflare, the app lock, iPad-specific layouts. Each is listed with a size under (d).

## (d) Staged plan

Sizes are engineer-days for option 2 on L2. CI here means GitHub-hosted `macos-15` runners, free for this public
repository; the current iOS check takes about 12 minutes.

**Stage 0: decisions and scaffolding (3–4 days).** The maintainer decides this report; Atlas settles the module names
and the convention plugin. An iOS-aware Kotlin Multiplatform convention for new modules; iOS targets on `i18n`,
`i18n-sy`, `i18n-kmk` (`// KMK` edits) and on `:core:preference` with an `NSUserDefaults` store; a spike that puts
one moko string and one `:presentation-core` component on screen in a Compose iOS app. *CI:* compile and simulator
tests of these modules, added to `build_ios_check.yml` by the Test and release engineer.

**Stage 1: the iOS core (10–14 days).** `:ios-core` on the shared schema (copied with the two imports replaced, the
schema comparison test), database handler, models and repositories for manga, chapters, categories and history,
preferences, the `JsExtension` mapping, chapter sync, cover and page fetching through `DarwinHostHttp`, a disk cache.
*CI:* everything; JVM-target tests on Linux, iOS tests on the simulator. No device needed.

**Stage 2: app shell and the first screens (14–20 days).** The Compose iOS module and the Xcode project (Swift entry
point, `Info.plist`, icons), borrowed `:presentation-core` components and colour schemes, navigation, screens for
sources and repositories, browse, details, library and settings. An in-app smoke mode like the one decided for
desktop (ANSWER 005, item 5): the app walks a fixed script against a bundled fixture source and saves screenshots.
*CI:* build the simulator app without signing, install and launch it with `simctl`, run the smoke script and upload
the screenshots; Compose UI tests on the simulator; an App Thinning size report. *Device:* feel, startup time,
VoiceOver, scrolling performance.

**Stage 3: the reader (8–12 days).** Paged and webtoon viewers, zoom, Skia decoding (the desktop
`SkiaPageDecoder`, 220 lines, uses Skia APIs that also exist on iOS), downsampling and tall-image splitting,
prefetch, progress and history. *CI:* decoder and loader tests, the reader steps of the smoke script. *Device:*
memory under long webtoon chapters, frame rate.

**Stage 4: distribution (2–4 days, plus the maintainer).** An unsigned device IPA built in CI (`xcodebuild` with
signing disabled) as an artifact for AltStore / SideStore users, who sign it with their own Apple ID (free accounts
re-sign every 7 days, 3 apps at most). Optionally a TestFlight lane with signing secrets. *Maintainer:* Apple
Developer Program membership (99 USD a year) for TestFlight, App Store and device builds without 7-day profiles; the
signing certificate and provisioning profile as repository secrets; an App Store Connect app record and bundle id;
someone with an iPhone for the device passes; the App Store question.

**First usable version: 37–54 engineer-days** (milestone I-B of PLAN.md, reduced to the scope above). The SwiftUI
alternative: stage 1 plus a Swift bridge (3–4 days), the screens in Swift (16–24), the reader in UIKit (8–12) and the
same stage 4: 40–58 days.

**Later stages, each independent:** downloads and offline reading with a background `URLSession` (6–10 days);
backup restore and create of `.tachibk` files, with common twins of the protobuf models tested against desktop
backups (5–8); updates tab and best-effort background refresh (4–6); the interactive Cloudflare web view (3–5);
trackers, whose code is borrowed JVM code on OkHttp and needs Ktor versions, with OAuth through
`ASWebAuthenticationSession` (10–20); the local source through the Files app (3–5); app lock (1–2); iPad layouts
(3–5).

### Risks

| Risk | Mitigation |
|---|---|
| The App Store rejects the app | Sideload and TestFlight first; App Store only by the maintainer's decision |
| The two logic layers (JVM and `:ios-core`) drift | Shared schema with a generated-schema check; behaviour tests against the JVM code where both exist; keep `:ios-core` small |
| Library versions on iOS (Kotlin 2.4.20, Compose 1.12.2, moko 0.26.4, Voyager beta, Xcode 26) | Stage 0 spike resolves and links every library before screens are written |
| quickjs-kt's residual crash paths (PLAN.md 8.7) also exist on iOS | Unchanged; the native fix in quickjs-kt remains the real cure |
| `WKWebView` not freed after calls | Check with Instruments on a device (IOS_CI.md); bounded to 2 web views at once |
| Memory kills in the reader | Downsampling and splitting from the start; device test in stage 3 |
| The JavaScript catalogue is small and maintained by a third-party fork | Re-check its size before stage 2 (PLAN.md 8.9 condition) |
| Capacity: iOS is priority 4 of 5 | Stages are independent; stage 1 has value for tests even if stage 2 waits |

## Open questions

For the maintainer:

1. **UI approach:** Compose Multiplatform on a KMK common core (recommended), or SwiftUI over shared logic?
2. **Logic layer:** a separate `:ios-core` with some duplication (recommended), or making `:domain` / `:data`
   multiplatform in place with the merge cost that brings?
3. **Distribution:** unsigned IPA for sideloading first; when (if at all) to join the Apple Developer Program
   (99 USD a year) for TestFlight; whether to attempt the App Store.
4. **Device testing:** who has an iPhone (and which iOS version) for the device passes of stages 2 and 3?
5. **Bundle id:** the macOS desktop app uses `app.komikku`. Should the iOS app use the same id, which Apple treats as
   one product across platforms (universal purchase), or its own, e.g. `app.komikku.ios`?
6. **Encryption:** is the system's Data Protection acceptable on iOS instead of Android's optional SQLCipher?

For Atlas (structure, before stage 0):

7. Module names and boundaries (`:ios-core`, an iOS UI module, the Xcode project folder), and a new convention plugin
   for real multiplatform modules with iOS targets.
8. The three `// KMK` build edits to `i18n`, `i18n-sy`, `i18n-kmk` for iOS targets (files `master` owns).
9. Borrowing `:presentation-core` files and the colour schemes into the iOS module with a second allowlist, versus
   an iOS target on `:presentation-core`.
10. Navigation on iOS: Voyager (as desktop) or the AndroidX ViewModel / navigation that upstream is moving to.
11. New dependencies: `coil-network-ktor3`, possibly kotlinx-datetime.
12. Whether to merge the two JavaScript source stores (JVM `MangayomiSourceStore` in `:app-shared`, `IosJsSourceStore`
    in `:source-js`) into one common implementation during stage 1 (both modules are KMK-only).

## How the numbers were measured

- File and line counts: `find <module>/src/<sourceSet> -name '*.kt' | xargs wc -l`, per source set, excluding
  `build/`.
- Imports per module: a small script counting, per file, the imports matching `java.*`, `javax.*`, `android.*`,
  non-Compose `androidx.*`, `okhttp3`, `rx.`, `uy.kohesive.injekt`, `org.jsoup`, `com.hippo.unifile`, `logcat`,
  `kotlin.reflect`, `kotlinx.coroutines.guava`, `androidx.paging`, `eu.kanade.tachiyomi.(source|network|util)` and
  `tachiyomi.core.common`; equivalent `rg '^import (java|javax|android)\.' <dir>` checks. Inline fully qualified uses
  were checked separately (0 in `:domain`, `:data`, `:presentation-core`; 9 in `:source-api`, 8 in `:core:common`).
- Borrowed sources: the four lists of `app-shared/borrowed-app-sources.gradle.kts`, resolved against
  `app/src/main/java`.
- Library platforms: the Gradle module metadata of the artifacts in the local Gradle cache (`iosArm64` /
  `iosSimulatorArm64` variants seen for coil-core 3.5.0, sqldelight-androidx-driver 0.0.17, sqlite-bundled 2.6.2,
  moko-resources 0.26.4, material-kolor, quickjs-kt 1.0.15, ksoup 0.2.6, paging-common 3.4.2,
  kotlinx-collections-immutable 0.5.2); coil-network-okhttp has JVM and Android only. Voyager, Okio, xmlutil and
  logcat have only their JVM variants in the cache, so their iOS artifacts are unverified.
- `master` movement: `git merge-base HEAD origin/master` and `git diff --stat` against `origin/master`.
- CI: `gh run list --repo komikku-app/komikku --workflow build_ios_check.yml --branch multiplatform --limit 5` and
  the log of run 46099469392 (read only). Nothing was built for iOS locally.
