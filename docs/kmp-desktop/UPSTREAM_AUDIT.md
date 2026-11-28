Status: Upstream integrator — done: A1 audit, conflict prediction, DECISION 004 check of #2023, the two `master` checks, A3 proposal, the three Android fix patches, A4 (first merge of `master`, section 7) — left: the next merge after #2023 (section 7.3)

# Upstream audit: the files `master` owns and the coming merge

Track A of [INSTRUCTION 001](ATLAS_INSTRUCTIONS.md) (items 1 and 3), the check that [DECISION 002 and 004](ATLAS_INSTRUCTIONS.md)
ask for, and the two pre-existing items listed in DECISION 004. Read-only work: `git fetch`, `git diff`, `git merge-tree`
and `gh pr view` / `gh pr diff`. No working tree was touched, nothing was merged, no Gradle run was needed.

State of the audit: `multiplatform` at `03b9ab6e72` (2026-11-28), `origin/master` at `15610e67e4` (2026-11-27), pull
request [#2023](https://github.com/komikku-app/komikku/pull/2023) at `92641111b6` (based on `15610e67e4`).

## 1. A1 — where we stand against `master`

### 1.1 Merge base and distance

- **Merge base:** `290267dd4c` ("Fix LANraragi metadata failing to parse on newer servers").
- **`master` since the base:** 98 commits, 248 files, +10065 / −2534 lines. 130 of the files are translations
  (non-base locales of `i18n`, `i18n-sy`, `i18n-kmk`) and 8 are the cherry-pick log and agent skills; the other 110 files
  carry +4312 / −482 lines.
- **`multiplatform` since the base:** 524 commits, 658 files, +89316 / −2106 lines (almost all in modules and source
  sets `master` does not have).

### 1.2 What `master` changed (98 commits by area)

| Area | Commits | Main modules / files |
|------|--------:|----------------------|
| MangaDex and trackers (MangaDex auth renewal and PKCE, MDList logout and covers, MAL / Shikimori / tracker dates, duplicate detection) | 15 | `app` `exh/md/**`, `data/track/**`, `core:common` (new `MangaDexUserAgentInterceptor`) |
| WebView and Cloudflare (challenge detection by `cf-mitigated`, one WebView per host, source headers only to the source, Origin header, renderer death, user-agent metadata) | 13 | `core:common` interceptors and `WebViewUtil`, `app` `ui/webview/**` |
| Library, history, reader, downloads, search fields (history resume rules, range selection, reset-info confirmation, split / double pages, downloads of sources sharing a folder) | 18 | `app` `LibraryScreenModel`, `HistoryScreenModel`, `ReaderViewModel`, `DownloadCache`; `domain` `GetNextChapters` |
| Extensions and sources (installer failures, invalid filters, stub sources, store icon) | 7 | `app` `extension/**`, `AndroidSourceManager`, `SourceFilterDialog`; `presentation-core` `SettingsItems`; `domain` `StubSource` |
| R8 shrinking of app code and keep rules | 7 | `app/proguard-rules.pro`, `app/build.gradle.kts`, `source-api` (consumer rules, `PagePreviewSource`, delegated sources) |
| Sync and backup | 4 | `app` `data/sync/**`, `MangaRestorer`; `data` (`mangas.sq`, new migration `48.sqm`) |
| Covers and local source | 3 | `app` `data/coil/**`, `domain` `MangaCover`, `source-local` |
| Start-up and stability (Samsung / Android TV crashes, library update on Android 8, back gestures) | 4 | `app` `App.kt`, `MainActivity`, `LibraryUpdateJob`; `gradle/androidx.versions.toml` |
| Translations (Weblate) | 13 | non-base `strings.xml` / `plurals.xml` |
| Tooling and docs (cherry-pick skills and log, pull-request skill, `ISSUES.md`) | 10 | `.claude/**`, `cherrypick_log.md`, `ISSUES.md` |
| Dependencies (Renovate) | 3 | `gradle/*.toml`, workflows |
| Release | 1 | version bump |

Two changes reach the desktop directly through shared modules and need a check at the merge: the new SQLDelight
migration `48.sqm` (the desktop database uses the same `:data` schema, so existing desktop databases migrate on first
start) and the new `MangaDexUserAgentInterceptor` in `core/common/src/main` (compiled for the desktop too).

### 1.3 Our changes to files `master` owns

79 files exist on `origin/master` and were changed on this branch since the base: 61 modified in place and 18 moved
(renamed to a source set or module `master` does not have). Each file is listed once, under its main reason.

**Borrowed-source enablement — 8 files.** Edits so a borrowed `app` file (or a module file) compiles for the desktop;
Android behaviour unchanged.
- `SetReadStatus.kt` (KDoc links fully qualified, imports dropped), `SourcePreferences.kt` (the value of
  `LocaleHelper.getDefaultEnabledLanguages()` inlined), `SearchScreenModel.kt` (`SourceFilter` moved to `:app-shared`),
  `CustomColorScheme.kt` (Android-view helpers moved to the new `AndroidViewColorScheme.kt`)
- `DownloadCache.kt` (a `closed` flag in 11 methods and `cancelAndJoin()` for the desktop teardown),
  `ReleaseServiceImpl.kt` (a download-link selector for the desktop installers)
- `core/archive` `CbzCrypto.kt` (key storage split into `CbzKeyStore` per platform), `domain` `StorageManager.kt` (the
  Android permission flow moved to `androidMain`)

**Visibility changes — 2 files.**
- `BulkFavoriteScreenModel.kt` (nine `internal` members public; also carries the `reverseSelection` fix)
- `MigrateMangaDialog.kt` (`Screen.MigrateMangaDialog` public, an `onFailure` callback and a result for the desktop)

**Moved code — 29 files.** The platform-neutral part moved into a new shared KMK file that Android now calls (11), or
the whole file moved to another source set or module (18).
- Android calls a shared file now: `LibraryScreenModel.kt` (−882 lines, to `LibraryActions.kt` and the library filter
  / sort files), `FeedScreenModel.kt`, `FeedScreen.kt`, `SourceFeedDialogs.kt`, `SourceFeedScreenModel.kt` (to
  `FeedItems.kt` and shared dialogs), `SourceFilterDialog.kt` (to `SourceFilterItems.kt`), `SettingsSearchScreen.kt`,
  `PreferenceScreen.kt` (to `SettingsSearch.kt`), `SecureActivityDelegate.kt` (to `AppLockRules.kt`),
  `MangaScreenModel.kt` (to `MangaInfoEdits.kt`), `MigrationConfigScreen.kt` (model and options sheet to shared files)
- `core/common` `src/main` → `src/androidMain` (9): `CloudflareInterceptor`, `WebViewInterceptor` (both also edited),
  `DeviceUtil`, `GLUtil`, `WebViewUtil`, `AndroidPreference`, `AndroidPreferenceStore`, `AndroidStorageFolderProvider`,
  `ImageUtil`
- `core/common` → new module `core/preference` (5): `CheckboxState`, `InMemoryPreferenceStore`, `Preference`,
  `PreferenceStore`, `TriState`
- `core/archive` `src/main` → `src/androidMain` (4): `ArchiveInputStream`, `ArchiveReader` (also edited),
  `UniFileExtensions`, `ZipWriter`

**Real bug fixes (Android behaviour changes) — 9 files.** More fixes sit inside the moved code above (see section 3).
- `GetSourcesWithFavoriteCount.kt`, `MigrateSourceScreen.kt` (plus the JavaScript-source part), `MigrateSourceScreenModel.kt`,
  `domain` `Source.kt`, `MigrateMangaUseCase.kt`, `MigrationListScreen.kt`, `MigrationListScreenModel.kt`,
  `data` `CustomMangaRepositoryImpl.kt`, `MdList.kt`

**Other: JavaScript sources on Android (a feature, not a fix) — 7 files.** Android got the Mangayomi JavaScript sources
before the rule "nothing is implemented for Android on this branch" existed.
- `KMKDomainModule.kt`, `AndroidSourceManager.kt` (waits for the installed JavaScript sources before it reports itself
  initialized), `ExtensionManager.kt` (icons), `SourcesScreen.kt`, `SettingsBrowseScreen.kt`, `ExtensionsTab.kt`,
  `BrowseSourceScreen.kt`
- Related, inside build files: Android's QuickJS engine switched from the `quickjs-android` AAR to `quickjs-kt`
  through `:core:quickjs` (`935c6c9710`; `core/common/build.gradle.kts`, `libs.versions.toml`, `proguard-rules.pro`).
  Every APK extension that uses `app.cash.quickjs.QuickJs` runs on the new engine in an Android build of this branch.

**Build files — 20 files.**
- `settings.gradle.kts`; `buildSrc/build.gradle.kts`, `buildSrc/settings.gradle.kts`, `ProjectExtensions.kt`
- `app/build.gradle.kts` (source borrowing, `:app-shared`, `:source-js`), `app/proguard-rules.pro` (quickjs-kt,
  `app.komikku.**` serializers)
- Desktop targets: `core-metadata`, `core/archive`, `core/common`, `data`, `domain`, `i18n`, `i18n-sy`, `i18n-kmk`,
  `presentation-core`, `source-api`, `source-local` (`build.gradle.kts` each)
- `gradle/androidx.versions.toml`, `gradle/kotlinx.versions.toml`, `gradle/libs.versions.toml`

**CI — 1 file.** `.github/workflows/build_pull_request.yml` (a desktop job, `continue-on-error`).

**Docs — 1 file.** `AGENTS.md` (pointers to `BUILDING.md` and the `multiplatform` branch).

**Other — 2 files.** `.gitattributes` (`*.icns` binary), `i18n-kmk` base `strings.xml` (+131 lines, one block appended at
the end of the file).

PROGRESS.md has no "Changes to files owned by master" section yet, although COORDINATION.md asks for it; this list can
seed it.

## 2. Predicted conflicts of the coming merge

### 2.1 Merging `origin/master` as it is now: 5 conflicting files

From `git merge-tree --write-tree --name-only --messages origin/master HEAD` (no working tree change):

| File | Cause | Resolution |
|------|-------|------------|
| `core/common/build.gradle.kts` | `master` adds `implementation(androidx.webkit)` next to `api(libs.preferencektx)`, which we commented out and moved to `androidMain` | Put `androidx.webkit` in `androidMain.dependencies` (it is an Android AAR; `master`'s new `WebViewUtil` code needs it). |
| `core/common/src/androidMain/.../CloudflareInterceptor.kt` (moved file) | `master` rewrote it (+189 / −40: one WebView per host, challenge detection by the `cf-mitigated` header, non-GET requests); we replaced its detection and constants by calls to the shared `CloudflareChallenge` | Take `master`'s file whole; update the desktop's `CloudflareChallenge` to `master`'s detection (see 2.2). |
| `core/common/src/androidMain/.../WebViewInterceptor.kt` (moved file) | `master` moved `parseHeaders` into `WebViewUtil` (`Headers.toWebViewHeaders()`, now also dropping `Origin` and merging header names case-insensitively) and added `prepareWebView()`; we delegated `parseHeaders` to `CloudflareChallenge.browserHeaders` | Take `master`'s file whole; bring `CloudflareChallenge.browserHeaders` to `master`'s rules for the desktop. |
| `SourceFilterDialog.kt` | `master` changed one line in `FilterItem` (an unknown tri-state shows as disabled instead of throwing, `a222d3a220`); we moved `FilterItem` to `SourceFilterItems.kt` | Take `master`'s file; the shared `SourceFilterItems.kt` still throws on an unknown tri-state, so without this Android would lose `master`'s fix. |
| `LibraryScreenModel.kt` | `master` reworked range selection (`LastSelection`, `481bfeb75a`) and added a reset-info confirmation (`084750b89b`); we moved the selection bodies to `LibraryActions.kt` | Take `master`'s file whole. The clean-merged hunks already rename `lastSelectionCategory` to `lastSelection` elsewhere in the file, so keeping our side of the two conflict hunks would not compile. Port `master`'s range rule into the desktop's `toggleLibraryRangeSelection`. |

`WebViewUtil.kt` (+91 lines on `master`) merges cleanly into its moved `androidMain` path through rename detection, as
do the other 15 moved files `master` did not touch.

### 2.2 Clean merges that need a look (semantic risk)

1. **The desktop build breaks on `MdList.kt`.** It is a `desktopCopy` borrowed file; `master` now creates a
   `MangaDexSessionNotifier`, a new `app` file that uses `R`, `NotificationReceiver` and Android notifications. It is
   not borrowed and cannot be. A desktop counterpart (same package, `app-shared/src/desktopMain`, a no-op or the tray
   notifier) is needed. The other 14 borrowed files `master` changed only add imports the desktop already has
   (`Manga.kt`, `AddTracks.kt`, `TrackPreferences.kt`, `MangaRestorer.kt`, `DownloadCache.kt`, `MyAnimeList*.kt`,
   `ShikimoriApi.kt`, `SimilarHandler.kt`, `MangaDexAuthInterceptor.kt`, `MangaDexLoginHelper.kt`, `MdConstants.kt`,
   `MdUtil.kt`, `UpdateMangaFromRemote.kt`); only a compile proves it.
2. **Cloudflare detection would differ between the platforms.** The desktop interceptor and `DesktopWebView` use the
   shared `CloudflareChallenge` (403 / 503 plus the challenge page markers; no `Origin` filtering; header names grouped
   case-sensitively). After the merge Android uses `cf-mitigated` and the new header rules. Port them to
   `CloudflareChallenge`, with a desktop test.
3. **`master`'s MDList logout fix** (`MdList.logout` now clears the interceptor's credentials) makes the desktop-only
   workaround in `DesktopTrackerLogin.logout` redundant; it can call the borrowed logout again.
4. **`DownloadCache.kt`** (both sides, clean): `master`'s fix for sources sharing a download folder lands next to our
   `closed` guards. Check that the new code paths also return early when closed. One of our guards
   (`cleanInvalidDownloads`) lacks its `// KMK` markers.
5. **`AndroidSourceManager.kt`** (both sides, clean): `master` now writes stub sources straight into `stubSourcesMap`; our
   JavaScript-source block registers stubs right after. Read the merged block once.
6. **`MdList.kt`** (both sides, clean): `master`'s session notifier and our "do not cache a missing MangaDex" getter sit
   in different hunks; fine for Android once item 1 is solved.
7. **Migration `48.sqm`**: run `:data` `DesktopDatabaseTest` (migrated and fresh databases reach the new version).
8. **`core/common/src/test`**: `master` adds `MangaDexUserAgentInterceptorTest`, which our KMP plugin compiles as
   `jvmSharedTest` (desktop and Android host). On the desktop `http.agent` is usually unset, so MangaDex requests get
   `Tachiyomi Dalvik/2.1.0`, the interceptor's fallback; harmless, worth knowing.
9. **`app/build.gradle.kts`, `proguard-rules.pro`**: `master` lets R8 shrink app code (`8dd0609e13`). Our Android build
   includes `:app-shared` and `:source-js`; only the `app.komikku.**` serializer rules keep them. No Android release
   is built here, so this is a note for whoever takes JavaScript sources to `master`.
10. **`i18n-kmk` base `strings.xml`**: both sides append; clean. After #2023 it also gets `migration_failed` (see 3).
11. **Fixes that do not reach the desktop by the merge** (the desktop has its own copy of the screen): history resume
    rules in `HistoryScreenModel` (the `GetNextChapters` part does arrive, it is in `domain`), WebView header and
    renderer fixes, the cover fetcher's concurrent-read fix, the reader's split / double-page exclusivity, library
    range selection. A parity check after the merge.

### 2.3 If #2023 is merged into `master` first: 16 conflicting files

`git merge-tree` of `92641111b6` (the pull request's head, i.e. `master` plus #2023) against `HEAD` adds 11 files to the
5 above: `GetSourcesWithFavoriteCount.kt`, `MigrateSourceScreen.kt`, `BulkFavoriteScreenModel.kt`, `FeedScreenModel.kt`,
`MigrateSourceScreenModel.kt`, `SourceFeedScreenModel.kt`, `MangaScreenModel.kt`, `MigrateMangaUseCase.kt`,
`MigrationListScreen.kt`, `MigrationListScreenModel.kt`, `domain` `Source.kt`. All are our copies of the same fixes in
a different shape (section 3); each resolves in `master`'s favour. Two more of them merge **cleanly but wrongly** and
must be fixed by hand: `CustomMangaRepositoryImpl.kt` ends up with both versions of the `edits.json` fix, and
`i18n-kmk` base `strings.xml` with both `migration_failed` and our `desktop_migration_failed`.

## 3. DECISION 004 check: pull request #2023

#2023 is open, 12 commits on `master`'s current tip, mergeable. It touches 20 files (11 `app`, 1 `data`, 1 `domain`,
`i18n-kmk` base, 6 new tests).

### (a) Android changes on this branch that #2023 does not carry

- **`MdList` no longer caches a missing MangaDex source** (`7aee688539`). Recorded in PROGRESS.md as a fix "for both
  platforms", not as an Android behaviour change. On `master`, `private val mdex by lazy { MdUtil.getEnabledMangaDex() }`
  (`MdList.kt` line 32) keeps a `null` for the life of the process: if MDList is used before MangaDex is installed,
  enabled or loaded, it stays unusable until a restart. A small, real Android fix; candidate for a patch file.
- **JavaScript sources on Android** (7 `app` files, `2527abe289`, `5a302b3166`, `9a416e0783`, `bed82d9c3b`) and **the
  QuickJS engine switch** (`935c6c9710`). Not bug fixes, so rightly not in #2023, but they are Android behaviour
  changes that live only here and are the largest Android difference on the branch. They need a maintainer decision
  (see the A3 recommendation).
- Left out by #2023 with reasons that hold on `master`: the settings-search index (`34ada5eb86`), the empty
  `Filter.Select` crash (`cda5152e54`; `master` fixed it in `presentation-core` `SettingsItems`), the range-selection
  crash (`2d720a702a`; `master` rewrote the code), `MigrateMangaDialog.onFailure` (desktop only), SY's
  `TimeRange.conflictsWith`. No other Android bug fix or Android behaviour change was found in the 79 files.

### (b) Changes in #2023 that are more than a pure bug fix

- **Fix 6 (failed migrations)** changes what users see: a new toast and string, failed entries stay in the list, "Migrate"
  / "Copy" for the whole list no longer navigates back when something failed, and a target whose source is missing
  now counts as failed. It also bundles a second bug in the same commit: after "Migrate now" on a single entry the
  details screen reopened the old entry (`lastMigratedTargetId`). The pull request says so in its notes; it is still two
  bugs in one commit, against "one bug per commit".
- **Fix 5** changes `MigrateMangaUseCase`'s return type from `Unit` to `Boolean` and logs failures that were swallowed;
  callers that ignore the result behave as before.
- **Fix 9** changes Mihon's sort order for descending sorts on the assumption that "not installed first" was meant for
  both directions (upstream has the same comparator).
- **Fix 10** drops built-in sources (the merged source) from the obsolete filter; a fix, but visible.
- **Fix 7** changes list keys only; no visible change beyond the crash.

### (c) Each fix: our copy and what happens at the merge

| # | Fix | Our copy | At the merge after #2023 |
|---|-----|----------|--------------------------|
| 1 | Feed limit `>=` | `isFeedFull()` in the shared `FeedItems.kt`, called by both screen models (`1cd37d0d52`, `74366d9715`) | Conflicts in `FeedScreenModel` and `SourceFeedScreenModel`; take `master`'s. `isFeedFull` stays for the desktop. |
| 2 | Source feed row without its saved search | `FeedItems.sourceFeedRows` skips it (`74366d9715`) | Conflict in `SourceFeedScreenModel`; take `master`'s. |
| 3 | `reverseSelection` by id | Same code, other comment (`18fb8d0294`), plus our visibility changes in the same file | Conflict; take `master`'s hunk, keep the visibility changes. |
| 4 | Lock time range across midnight | In the shared `AppLockRules.kt`, called by `SecureActivityDelegate` (`005e5a0da7`); `TimeRange.kt` untouched | **No conflict, does not fall away by itself:** `TimeRange.contains` gets `master`'s fix while Android's lock keeps using our `AppLockRules`. Two implementations of one rule until `SecureActivityDelegate` goes back to `master`'s shape. |
| 5 | Migration onto itself | Same code, other comment and log text (`ac0c6dea87`) | Conflict in `MigrateMangaUseCase`; take `master`'s (desktop gets it, the file is a `desktopCopy`). |
| 6 | Failed migrations | Same code and comments except the string: `desktop_migration_failed` (`b2304dc5b3`) | Conflicts in both migration-list files; take `master`'s. Switch the 3 desktop uses and 1 desktop test to `KMR.strings.migration_failed`, delete our key from base `strings.xml` (non-base copies are Weblate's; leave them). |
| 7 | `Source.key()` with a category `lastused` | Same code, other comment (`f18368c138`) | Conflict in `Source.kt`; take `master`'s. |
| 8 | Migrate tab spinner | Same `isLoading = false`, plus `loadFailed`, `retry()` and an `init` refactor for the desktop pane (`23e8082ee0`) | Conflict; take `master`'s. The desktop pane reads `loadFailed` / `retry()`: either re-add them minimally inside `// KMK`, or let the pane track the failure from `Event.FailedFetchingSourcesWithCount` (no Android diff). |
| 9 | Not installed first when descending | Same code, other comment (`4e928530fb`) | Conflict; take `master`'s. |
| 10 | Obsolete filter | `isStub \|\| isObsolete(obsoleteJs)`: the same rule plus obsolete JavaScript sources (`38167b325e`, `bed82d9c3b`) | Conflict; take `master`'s line; the JavaScript part stays only if JavaScript sources stay on Android. |
| 11 | Local entry status | In `MangaInfoEdits.localInfoEdit`, called by `MangaScreenModel` (`35c5f8081b`, `022b144627`) | Conflict in `MangaScreenModel`; take `master`'s. `MangaInfoEdits` keeps the fix for the desktop. |
| 12 | `edits.json` after the last reset | Deletes the file after the write block (`6f3f43a72c`); #2023 deletes and returns before it | **Clean merge with both blocks.** Remove ours by hand. |

