# Komikku – AI Agent Guide

Komikku is an Android manga reader (min SDK 26, target SDK 36, JVM target 17 / Kotlin) forked from **Mihon** + **TachiyomiSY**. `applicationId`: `app.komikku` (debug: `app.komikku.dev`).

Features: configurable reader, downloads/offline reading, trackers (MyAnimeList, AniList, Kitsu, MangaUpdates, Bangumi, Kavita, Komga, MangaDex, Shikimori, Suwayomi), recommendations, metadata editing, library categories/tags/filters, multi-source browsing and feed tabs.

### Upstreams

| Upstream | Code marker | Strings |
|----------|-------------|---------|
| Mihon | none (base code) | `MR` (`i18n/`) |
| TachiyomiSY | `// SY -->` … `// SY <--` (legacy `// EXH` blocks also come from SY) | `SYMR` (`i18n-sy/`) |
| **Komikku** (this repo) | `// KMK -->` … `// KMK <--` | `KMR` (`i18n-kmk/`) |

---

## Mandatory rules for AI agents

**Read this section before every change.** These rules override shortcuts (e.g. copying nearby `MR` imports or only running `compileDebugKotlin`).

### Git

| Rule | Required behavior |
|------|-------------------|
| Branch | Create a **feature branch** for the task (`git checkout -b <type>/<short-description>`, e.g. `feat/manga-recommendations`). |
| Commit | **OK** on a feature branch when work is ready. **Never** commit directly to `master` / `main` unless the user explicitly asks. |
| Push | **OK** to push the **current feature branch** when work is ready. **Never** push to `master` / `main` unless the user explicitly asks. |

Before `git push`, confirm the current branch is not `master` or `main` (`git branch --show-current`).

### Fork markers

Every Komikku addition or modification to existing code **must** be wrapped:

```kotlin
// KMK -->
// your code here
// KMK <--
```

- Use `// KMK` for all new code. Never add new `// SY` or `// EXH` blocks.
- Keep upstream `// SY` / `// EXH` blocks intact; when changing code inside one, wrap your change in a nested `// KMK` block.
- Markers apply to `.sq` queries and mappers too.

### Internationalization (strings)

| String kind | Module | Resource class | Base folder only |
|-------------|--------|----------------|------------------|
| **Komikku-only** (new features, KMK UI, library-update errors, WebDAV, Discord, etc.) | `i18n-kmk/` | **`KMR`** | `i18n-kmk/src/commonMain/moko-resources/base/` |
| Mihon upstream | `i18n/` | `MR` | `i18n/src/commonMain/moko-resources/base/` |
| TachiyomiSY upstream | `i18n-sy/` | `SYMR` | `i18n-sy/src/commonMain/moko-resources/base/` |

**Hard rules:**

