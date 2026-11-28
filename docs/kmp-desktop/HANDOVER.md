# Komikku Desktop / JS Sources — Handover

State as of 2026-11-24 (night), branch `claude/kmp-multiplatform-support` (the I2b review and its fixes, step I3 and its
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
from P15 on (settings search and app lock and their review fixes: done; notifications / tray (P17) and single-entry migration (P18) and their review fixes: done, last Part A commit `506da59`; the migration list and its configuration (P19) and its review fixes: done, last Part A commit `4ea5fda`; Browse → Migrate (P20) and SY source categories (P21) and their review fixes: done, last Part A commit `b8b0b07`; extension info and the sources' right-click menu (P22) and its review fixes: done, last code fix `9ae0d0b`, last Part A
commit the docs commit "Record the P22 review fixes"), then the maintainer's first real run of the workflow and of the
app; see "Done", "Next session" and "Left to do").
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
  Full `:desktopApp:test` on `d8c42bb`: 73 classes, 323 tests, 0 failures.
- **P15 / P16 review** (medium, `925e360..c2589d7`, 8 findings) — all fixed in `005e5a0` … `3238154`, tests `39b70ae` and the
  docs commit after them (PROGRESS.md "Review fixes (P15 / P16)"): the lock hides and disables the app's dialogs, menus and
  other popups (Compose draws them in scene layers above the window content; the gate wraps every layer of its content
  through Compose's internal scene context, read through its JVM accessor) and the toasts (host inside the gate), closes
  an open native file chooser; the lock is on while its secret is stored (Android's backed-up "Require unlock" is left
  alone, so a restore cannot switch it), the wrong-entry count and wait survive restarts (capped when the clock was set
  back); a search result highlights once; the results are remembered by query; the wait text formatted through its
  resource. **Two Android behaviour changes** (bug fixes): lock time ranges across midnight match, the settings search's
  highlighted index skips disabled groups. Full `:desktopApp:test` on `39b70ae`: 73 classes, 330 tests, 0 failures.

- **P17** notifications and tray (`482bdaa` … , PROGRESS.md "P17 — notifications and system tray"): Android's notifiers'
  texts (new chapters after a library update, update errors, downloader errors / warnings, backup / restore results, an
  app update) through a `DesktopNotifier` interface; the AWT system tray icon (`DesktopTray`, Settings → Appearance,
  default on; open / exit menu, update progress as tooltip) shows them and degrades to "no notifications" without a tray
  or headless; entry titles left out with Android's "Hide notification content" (now in Settings → Security) **or while
  the app is locked**. Test `DesktopNotificationsTest` (5). No real tray run. Not reviewed yet.
- **P18** source migration, single entry (`c8d5248` … , PROGRESS.md "P18 — source migration (single entry)"): Mihon's /
  Komikku's `MigrateMangaUseCase`, flag labels and "what to include" dialog borrowed (`desktopCopy`; the dialog made
  public); "Migrate" in the details of library entries opens a migration search (Android's `MigrateSearchScreenModel`
  rules on the borrowed `SearchScreenModel`, migration sources or else the global search's), a result → the dialog →
  Copy / Migrate → the new entry's details. Test `MigrateMangaUiTest` (3). Not built: the migration list and its
  configuration screen. Full `:desktopApp:test` on `550f9fa`: 75 classes, 338 tests, 0 failures. Not reviewed yet;
  review range of P17 + P18: `56d2012..` the docs commit "Record the desktop
  single-entry migration (P18)".
- **P17 / P18 review** (medium, `56d2012..d0b24d0`, 7 findings) — 6 fixed in `ac0c6de`, `9504aaf`, `02609c0` and the docs
  commit after them, 1 no change (PROGRESS.md "Review fixes (P17 / P18)"): no migration of an entry onto itself (dropped
  from the results, the click ignored, and the borrowed use case returns at once — **Android behaviour change**, bug fix);
  the use case returns whether it migrated and the dialog takes an optional `onFailure` (desktop: a message, the search
  stays; Android unchanged apart from a log line); Android's migration search has no pinned / all chips, the desktop's
  neither; each library update run has a `runId` and each backup result is notified once; no "null" chapter in download
  error titles. No change: the tray labels (the desktop has no app language change). Found in the full run: a test race in
  `DesktopNotificationsTest` (events emitted before the collectors subscribed). Full `:desktopApp:test` on `02609c0`: 75
  classes, 344 tests, 0 failures. Last Part A commit `506da59` (the docs commit; last code fix `02609c0`).

- **P19** migration list and configuration (`3cb9765` … , PROGRESS.md "P19 — migration list and configuration"): Android's
  `MigrationListScreenModel` (automatic matching, smart / deep search, throttle, skip, migrate / copy now and all), its
  dialogs, the options sheet and the configuration's screen model (moved unchanged out of `MigrationConfigScreen` into
  `MigrationConfigScreenModel.kt`, Android uses it) borrowed (`desktopCopy`); desktop `MigrationConfigScreen` (sources,
  order by move up / down, select pinned / none / enabled / all, source search) and `MigrationListScreen`; "Migrate" from
  the details and the library selection go through them, the migration search uses the migration sources only and hands
  its pick back to the list, a click on a duplicate in the duplicate dialog migrates it (details, bulk favourites). Not
  built: Browse → Migrate, merged sources. Tests `MigrationListUiTest` (2), `MigrateMangaUiTest` +1, `DuplicateMangaUiTest`
  +1, `LibraryGroupSelectionUiTest` extended. Review range `506da59..2c18c9d`.