## 4. The two items not yet checked on `master`

### 4.1 `GetApplicationRelease` with an odd tag — a latent bug on `master`

`domain/.../release/interactor/GetApplicationRelease.kt` on `master`:

```kotlin
val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")
return if (isPreview) {
    newVersion.toInt() > commitCount
} else {
    val oldVersion = versionName.replace("[^\\d.]".toRegex(), "")
    val newSemVer = newVersion.split(".").map { it.toInt() }
    val oldSemVer = oldVersion.split(".").map { it.toInt() }
    oldSemVer.mapIndexed { index, i ->
        if (newSemVer[index] > i) { return true }
        if (newSemVer[index] < i) return false
    }
    false
}
```

- A tag without digits (`nightly`) or a preview tag with a dot (`r12.4`) throws `NumberFormatException`; a stable tag
  with fewer parts that matches the current version so far (`v1.14` against `1.14.1`) throws
  `IndexOutOfBoundsException`.
- `await` runs `isNewVersion` inside `filter` over every release of the first page (KMK `releaseNotes`), so **one** such
  release makes the whole check fail, valid newer releases included. Callers catch it (`MainActivity` logs it,
  `AppUpdateJob` returns failure, About shows an error): no crash, but no update is offered.
- Only releases with a matching download link get that far, but `getDownloadLink` falls back to `map[null]`, which is
  any asset without an ABI suffix: a desktop installer attached to a release would qualify.
