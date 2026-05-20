# Komikku – AI Agent Guide

Komikku is an Android manga reader (min SDK 26, target SDK 36, Java 17 / Kotlin) forked from TachiyomiSY and Mihon. It uses Jetpack Compose + Material3, Voyager navigation, SQLDelight, and Injekt for DI.

---

## Module Layout

| Module | Purpose |
|---|---|
| `app/` | Main app: UI screens, DI wiring, build variants |
| `domain/` | Use-cases (interactors) and repository interfaces |
| `data/` | SQLDelight DB, repository implementations |
| `core:common/` | Network (OkHttp), security, storage utilities |
| `core:archive/` | CBZ/archive reading with optional encryption |
| `core-metadata/` | Comic-info metadata parsing |
| `source-api/` | KMP source API (`HttpSource`, `ParsedHttpSource`) |
| `source-local/` | Local manga source implementation |
| `presentation-core/` | Shared Compose components and theming |
| `presentation-widget/` | Home-screen Glance widget |
| `i18n/` | Base Mihon strings (moko-resources) |
| `i18n-kmk/` | Komikku-specific strings |
| `i18n-sy/` | TachiyomiSY strings |
| `flagkit/` | Country-flag drawables |
| `telemetry/` | Firebase/crashlytics wrapper (optional variant) |
| `macrobenchmark/` | Macrobenchmark tests |

Version catalogs live in `gradle/`: `libs.versions.toml`, `kotlinx.versions.toml`, `androidx.versions.toml`, `compose.versions.toml`, `sy.versions.toml`.

---

## Build Variants & Flags

Build types: `debug` (`.dev` suffix), `release`, `releaseTest`, `foss` (`.foss`), `preview` (`.beta`), `benchmark`.

Gradle property flags passed with `-P`:

```bash
./gradlew assembleDebug                        # standard debug build
./gradlew assembleRelease -Penable-updater     # enable in-app updater
./gradlew assembleRelease -Pinclude-telemetry  # enable Firebase / Crashlytics
./gradlew assembleRelease -Pdisable-code-shrink  # skip R8 minification
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

---

## Architecture Patterns

**DI** – Uses `uy.kohesive.injekt` (not Hilt/Dagger). Singletons are registered in `AppModule.kt` via `addSingleton`/`addSingletonFactory`; consumed via `injectLazy<T>()` or `Injekt.get<T>()`.

**Navigation** – Voyager (`cafe.adriel.voyager`). Each screen has a companion `ScreenModel` extending `StateScreenModel<State>`. Use `screenModelScope` for coroutines; never create standalone `CoroutineScope` instances.

**Domain layer** – Follow *interactor* pattern: one class per operation in `domain/…/interactor/`. Repository interfaces live in `domain/`, implementations in `data/`.

**Database** – SQLDelight. Schema and queries are in `data/src/main/sqldelight/tachiyomi/`. Add `.sq` files there; generated Kotlin is in `build/`. Run `./gradlew generateSqlDelightInterface` after schema changes.

**Image loading** – Coil 3 (`coil3.*`). Use `imageLoader` extension and build `ImageRequest` objects; do **not** use Glide or Picasso.

**Reader** – The one screen still backed by a traditional `Activity` (`ReaderActivity` + `ReaderViewModel`). All other screens are Compose + Voyager.

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
- `exh.*` – SY/E-Hentai extensions

---

## Strings & i18n

Add Komikku-specific strings to `i18n-kmk/src/commonMain/moko-resources/base/`. Do **not** modify `i18n/` (upstream Mihon strings) or `i18n-sy/` unless syncing upstream. Translations are managed externally via Weblate.

---

## Key Files

- `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt` – feature flags
- `buildSrc/src/main/kotlin/mihon/buildlogic/AndroidConfig.kt` – SDK/Java versions
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` – DI wiring
- `app/build.gradle.kts` – all build variants, splits, compiler opts
- `settings.gradle.kts` – module graph

