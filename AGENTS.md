# Komikku – AI Agent Guide

Komikku is an Android manga reader (min SDK 26, target SDK 36, JVM 17 / Kotlin) forked from **Mihon** + **TachiyomiSY**. Stack: Jetpack Compose + Material3, Voyager navigation, SQLDelight, Injekt DI. `applicationId`: `app.komikku`.

---

## Module layout

| Module | Purpose |
|--------|---------|
| `app/` | UI (`eu.kanade.*`, `exh/`, `mihon/`), DI, workers, build variants |
| `domain/` | Use cases in `…/interactor/` (e.g. `GetManga`), models, repo interfaces |
| `data/` | SQLDelight DB, `*RepositoryImpl` (`tachiyomi.data.*`) |
| `core:common/` | Network (OkHttp), security, storage, shared utils |
| `core:archive/` | CBZ/archive reading with optional encryption |
| `core-metadata/` | Comic-info metadata parsing |
| `source-api/` / `source-local/` | Extension `Source` API + local source |
| `presentation-core/` | Shared Compose components |
| `presentation-widget/` | Home-screen Glance widget |
| `i18n/` | Mihon strings → `MR` (moko-resources) |
| `i18n-kmk/` | Komikku strings → `KMR` |
| `i18n-sy/` | TachiyomiSY strings → `SYMR` |
| `flagkit/` | Country-flag drawables |
| `telemetry/` | Firebase/Crashlytics (noop unless `-Pinclude-telemetry`) |
| `macrobenchmark/` | Macrobenchmark tests |

Dependency flow: `app` → `domain` → `source-api`; `data` implements `domain` repos.

Version catalogs: `gradle/libs.versions.toml`, `kotlinx.versions.toml`, `androidx.versions.toml`, `compose.versions.toml`, `sy.versions.toml`.

---

## Architecture

**DI** – `uy.kohesive.injekt` (not Hilt). Register in `AppModule.kt`, `DomainModule.kt`, `KMKDomainModule.kt`, `SYDomainModule.kt` via `addSingleton` / `addSingletonFactory`. Resolve with `Injekt.get<T>()` or `injectLazy<T>()`.