- Today it does not happen: the latest `komikku` tags are all `vX.Y.Z` and the preview tags `rNNNNN`. It becomes real the
  day an odd tag is published in either repository, for example a desktop release in the same repository.

**Fix sketch (patch, one commit):** parse each part with `toIntOrNull()`, treat a tag that does not parse as "not new"
(skip it), and compare the parts with a missing part counting as 0:

```kotlin
val newParts = newVersion.split(".").map { it.toIntOrNull() ?: return false }
val oldParts = oldVersion.split(".").map { it.toIntOrNull() ?: return false }
for (index in 0 until maxOf(newParts.size, oldParts.size)) {
    val new = newParts.getOrElse(index) { 0 }
    val old = oldParts.getOrElse(index) { 0 }
    if (new != old) return new > old
}
return false
```

and `newVersion.toIntOrNull()?.let { it > commitCount } ?: false` for previews. **Test:** `master` has
`domain/src/test/.../GetApplicationReleaseTest.kt` (MockK `ReleaseService`); add cases "a release with an odd tag next to
a newer valid one gives the valid one" (`nightly`, `v2.0.0`), "a shorter tag equal so far is not new" (`v1.0` against
`v1.0.1`), and "a preview tag with a dot is skipped" (`r12.4`, `r2000`). Fails today with the exceptions above. The
`map[null]` fallback is a separate question for the maintainer (only relevant once desktop releases are published next to
the APKs); not part of this patch.

