# Komikku – AI Agent Guide

Komikku is an Android manga reader (min SDK 26, target SDK 36, Java 17 / Kotlin) forked from **Mihon** + **TachiyomiSY**. It uses Jetpack Compose + Material3, Voyager navigation, SQLDelight, and Injekt for DI.

---

## Module Layout

| Module | Purpose |
|--------|---------|
| `app/` | Main app: UI screens (`eu.kanade.*`), DI wiring, build variants |
| `domain/` | Use-cases (`*Interactor`), models and repository interfaces (`tachiyomi.domain.*`) |
| `data` | SQLDelight DB, repository implementations `*RepositoryImpl` (`tachiyomi.data.*`) |
| `core:common/` | Network (OkHttp), security, storage utilities |
| `core:archive/` | CBZ/archive reading with optional encryption |
| `core-metadata/` | Comic-info metadata parsing |
| `source-api` / `source-local` | Extension `Source` API + local source |
| `presentation-core` | Shared Compose components and theming |
| `presentation-widget/` | Home-screen Glance widget |
| `i18n/` | Base Mihon `MR` strings (moko-resources) |
| `i18n-kmk/` | Komikku-specific `KMR` strings |
| `i18n-sy/` | TachiyomiSY `SYMR` strings |
| `flagkit/` | Country-flag drawables |
| `telemetry/` | Firebase/crashlytics wrapper (optional variant) |
| `macrobenchmark/` | Macrobenchmark tests |

Dependency flow: `app` → `domain` → `source-api`; `data` implements `domain` repos.

Version catalogs live in `gradle/`: `libs.versions.toml`, `kotlinx.versions.toml`, `androidx.versions.toml`, `compose.versions.toml`, `sy.versions.toml`.

---

## Architecture Patterns

**DI** – Uses `uy.kohesive.injekt` (not Hilt/Dagger). Singletons are registered in `AppModule.kt` via `addSingleton`/`addSingletonFactory`; consumed via `injectLazy<T>()` or `Injekt.get<T>()`.

**Navigation** – Voyager (`cafe.adriel.voyager`). Each screen has a companion `ScreenModel` extending `StateScreenModel<State>`. Use `screenModelScope` for coroutines; never create standalone `CoroutineScope` instances.

**Domain layer** – Follow *interactor* pattern: one class per operation in `domain/…/interactor/`. Repository interfaces live in `domain/`, implementations in `data/`.

**Database** – SQLDelight. Schema and queries are in `data/src/main/sqldelight/tachiyomi/`. Add `.sq` files there; generated Kotlin is in `build/`. Run `./gradlew generateSqlDelightInterface` after schema changes.

**Image loading** – Coil 3 (`coil3.*`). Use `imageLoader` extension and build `ImageRequest` objects; do **not** use Glide or Picasso.

**Reader** – The one screen still backed by a traditional `Activity` (`ReaderActivity` + `ReaderViewModel`). All other screens are Compose + Voyager.

## UI pattern

