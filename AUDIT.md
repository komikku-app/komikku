# Komikku Codebase Audit Report

## 1. Critical Bugs

| # | Issue | Location |
|---|-------|----------|
| 1 | **WebViewScreenModel uses wrong state type** — `StateScreenModel<StatsScreenState>` (copy-paste bug, never uses the state) | `ui/webview/WebViewScreenModel.kt:23` |
| 2 | **3 trackers crash with `TODO("Not yet implemented")`** — Suwayomi, Komga, Kavita search will throw `NotImplementedError` | `data/track/suwayomi/Suwayomi.kt:69`, `komga/Komga.kt:73`, `kavita/Kavita.kt:75` |
| 3 | **SQLDelight version conflict** — dialect `2.3.2` > core `2.2.1`, potential runtime errors | `libs.versions.toml:7,94` |
| 4 | **`InMemoryPreferenceStore.getStringSet()` throws TODO** — crashes any test/preview using StringSet prefs | `core/common/.../InMemoryPreferenceStore.kt:51` |

## 2. Performance Issues

| # | Issue | Location |
|---|-------|----------|
| 5 | **N+1 queries in library** — `getMergedMangaById.await()` called inside filter lambdas for every item | `LibraryScreenModel.kt:461,794` |
| 6 | **Missing throttle in bulk favorite** — can hammer source APIs during batch operations | `BulkFavoriteScreenModel.kt:263,365` |
| 7 | **29k lines of hardcoded tag data** — `exh/eh/tags/*.kt` are massive Kotlin objects that should be a JSON resource loaded at runtime | `exh/eh/tags/` (8 files) |

## 3. God Classes (Architecture)

| File | Lines | Functions | DI Deps | Problem |
|------|-------|-----------|---------|---------|
| `MangaScreenModel.kt` | 2,200 | 82 | 56 | Does everything: chapters, downloads, tracking, metadata, UI state |
| `LibraryScreenModel.kt` | 1,839 | 50 | 31 | Filter logic + N+1 issues |
| `ReaderViewModel.kt` | 1,548 | 65 | 29 | Only ViewModel in codebase (inconsistent pattern) |
| `EHentai.kt` | 1,478 | 65 | — | Implements 6 interfaces |
| `MangaScreen.kt` | 1,447 | — | — | Monolithic Compose screen |
| `TrackInfoDialog.kt` | 984 | — | 51 | Massive DI constructor |

## 4. Missing Test Coverage

| Layer | Tests | Coverage |
|-------|-------|----------|
| Domain interactors (98 total) | 2 | **2%** |
| Domain services (15 total) | 3 | 20% |
| Domain models (41 total) | 1 | 2% |
| Data layer (18 repos, 46 migrations) | 0 | **0%** |
| Presentation (33 ScreenModels) | 0 | **0%** |
| Core modules (archive, network) | 0 | **0%** |

**Most critical untested paths:**
- `ChapterSort` (pure logic, affects reading)
- `SyncChaptersWithSource` (chapter reconciliation)
- `NetworkToLocalManga` (every manga fetch)
- All 46 SQLDelight migrations
- `CbzCrypto` (encryption, security-critical)

## 5. Feature Gaps

| # | Feature | Status |
|---|---------|--------|
| 8 | **Backup restore doesn't trigger library/tracker update** | TODO in code |
| 9 | **Library update skip reasons not shown to user** | Only logged |
| 10 | **Chapter list long-click in reader** | Not wired |
| 11 | **Animated cover handling** | Not implemented |
| 12 | **Bulk unfavorite can't delete chapters** | Works for single manga but not bulk |
| 13 | **Favorites sync not atomic** | Applied incrementally, risk of partial state |
| 14 | **Tracker scores not normalized** | Different scales per tracker |
| 15 | **Private extension installer gated** | Hidden in stable builds |

## 6. Code Quality

| # | Issue | Scope |
|---|-------|-------|
| 16 | **Download logic duplicated 4x** across `MangaScreenModel`, `ReaderViewModel`, `UpdatesScreenModel`, `LibraryUpdateJob` | 4 files |
| 17 | **`MERGED_SOURCE_ID` checked 111 times** — missing abstraction for merged-source-aware operations | ~20 files |
| 18 | **166 `@Suppress` annotations** (40+ `DEPRECATION`, 8 `UNCHECKED_CAST`) | 104 files |
| 19 | **98+ `Injekt.get()` calls in composables** — bypasses lifecycle management | 36 files |
| 20 | **Legacy database models still in use** (`eu.kanade.tachiyomi.data.database.models.*`) | 48 files |
| 21 | **`ClearDatabaseScreenModel` bypasses repository** — direct DB access | `ClearDatabaseScreen.kt:226` |

## 7. Build & Dependencies

| # | Issue | Severity |
|---|-------|----------|
| 22 | **Voyager on beta** (`1.1.0-beta03`) | High |
| 23 | **Deprecated AGP split API** (`isEnable`, `isUniversalApk`) | Medium |
| 24 | **`android.nonTransitiveRClass=false`** — hurts build speed + APK size | Medium |
| 25 | **7 JitPack commit-hash deps** — not reproducible builds | Medium |
| 26 | **RxJava 1.x still in use** | Low |

---

## Additional Findings (from detailed exploration)

### DI Issues
- Duplicate factory registrations in `SYDomainModule` for `GetFlatMetadataById`/`InsertFlatMetadata`
- `SmartSearchMerge` uses `Injekt.get()` in constructor defaults alongside DI registration

### Naming Convention Violations
- `ShouldUpdateDbChapter`, `IsTrackUnfollowed`, `MigrateMangaUseCase`, `SmartSearchMerge` break the `Get/Set/Insert/Delete/Update` pattern

### Mixed Patterns
- `ExtensionInstallActivity` and `DeepLinkActivity` extend raw `Activity()` instead of `BaseActivity()`
- `SourcePreferencesScreen` embeds a legacy `PreferenceFragmentCompat` in Voyager
- `SettingsAdvancedScreen` accesses `NetworkHelper` directly in a composable

### Tech Debt Markers
- 41 TODO/FIXME/HACK comments across 33 files
- `FavoritesSyncHelper` applies DB changes incrementally (not atomic)
- `HttpSource.kt` has 13 `@Suppress("DEPRECATION")` annotations
- `MERGED_SOURCE_ID` pattern duplicated 111 times without abstraction