### 4.2 `LibraryScreenModel` group type under a category filter — a real bug on `master`

With "filter by categories" on and at least one included category, `master` shows one ungrouped page, but the state
keeps the preference's group type, and the update button and pull-to-refresh use that.

`LibraryScreenModel.kt` (`master`), grouping:

```kotlin
.applyGrouping(
    data.categories,
    if (filterCategory && includedCategories.isNotEmpty()) {
        LibraryGroup.UNGROUPED
    } else {
        groupType
    },
    showHiddenCategories,
)
```

where `UNGROUPED` gives a single page `Category(0, "Ungrouped", …)`. `State.groupType` stays the preference value
(`groupLibraryBy().changes()` → `state.copy(groupType = it)`).

`LibraryTab.kt` (`master`), lines 121–133, called with `state.activeCategory` (the ungrouped page) by the toolbar's
"Update category" and by pull-to-refresh:

```kotlin
LibraryUpdateJob.startNow(
    context = context,
    category = if (state.groupType == LibraryGroup.BY_DEFAULT) category else null,
    group = state.groupType,
    ...
)
```

So with the default grouping it starts an update of `Category(0)`, and `LibraryUpdateJob` keeps
`libraryManga.filter { categoryId in it.categories }`: the entries of the **Default** category, not the ones shown. With
the included categories not containing Default, nothing or the wrong entries are updated while the snackbar says
"Updating category". The `sort.takeIf { groupType != LibraryGroup.BY_DEFAULT }` in the same flow reads the preference
value too, so the ungrouped page takes the per-category sort path instead of the global sort; whether that is
intended was not checked.

