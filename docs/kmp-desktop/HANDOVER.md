# Komikku Desktop / JS Sources — Handover

State as of 2026-11-24, branch `claude/kmp-multiplatform-support` (the I2b review and its fixes, step I3 and its
review fixes, step I4, the repository store and UI, with its review fixes, desktop parity step P1, global search
and source toggles, with its review fixes, parity step P2, library grouping and batch actions, with its review
fixes, parity step P3, scheduled library updates and backups, with its review fixes, parity step P4, SY delegated
sources, with its review fixes, parity step P5, AVIF / JPEG XL, with its review fixes, parity step P6, the app
update check, with its review fixes, parity step P7, WebView / Cloudflare, with its review fixes, are done, and parity
step P8, automatic Cloudflare solving through a FlareSolverr-compatible service, with its review fixes, are done, and
step P9, the desktop release pipeline and signing hooks, with its review fixes, is built but never executed, and step
P10, manga details parity (duplicates on add, edit info, cover actions), with its review fixes, is done, and step
P11, reader parity (neighbouring chapters in the viewers, wide pages, animated pages), with its review fixes, is done
(last Part A commit `0d88469`), and step P12, desktop Settings → Advanced and library extras, with its review fixes, is
done (last Part A commit `0f596c4`); P13 (feed and saved searches) is under way: saved searches in the desktop catalogue
and their review fixes (with a filter panel) are done, the global Feed tab and its review fixes are done (last Part A
commit `e4f266a`), the per-source feed and "add to feed" from the catalogue (P13 rest) and P14 (browse leftovers) and their review fixes
are done (last Part A commit: the docs commit "Record the P13 rest / P14 review fixes"); next: the remaining parity gaps
from P15 on (settings search and app lock: built, not reviewed; next notifications / tray, source migration), then the maintainer's first real run of
the workflow and of the app; see "Done" and "Left to do").
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

- **P7** WebView and Cloudflare (`2093b68` … , PROGRESS.md "P7 — WebView and Cloudflare"): **no embedded browser**
  (evaluated: jcefmaven = the candidate for later, 118–138 MB natives per platform, JVM flags, macOS helper signing;
  KCEF stalled since 2025-03; JavaFX WebView = WebKit, no Linux / Windows arm64; FlareSolverr = cheapest automatic
  backend to add next). Built: a `BrowserBackend` abstraction in `core:common` desktop (`app.komikku.network.browser`,
  `BrowserBackends.current` = none), the desktop `CloudflareInterceptor` on it with Android's detection shared
  (`CloudflareChallenge`, Android unchanged), `cf_clearance` into the shared jar, one retry, `CloudflareBypassException`
  with an actionable message when there is no backend / the check is not passed; "Open in WebView" (catalogue top bar and
  error screen, details) = system browser + a dialog for pasted cookies (registrable domain, one year) and the
  User-Agent (`default_user_agent`, restart for extension sources). Tests `DesktopCloudflareInterceptorTest` (11),
  `DesktopWebViewTest` (7), `WebViewUiTest` (1), `CookieManagerTest` +1. Extensions using `android.webkit.WebView` still
  fail at that call (`NoClassDefFoundError`; what is needed is in PROGRESS).
- **P7 review** (medium, `2093b68^..efae9ab`, 10 findings) — all fixed in `ed37378`, `fc72df6`, `6c8bc49` (PROGRESS.md
  "Review fixes (P7)"): a failed solve is remembered per host for 45 s (waiters and followers fail at once; a new
  clearance or a manual save clears it); the solver's / a pasted User-Agent is kept per host, bound to a hash of its
  clearance, persisted (`desktop_cloudflare_user_agents`) and sent to that host's requests while the clearance is in the
  jar, also for sources with their own User-Agent (no restart; the default User-Agent only with an explicit checkbox);
  the challenged redirect target is the one solved; pasted cookies host-only except Cloudflare's `cf_*` / `__cf*`, and
  replaced when the site sets its own (`DesktopCookieManager.setPastedCookies`); unusable pasted entries skipped and
  named instead of rejecting the paste; a blank User-Agent keeps the current one; details resolve the address on click
  off the UI thread; the WebView progress dialog is cancellable and never stuck; honest messages after an embedded
  session. Last Part A commit `6c8bc49`.