**UI & navigation** – [Voyager](https://voyager.adriel.cafe/): `Screen` in `eu.kanade.tachiyomi.ui.*`, composables in `eu.kanade.presentation.*`. Base type: `eu.kanade.presentation.util.Screen`. State via `rememberScreenModel { … }`; most models extend `StateScreenModel<State>` or bases like `SearchScreenModel`; some use plain `ScreenModel`. Prefer `screenModelScope` and `ioCoroutineScope`; use `launchIO` / `withIOContext` from `tachiyomi.core.common.util.lang`. `rememberCoroutineScope()` is fine in Compose; long-lived services may use their own `CoroutineScope`.

**Activities (not Voyager)** – `MainActivity` (shell), `ReaderActivity` + `ReaderViewModel`, `WebViewActivity`, `UnlockActivity`, OAuth login activities, `DeepLinkActivity`. Reader: `ReaderActivity.newIntent(context, mangaId, chapterId)`. Web: both `WebViewScreen` (Voyager) and `WebViewActivity.newIntent(...)`.

Example: `DeepLinkScreen` + `DeepLinkScreenModel` in `app/src/main/java/eu/kanade/tachiyomi/ui/deeplink/`.

**Domain / data** – One class per operation under `domain/…/interactor/` (verb names, not `*Interactor` suffix). Also `app/src/main/java/eu/kanade/domain/…/interactor/` for app-specific cases. Wire repos in `eu.kanade.domain.DomainModule.kt` (+ `KMKDomainModule`, `SYDomainModule`).

**Database** – SQLDelight in `data/src/main/sqldelight/tachiyomi/` (`.sq` queries, `migrations/*.sqm`). After schema changes add a new `.sqm` and often `// KMK` blocks in `.sq` / mappers. Regenerate: `./gradlew :data:generateSqlDelightInterface` (or any compile that touches `:data`).

**App preference migrations** – `app/src/main/java/mihon/core/migration/migrations/` (`mihon.core.migration.Migration`).

**Images** – Coil 3 (`coil3.*`, `context.imageLoader`). No Glide/Picasso.

---

## Komikku-specific work

- New user-facing strings: **`KMR`** in `i18n-kmk/src/commonMain/moko-resources/base/`. Shared/upstream: **`MR`** (`i18n`). SY-only: **`SYMR`** (`i18n-sy`).
- Do not edit locale `strings.xml` in `i18n/` or `i18n-sy/` except when syncing upstream; translations via [Weblate](https://hosted.weblate.org/engage/komikku-app/).
- Komikku code/DI: search `// KMK` (e.g. `KMKDomainModule`, `HideCategory`, library-update errors).
- Prefs: `eu.kanade.domain.*.service.*Preferences` (e.g. `SourcePreferences.relatedMangas()`).

---

## Extensions & sources

- Catalog sources: installable APK extensions (not in this repo).
- In-repo: delegated sources and metadata in `exh/` (E-Hentai, NHentai, MangaDex, `exh/recs/`).
- `source-api`: `eu.kanade.tachiyomi.source.*` — avoid breaking extension ABI.

---

## Build & CI

Build types: `debug` (`.dev`), `release`, `releaseTest` (`.rt`), `foss` (`.foss`), `preview` (`.beta`, CI default), `benchmark`.

Gradle `-P` flags (`buildSrc/.../BuildConfig.kt`):

| Flag | Effect |
|------|--------|
| `include-telemetry` | Firebase Analytics + Crashlytics |
| `enable-updater` | In-app update checker |
| `disable-code-shrink` | Skip R8 minification |
| `include-dependency-info` | Dependency metadata in APK |

```bash
./gradlew spotlessApply              # format (CI: spotlessCheck)
./gradlew assemblePreview            # main CI/dev APK
./gradlew assemblePreview -Pinclude-telemetry -Penable-updater  # full upstream CI build
./gradlew testReleaseUnitTest        # CI unit tests (or ./gradlew test for all modules)
./gradlew installDebug               # device install
./gradlew :data:generateSqlDelightInterface  # after .sq / .sqm changes
```

JDK **17**.

---

## Fork-origin markers

Preserve inline blocks when editing:

```kotlin
// KMK -->  … // KMK <--   Komikku
// SY -->   … // SY <--    TachiyomiSY
// EXH -->  … // EXH <--   E-Hentai / exh (existing); prefer KMK for new Komikku-only code
```

Package roots: `eu.kanade.tachiyomi.*` (legacy UI), `tachiyomi.*` (domain/data), `mihon.*` (Mihon upstream), `exh.*` (enhanced sources).

---

## Tests

- Unit tests: `domain/src/test/`; app: `app/src/test/.../MigratorTest.kt`. No broad UI test suite.

---

## Conventions

- **Logging** – Prefer `xLogE()` / `xLog()` helpers from `exh.log` for Komikku code, Mihon uses `logcat { }` from `tachiyomi.core.common.util.system`. Avoid raw `android.util.Log`.
- **Formatting** – Spotless + ktlint (`buildSrc/.../mihon.code.lint.gradle.kts`).
- **Fork edits** – New Komikku features inside `// KMK` islands; keep `// SY` / `// EXH` blocks intact when merging upstream.

---

## Key files

- `App.kt` – Injekt bootstrap, logging setup
- `MainActivity.kt` – Voyager host
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` – core DI
- `app/src/main/java/eu/kanade/domain/DomainModule.kt` – domain interactors
- `buildSrc/.../BuildConfig.kt`, `AndroidConfig.kt` – flags, SDK versions
- `app/build.gradle.kts`, `settings.gradle.kts`

---

## Cursor Cloud specific instructions

### Environment

The VM update script installs the Android SDK (platform 36, build-tools 35.0.1, platform-tools, cmdline-tools) into `/opt/android-sdk` and writes `local.properties` with `sdk.dir`. JDK 21 is pre-installed and works fine for compiling to JVM target 17. `ANDROID_HOME`, `JAVA_HOME`, and `PATH` are set in `~/.bashrc`.

### Running key commands

All Gradle commands require the environment variables set above. Export them before invoking `./gradlew` if running in a fresh shell:

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

| Task | Command |
|------|---------|
| Format check (lint) | `./gradlew spotlessCheck` |
| Auto-fix formatting | `./gradlew spotlessApply` |
| Debug APK build | `./gradlew assembleDebug` |
| Preview APK build (CI) | `./gradlew assemblePreview` |
| Unit tests (CI) | `./gradlew testReleaseUnitTest` |
| All module tests | `./gradlew test` |
| SQLDelight codegen | `./gradlew :data:generateSqlDelightInterface` |

### Gotchas

- First Gradle build downloads ~1 GB of dependencies; subsequent builds use the Gradle cache and are much faster.
- `local.properties` is `.gitignore`d — it must be recreated if missing (the update script handles this).
- No Android emulator or device is available on the Cloud VM, so `installDebug` will fail. Build verification is done via `assembleDebug`.
- `google-services.json` and `client_secrets.json` are not present (CI secrets); builds without `-Pinclude-telemetry` succeed without them.
- Gradle daemon may use significant memory (`-Xmx4g` in `gradle.properties`). If OOM occurs, kill and restart the daemon with `./gradlew --stop`.