**Fix sketch (patch, one commit):** a KMK `val effectiveGroupType` on `LibraryScreenModel.State`
(`if (filterCategory && includedCategories.isNotEmpty()) LibraryGroup.UNGROUPED else groupType`), used by the grouping
above and by `LibraryTab`'s `onClickRefresh` (`category` then becomes `null` and the job takes its `UNGROUPED` branch,
which follows `groupLibraryUpdateType` like any ungrouped page; this matches the desktop fix `e9b43139e3`). **Test:**
`master`'s `app/src/test` has no library tests, but `State` is a data class with defaults, so a plain JUnit test of
`effectiveGroupType` (filter on with included categories → `UNGROUPED`; filter off or no included category → the
preference value) works without Injekt. The update itself (`LibraryTab` → `LibraryUpdateJob`) has no test means on
`master`; compile-checked only.

## 5. A3 — shrinking the conflict surface

The principle: every line we change in a file `master` owns is a future conflict, and Android code here is never
tested (no Android tests or CI on this branch), so a conflict resolved in shared Android code is the riskiest kind.
Where we moved Android code into a shared KMK file, each upstream fix to that code now conflicts and must be ported by
hand anyway; a desktop-only copy needs the same port without the conflict and leaves Android byte-identical to
`master`. The price is that parity is kept by tests and by reading `master`'s changes at each merge, not by sharing.

