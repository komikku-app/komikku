# app/ Module

The monolithic application module containing UI, domain, data services, and DI wiring. Organized by fork origin (4 package roots), not architectural layer.

## Package roots

| Root | Origin | Content |
|---|---|---|
| `eu.kanade.tachiyomi.*` | Original Tachiyomi | Activities, screens, widgets, DI, data services |
| `eu.kanade.domain.*` | Tachiyomi refactor | App-level domain interactors |
| `eu.kanade.presentation.*` | Compose migration | Compose screens, components, theme |
| `exh.*` | TachiyomiSY/ExHentai | E-Hentai, MangaDex, recommendations |
| `mihon.*` | Mihon upstream | Migration, upcoming, Shizuku |

## Key directories

| Path | Purpose |
|------|---------|
| `eu.kanade.tachiyomi.ui.*` | Voyager Screens + ScreenModels |
| `eu.kanade.presentation.*` | Compose UI (screens, components, theme) |
| `eu.kanade.tachiyomi.di/` | AppModule, PreferenceModule |
| `eu.kanade.domain/` | DomainModule, KMKDomainModule, SYDomainModule |
| `eu.kanade.tachiyomi.data.*` | Infrastructure: backup, download, sync, track, coil |
| `exh/` | E-Hentai/ExHentai features (see exh/AGENTS.md) |
| `mihon/feature/` | Self-contained feature modules (migration, upcoming) |

## DI modules (loaded in order)

1. `PreferenceModule` – All preference stores
2. `AppModule` – Infrastructure singletons (DB, network, downloads)
3. `DomainModule` – Core domain interactors + repos
4. `KMKDomainModule` – Komikku-only (library update errors)
5. `SYPreferenceModule` – SY/ExH preferences
6. `SYDomainModule` – SY domain (metadata, merge, feed)

## Screen pattern

Voyager `Screen` + `ScreenModel` in `ui/`, Composable in `presentation/`:
- `LibraryTab.kt` → `LibraryScreenModel.kt` → `presentation/library/`
- `MangaScreen.kt` → `MangaScreenModel.kt` → `presentation/manga/`
- `ReaderActivity` uses `ReaderViewModel` (AndroidX ViewModel, not Voyager)

## Theme system

19 named color schemes extending `BaseColorScheme`. KMK adds cover-based dynamic theming via `DynamicMaterialExpressiveTheme`.

## Fork markers

All Komikku additions wrapped in `// KMK -->` ... `// KMK <--`. SY additions in `// SY -->` ... `// SY <--`.

## Conventions

- Logging: `xLogE()` / `xLog()` from `exh.log` for KMK code; `logcat {}` for Mihon code
- Preferences: `eu.kanade.domain.*.service.*Preferences`
- Strings: `KMR` + `i18n-kmk/` for Komikku-only features
