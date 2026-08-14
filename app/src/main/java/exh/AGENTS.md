# exh/ Module

E-Hentai/ExHentai, MangaDex, NHentai, 8Muses, Pururin, LANraragi integration. Package root: `exh.*`.

## Key directories

| Path | Purpose |
|------|---------|
| `exh/eh/` | E-Hentai update helpers, tag definitions |
| `exh/md/` | MangaDex API client, handlers, DTOs, OAuth |
| `exh/recs/` | Recommendation system (6 sources) |
| `exh/favorites/` | Two-way favorites sync with ExHentai |
| `exh/ui/metadata/` | Metadata viewer + per-source description adapters |
| `exh/ui/batchadd/` | Batch gallery adding from URL list |
| `exh/ui/smartsearch/` | Smart search UI |
| `exh/search/` | Custom search engine with query parsing |
| `exh/smartsearch/` | Fuzzy title matching for library lookup |
| `exh/uconfig/` | E-Hentai server profile auto-configuration |
| `exh/log/` | XLog logging wrappers (KMK) |

## Source implementations

**`EHentai.kt`** (1463 lines) – Core E-Hentai/ExHentai source:
- Implements5 interfaces: `HttpSource`, `MetadataSource`, `UrlImportableSource`, `NamespaceSource`, `PagePreviewSource`
- Handles both EH and EXH (controlled by `exh: Boolean` constructor param)
- KMK: per-language source IDs (36 total:18 EH +18 EXH)
- Login via cookies: memberId, passHash, igneous, sk, s, hath_perks

**Delegated sources:** NHentai, 8Muses, Pururin, LANraragi (tracked in `SourceHelper.kt`)

**MangaDex:** Full API client stack with OAuth auth, external source page delegation

## Metadata system

**Two-tier model:**
1. `FlatMetadata` (DB) = `SearchMetadata` + tags + titles
2. `RaisedSearchMetadata` (runtime) = typed subclasses deserialized from JSON

**Per-source metadata:** `EHentaiSearchMetadata`, `MangaDexSearchMetadata`, `NHentaiSearchMetadata`, etc.

**UI:** Per-source `*DescriptionAdapter` composables for metadata display

## Recommendation system

`RecommendationPagingSource` base class with6 implementations:
- AniList (GraphQL), MAL (Jikan v4), MangaUpdates, MangaDex, Comick
- `StaticResultPagingSource` for pre-computed batch results
- `RecommendationSearchHelper` processes entire library, ranks by frequency

## Key patterns

- Fork markers: `// KMK -->` for Komikku additions
- DI: Injekt with `injectLazy()`, `ExhPreferences`
- URL import: `GalleryAdder.pickSource()` → `matchesUri()` → `mapUrlToMangaUrl()`
- Source ID constants in `source-api/.../SourceIds.kt`

## Conventions

- Prefer `// KMK -->` for new Komikku-only code (not `// EXH -->`)
- Logging: `xLogE()` / `xLog()` from `exh.log`
- Strings: `KMR` + `i18n-kmk/`