### 5.1 Candidates to move out

| # | Change | Move to | Effort | Removes |
|---|--------|---------|--------|---------|
| M1 | Cloudflare / WebView interceptors: Android files back to `master`'s shape; `CloudflareChallenge` desktop-only (`core/common/src/desktopMain`), brought to `master`'s detection and header rules, with a desktop test | `core:common` desktop source set | S (2–3 h) | 2 of the 5 conflicts, now and later |
| M2 | `SourceFilterDialog`: Android back to `master`'s `FilterItem`; `SourceFilterItems.kt` used by the desktop only (or moved to `app-shared/src/desktopMain`) with `master`'s tri-state fix | `:app-shared` desktop | S (1–2 h) | 1 conflict, and keeps `master`'s filter fix on Android |
| M3 | `LibraryScreenModel`: Android back to `master`'s file; `LibraryActions.kt` and the library filter / sort / grouping files used by the desktop only; `master`'s range rule ported with a test | `:app-shared` desktop | M (about 1 day) | 1 conflict; the largest future surface (−882 lines in the file upstream changes most) |
| M4 | After #2023: `FeedScreenModel`, `SourceFeedScreenModel`, `FeedScreen`, `SourceFeedDialogs`, `MangaScreenModel` back to `master`'s shape; `FeedItems.kt`, the shared feed dialogs and `MangaInfoEdits.kt` desktop-only | `:app-shared` desktop | S–M (half a day) | 3 of the 11 #2023 conflicts, permanently |
| M5 | `MigrateSourceScreenModel`: desktop pane tracks a failed read itself instead of `loadFailed` / `retry()` | `:desktopApp` | S (1–2 h) | the extra part of one #2023 conflict |
| M6 | `SecureActivityDelegate` back to `master`'s shape; `AppLockRules.kt` desktop-only (can call `TimeRange` once #2023 is merged) | `:app-shared` desktop | S | no conflict today; ends fix 4's double implementation |
| M7 | Desktop job of `build_pull_request.yml` moved into the desktop workflow (add a `pull_request` trigger there) | `.github/workflows/build_desktop.yml` | S (30 min) | the only CI diff; Renovate bumps that file often |
| M8 | Visibility: `BulkFavoriteScreenModel` members back to `internal`, reached through small public wrappers in `app-shared/src/desktopMain` (the `desktopCopy` is compiled in that module, as `DownloadCache.stopForDesktopTeardown()` already does) | `:app-shared` desktop | S–M | one file's diff; no conflict today |
| M9 | JavaScript sources on Android and the QuickJS switch: either removed from Android here (Android back to the `quickjs-android` AAR, `:core:quickjs` for desktop and iOS only) or proposed to `master` as a feature | — (maintainer) | M (half a day to a day to remove) | 7 `app` files and parts of 4 build files; no conflict today |

`MdList` (`7aee688539`) needs no move: as an Android fix it goes to `master` as a patch and falls away at a later merge.

### 5.2 What stays, and why

- **The KMP conversion of the shared modules** (20 build files, `settings.gradle.kts`, `buildSrc`): the desktop and iOS
  builds rest on it. `core/common/build.gradle.kts` will conflict whenever `master` adds an Android dependency there; it
  is resolved by hand (into `androidMain`). Teaching `mihon.library.kmp` to route a list of Android-only artifacts so the
  top-level `dependencies {}` block can stay as on `master` is possible but a build-convention change, not worth it yet.
- **The 18 moved files**: rename detection carried `master`'s +91 lines in `WebViewUtil` cleanly; they conflict only
  where we also edited them (M1 fixes that). Keeping them in `src/main` through a per-file Android-only list in the KMP
  plugin would make them zero-diff, but is a build-convention change with IDE side effects. Leave.
