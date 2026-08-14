# domain/ Module

Core domain layer with104 interactors,28 models, and18 repository interfaces. Package root: `tachiyomi.domain.*` (90%), `mihon.domain.*` (Mihon upstream), `exh.*` (SY features).

## Package structure

```
tachiyomi/domain/
├── manga/          # Manga, MangaUpdate, MangaCover, MergedMangaReference
├── chapter/        # Chapter, ChapterUpdate, ChapterRecognition
├── category/       # Category, CategoryUpdate
├── history/        # History, HistoryWithRelations
├── track/          # Track
├── updates/        # UpdatesWithRelations
├── source/         # Source, SavedSearch, FeedSavedSearch
├── library/        # LibraryManga, Flag
├── libraryUpdateError/  # LibraryUpdateError, LibraryUpdateErrorWithRelations
├── release/        # Release, AppUpdatePolicy
├── storage/        # StorageManager, StoragePreferences
├── download/       # DownloadPreferences
└── backup/         # BackupPreferences
```

## Interactor patterns

**Naming:** `{Verb}{Entity}` (e.g., `GetManga`, `SetMangaChapterFlags`, `InsertTrack`, `DeleteChapters`)

**Method conventions:**
- `await(...)` – One-shot suspend read/write
- `subscribe(...)` – Long-lived reactive stream (Flow)
- `invoke(...)` / `operator fun invoke` – Single-purpose use case

**Three archetypes:**
1. **Simple delegate** (~60%): Constructor-injected repo, `await()`/`subscribe()` forwarding
2. **Error-wrapping** (~20%): try/catch + logcat + safe default
3. **Business logic** (~20%): Domain rules, multiple repos, `sealed interface Result`

## Repository interfaces (18)

All pure interfaces in `domain/.../repository/`. Implemented in `data/` module.

**Method patterns:**
- `getXById(id): T?` / `getXAsFlow(...): Flow<T>`
- `insertAll(list)` / `update(update)` / `delete(id)`

## Models (28)

**Core entities:** `Manga`, `Chapter`, `Category`, `Track`, `History`

**Update DTOs:** `MangaUpdate`, `ChapterUpdate`, `CategoryUpdate` (nullable fields for partial updates)

**View/join models:** `LibraryManga`, `HistoryWithRelations`, `UpdatesWithRelations`, `MangaWithChapterCount`

## Preferences (8 classes)

In `domain/.../service/`: `LibraryPreferences`, `HistoryPreferences`, `UpdatesPreferences`, `DownloadPreferences`, `BackupPreferences`, `StoragePreferences`, `ExhPreferences`

Wrap `PreferenceStore` from `core:common`. Registered in `AppModule.kt`.

## Conventions

- One class per file, verb-based naming (not `*Interactor` suffix)
- `withNonCancellableContext {}` for atomic multi-repo mutations
- `sealed interface Result` per interactor (not shared)
- Fork markers: `// KMK -->` for Komikku additions

## DI wiring

Interactors registered as **factories** (new instance per injection). Repositories registered as **singletons**. Wire in `DomainModule.kt`, `KMKDomainModule.kt`, `SYDomainModule.kt`.