- **Navigation**: [Voyager](https://voyager.adriel.cafe/) — `Screen` subclasses in `eu.kanade.tachiyomi.ui.*`, composables in `eu.kanade.presentation.*`.
- **State**: `*ScreenModel` (`StateScreenModel` / `ScreenModel`) via `rememberScreenModel { … }`; inject deps with `Injekt.get()` or constructor defaults.
- **Reader**: `ReaderActivity` (not Voyager) — `ReaderActivity.newIntent(context, mangaId, chapterId)`.
- Base types: `eu.kanade.presentation.util.Screen`, `ioCoroutineScope` on `ScreenModel`.

Example: `DeepLinkScreen` + `DeepLinkScreenModel` in `app/src/main/java/eu/kanade/tachiyomi/ui/deeplink/`.

## Domain / data

- Business logic: small classes in `domain/.../interactor/` (e.g. `GetManga`, `SetMangaCategories`).
- Wire in `app/.../DomainModule.kt`, `KMKDomainModule.kt`, `SYDomainModule.kt` (Injekt `addFactory` / `addSingletonFactory`).
- DB: SQLDelight under `data/src/main/sqldelight/tachiyomi/` (`.sq` queries, numbered `migrations/*.sqm`). Schema changes need a new `.sqm` and often `// KMK` blocks in `.sq` / mappers.
- App prefs migrations: `app/src/main/java/mihon/core/migration/migrations/` (implements `mihon.core.migration.Migration`).

## Komikku-specific work

- Prefer **`KMR`** for new user-facing strings (`i18n-kmk/src/commonMain/moko-resources/base/`). Use **`MR`** for shared / upstream strings (`i18n`).
- Komikku DI: `KMKDomainModule`, `HideCategory`, library-update error repos — search `// KMK`.
- Feature flags / prefs: `eu.kanade.domain.*.service.*Preferences` (e.g. `SourcePreferences.relatedMangas()`).

## Extensions & sources

- Catalog sources: installable APK extensions (not in this repo). In-repo: **delegated** sources and metadata in `exh/` (E-Hentai, NHentai, MangaDex helpers, recommendations in `exh/recs/`).
- `source-api`: `eu.kanade.tachiyomi.source.*` — do not break extension ABI without strong reason.

---

## Build Variants, Flags & CI

Build types: `debug` (`.dev` suffix), `release`, `releaseTest`, `foss` (`.foss`), `preview` (`.beta`), `benchmark`.

Gradle property flags passed with `-P`:

JDK **17**. From repo root:

```bash
./gradlew assembleDebug                        # standard debug build
./gradlew assembleRelease -Penable-updater     # enable in-app updater
./gradlew assembleRelease -Pinclude-telemetry  # enable Firebase / Crashlytics
./gradlew assembleRelease -Pdisable-code-shrink  # skip R8 minification
./gradlew spotlessApply    # format (CI: spotlessCheck)
./gradlew assemblePreview   # main dev/CI APK (app id suffix `.beta`)
./gradlew testReleaseUnitTest
```

---

## Key Developer Commands

```bash
# Format code (must be clean before commit)
./gradlew spotlessApply

# Check lint / formatting
./gradlew spotlessCheck

# Run unit tests
./gradlew test

# Install debug build on connected device
./gradlew installDebug
```

Optional Gradle props (see `buildSrc/.../BuildConfig.kt`): `-Pinclude-telemetry`, `-Penable-updater`, `-Pdisable-code-shrink`.

---

## Fork-Origin Code Markers

Inline comment blocks tag code by origin – preserve them when editing:

```kotlin
// KMK -->  (Komikku-specific addition)
...
// KMK <--

// SY -->   (from TachiyomiSY)
...
// SY <--

// EH       (E-Hentai / extended features from exh/*)
```

Package roots reflect origin:
- `eu.kanade.tachiyomi.*` – legacy Tachiyomi code
- `tachiyomi.*` – newer domain/data layers
- `mihon.*` – Mihon refactors
- `exh.*` – enhanced/ SY / E-Hentai extensions

---

## Tests, Strings & i18n

- Unit tests: mainly `domain/src/test/`; app: `MigratorTest.kt`. No broad UI test suite.
- Translations: [Weblate](https://hosted.weblate.org/engage/komikku-app/) — do not hand-edit locale `strings.xml` except `i18n-kmk/.../base/` for new Komikku keys.

Add Komikku-specific strings to `i18n-kmk/src/commonMain/moko-resources/base/`. Do **not** modify `i18n/` (upstream Mihon strings) or `i18n-sy/` unless syncing upstream. Translations are managed externally via Weblate.

## Conventions

- **Logging**: `xLogLogcatLogger` (`exh.log`),not logcat library or raw `Log`.
- **Coroutines**: `launchIO` / `withIOContext` from `tachiyomi.core.common.util.lang`.
- **Formatting**: Spotless + ktlint (`buildSrc/.../mihon.code.lint.gradle.kts`).
- New Komikku features: follow existing `// KMK` islands; keep Mihon/SY blocks intact when merging upstream.

---

## Key Files

- `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt` – feature flags
- `buildSrc/src/main/kotlin/mihon/buildlogic/AndroidConfig.kt` – SDK/Java versions
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` – DI wiring
- `app/build.gradle.kts` – all build variants, splits, compiler opts
- `settings.gradle.kts` – module graph

Key entrypoints: `App.kt` (Injekt bootstrap), `MainActivity.kt`, `settings.gradle.kts` (modules).