- **Borrowed-source enablement hooks** (8 files: `DownloadCache`, `ReleaseServiceImpl`, `CbzCrypto`, `StorageManager`,
  `SourcePreferences`, `SetReadStatus`, `SearchScreenModel`, `CustomColorScheme`): small, inside KMK markers, and what
  makes borrowing possible. `DownloadCache` is the largest (guards in 11 methods of a file `master` changes often);
  watch it, and if it conflicts twice, replace the guards by cancelling an injected scope.
- **`MigrateMangaDialog`** (public, `onFailure`): needed by the desktop's single-entry migration; small.
- **`i18n-kmk` base `strings.xml`**: all new strings must go there (AGENTS.md); ours are one block at the end, which
  merges cleanly.
- **`AGENTS.md`** (4 lines) and **`.gitattributes`** (1 rule): tiny; the `AGENTS.md` pointer could go to `master` as a
  docs change if the maintainer wants it there.
- **The remaining moved code** (`SettingsSearchScreen` / `PreferenceScreen`, `MigrationConfigScreen`): no conflict yet;
  move only if it conflicts twice (COORDINATION.md "Merging master").

### 5.3 Recommendation

1. **Do M1, M2 and M3 as part of the coming merge** (resolve the three in `master`'s favour and keep our versions
   desktop-only). Together they remove 4 of the 5 predicted conflicts for good; the fifth (`core/common/build.gradle.kts`)
   is a one-line hand resolution. Also fix the `MdList` desktop build break (2.2 item 1) and port `master`'s Cloudflare
   rules to `CloudflareChallenge` (2.2 item 2) in the same merge.
2. **Merge now rather than after #2023**: 5 conflicts now; when #2023 lands, the next merge's 11 conflicts are all "take
   `master`'s" plus the two hand fixes (`CustomMangaRepositoryImpl`, `desktop_migration_failed`). Do M4–M6 in that
   second merge.
3. **M7** any time (cheap). **M8** only if `BulkFavoriteScreenModel` conflicts.
4. **Ask the maintainer about M9** (JavaScript sources and the QuickJS engine on Android): it is the largest Android
   difference on the branch and the only one that is a feature.
5. **Leave** the module conversion, the moved files, the enablement hooks, the strings and the docs as they are.

## 6. Open points

- M1–M6 reverse earlier decisions to share code with Android (PROGRESS.md "Shared with Android"); that is a structure
  decision for Atlas, hence the question rather than doing it.
- The patch files for 4.1, 4.2 and `MdList` need a checkout of `master` (`spotlessCheck`, `:app:compileDebugKotlin`, the
  tests); not prepared here.
- Not verified by a build: that the 14 other changed borrowed files compile for the desktop, and that `master`'s new
  `core/common` test compiles in our `jvmSharedTest`. The merge's gates will show it.
- Whether the ungrouped page's sort under a category filter (4.2, last paragraph) is a second bug was not checked.

## 7. A4 — the first merge of `master` (2026-11-28)

`origin/master` `15610e67e4` merged into `multiplatform` `209b80d910` on the branch `merge/master-a4` (merge commit
`1e930163bc`), under [ANSWER 009](ATLAS_INSTRUCTIONS.md). The conflicts and their resolutions are in PROGRESS.md
("Merge of `master` A4"). The five predicted conflicts were the only ones.

### 7.1 Desktop copies of `master` code

Each copy carries a header naming the `master` file it follows and the `master` commit it was last synced to. At each
merge of `master`, re-sync every copy whose `master` file changed:
`git diff <synced>..origin/master -- <master file>`, port what applies, and move the commit in the header and here.

| Desktop copy | Follows (`master` path) | Synced to |
|--------------|-------------------------|-----------|
| `app-shared/src/desktopMain/kotlin/eu/kanade/tachiyomi/ui/library/LibraryActions.kt` | `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryScreenModel.kt` (selection, range selection, batch actions) | `15610e67e4` |
| `app-shared/src/desktopMain/kotlin/eu/kanade/tachiyomi/ui/library/LibraryFilterSort.kt` | `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryScreenModel.kt` (filters, grouping, sort, text search) | `15610e67e4` |
| `app-shared/src/desktopMain/kotlin/eu/kanade/tachiyomi/ui/browse/source/browse/SourceFilterItems.kt` | `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/SourceFilterDialog.kt` (`FilterItem`, tri-state conversions) | `15610e67e4` |
| `core/common/src/desktopMain/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareChallenge.kt` | `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt` (`shouldIntercept`, `SERVER_CHECK`, `COOKIE_NAMES`) and `core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt` (`Headers.toWebViewHeaders`, `isRequestHeaderSafe`) | `15610e67e4` |

