# data/ Module

SQLDelight database layer with23 tables/views,46 migrations, and18 repository implementations. Package root: `tachiyomi.data.*`.

## Schema organization

**SQL files** in `src/main/sqldelight/tachiyomi/data/`:
- `mangas.sq` (18 queries), `chapters.sq` (12), `history.sq` (6), `categories.sq` (5)
- `manga_sync.sq` (5), `merged.sq` (10), `saved_search.sq` (7), `feed_saved_search.sq` (10)
- `sources.sq` (3), `excluded_scanlators.sq` (3), `extension_store.sq` (5)
- `libraryUpdateError.sq` (5), `libraryUpdateErrorMessage.sq` (3)
- EH-specific: `search_titles.sq`, `search_tags.sq`, `search_metadata.sq`, `eh_favorites.sq`

**Views** in `src/main/sqldelight/tachiyomi/view/`:
- `libraryView` (~100 lines) – Manga + aggregated chapter stats + categories (UNION for merged source 6969)
- `updatesView` (~60) – Chapter updates with excluded scanlator awareness
- `historyView` (~50) – History + max read tracking + chapter count stats
- `libraryUpdateErrorView` – Error details with manga metadata

**Migrations** in `src/main/sqldelight/tachiyomi/migrations/`: `1.sqm` through `46.sqm`

## Repository implementations (18)

All in `tachiyomi.data.*`:
- `MangaRepositoryImpl` (219 lines), `ChapterRepositoryImpl` (198), `CategoryRepositoryImpl` (93)
- `HistoryRepositoryImpl` (120), `TrackRepositoryImpl` (86), `SourceRepositoryImpl` (116)
- `SavedSearchRepositoryImpl` (67), `FeedSavedSearchRepositoryImpl` (115)
- `MangaMergeRepositoryImpl` (127), `UpdatesRepositoryImpl` (105)
- `ExtensionStoreRepositoryImpl` (149), `LibraryUpdateErrorRepositoryImpl` (74)
- `MangaMetadataRepositoryImpl` (119), `FavoritesEntryRepositoryImpl` (66)
- `CustomMangaRepositoryImpl` (106) – File-based JSON, not SQL

**Common patterns:**
```kotlin
// Simple query with mapper
handler.awaitOne { mangasQueries.getMangaById(id, MangaMapper::mapManga) }

// Reactive query (Flow)
handler.subscribeToOne { mangasQueries.getMangaById(id, MangaMapper::mapManga) }

// Transaction with partial update
handler.await(inTransaction = true) { updates.forEach { mangasQueries.update(...) } }
```

## Mappers (11)

Objects: `MangaMapper`, `ChapterMapper`, `CategoryMapper`, `HistoryMapper`, `TrackMapper`, `MergedMangaMapper`, `SavedSearchMapper`, `FeedSavedSearchMapper`

Lambdas (KMK): `LibraryUpdateErrorMapper`, `LibraryUpdateErrorWithRelationsMapper`, `LibraryUpdateErrorMessageMapper`

## Column adapters

`DateColumnAdapter` (Long↔Date), `StringListColumnAdapter` (String↔List<String>), `UpdateStrategyColumnAdapter` (Long↔UpdateStrategy), `MemoColumnAdapter` (ByteArray↔JsonObject)

## SQL patterns

- **Coalesce updates:** `SET col = coalesce(:param, col)` for partial updates
- **UPSERT:** `INSERT ... ON CONFLICT DO UPDATE`
- **Sync-aware versioning:** Triggers increment `version` only when `is_syncing = 0`
- **Merged source handling:** All views use `UNION` for source ID 6969

## Key file

`DatabaseHandler.kt` / `AndroidDatabaseHandler.kt` – Core DB handler with coroutine dispatching

## Conventions

- After schema changes: create new `.sqm`, often add `// KMK` blocks in `.sq` / mappers
- Regenerate: `./gradlew :data:generateSqlDelightInterface`
- Views require full `DROP + CREATE` (SQLDelight limitation)
