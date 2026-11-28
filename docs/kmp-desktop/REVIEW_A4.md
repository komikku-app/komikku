# Review of A4, the first merge of `master` into `multiplatform`

Status: Reviewer — done: the merge `1e930163bc` and the follow-ups `611bb99ef2`, `ed23d6fa1f`, `1e3ee2a04e`,
`7aea238c0f` on `merge/master-a4` reviewed against ANSWER 009 and Forge's five focus points — left: re-check the
fixes when the owners report; the items under "Not checked"

**Verdict: land as is.** The merge does what ANSWER 009 asks and nothing else: no file `master` owns differs from
`master` in a way it did not differ before, the merge commit is the automatic merge plus the five predicted conflicts
and four desktop compile fixes, and Android only changes where `master` changed it (or where ANSWER 009 returned a
file to `master`). No finding blocks landing; the one Medium is a desktop gap that existed before the merge, and the
rest are small follow-ups.

## Summary

| Area | Owner | High | Medium | Low |
|------|-------|------|--------|-----|
| 1. Hidden merge damage on files `master` owns | Merge author | 0 | 0 | 0 |
| 2. Desktop copies against `master` `15610e67e4` | Shared-code engineer | 0 | 1 | 1 |
| 3. Desktop compile fixes (MDList notifier, `GetNextChapters` DI, PKCE login) | see each | 0 | 0 | 4 |
| 4. Android behaviour | Merge author | 0 | 0 | 0 |
| 5. KMK markers and strings | Shared-code engineer | 0 | 0 | 1 |

Medium:
- 2.1 (Shared-code engineer): the desktop Cloudflare interceptor still loads the challenged URL as is for non-GET
  requests; `master`'s fix (`14c94f5cec`, solve on the site's root) has no desktop equivalent, although
  UPSTREAM_AUDIT.md 7.4 says it has one.

Code review, high effort, 2026-11-28. Reviewer role; the owning roles fix. Reviewed read-only on the refs (the
worktree `.git/forge/wt-master` was not checked out anywhere else); file and line numbers are at `7aea238c0f` unless
another commit is named. Merge base `290267dd4c`; `multiplatform` parent `209b80d910`; `master` parent `15610e67e4`.
Specification: ATLAS_INSTRUCTIONS.md ANSWER 009. Not redone, as asked: `LibraryScreenModel`, `SourceFilterDialog` and
`WebViewInterceptor` byte-identical to `master`, and the one `actual` line in the Android `CloudflareInterceptor`.

**ANSWER 009, point by point:**
- A file `master` owns is byte-identical unless it is a hook or build file: met. 76 files `master` owns still differ
  (79 before the merge; the three now equal are `build_pull_request.yml`, `LibraryScreenModel.kt`,
  `SourceFilterDialog.kt`), all on UPSTREAM_AUDIT.md 7.2's list or 7.3's "move back later" list; none is new.
- Desktop copies carry a header naming the `master` file and the synced commit: met for all four
  (`LibraryActions.kt`, `LibraryFilterSort.kt`, `SourceFilterItems.kt`, `CloudflareChallenge.kt`), and 7.1 lists them.