- **P8** automatic Cloudflare solving (`1be55e8` … , PROGRESS.md "P8 — automatic Cloudflare solving through a
  FlareSolverr-compatible service"): `FlareSolverrBackend` (`POST /v1`, `request.get`, cookies + `userAgent` mapped to
  the browser-session result, ≥ 60 s solve time, clear errors for not configured / not `http(s)` / unreachable / error
  answer / timeout, no credentials in logs) installed as `BrowserBackends.current`; Settings → Browse "Cloudflare solver
  (FlareSolverr)" with the address (desktop preference `desktop_flaresolverr_url`, empty = off; no Android key exists),
  "Test connection" and the privacy note. With it, browse, library updates, global search and downloads get past
  challenges; "Open in WebView" stays the manual fallback (the solver shows no pages). Tests `FlareSolverrBackendTest`
  (9, `MockWebServer` as the solver), `FlareSolverrSettingsUiTest` (1). Not run against a real solver.
- **P8 review** (medium, `6c8bc49..65ccc46`, 8 findings) — all fixed in `90ce3b8`, `26c5bec`, `b613e10`, `f761ac7`
  (PROGRESS.md "Review fixes (P8)"): cookie expiry from `expires` or `expiry` (FlareSolverr v3); a connect timeout is an
  unreachable solver (`EventListener`), a timeout after connecting stays a timed-out solve; a cancelled request cancels
  the solver call (polled) and starts no solve; backend failures (`BrowserUnavailableException`) are not remembered per
  host and another solver address forgets the remembered failures; `…/v1/` and doubled slashes normalised; safe JSON
  casts everywhere and a caught "Test connection"; typed `FlareSolverrException` reasons with `KMR` texts, no doubled
  period; the address' password masked (`••••`) in the subtitle and the edit field, kept on save. Found and fixed in the
  round: the address could not be emptied from the settings ("Disable" entry). Last Part A commit `6112d7b`.
- **P9** desktop release pipeline and signing hooks (`c712c3f` … , PROGRESS.md "P9 — desktop release pipeline and
  signing hooks"): `build_desktop.yml` builds with `-Penable-updater` on Linux x64, Windows x64, macOS arm64 and (new)
  macOS Intel x64, names the assets `Komikku-<v>-<os>-<arch>.<ext>` (`.github/scripts/desktop_release_assets.sh`;
  Linux app image packed as `.tar.gz`, now matched by the update check) with SHA-256 sums, uploads them as workflow
  artifacts and, only with `release_mode` `existing` / `draft` (manual input, or the `DESKTOP_RELEASE_MODE` variable
  for `desktop-v*` tags), to the `v<versionName>` release of `komikku-app/komikku` (never publishes or edits a release,
  never replaces a published file, only `Komikku-<v>-…` names). Signing hooks, off without settings: macOS signing +
  notarization through the Compose DSL from `KOMIKKU_MAC_*` / `komikku.desktop.mac*`, Windows `signtool` in the workflow
  (the plugin has none); secrets and how to make them in BUILDING.md "Signing". Test: `DesktopAppUpdateCheckerTest`
  "the release pipeline's asset names …". **Never executed**; what the maintainer must do for the first run: PROGRESS
  P9 "What the maintainer must do before the first run".
- **P9 review** (medium, `6112d7b..fa491eb`, 7 findings) — all fixed in `32fd74c` and the docs commit after it
  (PROGRESS.md "Review fixes (P9)"): in a release mode `plan` fails unless the release tag (and for `desktop-v<x>` also
  `v<x>`, decided: same commit required) points to the run's commit; releases found through the API list (drafts
  included), any request failure is an error, never a second draft; a published release only gets its missing files,
  existing ones compared by SHA-256 (`digest` or download), a difference fails before any upload, checksum file last
  (new `.github/scripts/desktop_release_upload.sh`); no workflow-level concurrency: packaging cancelled per ref and
  runner, the release job in a per-tag group that never cancels; suffixed `versionName`s refused before the build
  (decided: unsupported); `printVersionName` prints `desktopVersionName=<v>`; checksum lines normalised to
  `<hash>  <name>` and sorted by name. Checked with local simulations (stub `gh`, stand-in `sha256sum` / `./gradlew`, a
  local tag repository), `actionlint` 1.7.12, `shellcheck`; still never executed.