- **P19 review** (medium, `506da59..2c18c9d`, 8 findings) — all fixed in `692412e` … `8fc858f` and the docs commit after
  them (PROGRESS.md "Review fixes (P19)"): the configuration and the list wait for the sources before creating their
  models; leaving the list closes only its own screens (and swaps the migrated entry's details right below it); after
  "Migrate / Copy now" on the only entry the new entry's details are shown (Copy too, like Android's "Copy"); room under
  the configuration's last row for its Continue button; failed migrations reported, failed entries kept, no details swap
  (**Android behaviour change**: the borrowed model's rules and a toast on Android's list too); an explicit "no migration
  sources" state in the migration search with a way to the configuration; the search's unreachable own dialog removed
  and its tests moved to the real details → configuration → list → search flow; KMK markers on the moved configuration
  model's declarations. Full `:desktopApp:test` on `8fc858f`: 76 classes, 352 tests, 0 failures. Last Part A commit
  `4ea5fda` (the docs commit "Record the P19 review fixes").
- **P20** Browse → Migrate (`74c0921` … , PROGRESS.md "P20 — Browse → Migrate"): Android's `MigrateSourceScreenModel`
  (`desktopCopy`), `GetSourcesWithFavoriteCount` (`desktopCopy`: it uses `source-local`) and `MigrateMangaScreenModel`
  (`shared`) borrowed, desktop counterparts of `installedExtension` and `SEARCH_DEBOUNCE_MILLIS`; a Migrate tab in Browse
  after Extensions (sources with library counts, not-installed / obsolete marks, sort by name / count both ways, source
  search, obsolete filter, guide link, long press copies the id) → a source's entries (click → configuration for it,
  cover → details, long-press selection, select all / invert, "Migrate" → configuration for the selection) → P19's
  configuration and list; the migration search's row header opens the source's own search, whose pick is the match
  (Android's `MigrateSourceSearchScreen`). Tests `BrowseMigrateUiTest` (2), `BrowseMigrateTabUiTest` (1),
  `MigrateMangaUiTest` +1. Not reviewed yet.
- **P21** SY source categories (`f54b2d8` … , PROGRESS.md "P21 — SY source categories"): SY's category interactors and
  `SourceCategoryScreenModel` borrowed (`shared`); Settings → Browse "Edit categories" (add / rename / delete with
  Android's dialogs) and "Filter sources in categories"; the sources tab's options dialog has "Categories" (Android's
  `SourceCategoriesDialog`), the existing grouping shows them. Test `SourceCategoriesUiTest` (2). Not reviewed yet;
  review range of P20 + P21: `4ea5fda..` the docs commit "Record Browse → Migrate (P20) and source categories (P21)".
- **P20 / P21 review** (medium, `4ea5fda..955f27c`, 7 findings) — all fixed in `eb44922`, `f18368c`, `6278d48`, `4e92853`,
  `ad88446`, `23e8082` and the docs commit after them (PROGRESS.md "Review fixes (P20 / P21)"): no bulk selection in a
  source's search opened from the migration search (click picks, long press opens the details); a category copy's
  `Source.key()` is `"<id>-category-<name>"` (a `lastused` category crashed the sources list, Android too); the obsolete
  filter keeps only stubs and obsolete extensions (no JavaScript sources) and follows the observed extensions; bottom
  padding under the "Add" button of the source and library categories; a failed read ends the Migrate tab's loading
  (`loadFailed`, `retry()`, desktop Retry); stubs first in both sort directions. **Three Android behaviour changes** (bug
  fixes): no crash for a `lastused` category, stubs first when descending, no endless spinner after a read error. Open:
  Android's obsolete filter still keeps JavaScript sources.
- **P22** extension info (`8377af6` … , PROGRESS.md "P22 — extension info and the sources' right-click menu"): Android's
  `ExtensionDetailsScreenModel` (`desktopCopy`) and `ToggleIncognito` (`shared`) borrowed; a desktop `ExtensionDetailsScreen`
  (warnings, package, version, language, age rating, Uninstall, the extension's incognito switch, its sources' switches
  and settings, enable / disable all, clear cookies) opened from the source options dialog, the sources' right-click menu
  (which also has SY's "Categories" now) and a click on an installed extension. No App info / WebView / repository
  actions. Test `ExtensionDetailsUiTest` (2).
- **P22 review** (medium, `b8b0b07..c968ba3`, 4 findings) — all fixed in `c995099`, `19f2eeb`, `9ae0d0b` and the docs commit
  after them (PROGRESS.md "Review fixes (P22)"): an extension update replaces its installed entry in one emission (the
  open extension info no longer closes, the source manager no longer drops and re-adds the sources); "Clear cookies" in
  the extension info clears through `DesktopWebView.clearCookies` for each source's base and home address (cookies for
  the registrable domain such as `cf_clearance` included), on IO, with Android's "Cookies cleared" toast; Uninstall asks
  first in the extension info and on the Extensions tab's rows. Android unchanged. Left open: cookies with their own
  non-root `Path` stay (Android too; desktop fixed in P23); the off-UI-thread clearing has no test. Full `:desktopApp:test` on `9ae0d0b`: 80 classes, 367
  tests, 0 failures.
- **P23** small open points from reviews (`83a12f0` … , PROGRESS.md "P23 — small open points from reviews"): "Clear
  cookies" on desktop (WebView dialog, extension info) is host-wide (`DesktopCookieManager.removeForHost`: every path,
  parent-domain cookies of the host, other hosts kept); Android's obsolete-only filter in Browse → Migrate keeps only
  stubs and obsolete extensions (**Android behaviour change**, bug fix, compile-checked only); Android's
  `MigrateSearchScreen` dialog branch verified unreachable (no change); an installed JavaScript source gone from every
  repository is obsolete in the desktop Migrate tab's filter and label; a test for the library categories' bottom
  padding. Not reviewed yet.

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

## Next session (start here)

- **2026-11-28: new set-up, read [`COORDINATION.md`](COORDINATION.md) first.** The branch is now `multiplatform`
  (renamed from `claude/kmp-multiplatform-support`; older entries below still use the old name). It is the permanent
  branch for every platform other than Android; Android stays on `master`, which is merged in regularly. Work is
  directed by the Architect (alias **Atlas**, cloud session, runs at 20:00 / 01:00 / 06:00 UTC+7) and carried out by
  the Delivery Lead (alias **Forge**, on the maintainer's Mac) and its team; they talk in the coordination thread
  (link in the next entry). The earlier separate Mac and iOS sessions are parked. Priorities: macOS solid → review the
  unreviewed work → Windows and Linux → a first iOS app → remaining Android parity; plus the standing task of merging
  `master`.
- **Coordination thread:** draft pull request <https://github.com/komikku-app/komikku/pull/2022> (number 2022, branch
  `multiplatform-coordination`, never merged). Atlas's instructions and Forge's reports are its comments; the rules
  are in COORDINATION.md "How we talk". Atlas's entries are also kept, numbered, in
  [`ATLAS_INSTRUCTIONS.md`](ATLAS_INSTRUCTIONS.md) (the authoritative copy; start with INSTRUCTION 001).
- **Forge's team roster (2026-11-28, second Forge session; INSTRUCTION 001, ANSWER 005).** One role per member;
  members do not post to the thread or push, commit only their own files, and run Gradle one build at a time through
  a shared lock. Every member runs on Opus 5.5, high effort: it is the only model this session can give its
  sub-agents. Members run only while they have work. The first session's team ended before its A1–A3, B and E
  outputs reached the branch; those tracks were started again.

  | Role | Model, effort | Now on |
  |------|---------------|--------|
  | Forge, Delivery Lead | Opus 5.5, high | planning, integration, the thread, pushes, PROGRESS.md / HANDOVER.md |
  | Upstream integrator | Opus 5.5, high | A1 / A3 and the #2023 check done (`UPSTREAM_AUDIT.md`); now the three Android fix patches (`android-fixes/`); A4 waits for Atlas's answer to the A3 question |
  | Reviewer | Opus 5.5, high | track B done (`REVIEW_M3.md`, `REVIEW_IOS.md`); now the instance lock, Linux notifications and the new CI jobs (`REVIEW_STEP_D1.md`) |
  | macOS engineer | Opus 5.5, high | C2 menu-bar template icon; C1 screen pass of the packaged app (`MAC_ON_DEVICE_PASS.md`) and its macOS fixes; the only role that drives the screen |
  | Test and release engineer | Opus 5.5, high | D steps 1–2 committed (Windows test job, smoke test); now the containment of the review's CI supply-chain finding and the results of desktop run 46117892032; owns `build_desktop.yml` |
  | Shared-code engineer | Opus 5.5, high | one running instance per data folder (ANSWER 005 item 4) |
  | Windows and Linux engineer | Opus 5.5, high | Linux notifications without a tray (ANSWER 005 item 3); the `--ui-smoke` design (`UI_SMOKE_DESIGN.md`, item 5) |
  | iOS engineer | Opus 5.5, high | track E done (`IOS_OPTIONS.md`); now the review's iOS findings (web-view leak, two lows; ANSWER 010); no new iOS app work until the maintainer decides (ANSWER 007) |
  | Extension runtime engineer | Opus 5.5, high | owns `:desktop:extension-runtime` and `:desktop:android-compat`: the review's three mediums, the compat-layer registry policy, the catalogue measurement (`EXTENSION_COMPAT.md`; ANSWER 010) |
- **Where each track's state is (INSTRUCTION 006; a new session resumes from these, it does not restart):**
  - A1–A3, the #2023 check, the two unchecked `master` items: [`UPSTREAM_AUDIT.md`](UPSTREAM_AUDIT.md) (status line
    at its top).
  - B, the review of the M3 range and of the iOS session's commits: [`REVIEW_M3.md`](REVIEW_M3.md),
    [`REVIEW_IOS.md`](REVIEW_IOS.md) (done; fixes being routed). Reviews of later steps: `REVIEW_STEP_*.md`.
  - Android fix patches for `master` (ANSWER 003): [`android-fixes/`](android-fixes/), prepared in a local checkout
    of `master` that is never pushed.
  - C1, the on-device pass of the packaged macOS app: [`MAC_ON_DEVICE_PASS.md`](MAC_ON_DEVICE_PASS.md). C2, the
    menu-bar template icon: the commits touching `desktopApp/.../notification/MacTrayIcon.kt`.
  - D: the plan and its CI notes in [`WINDOWS_LINUX_TEST_PLAN.md`](WINDOWS_LINUX_TEST_PLAN.md); the CI jobs in the
    commits touching `.github/workflows/build_desktop.yml` and `.github/scripts/desktop_smoke_*`; Linux notifications
    in the commits touching `desktopApp/.../notification/`; the `--ui-smoke` design in
    [`UI_SMOKE_DESIGN.md`](UI_SMOKE_DESIGN.md).
  - One instance per data folder (ANSWER 005 item 4): the commits touching `desktopApp/.../instance/`.
  - E, the iOS options report: [`IOS_OPTIONS.md`](IOS_OPTIONS.md). The iOS review fixes: the commits touching
    `source-js/**/ios*` and `.github/workflows/build_ios_check.yml` after `8e0f5b1`.
  - The extension runtime (ANSWER 010: path checks, converter, image formats, compat registry, catalogue
    measurement): [`EXTENSION_COMPAT.md`](EXTENSION_COMPAT.md) and the commits touching `desktop/extension-runtime`
    and `desktop/android-compat` after `8e0f5b1`.
  - A4, the first merge of `master` (approved in ANSWER 009): not started; it runs in a separate local checkout on a
    local branch and reaches `multiplatform` only when green.