- Desktop tests pin the behaviour, written from `master`'s code, including the filter fix: met
  (`LibraryActionsTest`, `SourceFilterItemUiTest`, `CloudflareChallengeTest`; checked against `master`'s code below).
- `MdList` / `MangaDexSessionNotifier` solved on the desktop side: met (desktop counterpart; `master`'s `MdList` body
  is untouched apart from our earlier change that 7.3 already lists for #2023).
- Desktop Cloudflare detection follows `master`'s `cf-mitigated` check in the same merge: met (`1e3ee2a04e`).
- JavaScript / QuickJS on Android left alone: met (`AndroidSourceManager.kt`, `app/build.gradle.kts`,
  `app/proguard-rules.pro` and `core/common/build.gradle.kts`'s `:core:quickjs` line keep exactly the delta they had
  before the merge).

## 1. Hidden merge damage on files `master` owns (Merge author)

**Method.** Rename detection hides moves, so every set was computed with `--no-renames`:
- `git diff 15610e67e4 7aea238c0f`, limited to paths in `master`'s tree, against `git diff 290267dd4c 209b80d910`:
  no path `master` owns differs now that did not differ before.
- The 17 paths both sides changed: for each, the `+`/`-` lines of our delta after the merge
  (`git diff 15610e67e4 7aea238c0f`) were compared with our delta before it (`git diff 290267dd4c 209b80d910`).
  Thirteen are the same delta on top of `master`'s new content (`AGENTS.md`, `app/build.gradle.kts`,
  `app/proguard-rules.pro`, `DownloadCache.kt`, `MdList.kt`, `AndroidSourceManager.kt`, `MangaScreenModel.kt`, the two
  version catalogs, `i18n-kmk` base `strings.xml`, and the three moved `core/common` files). The other four are the
  conflicts resolved to `master` (`build_pull_request.yml` was already `master`'s from ANSWER 009 item 3) and
  `core/common/build.gradle.kts`, whose only new difference is `master`'s `androidx.webkit` moved into
  `androidMain.dependencies` (an Android AAR, inside the existing KMK block, lines 25–54).
- The merge replayed with `git merge-tree --write-tree 209b80d910 15610e67e4`: the same five conflicts, and the merge
  commit's tree differs from the automatic result only in those five files and the four desktop compile fixes
  (`MangaDexSessionNotifier.kt`, `DesktopTrackerLogin.kt`, `TrackingTest.kt`, `LibraryInteractors.kt`). No other
  hand edit hides in the merge.
- Files `master` changed at a path we had moved: `CloudflareInterceptor.kt`, `WebViewInterceptor.kt` and
  `WebViewUtil.kt`. The third was not on Forge's list: `core/common/src/androidMain/.../util/system/WebViewUtil.kt` is
  byte-identical to `master`'s `src/main` file. All 18 moved files were matched by blob: 16 identical, the two
  `actual` hooks (`CloudflareInterceptor`, `ArchiveReader`) as UPSTREAM_AUDIT.md 7.2 says.
- Files `master` added in source folders our build reorganised: `MangaDexUserAgentInterceptor.kt`
  (`core/common/src/main/kotlin`, compiled through `jvmSharedMain`, so on both targets, and wired by the shared
  `NetworkHelper.kt`) and its test (`core/common/src/test/kotlin`, run through `jvmSharedTest`, see
  `buildSrc/src/main/kotlin/mihon/buildlogic/KmpLibrary.kt` lines 27–34). Both compile and run where `master` expects.
- `master`'s code that now calls into our shared modules: the new `GetNextChapters` constructor (registered with five
  arguments in `master`'s `DomainModule.kt` and in our `LibraryInteractors.kt` line 192, `GetChapter` registered at
  line 181), migration `48.sqm` and the `mangas.sq` changes (byte-identical; desktop databases migrate to 48 too, which
  is the intended shared schema), the `MangaCover.vibrantCoverColorMap` type change (no desktop code writes it), and
  `master`'s new R8 rules (narrower keeps, `source-api/consumer-proguard.pro`; our module keeps the same
  `consumerProguardFile` line, `source-api/build.gradle.kts` line 68). No Android-side code of ours in `eu.kanade.*`,
  `tachiyomi.*` or `mihon.*` uses reflection that the removed blanket keeps covered (searched for `Class.forName`,
  declared members, `ServiceLoader`, QuickJS interface bindings); our `app.komikku.*` and quickjs-kt keeps are unchanged.

**Result: no hidden merge damage found.**

## 2. Desktop copies against `master` `15610e67e4` (Shared-code engineer)

**What was compared.**
- `SourceFilterItems.kt` lines 26–151 against `master`'s `SourceFilterDialog.kt` lines 135–259: identical except the
  name and visibility (`SourceFilterItem`, public, and its recursive call at line 129), as the header says. `master`'s
  invalid-filter fix (`a222d3a220`, `else -> TriState.DISABLED`, line 141) is in.
- `LibraryActions.kt` range selection against `master`'s `LibraryScreenModel.kt` lines 1376–1438 (`481bfeb75a`): the
  anchor rule, the `lastMangaIndex == -1 || curMangaIndex == -1` guard, the `subList` range and the anchor resets in
  clear / select all / invert (desktop `LibraryScreenModel.kt`) match.
- `LibraryFilterSort.kt`: `applyLibraryFilters`, `applyLibrarySort`, `filterManga`, `filterTracks`, the query part of
  `filterLibrary` and the `BY_DEFAULT` grouping were diffed against `master`'s bodies (leading whitespace ignored):
  the only differences are the parameters the move added, all inside KMK markers. `master` changed nothing else in
  those functions in this range; its other `LibraryScreenModel` change (reset-info confirmation, `084750b89b`) has no
  desktop counterpart to sync (7.4).