- **P10** manga details parity (`8704044` … , PROGRESS.md "P10 — manga details: duplicates, metadata editing, cover
  actions"): Android's `GetDuplicateLibraryManga` before "Add to library" with a desktop duplicate dialog (open the
  existing entry / add anyway / cancel; no migrate; the catalogue and global search add through the details); SY's
  edit info for library entries on the new shared `MangaInfoEdits` (KMK, `desktopCopy`; Android's
  `updateMangaInfo` builds its values with it, unchanged), stored in the same custom-info `edits.json` that backups
  carry (local entries: database + `ComicInfo.xml`); a cover dialog (view large, save the image file through a file
  chooser, set a custom cover from an image file via the borrowed `editCover` / `CoverCache`, delete it), the desktop
  cover loader serving custom covers first, custom covers dropped on removal like Android. Not done: autofill from
  tracker, share, migrate. Tests `DuplicateMangaUiTest` (3), `EditMangaInfoUiTest` (2), `MangaInfoEditsTest` (4),
  `MangaCoverActionsUiTest` (2). Found in SY's `CustomMangaRepositoryImpl`: resetting the last edited entry is not
  written to `edits.json` (fixed in the review round).
- **P10 review** (medium, `2115049..5311cf8`, 8 findings) — all fixed in `87cdb35`, `65b8e0b`, `35c5f80`, `6f3f43a`
  and the docs commits after them (PROGRESS.md "Review fixes (P10)"): the desktop cover keyer keys by entry while a
  custom cover exists (like Android's `MangaCoverKeyer`); "Save" uses the fetcher's disk-cache key and loads a
  memory-only cover again without memory reads; "Edit cover" accepts a file only when Skia decodes it on this machine
  (AVIF / JXL / HEIF stay in the chooser) and fails visibly for entries that cannot get a cover; "Edit info" stays
  open until saved, shows a failure with the input kept, and no longer overwrites a local entry's fresh row. **Two
  Android behaviour changes** (bug fixes, decided by the coordinator): a local entry saved with the "Default" status
  keeps its status (`MangaInfoEdits.localInfoEdit`), and removing the last custom info deletes `edits.json`
  (`CustomMangaRepositoryImpl`, KMK block). Last Part A commit `fe75585`.
- **P11** reader parity (`b4d2c57` … `d253e83`, PROGRESS.md "P11 — reader: neighbouring chapters, wide pages, animated
  pages"): the viewers hold the neighbouring chapters' pages like Android's adapters (new desktop
  `ReaderViewerItems`; preload near the end and on transitions; reaching a neighbour's page switches the current
  chapter with history / read timer like Android's `loadNewChapter`; skip preferences through `ReaderChapterList` as
  before); wide pages in the paged viewer (alone in double pages, "Split wide pages" with an `InsertPage` second
  half, "Rotate wide pages to fit", their inverts, page layout single / double / automatic by window orientation);
  animated GIF / WebP pages played from Skia's codec frames (memory limit 128 MB, else the first frame). Android code
  unchanged. **Desktop default change:** automatic layout = double pages in a landscape window. Tests
  `ReaderNeighbourChaptersTest` (4), `ReaderUiTest` (+1), `ReaderWidePagesUiTest` (4), `ReaderAnimatedPagesTest` (3).
- **P11 review** (medium, `fe75585..86b5d78`, 10 findings) — all fixed in `bcd9325`, `8fbfb74` and the docs commits
  after them (PROGRESS.md "Review fixes (P11)"): one navigation design in `ReaderModel` — the current item is the single
  source of truth, every change goes through `commit` (re-anchored by identity, normalised to its spread, progress /
  preload / switch for a newly shown spread), chapter switches serialised and following the item the viewer is on,
  one spread function (`State.spreads`) for page turns both ways, progress (a two-page spread's last page marks the
  chapter read) and preloading (neighbouring spreads at their widths, never a shown page); the zoom kept across list
  rebuilds; the previous chapter entered on its last item; animated pages show their first frame at once and decode
  the other frames only while shown, on a slot of their own, one frame set per page, any failure → first frame. Last
  Part A commit `0d88469`.
- **P12** advanced settings and library extras (`9e4c28a` … , PROGRESS.md "P12 — advanced settings and library
  extras"): desktop Settings → Advanced in Android's order (open the log folder of the new desktop log file
  `<data>/logs/komikku.log`, SY's log level applied at once, reindex downloads, clear database with Android's model
  rules, clear cookies, DNS over HTTPS, default user agent and its reset, the per-site user agents of P7 / P8 listed and
  removable, the FlareSolverr group and the delegated sources toggle moved there from Browse); Data and storage →
  clear chapter cache with its size and "clear on app launch"; library: continue reading button, "Open random entry",
  source icon badge (and their display settings). Not done: "Clear WebView data" (no embedded browser), language flag
  icons (Android drawables), continue reading by Android's chapter filters (the desktop details' rule instead). Tests
  `AdvancedSettingsUiTest` (3), `DesktopLogFileTest` (2), `LibraryExtrasUiTest` (3), settings tests updated.