- **All new strings go to `i18n-kmk/`** (`import tachiyomi.i18n.kmk.KMR`). `MR` / `SYMR` are upstream-owned; reuse existing entries, but only add or change them when syncing upstream.
- **Never** edit non-`base` locale `strings.xml` or `plurals.xml` files in `i18n-kmk/`, `i18n/`, or `i18n-sy/` — [Weblate](https://hosted.weblate.org/engage/komikku-app/) owns translations.
- Inside a `// KMK` block or for Komikku-only behavior, default to **`KMR`** even if nearby code imports `MR`.

**Examples (→ `i18n-kmk`, not `i18n`):** library update error UI, sync-before-update messages, WebDAV/Discord settings, updater notifications, `mihon/feature/*` Komikku screens.

**Self-check before finishing:** `git diff` must not add new `<string name="…">` or `<plurals name="…">` entries under non-`base` locales in `i18n-kmk/src/`, `i18n/src/`, or `i18n-sy/src/`.

### Formatting & build verification

**“Build passes” is not enough.** After Kotlin/XML edits, run **in this order** before marking work complete:

```bash
./gradlew spotlessApply    # fix formatting
./gradlew spotlessCheck    # must pass (CI gate)
./gradlew assembleDebug    # or :app:compileDebugKotlin for a faster compile-only check
```

- **Do not** skip `spotlessCheck`. If it fails, run `spotlessApply` and re-run `spotlessCheck`.
- Use `compileDebugKotlin` instead of `assembleDebug` only if the user asked for a quick compile check — Spotless still applies.
- Spotless + ktlint config: `buildSrc/src/main/kotlin/mihon.code.lint.gradle.kts` (trim trailing whitespace, end with newline; non-base i18n locales and `**/build/**` excluded).

---

## Tech stack

- **UI:** Jetpack Compose + Material 3, [Voyager](https://voyager.adriel.cafe/) navigation, Coil 3 for images
- **DI:** Injekt (`uy.kohesive.injekt`)
- **Database:** SQLDelight, SQLCipher encryption
- **Network:** OkHttp 5 with DNS-over-HTTPS
- **Serialization:** Kotlinx Serialization (JSON/Protobuf)
- **JS engine:** QuickJS (used by sources)
- **Concurrency:** Kotlin coroutines + Flow for new code; RxJava 1 remains in `source-api` for extension compatibility
- **Toolchain:** JDK 21 to build (matches `.github/.java-version`), bytecode targets Java 17, Gradle 9.3+, compileSdk 36

Version catalogs in `gradle/`: `libs.versions.toml`, `kotlinx.versions.toml`, `androidx.versions.toml`, `compose.versions.toml`, `sy.versions.toml`.

---

## Module layout

| Module | Purpose |
|--------|---------|
| `app/` | UI (`eu.kanade.*`, `exh/`, `mihon/`), ScreenModels, DI, workers, build variants |
| `domain/` | Use cases in `…/interactor/` (e.g. `GetManga`), models, repo interfaces |
| `data/` | SQLDelight DB, `*RepositoryImpl` (`tachiyomi.data.*`) |
| `core:common/` | Network (OkHttp), security, storage, shared utils |
| `core:archive/` | CBZ/archive reading with optional encryption |
| `core-metadata/` | Comic-info metadata parsing |
| `source-api/` / `source-local/` | Extension `Source` API + local source |
| `presentation-core/` | Shared Compose components |
| `presentation-widget/` | Home-screen Glance widget |
| `i18n/` / `i18n-sy/` / `i18n-kmk/` | Strings → `MR` / `SYMR` / `KMR` (see [Internationalization](#internationalization-strings)) |
| `flagkit/` | Country-flag drawables |
| `telemetry/` | Firebase/Crashlytics (noop unless `-Pinclude-telemetry`) |
| `macrobenchmark/` | Macrobenchmark tests (CI-only) |
| `buildSrc/` | Convention plugins and build logic |

Dependency flow: `app` → `domain` → `source-api`; `data` implements `domain` repos.

Package roots: `eu.kanade.tachiyomi.*` (legacy UI), `tachiyomi.*` (domain/data), `mihon.*` (Mihon upstream), `exh.*` (enhanced sources, SY).

---

## Architecture

**DI** – Injekt, not Hilt. Register in `AppModule.kt`, `DomainModule.kt`, `KMKDomainModule.kt`, `SYDomainModule.kt` (imported in `App.kt`): `addSingleton` / `addSingletonFactory` for repositories and services, `addFactory` for interactors. Resolve with `injectLazy<T>()` in class fields or `Injekt.get<T>()` in functions.

**UI & navigation** – Voyager `Screen`s in `eu.kanade.tachiyomi.ui.*`, composables in `eu.kanade.presentation.*`. Base type: `eu.kanade.presentation.util.Screen`. State via `rememberScreenModel { … }`; most models extend `StateScreenModel<State>` or bases like `SearchScreenModel`; some use plain `ScreenModel`. Example: `DeepLinkScreen` + `DeepLinkScreenModel` in `app/src/main/java/eu/kanade/tachiyomi/ui/deeplink/`.

**Coroutines** – In ScreenModels use `screenModelScope` / `ioCoroutineScope`. Helpers in `tachiyomi.core.common.util.lang`: `withIOContext`, `withUIContext`, `CoroutineScope.launchIO` (on a given scope). The top-level `launchIO` / `launchNow` run on **GlobalScope** — avoid them for lifecycle-bound work. `rememberCoroutineScope()` is fine in Compose; long-lived services may own their own `CoroutineScope`.

**Activities (not Voyager)** – `MainActivity` (shell / Voyager host), `ReaderActivity` + `ReaderViewModel`, `WebViewActivity`, `UnlockActivity`, OAuth login activities, `DeepLinkActivity`. Reader: `ReaderActivity.newIntent(context, mangaId, chapterId)`. Web: both `WebViewScreen` (Voyager) and `WebViewActivity.newIntent(...)`.

**Domain / data** – One class per operation under `domain/src/main/java/tachiyomi/domain/*/interactor/` (verb names like `GetTracksPerManga`, `HideCategory`; no `*Interactor` suffix). App-specific cases go in `app/src/main/java/eu/kanade/domain/…/interactor/`. `Manga` / `Chapter` are domain models; `SManga` / `SChapter` are source-layer types — convert at the boundary (`SManga.toDomainManga()`, `Manga.toSManga()`, `Chapter.toSChapter()`).

**Database** – SQLDelight in `data/src/main/sqldelight/tachiyomi/`. For schema changes:
1. Add a new `migrations/*.sqm` file
2. Update the `.sq` queries
3. Update the `*RepositoryImpl` mapper
4. Regenerate: `./gradlew :data:generateSqlDelightInterface` (or any compile that touches `:data`)

**Preferences** – `eu.kanade.domain.*.service.*Preferences` (e.g. `SourcePreferences.relatedMangas()`). App preference migrations: `app/src/main/java/mihon/core/migration/migrations/` (`mihon.core.migration.Migration`).

**Images** – Coil 3 (`coil3.*`, `context.imageLoader`), configured in `App.kt`. No Glide/Picasso.

**Logging** – Komikku code: `xLogE()` / `xLog()` from `exh.log`. Mihon code: `logcat { }` from `tachiyomi.core.common.util.system`. Avoid raw `android.util.Log`.

---

## Extensions & sources

- Catalog sources: installable APK extensions (not in this repo).
- In-repo: delegated sources and metadata in `exh/` (E-Hentai, NHentai, MangaDex, `exh/recs/`).
- `source-api`: `eu.kanade.tachiyomi.source.*` — **avoid breaking extension ABI**.

---

## Adding a Komikku feature

1. **Interactor:** `domain/src/main/java/tachiyomi/domain/<area>/interactor/MyFeature.kt`
2. **DI:** `addFactory { MyFeature(get()) }` in `KMKDomainModule.kt` (or inside a `// KMK` block in `DomainModule.kt`)
3. **UI:** ScreenModel + Screen under `app/src/main/java/eu/kanade/tachiyomi/ui/`
4. **Strings:** `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`, referenced via `KMR`
5. **Markers:** wrap edits to existing files in `// KMK -->` / `// KMK <--`

Reference: `domain/src/main/java/tachiyomi/domain/category/interactor/HideCategory.kt` and its registration in `DomainModule.kt`.

---

## Build & CI

Build types (`applicationIdSuffix`): `debug` (`.dev`), `release`, `releaseTest` (`.rt`), `foss` (`.foss`), `preview` (`.beta`, CI default), `benchmark` (`.benchmark`).

Gradle `-P` flags (`buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`):

| Flag | Effect |
|------|--------|
| `include-telemetry` | Firebase Analytics + Crashlytics |
| `enable-updater` | In-app update checker |
| `disable-code-shrink` | Skip R8 minification |
| `include-dependency-info` | Dependency metadata in APK |

```bash
./gradlew spotlessApply                    # format
./gradlew spotlessCheck                    # CI gate
./gradlew :app:compileDebugKotlin          # compile-only check
./gradlew assembleDebug                    # debug APK
./gradlew assemblePreview                  # preview (beta) APK, CI equivalent
./gradlew assemblePreview -Pinclude-telemetry -Penable-updater  # full CI build
./gradlew assembleRelease                  # release APK
./gradlew testReleaseUnitTest              # CI unit tests
./gradlew test                             # all unit tests
./gradlew installDebug                     # install to device/emulator
./gradlew :data:generateSqlDelightInterface  # after .sq / .sqm changes
```

**Workflows** (`.github/workflows/`):
- `build_pull_request.yml` – PR validation: dependency review → wrapper validation → `spotlessCheck` → `assemblePreview` → `testReleaseUnitTest` → APK signing (authorized forks)
- `build_push.yml` – push to `master`: full build + signing
- `build_release.yml` – release builds from `v*` tags
- `build_preview.yml` – manual preview builder
- `build_benchmark.yml` – manual benchmark builder

---

## Tests

- **Framework:** JUnit (Jupiter) + Kotest assertions + MockK
- **Locations:** `domain/src/test/`, `app/src/test/` (e.g. `MigratorTest.kt`). No broad UI test suite — focus on domain and critical logic.
- **Single class:** `./gradlew :domain:testReleaseUnitTest --tests "*.ClassName"`

---

## Key files

- `App.kt` – Injekt bootstrap, logging setup, Coil initialization
- `MainActivity.kt` – Voyager navigation host
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` – core DI
- `app/src/main/java/eu/kanade/domain/DomainModule.kt` – domain interactors
- `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`, `AndroidConfig.kt` – flags, SDK versions
- `app/build.gradle.kts`, `settings.gradle.kts`
- `CONTRIBUTING.md` – prerequisites and contribution process

---

## Upstream cherry-pick log

`cherrypick_log.md` (repo root) lists commits from `mihon/main` and `tachiyomiSY/master` (Renovate excluded) with their cherry-pick status on `master`. It is generated by `.claude/skills/cherrypick-log/update_cherrypick_log.py`, and `?` rows are reviewed with the `cherrypick-log` skill. Rules, commands and the porting guide: `.claude/skills/cherrypick-log/README.md`. Never hand-edit columns other than Status and Notes; use the script's `mark` command.

---

## Common issues

- **Gradle OOM** – the daemon uses `-Xmx4g` (`gradle.properties`); run `./gradlew --stop` and retry.
- **Spotless failures** – run `spotlessApply`, then `spotlessCheck`.
- **SQLDelight errors** – run `./gradlew :data:generateSqlDelightInterface` after schema changes (see [Database](#architecture)).
- **Missing `google-services.json` / `client_secrets.json`** – these are CI secrets; builds without `-Pinclude-telemetry` don't need them.
- **First build is slow** – it downloads ~1 GB of dependencies; later builds use the Gradle cache.