- `CloudflareChallenge.kt` against `master`'s `CloudflareInterceptor.shouldIntercept` (line 60–65) and `WebViewUtil`
  `toWebViewHeaders` / `isRequestHeaderSafe` (lines 149–175): identical rules (`cf-mitigated: challenge` from a
  `Server` in `SERVER_CHECK`; case-insensitive merge keyed in lower case; `Origin` dropped).
- The pinning tests assert `master`'s behaviour, not ours: the unknown tri-state shows as ignored and a click includes
  it; an out-of-range select shows no value (this goes through `master`'s `SelectItem`, byte-identical in
  `presentation-core`); the anchor is the last clicked entry, not the last selected; the old challenge-page markers
  without the header are no challenge.

**Desktop counterparts of `master` files `master` changed in this range:** `LibraryUpdateJob` (scheduling stub),
`Notifications` and `PagerConfig` (constants only), `MangaDexSessionNotifier` (3.1) and the desktop
`CloudflareInterceptor` (2.1, 2.2). Only the last two carry `master` behaviour that matters.

### 2.1 Medium: non-GET Cloudflare challenges are still solved on the request's URL on the desktop

- **File, line:** `core/common/src/desktopMain/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`
  lines 121–129 (`solve(…, challengedUrl, …)`) and 161–163 (`BrowserSession(url = url, …)`);
  `docs/kmp-desktop/UPSTREAM_AUDIT.md` lines 467–468.
- **Problem:** `master`'s `14c94f5cec` ("Solve Cloudflare challenges of non-GET requests on the site's root",
  `CloudflareInterceptor.kt` lines 154–161 at `15610e67e4`) loads the site's root page when the challenged request is
  not a GET, because a browser can only load a page with GET and the request's own URL usually answers GET without a
  challenge. The desktop interceptor hands the challenged URL to the backend whatever the method, so a challenged POST
  (search APIs, logins) waits for a `cf_clearance` that never comes: 30 s (`DEFAULT_TIMEOUT`, line 245), then a failed
  solve that `CloudflareHostState` remembers for 45 s, failing the host's other requests too. UPSTREAM_AUDIT.md 7.4
  says this part of `master`'s work "has desktop equivalents of its own (`CloudflareHostState`)"; one solve per host
  and failure memory do, non-GET does not. Not a regression from the merge (the desktop did the same before), but a
  `master` fix the desktop misses while the audit says it has it.