- **P12 review** (medium, `0d88469..9bec249`, 6 findings) — all fixed in `246200c`, `1824d14`, `0f596c4` and the docs
  commits after them (PROGRESS.md "Review fixes (P12)"): the reader loads a chapter's page list single-flight (one
  in-flight load per chapter, registered before it starts, joined by preloads, adjacent loads and chapter switches); the
  source badge collects the installed extensions; the continue-reading click catches, logs and shows failures; "Clear
  database" runs on IO (non-cancellable) with progress and disabled buttons; the per-site user agents are a `StateFlow`
  collected by the settings; the log file and crash handler are installed before `DesktopInjekt.init()`. The once-seen
  `DesktopExtensionManagerTest` timeout: cause not evident, what was checked is recorded. Last Part A commit `0f596c4`.
- **P13** feed and saved searches — item 1 done (PROGRESS.md "P13 — feed and saved searches"): the desktop catalogue
  lists the source's saved searches (a "Saved Searches" row; no filter sheet on desktop yet), applies one (query and
  filters), saves the current search under a name with Android's dialog (blank / duplicate names rejected) and deletes
  one with Android's dialog. Same `saved_search` storage and `FilterSerializer` JSON as Android (`GetExhSavedSearch.kt`,
  `BrowseSourceDialogs.kt` now shared borrowed sources; `FilterSerializer` / `GetExhSavedSearch` in the desktop DI), so
  backups carry them. Test `CatalogueSavedSearchUiTest` (1). Verification: `spotlessApply`, `spotlessCheck`, `:desktopApp:compileKotlin`,
  `:app:verifyBorrowedSources`, `:app:compileDebugKotlin` pass; `:desktopApp:test --tests '*CatalogueSavedSearchUiTest*'
  --tests '*BrowseCatalogueUiTest*'` 2 tests, 0 failures. Not run: the full `:desktopApp:test` suite, a real window.
  Commit `2a42392` (review range `2a42392^..` the docs commit after it).
  Not done: the global Feed tab in Browse, the per-source feed.