- **Open pull requests into `master` (Android bug fixes; the maintainer reviews and merges):**
  <https://github.com/komikku-app/komikku/pull/2023> — 15 fixes found during the port (ATLAS_INSTRUCTIONS.md,
  DECISION 004).
- **Cold start after a pause (cloud session, review fixes M2, 2026-11-27):** read this entry first, whatever the pause.
  Branch `claude/kmp-multiplatform-support`, HEAD = the "Review fixes (M2)" docs commit (last code commit `18141a2`).
  The six fixable findings of the review of `cbdfdff..365f98a` are fixed (PROGRESS "Review fixes (M2)": osascript
  argument marker, "Show system notifications" setting, one file dialog at a time, surrogate-safe truncation, "handed
  to the system" KDoc, stack traces in the self-test report); the seventh needed a Mac, is in
  [`M2_REVIEW_FINDINGS.md`](M2_REVIEW_FINDINGS.md) and was since checked and accepted by the Mac session (see there).
  Last full local `:desktopApp:test` (Linux, on `18141a2`): 89 classes, 410 tests, 0 failures; `spotlessCheck`,
  `:app:verifyBorrowedSources`, `:desktopApp:compileKotlin` pass. The recurring scheduled tasks are disabled and no
  cloud run is scheduled. The self-hosted-runner change stays parked on `claude/kmp-self-hosted-mac-runner`. The Mac
  session owns the open work: [`MAC_SESSION_HANDOVER.md`](MAC_SESSION_HANDOVER.md) and its own notes (the M3 entry
  below).
  CI: newest green desktop run, the Mac build to use: <https://github.com/komikku-app/komikku/actions/runs/46057124295>
  (commit `33443c0`, the Mac session's `run 12` bump on top of these fixes; all four jobs green, 2026-11-27 19:43 UTC).
  It superseded (cancelled) this round's own run 46056229846 (`run 11`, `4db89c9`). The earlier Linux failure of
  `MangaCoverActionsUiTest` (run 46051450459) was fixed by the Mac session (`653ca54`).
- **M3 — real APK extensions on the Mac, 2026-11-28 (PROGRESS "M3 — real APK extensions on the Mac"):** the load
  failure is fixed: the converter writes stack map frames (ASM), retargets R8-inlined constructors, keeps `-` in member
  names and copies the APK's resources; the host has `okhttp-zstd`, `AppInfo`, `http.agent`, and the compat layer
  `LruCache`, `Activity`, `Bundle`, activity lifecycle callbacks and `ApplicationInfo`. 1,699 of Keiyoushi's 1,701
  extensions create their sources (EXTENSION_COMPAT.md; the 2 left need `android.graphics.ColorMatrix` and
  `android.icu.text.RuleBasedCollator`), and 275 of them need `android.graphics` classes to show pages; MangaDex, MangaPill and Weeb Central read live in the packaged app. CI's self-test now checks Hiperdex,
  Kagane, MangaDex and Weeb Central in the packaged app on every runner. A failed install shows its cause. Cmd+Q and
  the close button now run the bounded shutdown (it never ran before: Compose exited the JVM first), Cmd+H locks like
  minimising. Checked in the packaged app through the UI: Hiperdex and Kagane install and open (Hiperdex's images are
  blocked by its CDN, a source matter, not to be fixed; Kagane sits behind a Cloudflare challenge), Asura Scans reads a
  chapter, the native folder panel (New Folder, choose) and the library update notification (delivered as a Script
  Editor banner) work. Evidence was kept locally only. CI run 46051450459: green except one Linux test race in
  `MangaCoverActionsUiTest` (fixed in the test; the next run is started by the trigger bump after it). The M2
  review's open finding (folder panel left open when the app locks) is confirmed on the Mac and accepted
  (M2_REVIEW_FINDINGS.md). The cloud session's M2 review fixes were merged in; on the Mac, their `osascript` marker
  passes `-50kg Cinderella`, `-e`, `--` and `-` through unchanged (the old form failed with "illegal option"). After
  the merge: gates green, `:desktopApp:test` 410, `:desktop:extension-runtime:test` 25 (1 skipped),
  `:desktop:android-compat:test` 46, no failures; the packaged app's `--self-test` with Hiperdex, Kagane, MangaDex
  and Weeb Central passes on the Mac. CI run <https://github.com/komikku-app/komikku/actions/runs/46057124295>
  (`33443c0`, `run 12`): all four Package jobs green (Windows, Linux, macOS arm64, macOS x64). Not reviewed (range
  `debe34e..` the M3 docs commit). Next: the maintainer's re-test (TESTING_MAC.md "Re-test after M3"), then a review
  of the range.
- **2026-11-27: handed over to a session on the maintainer's Mac.** Start with
  [`MAC_SESSION_HANDOVER.md`](MAC_SESSION_HANDOVER.md): APK extensions still fail to load in the packaged app
  (`VerifyError: Expecting a stackmap frame` in a dex2jar-converted class, Hiperdex; not reproduced, not fixed), and
  the folder picker and macOS notification from M2 are unconfirmed. The recurring scheduled tasks are disabled; one
  last cloud run (2026-11-28 02:00 UTC+7) reviews `cbdfdff..365f98a` only.
- **M2 — fixes from the first Mac test, 2026-11-27 (PROGRESS "M2 — fixes from the first Mac test"):** the maintainer's
  four reports on the `.dmg` of run 45832027995 are fixed: (1) APK extensions failed to install (`cant find zipfs
  support`): the bundled runtime lacked `jdk.zipfs`; the module list is now `desktopApp/runtime-modules.txt` (every
  `java.*` module plus the service-loaded `jdk.*` ones; 121 MB runtime vs 116 MB before and 162 MB with
  `includeAllModules`), and the packaged app has a headless `--self-test` that CI runs with the packaged launcher on all
  four runners (modules, zip FS, dex2jar on a fixture APK, EC crypto, charsets, locale data, SQLite, QuickJS);
  (2) narrow window: Browse tabs one line and scrollable when they do not fit (`AdaptiveTabRow`), search placeholders
  and bar labels one line (`SingleLineText`), minimum window size 480 × 360 kept; (3) macOS folder chooser is the
  native panel (`FileDialog` + `apple.awt.fileDialogForDirectories`), Windows / Linux keep `JFileChooser`; (4) macOS
  notifications through `osascript` (fixed `on run argv` script, texts as arguments), used on macOS whether or not the
  tray icon is on. Template tray icon skipped (needs a monochrome icon). CI: run 45938000837 (commit `9b58d72`) green on all four jobs, the self-test passing on Linux, Windows, macOS arm64 and x64; artifacts `Komikku-desktop-{macos-ARM64,macos-X64,windows-X64,linux-X64}-9b58d72…` (TESTING_MAC.md "Build to test" points there). The first self-test run (45936439938) failed on Windows only because the MSI / EXE tasks do not build the app image; `createDistributable` now runs on every runner. Verified locally:
  `spotlessApply`, `spotlessCheck`, `:app:verifyBorrowedSources`, `:desktopApp:compileKotlin`, `:desktop:extension-runtime:test`, targeted tests, then after `--stop` the full `:desktopApp:test` on `5e9af6b` (the last code commit): 86 classes, 396 tests, 0 failures (was 82 / 382). `:app:compileDebugKotlin` not run: no `app/` or shared module changed. Not verifiable without a Mac: the folder panel, the notification (Script Editor attribution and
  permission), the narrow-window look on macOS; the maintainer's re-test list is TESTING_MAC.md "Re-test after M2".
  Not reviewed yet (range `cbdfdff..` the M2 docs commit). Next: the maintainer's re-test, then a review of the range.
- **CI run 45832027995 (commit `6f6ccbc`, teardown round 2):** Windows and both macOS jobs passed; the Linux job failed
  after 21 s on Maven Central `429 Too Many Requests` (dependency download, no test ran). Its failed job was re-run on
  2026-11-26 15:08 UTC and attempt 2 is green on all four jobs (checked 18:08 UTC); nothing is open. Locally the full `:desktopApp:test` passed on the same code (82
  classes, 382 tests, 0 failures). No further review round is planned for the teardown code unless the flake returns.
- **Review fixes (temp directory teardown, round 2), 2026-11-26:** the seven findings on `6e9a102..250f9f1` are
  handled (PROGRESS "Review fixes (temp directory teardown, round 2)"): every writer stop runs to its end under
  `NonCancellable` with its own 10 s bound, the caller's cancellation is rethrown, unfinished stops are returned by
  name and fail the test class; late writers bounded and keep the interrupt; `DownloadCache` KDoc says what the close
  guarantees (no `_isInitializing` reset). Found by a test: a second `DesktopInjekt.init()` keeps the singletons
  already created (Injekt has no public clear), now documented, not fixed. Preferences: no change (synchronous
  writes). Verified locally: full `:desktopApp:test` 82 classes, 382 tests, 0 failures; CI desktop build started by the marker bump `6f6ccbc`: run 45832027995 (not checked yet). Next: wait for the maintainer's Mac test results; review `250f9f1..HEAD` if wanted.
- **CI run 45726638907 is green (checked 2026-11-26):** all four package jobs passed on commit `2acd2ff`, the Linux job
  with the full desktop tests. Newer macOS `.dmg` artifacts than the one in TESTING_MAC.md are in that run.
- **Self-hosted Mac runner: parked.** The change that put the macOS jobs of `build_desktop.yml` and
  `build_ios_check.yml` on the maintainer's Mac (label `self-hosted`, fork pull requests kept on `macos-15`, docs in
  AGENTS.md / BUILDING.md) is commit `98e84f7`, kept on the branch `claude/kmp-self-hosted-mac-runner` and reverted
  here on 2026-11-25: the runner is assigned to another project for now. To bring it back when the maintainer says
  the runner is free: `git cherry-pick 98e84f7` (it assumes an Apple silicon Mac). Until then all jobs run on
  GitHub-hosted runners.
- **Run 45655979842 flake (2026-11-25), fixed:** `JsSourceBrowseUiTest > executionError` (`DeletionException`:
  `DirectoryNotEmptyException` on the `@TempDir` root only) was `DownloadCache`'s index, written into `cache/` a second
  after the last change from its own scope; the compat `Context.cacheDir` re-creates `cache/` on access, so a late
  write landed in the root while JUnit deleted it. Not reproduced locally (inferred from the report and a snapshot of
  the data folder at `@AfterAll`: the index was the last write, ~0.3 s before clean-up). `DesktopInjekt.stopDelayedWriters()`
  now cancels and joins it, run after every test class by the autodetected `DesktopDataDirTeardown` (PROGRESS M1
  "Flaky `@TempDir` clean-up"). Other delayed writers into the data folder register with
  `DesktopInjekt.onCreatedWriter` if one shows up. After the fix (`f49581d`): `:app:verifyBorrowedSources`,
  `:app:compileDebugKotlin` pass and the full `:desktopApp:test` passed with 81 classes, 373 tests, 0 failures; the
  fix has not been code-reviewed and has not run in CI yet (no desktop build was started for it).
- **Review fixes (temp directory teardown), 2026-11-25:** the eight findings on `c8c2fa7` / `f49581d` are handled
  (PROGRESS "Review fixes (temp directory teardown)"): late writers stopped at once, compat folder getters stop
  creating folders after `stopDelayedWriters()`, `DownloadCache` closed flag and `internal` `cancelAndJoin()` (desktop:
  `stopForDesktopTeardown()`), every stop attempted, teardown timeout logged, autodetection limited to the teardown
  (mockk's `MockKExtension` is also service-registered), push trigger on the marker file only. Not reviewed yet.
- **`ReaderNavigationTest` regression after those fixes, 2026-11-25, fixed:** CI run 45721165455 (Linux: 377 tests,
  1 failed) and every local run failed `next then previous across a chapter boundary …` (`expected:<1.3> but was:<P2>`).
  A race in the test (raw `moveTo(indexOf(…))` while the chapter switch rebuilds the items), deterministic once
  `0e58a88` stopped autodetecting `MockKExtension` (timing); not the teardown code, not an app bug (the viewers move by
  item). The test now moves by item, plus a regression test for the switch-landed-first order (PROGRESS "Review fixes
  (temp directory teardown)"). State: all committed and pushed (`57f9178`); last full local `:desktopApp:test` after the
  fix: 82 classes, 378 tests, 0 failures. **To do first:** check CI run 45726638907 (marker bump `2acd2ff`; the Linux job
  runs the tests); the teardown fixes and this test fix are not reviewed yet.
- **M1 — first macOS CI build (done, PROGRESS "M1 — first macOS CI build"):** `build_desktop.yml` runs on this branch
  through a temporary `push` trigger (bump `.github/desktop-build-trigger`; branch runs are always release mode `none`).
  Build to test: <https://github.com/komikku-app/komikku/actions/runs/45625758564> (first green run 45623103294; macOS arm64 + x64 `.dmg`,
  Windows `.msi` / `.exe`, Linux `.deb` / `.rpm` / `.tar.gz`). The maintainer's Mac test guide:
  [`TESTING_MAC.md`](TESTING_MAC.md). Nobody has opened the app on a Mac yet; the suspects are listed in PROGRESS M1.
  Remove the trigger and the marker file before the port goes to master.
- **Review fixes (P23 fixes):** the five findings on `80d94dd..25a39cd` are fixed (PROGRESS "Review fixes (P23
  fixes)"); not reviewed yet.
- **State:** branch `claude/kmp-multiplatform-support`, HEAD = the M1 docs commit (last code commit `cd5e31d`, last CI change `a1d10c5`), in
  sync with origin. Another session may push iOS commits to the same branch (`source-js/**/ios*`,
  `.github/workflows/build_ios_check.yml`, `docs/kmp-desktop/IOS_CI.md`): leave those files alone, merge (never rebase or
  force) when a push is rejected.
- **Last full `:desktopApp:test`:** on `cd5e31d` (P23 fixes review): 81 classes, 373 tests, 0 failures, `:desktop:android-compat:test` 7 classes, 37 tests, 0 failures. Before that, on `0b55233`: 81 classes, 371 tests, 0 failures (was 81 / 369: `BrowseMigrateUiTest` +1,
  `ExtensionDetailsUiTest` +1), the timing-sensitive classes (`DesktopBackupJobsTest`, `DesktopExtensionManagerTest`,
  `ReaderNavigationTest`, `MangayomiSourceStoreTest`) included and passing; `:desktop:android-compat:test` 7 classes,
  36 tests, 0 failures (was 35: `CookieManagerTest` +1).
- **Reviewed:** `c968ba3..80d94dd` (the P22 review fixes and P23); its six findings are fixed (PROGRESS "Review fixes
  (P23)"). **Not reviewed:** those review fixes, `80d94dd..` the P23 review-fix docs commit. Open from them: no
  deterministic race test for the extension manager's atomic entry updates; the "no repository → not obsolete" test was
  not run failing-first. Never run: the desktop app in a real window (on any OS), the release job of `build_desktop.yml`, Android on a device (item 7).
- **Next items** (from PROGRESS "Remaining parity gaps", in this order: small and headless-testable first, then by user
  impact, the big ones last):
  1. Reader leftovers (gap 4): tap-zone / keyboard navigation settings, SY's shift / invert double pages and centre
     margins, cropped borders, webtoon split / rotate of wide pages.
  2. Manga details leftovers (gap 3): "Autofill from tracker" in edit info, then sharing (cover / link).
  3. Small open points: the source feed's incognito toggle (gap 1); Android's own "Clear cookies" (extension info,
     WebView) still misses non-root `Path` and parent-domain cookies (Mihon's `cookieJar.remove`); a test that the extension info's clearing runs off the UI thread.
  4. Recommendations (library selection and details, gaps 2 / 3), then SY's metadata / namespace-tag view and page
     previews for the delegated sources (P4).
  5. System integration (gap 5): minimise to tray, notification actions.
  6. The large ones: merged entries and sources, SY's MangaDex screens / batch add, an embedded browser backend
     (jcefmaven, PROGRESS P7 "What the embedded backend still needs").
- Process per item: build in small pushed commits, record it in PROGRESS.md and here, then the coordinator's
  `/code-review medium <range>` and a review-fix round (failing test first), as for P1–P22.

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
   P15 (settings search) and P16 (app lock) and their review fixes are done; P17 (notifications / tray) and P18 (single-
   entry migration) and their review fixes are done (last Part A commit `506da59`); P19 (the migration list and its
   configuration, migration from the duplicate dialog) and its review fixes are done; P20 (Browse → Migrate) and P21 (SY
   source categories) and their review fixes are done; P22 (extension info) and its review fixes and P23 (small open points from reviews, not reviewed) are done; **next**: the
   items in "Next session" above; then the maintainer's
   test of the CI `.dmg` on a Mac (TESTING_MAC.md; `build_desktop.yml` has run artifacts-only in M1), then a release
   run (`existing` from the tag on the Android draft, PROGRESS P9) and certificates for signing, and the
   first real run of the app (item 7). Known gaps: P6's (no real request against `api.github.com`, the dialog not
   shown in a real window, no build with `-Penable-updater` / `-Pdesktop-preview` run; Android's own
   `GetApplicationRelease.isNewVersion` still throws on an odd tag), P5's (macOS / Windows
   Skia behaviour for AVIF / JPEG XL unverified, see the CI test output; no real AVIF cover loaded), P4's
   (no metadata view / page previews / MangaDex settings, follows and similar screens / batch add on desktop; not run
   against real extension APKs or servers), and: no SY data-saver exclusion; no merge / recommendations /
   SY title cleaning / MangaDex follow / reset info from the library selection; desktop UI not run in a real window.
   P7's / P8's (no embedded browser: jcefmaven backend, PROGRESS P7 "What the embedded backend still needs"; without a
   FlareSolverr-compatible service automatic flows hitting a challenge need the manual step; the FlareSolverr backend
   not run against a real solver; no per-source opt-out; no Settings → Advanced network group; `android.webkit.WebView`
   for extensions; not run against a real Cloudflare site or in a real window). After signing: notifications / tray.
   From the first merge of `master` (A4; priority 5 in COORDINATION.md "Priorities", not now; UPSTREAM_AUDIT.md 7.4):
   no "MangaDex session expired" notification (the desktop `MangaDexSessionNotifier` only logs; Settings > Tracking
   shows MDList logged out), and no batch "reset info" in the library selection (so `master`'s confirmation for it,
   `084750b89b`, has no desktop counterpart).
5. I5–I7 (iOS core, iOS app, macOS CI) need a macOS host; re-check the JS catalogue size first (PLAN 8.9).
6. Optional: report the quickjs-kt bugs upstream (draft in `QUICKJS_KT_SECURITY_REPORT.md`, with the crash and
   the JNI state race added; the maintainer submits it).
7. First real run by the maintainer: `./gradlew :desktopApp:run`, Android `assembleDebug` on a device
   (QuickJS switch is unverified on device and under R8).
