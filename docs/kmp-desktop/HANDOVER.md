# Komikku Desktop / JS Sources — Handover

State as of 2026-11-24, branch `claude/kmp-multiplatform-support` (the I2b review and its fixes, step I3 and its
review fixes, step I4, the repository store and UI, with its review fixes, desktop parity step P1, global search
and source toggles, with its review fixes, parity step P2, library grouping and batch actions, with its review
fixes, parity step P3, scheduled library updates and backups, with its review fixes, parity step P4, SY delegated
sources, with its review fixes, parity step P5, AVIF / JPEG XL, with its review fixes, and parity step P6, the app
update check, with its review fixes, are done; next: WebView / Cloudflare, signing, the release pipeline for desktop
installers, and the maintainer's first real run; see "Done" and "Left to do").
Read with [`PLAN.md`](PLAN.md) (plan and decisions), [`PROGRESS.md`](PROGRESS.md) (step log,
deviations, moved files, compat classes), [`BUILDING.md`](BUILDING.md) (how to run/test/package),
[`MANGAYOMI_CONTRACT.md`](MANGAYOMI_CONTRACT.md) and [`MANGAYOMI_SURVEY.md`](MANGAYOMI_SURVEY.md).

## Working agreement (from the maintainer)

- Coordinator (planning, task instructions, code review) runs on Opus at medium effort; coding is
  delegated to sub-agents on Opus at high effort. Earlier the coordinator was Fable 5.2 medium, then Opus max.
- After each coding step: review it (`/code-review medium <range>`), then have a sub-agent fix the
  findings, then re-report the findings with outcomes.
- Commit and push after every step (and in small increments) so work survives usage-limit stops.
  Push only to `claude/kmp-multiplatform-support`. No Claude attribution in commits. No PR unless asked.
- Verification is compile/unit-test only: `spotlessApply` → `spotlessCheck`, module compile and test
  tasks, `:app:compileDebugKotlin`. No `assemble*`, no emulator, no `:desktopApp:run` until the
  maintainer asks. Use `--max-workers=2` (memory is tight) and `./gradlew --stop` between heavy runs.
- Don't ask for approval unless something would break the app; decide and record decisions here or in
  PROGRESS.md.
- When the usage limit hits, background agents keep their progress: resume them with SendMessage after
  the reset instead of starting new ones.

## Done

### Desktop app (Phases 0–9 of PLAN.md)
- PR #1671 rebased on master; Compose Multiplatform 1.12.2; `mihon.library.kmp` convention plugin
  (path-preserving KMP: `src/main` → `jvmSharedMain`).
- `core:common`, `source-api` (extension ABI unchanged, verified with `javap`), `core-metadata`,
  `core:archive`, `domain`, `data`, `source-local` compile for Android + desktop.
- `:desktop:android-compat` (Android FQN shim), `:desktop:compose-compat`, `:desktop:extension-runtime`
  (APK parsing, signatures, dex2jar, class loader, `linkCheck` task), `:app-shared` (source borrowing
  from `app` via `app-shared/borrowed-app-sources.gradle.kts`, checked by `:app:verifyBorrowedSources`).
- `:desktopApp`: library (Android's search/filter/sort/display), manga details, updates, history,
  browse + extensions, reader (paged + webtoon), downloads, backup, trackers, settings; packaging
  config and `build_desktop.yml`. Never launched; workflows never executed.
- Shared Android screens (Phase 6) deferred until the upstream ViewModel/Metro sync (PLAN "Gate outcome").

### iOS plan / JavaScript sources (PLAN.md section 8)
- **I0** contract + survey done (114 JS manga entries from 18 files; Apache-2.0).
- **I1/I2** `:source-js` (KMP host for Mangayomi JS extensions on `quickjs-kt` 1.0.15): engine,
  prelude, bridges, typed results, conformance tests. Reviewed at max level (15 findings) and all
  fixed in `cbe98eae24`, `2547914b21`, `195493a0f5`, `b5f7923dd7` (error envelope, hidden bindings,
  prototype lockdown, parity fixes, bytecode cache, `JsExtensionHost.extension(source, code)` →
  `JsExtension` with `metadata()`, CI runs `:source-js:desktopTest`).
- **I2b** (maintainer decision: switch Android to quickjs-kt): new `:core:quickjs` with a
  binary-compatible `app.cash.quickjs.QuickJs` (engine thread per instance, `set`/`get`, finalizer);
  old `quickjs-android` AAR removed; ProGuard keeps added; `:source-js` has an Android target; Android
  unit tests swap in quickjs-kt's JVM artifact. Commits `4f0c9489c3` … `712fa9294c`.

- **I3** source adapter (`c259852190` … `6c45bdf10a`, PROGRESS.md "I3 — source adapter"):
  `app.komikku.sourcejs.mihon.MangayomiJsSource` (an `HttpSource` + `ConfigurableSource` over a `JsExtension`: ids
  from `MD5("mangayomi-js/<id>")`, cached metadata refreshed after preference changes, filters and preferences
  mapped both ways, the source's `source_<id>` preference file shared with the settings screen, page headers in
  `Page.url`, details merge keeping what the source leaves out, empty pages for missing latest / search, network
  failures as `IOException`) and `MangayomiJsRuntime` (one host on the app's client and preference files).
  `:app` / `:desktopApp` depend on `:source-js`; ProGuard keeps `app.komikku.**` serializers. Proven headless on
  desktop (`JsSourceBrowseUiTest`: browse, details, library, settings, chapter loader); not in the source managers
  yet (I4).
- **I3 review** (max level, 12 findings) — all fixed in `6bdcd94` … `9efd135` (PROGRESS.md "Review fixes (I3)"):
  own download folder (`toString()` = `"<name> [JS] (<LANG>)"`, `stubName`), settings screen safe against `%` in
  list summaries and stored values of the wrong type, empty select filters as headers, typed first metadata read,
  only the missing member itself counts as no latest / no search, a runtime-wide call limit (4) next to the
  per-source one (2), `http(s)`-only covers / page images, header values with control characters dropped, failed
  metadata refreshes retried after 60 s, `exh.log` logging, the browse test closes its runtime.

- **I4** repository store and UI (`5780d0e` … , PROGRESS.md "I4 — repository store and UI"): index / `repo.json` /
  version / status logic in `:source-js` (`app.komikku.sourcejs.repo`); `MangayomiSourceStore` in `:app-shared`
  (`app.komikku.shared.jssource`: repositories in the `mangayomi_js_repos` preference, installed sources as files under
  `<filesDir>/mangayomi-js/` with atomic writes, `https` only, size limits, two-step install bound to the confirmed
  SHA-256, statuses up to date / update / text changed / obsolete, NSFW hidden, no auto-update, one runtime);
  sources merged into `AndroidSourceManager` / `DesktopSourceManager` with `StubSource(id, lang, stubName)` stubs; a
  shared screen model and Compose list, hosted by the Android `mihon.feature.jssources.JsSourcesScreen` (Extensions
  tab overflow, Browse settings) and a third desktop browse tab; "JS" label, notes banner, Android source icons.
  Android UI compile-only.
- **I4 review** (medium, 9 findings) — all fixed in `6337d12` … `a1ef663` (PROGRESS.md "Review fixes (I4)"): store
  file errors as `STORAGE_FAILED` and no crash from any failure in the screen model, the source managers wait for
  the loaded JavaScript sources (at most 15 s; a broken store counts as loaded-empty), refresh merges into the current
  repositories, a failed update keeps the old source open and registered, the desktop JS model is created with its
  tab and the store lookup is guarded, the install dialog shows the post-redirect host, obsolete only once every
  repository was read, no stale icon, `repo.json` URL resolution for index URLs without a path.

### Desktop parity
- **P1** global search and source toggles (`5137a56` … `2e39d59`, PROGRESS.md "P1 — global search and source
  toggles"): Android's `SearchScreenModel` / `GlobalSearchScreenModel`, `SourcesFilterScreenModel`,
  `GetEnabledSources`, `GetLanguagesWithSources` borrowed (`desktopCopy`), the toggle interactors and the global search
  result items `shared`, a desktop `LocaleHelper` counterpart. Desktop sources tab on `GetEnabledSources` with
  Android's grouping (last used, pinned, languages), pin button, long-press / right-click pin and disable, source
  filter screen (languages, all sources, single sources), global search (per-source rows with progress, results,
  errors; pinned / all / has results; row → the source's search, result → details; from the sources tab and the
  details title; JavaScript sources included). Headless tests `SourcesUiTest`, `GlobalSearchUiTest`. The sources tab
  now hides disabled languages / sources like Android.
- **P1 review** (medium, 6 findings) — all fixed in `03642a4`, `7d5b984`, docs `3240a78` (PROGRESS.md "Review fixes
  (P1)"): global search re-runs when the source filter reaches the state (first search from a title, chip clicks), the
  sources tab shows an error and recovers on the next preference change, fresh source lookups, only installed sources
  become the last used one, `hasPinnedSources` remembered.
- **P2** library grouping and batch actions (`a60bb21` … , PROGRESS.md "P2 — library grouping and batch actions"):
  Android's grouping (`groupLibraryItems` in `LibraryFilterSort.kt`) and selection / batch-action bodies (new KMK file
  `LibraryActions.kt`) moved out of `LibraryScreenModel` (Android calls them, behaviour unchanged) and borrowed with the
  change-category / remove dialogs and the download menu; desktop Group tab, pages per group, selection (long press,
  click, Shift-click range, Ctrl-click, select all, invert), selection bar (set categories, mark read / unread,
  download, update, remove). `LibraryUpdateRunner.Target.Entries`. Headless test `LibraryGroupSelectionUiTest`.
- **P2 review** (medium, 8 findings) — all fixed in `2d720a7`, `d8a8329`, `e9b4313`, `84508c4` (PROGRESS.md "Review
  fixes (P2)"): no range-selection crash without an anchor on the page (Android too), the effective grouping in the
  desktop state, group pages update through `Target.Group` (restrictions, `groupLibraryUpdateType`), the selection bar
  stays on an empty page, "update already running" keeps the selection, press modifiers reset on long press,
  `groupLibraryItems` suspends instead of `runBlocking`, no new SY markers in Komikku-owned files.
- **P3** scheduled updates and backups (`80c64c6` … , PROGRESS.md "P3 — scheduled updates and backups"): an
  app-lifetime `DesktopScheduler` (injected clock, catch-up once at start, no overlap, WorkManager-like busy / failed
  handling) runs the library update every `autoUpdateInterval()` hours (`Target.All`, due from `lastUpdatedTimestamp`,
  which every desktop update now stores) and the automatic backup every `backupInterval()` hours (borrowed
  `BackupCreator` in automatic mode, the backup jobs' single slot); settings entries (Library: interval, grouped-update
  mode; Data and storage: backup frequency, last automatic backup) and "Library last updated" in the updates list. No
  device restrictions on desktop; only while the app is open. Headless tests `DesktopSchedulerTest`,
  `DesktopScheduledJobsTest`, `ScheduleSettingsUiTest`.
- **P3 review** (medium, 8 findings) — all fixed in `d6110e7`, `3121d4e`, `560bd49`, `8f936c1` (PROGRESS.md "Review fixes
  (P3)"): a user-cancelled automatic backup waits a whole interval, updates wait for the sources (bounded), a taken backup
  slot shows Android's message, the scheduler owns the scheduled update's due time, exit stops and joins the work and
  automatic backups are written to a temporary file and rotated afterwards, clock-back recovery, `onStarted` before the
  progress, "Just now" refreshes.
- **P4** delegated sources (`678778d` … , PROGRESS.md "P4 — delegated sources"): SY's delegates (Pururin, MangaDex, 8Muses,
  NHentai, LANraragi), their URL import (`GalleryAdder`), MangaDex's API layer and the real MDList tracker borrowed
  (`desktopCopy`); `DesktopDelegatedSources` wraps matching extension sources in `EnhancedHttpSource` like
  `AndroidSourceManager` and publishes SY's id lookups (library search by tags, update exclusion); "Enable delegated
  sources" in Settings → Browse; MDList login by OAuth paste through the MangaDex source; desktop library updates skip
  `LIBRARY_UPDATE_EXCLUDED_SOURCES`. Headless tests `DelegatedSourcesTest` and additions to `TrackingTest`,
  `LibraryUpdateRunnerTest`, `SettingsUiTest`. No metadata view / page previews / MangaDex screens on desktop yet.
- **P4 review** (medium, 8 findings) — fixed in `9734788`, `7aee688`, `9c1a2d2` (PROGRESS.md "Review fixes (P4)"): MDList
  logout through the MangaDex source and the interceptor reset, MDList login gated on a usable MangaDex source (specific
  reason, refused before the browser step), `Throwable`-safe delegation and per-source guards in the source manager, the
  borrowed `MdList` no longer caches a missing MangaDex source (KMK, Android too), the excluded update sources read after
  the sources are ready, selected excluded entries reported as skipped. Left: the cause of a failed MDList code exchange
  stays in the borrowed helper's log (the desktop says to restart from the browser step).
- **P5** AVIF / JPEG XL (PROGRESS.md "P5 — AVIF / JPEG XL images"): no acceptable desktop decoder (Skiko's Skia has no
  such codecs; NightMonkeys needs Java 22 FFM and system libjxl / libheif; the rest is Android-only), so the desktop
  detects AVIF / JPEG XL / HEIF and shows the format on covers (`CoverImage`, Coil `UnsupportedImageFormatDecoder`) and an
  "can't be shown on desktop yet" reader page. Test `UnsupportedImageFormatTest` with real embedded samples.
- **P5 review** (medium, 4 findings) — all fixed in `26ad164`, `eb6c2d9`, `ac074ee` (PROGRESS.md "Review fixes (P5)"):
  Skia gets the first try for AVIF / JPEG XL / HEIF (covers and reader), only its failure becomes the format error
  (macOS / Windows behaviour unverified, decided at run time); OS-independent tests with an injected failing decode
  step; `CoverImage` sizes like a bare `AsyncImage`; desktop `ftyp` detection scans the whole box (up to 256 bytes),
  AVIF brands first (Android's native detection left unchanged).

- **P6** app update check (`9f4a7ec` … , PROGRESS.md "P6 — app update check"): Android's `GetApplicationRelease` and
  `ReleaseServiceImpl` (new defaulted download-link selector, KMK; Android unchanged) with the desktop's own version, behind
  Android's `-Penable-updater` flag (off by default; `-Pdesktop-preview` = preview channel); a release counts only with an
  installer for the running OS. Start-up check plus a 3-day `DesktopScheduler` task (Android's throttle), Settings → About
  "Check for updates", an update dialog with the release notes and "Open on GitHub" (browser; **no in-app download /
  install on desktop**, decided). The release side does not exist yet: `build_desktop.yml` uploads workflow artifacts only,
  so nothing is found until desktop installers are attached to the GitHub releases (PROGRESS "What the release pipeline
  still needs"). Tests `DesktopAppUpdateCheckerTest`, `AppUpdateUiTest`.
- **P6 review** (medium, `9f4a7ec^..ff465c0`, 8 findings) — all fixed in `c0ad31b`, `3a9a712`, `921518b` (PROGRESS.md
  "Review fixes (P6)"): "Open on GitHub" closes the dialog only when the browser opened (otherwise the address as
  selectable text); a `-Pdesktop-preview` build fails on a shallow clone / without a commit count and the desktop
  workflow checks out with `fetch-depth: 0`; the last-check time is written only after a check that got an answer, a
  failure retries after 10 minutes, and automatic checks are throttled to one per two days by that time (an up-to-date
  app no longer asks GitHub at every start); installers are matched by CPU architecture (Linux package format not
  distinguished); releases with a tag the interactor cannot compare are skipped on the desktop (Android's
  `isNewVersion` unchanged); no "(r0)" in About; the `main` comments are next to their statements.

### Security decision on record
quickjs-kt (1.0.15 and 1.0.18) turns a JS `Error` reaching the JVM into
`NewObject(FindClass(error.name), (String) ctor)` — script code can construct arbitrary classes
(proved: `FileOutputStream` creates/truncates files). `:source-js` closes the paths through its own
code; unhandled rejections and the `app.cash.quickjs` API stay exposed. The maintainer accepted this
and chose to switch anyway (PLAN 8.7 item 5). Fix = one native check
(`IsAssignableFrom(cls, java/lang/Throwable)` in `js_error_to_java_error`); disabled regression tests
mark the residual path in both modules.

### Review of `b019b0e402..712fa9294c` (I1/I2 review fixes + I2b) — done
- Max-level review (finders, runtime verification, gap sweep): 2 known + ~60 candidates; fixed in the commits
  after `cab5b6d` (each `fix(kmp)` / `test(kmp)` / `refactor(kmp)` / `ci(kmp)` / `docs(kmp)` commit names its
  theme). What was fixed, what is documented as a deviation and what is left: PROGRESS.md "Review fixes (I2b)".
- Highlights: `QuickJs` completion values are converted in JavaScript (objects no longer throw or run getters; no
  JVM crash from completion values), `close()` ends stuck calls, quickjs-kt engine create/close serialised
  process-wide with a sentinel (the concurrent-create JVM crash is gone under stress and `-Xcheck:jni`), proxies
  keep their instance alive, idle engine threads end; `:source-js` wrapper escape closed, only
  `JsSourceException`s leave a call, settled calls return, host promises handled, HTTP body cap tied to the heap,
  adapter API (`NotImplemented`, `sourceId`, tolerant `metadata()`, `search(null)` in one context).
- New residual risks for the maintainer (PLAN 8.7 item 5): a page script can still crash the app through
  quickjs-kt's non-`Throwable` `Throw` (thrown values / rejections with throwing getters); a per-create JNI
  reference race remains; QuickJS out-of-memory crashed the JVM depending on code shape (guarded by
  `OutOfMemoryCrashTest`, cause unknown).
- The light Android checks `:app:checkDebugDuplicateClasses` and `:app:mergeDebugJavaResource` pass.

## Left to do (in order)

1. Re-report the review's findings with their outcomes (coordinator), then decide on the residual risks above.
2. **I3** source adapter and its review fixes: done (see "Done"). Known gaps: PROGRESS.md "I3 — source adapter" and
   "Review fixes (I3)" (left open).
3. **I4** repository store + UI and its review fixes: done (see "Done"); the coordinator re-reports the findings with
   their outcomes. Known gaps: PROGRESS.md "I4 — repository store and UI" and "Review fixes (I4)" (Android UI not run,
   sources not in backups, `TEXT_CHANGED` costs a download per installed source per refresh, start-up may wait up to
   15 s for a slow store, no deep link / update badge). Milestone I-A (PLAN 8.4) then needs a first real run.
4. **Desktop parity gaps** (PROGRESS "Remaining parity gaps"): P1 (global search, source / language toggles, pins) and
   its review fixes are done; P2 (library grouping, selection and batch actions) and its review fixes are done; P3
   (scheduled library updates and backups) and its review fixes are done (last review-fix commit `ddfd839`); P4 (SY
   delegated sources) and its review fixes are done (last review-fix commit `9c1a2d2`). P5 (AVIF / JXL) and its review
   fixes are done (no decoder; Skia first, format state on failure; last review-fix commit `ac074ee`). P6 (app update
   check) and its review fixes are done (last review-fix commit `921518b`). **Next, in order:** WebView / Cloudflare
   (desktop `CloudflareInterceptor` is a pass-through); signing / notarization of the installers; the release pipeline
   for desktop installers (attach the `build_desktop.yml` outputs to the GitHub releases, built with `-Penable-updater`,
   names with the OS extension and, for per-architecture builds, the architecture; PROGRESS "What the release pipeline
   still needs"), which the update check needs to ever find something; then the
   maintainer's first real run (item 7). Known gaps: P6's (no real request against `api.github.com`, the dialog not
   shown in a real window, no build with `-Penable-updater` / `-Pdesktop-preview` run; Android's own
   `GetApplicationRelease.isNewVersion` still throws on an odd tag), P5's (macOS / Windows
   Skia behaviour for AVIF / JPEG XL unverified, see the CI test output; no real AVIF cover loaded), P4's
   (no metadata view / page previews / MangaDex settings, follows and similar screens / batch add on desktop; not run
   against real extension APKs or servers), and: no bulk favourites in global search, no "search
   globally" from the library, no SY source categories / data-saver exclusion; no migrate / merge / recommendations /
   SY title cleaning / MangaDex follow / reset info from the library selection; desktop UI not run in a real window.
   After WebView / Cloudflare and signing: notifications / tray.
5. I5–I7 (iOS core, iOS app, macOS CI) need a macOS host; re-check the JS catalogue size first (PLAN 8.9).
6. Optional: report the quickjs-kt bugs upstream (draft in `QUICKJS_KT_SECURITY_REPORT.md`, with the crash and
   the JNI state race added; the maintainer submits it).
7. First real run by the maintainer: `./gradlew :desktopApp:run`, Android `assembleDebug` on a device
   (QuickJS switch is unverified on device and under R8).