- **P13 saved searches review** (medium, `2a42392^..4337c40`, 6 findings) — all fixed in `cda5152`, `145f3c9` and the
  docs commits after them (PROGRESS.md "Review fixes (P13 saved searches)"): a desktop **filter panel** (toolbar button
  with an "active" badge; Android's filter rows, moved into the shared `SourceFilterItems.kt`; Reset, Save, Filter, the
  saved searches moved into it), fresh `getFilterList()` per request unless the user applied filters (the panel edits a
  copy; refreshed after the source's settings), every `getFilterList()` guarded off the UI thread (errors included),
  superseded saved-search applies cancelled, the search field re-synced only by an explicit event, no filter change
  without a restart. **Android behaviour change** (crash fix): a `Filter.Select` without options shows its name in
  Android's sheet instead of crashing. Last Part A commit `5ebb0da`.
- **P13 Feed tab** (`116039e` … , PROGRESS.md "Feed tab (P13 continued)"): a Feed tab in desktop Browse on the same
  `feed_saved_search` rows and interactors; the platform-neutral part of Android's `FeedScreenModel` moved into
  `FeedItems.kt` (`desktopCopy`, Android calls it, behaviour unchanged) and Android's add-search / delete dialogs shared;
  rows load one at a time like Android, with loading / empty / error states; a row opens the catalogue with its saved
  search (`CatalogueScreen(savedSearchId)`) or its latest updates, an entry the details; add (source → latest / popular /
  saved search) within Android's limit, reorder (move up / down), delete. Test `FeedUiTest` (2). Found in the full run:
  a start-up race in `DesktopExtensionManager` (fixed in `65779f3`). Full `:desktopApp:test` on `65779f3`: 67 classes,
  290 tests, 0 failures. Not done: the per-source feed.
- **P13 Feed tab review** (medium, `5ebb0da..8b6cbde`, 8 findings) — all fixed in `1cd37d0`, `1bba95e`, `642e5b5` and the
  docs commits after them (PROGRESS.md "Review fixes (P13 Feed tab)"): rows rebuilt (source, titles) when the sources
  change and on Refresh; a failure keeps the feed followed and shows an error state; the catalogue's pending saved-search
  opening tracked apart from loading (superseded → the listing starts; superseded source calls end quietly); the Feed
  tab's model created on first show, Android's "hide feed tab" / "feed tab in front" followed and offered in Settings →
  Browse; loaded rows kept across reorders / deletes, only new rows load; the add dialog's OK needs a source. **Android
  behaviour change** (bug fix, coordinator's decision): `isFeedFull` is `count >= MaxFeedItems`, the global feed refuses
  the 21st row. Full `:desktopApp:test` on `2d22abc`: 68 classes, 297 tests, 0 failures. Last Part A commit `e4f266a`.
- **P13 rest** (`74366d9` … , PROGRESS.md "Source feed (P13 rest)"): the source's landing page (Android's
  `SourceFeedScreen`: latest / popular / the source's own feed saved searches, search field into the catalogue, order /
  delete, filter button → the catalogue's filter panel), opened from the sources list while SY's "new source navigation"
  is on (Android's default; switch in Settings → Browse); "add to the source's feed" on each saved search of the
  catalogue's filter panel (Android's confirmation and limit). Android's `SourceFeedScreenModel` builds its rows with the
  shared `sourceFeedRows` / `fetchSourceFeedPage` (`FeedItems.kt`); **two small Android behaviour changes**: the source
  feed's limit is `isFeedFull` (`>=`), a row with a missing saved search is skipped instead of failing. Test
  `SourceFeedUiTest` (4).
- **P14** browse leftovers (`3eeca76` … , PROGRESS.md "P14 — browse leftovers"): bulk favourites (Komikku's borrowed
  `BulkFavoriteScreenModel`: duplicate check with P10's dialog plus "Allow all" / "Skip all" / "Skip it", Android's category
  rule and dialog) in the catalogue, global search, the Feed tab and the source feed; "Search globally" from the library
  search; the extension's name after the source's in global search rows. Desktop deviation: outside the selection mode a
  long press starts selecting (Android adds / removes that one entry). Test `BulkFavoriteUiTest` (4), feed tests +1 each.
  Full `:desktopApp:test` on `857aa60`: 70 classes, 306 tests, 0 failures.
- **P13 rest / P14 review** (medium, `e4f266a..9186dbf`, 6 findings) — all fixed in `1d15044`, `18fb8d0`, `bb716c6`,
  `96f73db`, `ce70339`, `f9e36c8` and the docs commit after them (PROGRESS.md "Review fixes (P13 rest / P14)"): three
  unused Android imports dropped (`spotlessCheck` had passed with them); the Feed tab and the source feed follow their
  rows again on Refresh after a read failure; the duplicate dialog's bulk choices wrap (`FlowRow`); "select inverse"
  compares by id (**Android behaviour change**, bug fix); order mode ends with nothing to order; a new catalogue listing
  ends the bulk selection (global search needed no change: no search runs while selecting). Full `:desktopApp:test` on
  `f9e36c8`: 70 classes, 313 tests, 1 failure (`ReaderNavigationTest`, passes alone: timing under load).

- **P15** settings search (`3682e16` … , PROGRESS.md "P15 — settings search"): Android's search (rules, breadcrumbs,
  highlighted index) moved into the shared `SettingsSearch.kt` (`desktopCopy`, Android calls it, unchanged); desktop
  Settings has the search button, results replace the search with their page, which scrolls to and highlights the item
  (`PreferenceList(highlightKey)`). Index in Android's order; tracking and data and storage indexed by their rows' titles
  (opened at the top). Test `SettingsSearchUiTest` (3). Not reviewed yet.
- **P16** app lock (`8603bab` … , PROGRESS.md "P16 — app lock (PIN / password)"): Android's lock rules (SY's times / days,
  "Lock when idle") moved into the shared `AppLockRules.kt` (Android's delegate calls it, unchanged); a desktop lock with
  the app's own PIN / password (PBKDF2 hash in an app-state preference, never in backups) on Android's "Require unlock"
  key, locked at start and by "Lock now", idle lock after the window was minimised, wrong entries throttled; a lock
  screen over the whole app (kept composed, inert); Settings → Security and privacy. No biometrics. Tests
  `DesktopAppLockTest` (5), `AppLockUiTest` (2). Not reviewed yet; the minimise wiring is not run in a real window.
  Full `:desktopApp:test` on `d8c42bb`: 73 classes, 323 tests, 0 failures. **Pending:** `/code-review medium 925e360..`
  the docs commit "Record the desktop app lock (P16)" and its fix round.

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
   check) and its review fixes are done (last review-fix commit `921518b`). P7 (WebView / Cloudflare) and its review
   fixes are done (last review-fix commit `6c8bc49`). P8 (FlareSolverr backend) and its review fixes are done (last
   Part A commit `6112d7b`). P9 (release pipeline for the desktop installers and signing hooks) and its review fixes
   are built, never executed (last Part A commit: see "P9 review"). P10 (manga details: duplicates, edit info, cover
   actions) and its review fixes are done (last Part A commit `fe75585`). P11 (reader: neighbouring chapters, wide
   pages, animated pages) and its review fixes are done (see "P11 review"; last Part A commit `0d88469`). P12 (Settings
   → Advanced, chapter cache, library extras) and its review fixes are done (last Part A commit `0f596c4`); **next** P13
   (feed and saved searches, PROGRESS "Remaining parity gaps" item 1): saved searches in the catalogue are built
   (see "Done"); the saved-search review fixes are done (last Part A commit `5ebb0da`), the global Feed tab and its
   review fixes are done; the per-source feed, "add to feed" from the catalogue and P14 (browse leftovers: bulk
   favourites, search globally from the library, extension names in global search) and their review fixes are done;
   P15 (settings search) and P16 (app lock) are built; **next**: the review of P15 / P16, then in this order —
   notifications and a tray icon for library updates / downloads, source migration (single entry from the details
   first); then the maintainer's
   first run of `build_desktop.yml` (PROGRESS P9 "What the maintainer must do before the first run": runner labels,
   a `release_mode: none` run, then `existing` from the tag on the Android draft, certificates for signing), and the
   first real run of the app (item 7). Known gaps: P6's (no real request against `api.github.com`, the dialog not
   shown in a real window, no build with `-Penable-updater` / `-Pdesktop-preview` run; Android's own
   `GetApplicationRelease.isNewVersion` still throws on an odd tag), P5's (macOS / Windows
   Skia behaviour for AVIF / JPEG XL unverified, see the CI test output; no real AVIF cover loaded), P4's
   (no metadata view / page previews / MangaDex settings, follows and similar screens / batch add on desktop; not run
   against real extension APKs or servers), and: no SY source categories / data-saver exclusion; no migrate / merge / recommendations /
   SY title cleaning / MangaDex follow / reset info from the library selection; desktop UI not run in a real window.
   P7's / P8's (no embedded browser: jcefmaven backend, PROGRESS P7 "What the embedded backend still needs"; without a
   FlareSolverr-compatible service automatic flows hitting a challenge need the manual step; the FlareSolverr backend
   not run against a real solver; no per-source opt-out; no Settings → Advanced network group; `android.webkit.WebView`
   for extensions; not run against a real Cloudflare site or in a real window). After signing: notifications / tray.
5. I5–I7 (iOS core, iOS app, macOS CI) need a macOS host; re-check the JS catalogue size first (PLAN 8.9).
6. Optional: report the quickjs-kt bugs upstream (draft in `QUICKJS_KT_SECURITY_REPORT.md`, with the crash and
   the JNI state race added; the maintainer submits it).
7. First real run by the maintainer: `./gradlew :desktopApp:run`, Android `assembleDebug` on a device
   (QuickJS switch is unverified on device and under R8).