- **Proposed fix:** in `intercept`, pass the browser `challengedUrl` when `response.request.method == "GET"`, else
  `challengedUrl.newBuilder().encodedPath("/").query(null).fragment(null).build()` (`master`'s expression), and keep
  the challenged host for the lock and the failure memory. Add a case to `DesktopCloudflareInterceptorTest` (a POST
  answered with a `cf-mitigated` challenge: the backend's session URL is the root). Correct 7.4.

### 2.2 Low: an interactive challenge holds the host for the full timeout on the desktop

- **File, line:** `core/common/src/desktopMain/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`
  lines 160–173; `master` `CloudflareInterceptor.kt` lines 167–176 (`b672f8028e`).
- **Problem:** `master` aborts at once when the challenge page posts `interactiveBegin` (a challenge no
  non-interactive WebView can pass) and fails the request. The desktop session waits for the cookie until the 30 s
  timeout, holding the host's lock, before failing the same way. Only latency, but every request to that host waits
  behind it.
- **Proposed fix:** if the backends can observe the challenge's `postMessage` events, end the session on
  `interactiveBegin` / `fail` as `master` does; otherwise record the gap in 7.4 instead of "has desktop equivalents".

## 3. Desktop compile fixes in the merge commit

**`GetNextChapters` / `GetChapter` DI: correct.** `master`'s constructor is `(GetChaptersByMangaId,
GetMergedChaptersByMangaId, GetManga, HistoryRepository, GetChapter)`; `LibraryInteractors.kt` line 192 passes five
`get()`s and every type is registered in the same function (`GetChapter` line 181, `GetChaptersByMangaId` 184,
`GetManga` 155). `registerLibraryInteractors` is called by the desktop only (`DesktopModules.kt` line 252), so Android
keeps `master`'s `DomainModule` registration.

**PKCE login change in `DesktopTrackerLogin` (lines 52–65): correct, and no security problem found.** It mirrors
`master`'s Android call sites exactly (`SettingsTrackingScreen.kt` line 169, `SettingsMangadexScreen.kt` line 167):
- MyAnimeList: `MyAnimeListApi.authUrl(trackPreferences, tracker)` creates a verifier per login, stores it and sends
  it as the challenge (MAL supports only the `plain` method; `master`'s choice); `MyAnimeList.login` reads it, refuses
  to exchange without one and deletes it after any completed exchange.
- MangaDex: `MdConstants.Login.authUrl(MdUtil.newLoginCodeVerifier(…))` takes the verifier and sends its S256
  challenge; `MangaDexLoginHelper.login` reads the stored verifier. The desktop's `android.util.Base64` honours
  `URL_SAFE` and `NO_PADDING` (`desktop/android-compat/.../Base64.kt` lines 54–55), which the S256 challenge depends on.
- The verifier comes from `SecureRandom` (50 bytes, 67 base64url characters, within RFC 7636's 43–128). It sits in the
  same preference store as the trackers' access tokens, which are far more sensitive, so it adds no exposure. The
  change replaces the old desktop MDList path (`getPkceChallengeCode()`, one verifier cached for the process and never
  stored) with one verifier per login, which is stricter.
- `loginWithRedirect` (lines 81–91) still reports failure correctly when `master`'s `login` returns early without a
  verifier (`tracker.isLoggedIn` stays false; MangaDex returns `false`).

**MDList notifier counterpart:** same FQN and members as `master`'s (`notifySessionExpired`, `dismiss`), the only
members `MdList` (lines 35, 40, 175 at `15610e67e4`) and `MangaDexLoginHelper` (line 75) use. Compiles and behaves as
documented.

### 3.1 Low: a rejected MangaDex session is only logged on the desktop

- **File, line:** `app-shared/src/desktopMain/kotlin/exh/md/network/MangaDexSessionNotifier.kt` lines 11–18.
- **Problem:** on Android the user gets a notification ("MangaDex session expired", tap to log in). On the desktop the
  counterpart writes a log line, so MDList sync and follows stop silently until the user happens to open
  Settings > Tracking. The desktop has a notification service (`desktopApp` `DesktopNotifier` /
  `DesktopNotifications`) and the two `KMR` strings exist (`i18n-kmk` base lines 122–123). Recorded in 7.4 already;
  listed here so it gets an owner. Owner: Forge to assign (desktop tracking).
- **Proposed fix:** let the counterpart call a desktop hook registered in Injekt (an interface in `app-shared`,
  implemented in `desktopApp` with `DesktopNotifier`), falling back to the log line when none is registered.

### 3.2 Low: the new counterpart is not listed where UPSTREAM_AUDIT.md says it is

- **File, line:** `app-shared/borrowed-app-sources.gradle.kts` lines 428–431; `docs/kmp-desktop/UPSTREAM_AUDIT.md`
  line 420.
- **Problem:** 7.1 says the counterpart "joins the existing ones listed in `borrowed-app-sources.gradle.kts`", but
  the MangaDex block's comment does not name it (other blocks do: "Desktop counterparts in app-shared/src/desktopMain:
  …"). The next person syncing `MdList` looks there. Owner: Merge author.
- **Proposed fix:** add "Desktop counterpart in app-shared/src/desktopMain: MangaDexSessionNotifier (logs instead of
  notifying)" to that comment.

### 3.3 Low: the MDList authorization URL is not pinned to the stored verifier

- **File, line:** `desktopApp/src/test/kotlin/app/komikku/desktop/track/TrackingTest.kt` lines 107–110.
- **Problem:** the MAL case checks that the URL carries the stored verifier (line 97); the MDList case only checks
  `client_id` and `code_challenge_method=S256`. Whether its `code_challenge` is the S256 of the verifier that
  `MangaDexLoginHelper` will send is exactly what this change and the desktop `Base64` must get right, and it is not
  tested. Owner: Forge to assign (desktop tracking).
- **Proposed fix:** read `trackPkceCodeVerifier(mdList)` after `authUrl()` and assert
  `code_challenge=` + base64url-without-padding of its SHA-256 (with `java.util.Base64`, independent of the compat
  class).

### 3.4 Low: "Open login page" again starts a new login, so a redirect from the first page fails

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/ui/more/TrackingSettingsScreen.kt` lines 137 and
  347; `DesktopTrackerLogin.kt` lines 53 and 62.
- **Problem:** `authUrl` is a lambda that now stores a new verifier on every call. The dialog's "open the login page"
  button calls it again, replacing the verifier of the page opened by `startLogin`. A user who clicks it because the
  first browser tab seemed slow, then authorizes in the first tab and pastes that redirect, gets "redirect invalid"
  (the exchange fails PKCE). Android behaves the same per tap, but there the redirect comes back on its own; on the
  desktop the user copies it by hand from whichever tab they used. Not a security issue. Owner: Forge to assign
  (desktop tracking).
- **Proposed fix:** build the URL once in `startLogin`, keep it in `Dialog.OAuthPaste`, and have the button reopen
  that URL.

## 4. Android behaviour (Merge author)

**No unintended change.** Android's code after the merge differs from before only by:
- `master`'s changes, taken whole (section 1);
- the four files ANSWER 009 returned to `master`: Android now runs `master`'s `LibraryScreenModel`
  (with `481bfeb75a`'s range rule and the reset-info confirmation), `master`'s filter rows (with `a222d3a220`) and
  `master`'s Cloudflare / WebView interceptors (`cf-mitigated` detection, one WebView per host, non-GET on the root);
- `LibraryActions.kt`, `LibraryFilterSort.kt`, `SourceFilterItems.kt` and `CloudflareChallenge.kt` leaving the Android
  compile (moved to desktop source sets; nothing outside desktop source sets references them, checked with `git grep`),
  and `SourceFilterItems.kt` leaving `:app-shared`'s `shared(…)` list.

Our pre-merge Android deltas are unchanged (section 1), including the ones 7.3 schedules for #2023 (`MdList`'s
uncached `mdex`, `MangaScreenModel`'s `MangaInfoEdits`) and the JavaScript-source wiring.

## 5. KMK markers and strings (Shared-code engineer)

**Clean.** The merge's own edits to files `master` owns are inside KMK blocks (`core/common/build.gradle.kts`; the
`actual` line Forge already checked). The desktop copies are Komikku-owned files with the required header; their
bodies keep `master`'s `// SY` / `// KMK` markers and the glue the move added is inside KMK markers. No string was
added or changed by our side: `i18n-kmk` base keeps the same delta, and every non-`base` locale file in `i18n`,
`i18n-sy` and `i18n-kmk` is byte-identical to `master` (all locale changes in the merge are `master`'s Weblate
updates). `git diff --check` is clean on the merge's own changes and the follow-ups.

### 5.1 Low: the shared `expect class` still describes the desktop interceptor of before P7

- **File, line:** `core/common/src/jvmSharedMain/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`
  lines 7–11.
- **Problem:** "Desktop (`src/desktopMain`): no WebView yet; passes responses through and reports challenges." The
  desktop solves challenges with a `BrowserBackend` since P7, and after this merge its detection is a copy of
  Android's. Older than the merge, but it is the file the merge made a permanent hook (7.2), so it will be read again.
- **Proposed fix:** "Desktop (`src/desktopMain`): solves challenges with a `BrowserBackend`; detection is the desktop
  copy `CloudflareChallenge` of Android's rules."

## Not checked

- No Gradle run (as asked). PROGRESS.md reports `spotlessCheck`, `:app:compileDebugKotlin`, `:desktopApp:test` (448),
  every module's `desktopTest` and `testReleaseUnitTest`; not reproduced.
- No minified Android build (`assemblePreview` / R8) and no Android run: `master`'s narrower keep rules were checked
  by reading for reflection only. ANSWER 009 has CI build it before it reaches `multiplatform`; that build and a quick
  extension smoke test on a device cover it.
- No live Cloudflare site or live MyAnimeList / MangaDex login on the desktop; 2.1, 2.2 and section 3 come from the code.
- The desktop's own copies of `master` behaviour outside the four ANSWER 009 copies (history resume rules, cover
  fetcher, split pages, sync fixes; UPSTREAM_AUDIT.md 7.4) were not re-audited; only the counterparts whose `master`
  file changed in this range were checked.