Tests that pin the parity, written from `master`'s code: `desktopApp` `LibraryActionsTest` (range selection),
`SourceFilterItemUiTest` (filter rows, `master`'s invalid-filter fix), `core:common` `CloudflareChallengeTest`
(`cf-mitigated` detection, browser headers).

Desktop counterparts (same name and members, desktop behaviour, no `master` logic to re-sync): the new
`app-shared/src/desktopMain/kotlin/exh/md/network/MangaDexSessionNotifier.kt` (logs instead of a notification) joins
the existing ones listed in `app-shared/borrowed-app-sources.gradle.kts`.

### 7.2 Hooks and build files: the allowed differences

The rule since ANSWER 009: a file `master` owns is byte-identical to `master`'s unless it is on this list. A hook is
a declaration-level line, never behaviour (ANSWER 014).

- **Borrowing hooks (`actual`)**, so shared code can use a platform class:
  - `core/archive/src/androidMain/kotlin/mihon/core/archive/ArchiveReader.kt`: `actual` on the class and on its six
    members the shared `expect class` declares (one modifier per declaration line, inside KMK markers).
  - `core/common/src/androidMain/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`:
    `actual class CloudflareInterceptor actual constructor(` instead of `class CloudflareInterceptor(`, inside KMK
    markers (kept by ANSWER 014, see below).
- **Borrowed-source enablement hooks** (5.2): `DownloadCache`, `ReleaseServiceImpl`, `CbzCrypto`, `StorageManager`,
  `SourcePreferences`, `SetReadStatus`, `SearchScreenModel`, `CustomColorScheme`; and `MigrateMangaDialog` (public,
  `onFailure`).
- **Build files** (1.3): `settings.gradle.kts`, `buildSrc` (3), `app/build.gradle.kts`, `app/proguard-rules.pro`, the
  shared modules' `build.gradle.kts` (11), `gradle/*.versions.toml` (3).
- **Moved files** (16 byte-identical moves to `androidMain` / `core/preference`, carried by rename detection).
- **Small rest:** `i18n-kmk` base `strings.xml` (our block at the end), `AGENTS.md`, `.gitattributes`.
- **Temporary differences** (a fix `master` gets as a patch, kept here until then; ANSWER 015):
  - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt`: the reader download race fix
    (`0a77c5bc21`, one KMK block and an import), sent to `master` as `android-fixes/httppageloader-race.patch` for
    #2023, byte-identical to this branch's file. Exit: removed by the merge of `master` that contains #2023.

**`CloudflareInterceptor.kt` is not byte-identical, by one line.** `NetworkHelper.kt` (shared code, byte-identical to
`master`) constructs it, so on this branch it is an `expect class` in `core/common/src/jvmSharedMain`, and Kotlin
requires the Android class to say `actual class CloudflareInterceptor actual constructor(` (inside KMK markers,
the same hook the file had before). The only byte-identical alternative is to move `NetworkHelper.kt` to
`androidMain` too and keep a desktop copy of it (about 200 lines). That copy would have to be re-synced by hand and
would silently miss what `master` adds to the clients (this merge added `MangaDexUserAgentInterceptor` there), so the
hook was kept, and Atlas confirmed it (ANSWER 014): if a merge of `master` ever conflicts on that line, reconsider
then.

### 7.3 What could move back next time

76 files `master` owns still differ from `master` (79 before the merge): 58 edited in place and 18 moved, 16 of them
byte-identical. Beyond the list in 7.2:

- **When #2023 is merged** (section 2.3 and 3, M4–M5): `GetSourcesWithFavoriteCount`, `MigrateSourceScreen`,
  `BulkFavoriteScreenModel` (fix part; visibility stays until M8), `FeedScreenModel`, `MigrateSourceScreenModel`,
  `SourceFeedScreenModel`, `MangaScreenModel`, `MigrateMangaUseCase`, `MigrationListScreen`, `MigrationListScreenModel`,
  `domain` `Source.kt`, `data` `CustomMangaRepositoryImpl`, and `MdList.kt` (our "do not cache a missing MangaDex"
  change, now patch 3 in #2023). Also `FeedScreen` and `SourceFeedDialogs` (M4), whose shared parts become desktop
  copies like the four of this merge.
- **Moved code without a conflict yet:** `SecureActivityDelegate` (M6, `AppLockRules.kt`), `SettingsSearchScreen` and
  `PreferenceScreen` (`SettingsSearch.kt`), `MigrationConfigScreen`. Same treatment as above when they first conflict,
  or earlier.
- **JavaScript sources on Android** (7 `app` files and the QuickJS build lines, M9): wait for the maintainer.
- **Desktop-only clean-up the merge made possible:** `DesktopTrackerLogin.logout` clears the MDList interceptor's
  credentials itself; `master`'s `MdList.logout` now does it (`desktopApp` file, harmless, not changed).

### 7.4 Parity checks left by this merge

- The desktop has no "MangaDex session expired" notification (`master`'s notifier): the counterpart logs, and
  Settings > Tracking shows MDList logged out.
- `master`'s Cloudflare work beyond detection: one solve per host and the memory of a failed solve have desktop
  equivalents of their own (`CloudflareHostState`). Two parts do not (REVIEW_A4.md 2.1 / 2.2, follow-ups after the
  landing): the desktop hands a challenged non-GET request's own URL to the browser instead of the site's root
  (`master`'s `14c94f5cec`), so a challenged POST waits for the 30 s timeout and fails; and it does not give up when
  the challenge turns interactive (`b672f8028e`), so it holds the host until the same timeout.
- The library's "reset info" confirmation (`084750b89b`) has no desktop counterpart because the desktop library has no
  batch "reset info".
- Section 2.2 item 11 still applies, except for the WebView header rules (ported in `CloudflareChallenge`) and range
  selection (ported in `LibraryActions`): history resume rules, the WebView renderer fixes, the cover fetcher and the
  reader's split pages reach Android only.
