# Komikku Desktop — Progress Log

See [`PLAN.md`](PLAN.md). Update this file in the same commit as each step.

## Step status

| Step | Status | Commit | Notes |
|------|--------|--------|-------|
| 0.1 Rebase PR #1671 on master, CMP bump | done | `3fb80ae833` | Compose Multiplatform 1.12.2 for Kotlin 2.4.20 |
| 0.2 Compose plugin clash fix, Material 3 | done | see log | `compose` catalog registered as `composeLibs`; default-package `Project.compose` accessor in buildSrc keeps every upstream `compose.*` reference unchanged; `org.jetbrains.compose` applied in `:desktopApp` (extension accessed by type), `compose.desktop.currentOs` + `nativeDistributions`; Material 3 demo UI (CMP material3 1.9.0) |
| 0.3 Remove `NetworkClient` / `:core:network` | todo | | |
| 0.4 File-backed desktop preference store | todo | | |
| 0.5 Desktop target for `i18n-sy`, `i18n-kmk` | todo | | |
| 0.6 `mihon.library.kmp` convention plugin | todo | | |

## Files moved out of upstream paths

Record every file moved to `src/androidMain` (old path → new path) so upstream cherry-picks can be
remapped.

_None yet._

## Android compat classes

List classes added to `:desktop:android-compat` and which module/extension needed them.

_None yet._

## Deviations from the plan and open issues

- **0.2 — compose name clash.** Renaming only the `Project.compose` accessor in buildSrc is not
  enough: Gradle registers every version catalog as a project extension with the catalog's name, so
  `org.jetbrains.compose` fails with "Cannot add extension with name 'compose'". Fix: the catalog is
  registered as `composeLibs` (root and buildSrc `settings.gradle.kts`), and
  `buildSrc/src/main/kotlin/ComposeCatalogAccessor.kt` declares `val Project.compose` in the
  *default package*. Build scripts are compiled in the default package, so that declaration takes
  precedence over the generated (star-imported) accessors: upstream build files keep `compose.*`
  unchanged, and in modules that apply `org.jetbrains.compose` the JetBrains extension is accessed
  by type (`extensions.configure<ComposeExtension> { ... }`). Verified in `:desktopApp`, where
  `compose.bom` still resolves to the catalog.
- **0.2 — Material 3 version.** CMP publishes `org.jetbrains.compose.material3` on its own version
  line; `material3:1.9.0` is what CMP 1.12.2's own `compose.material3` alias points to, so the
  catalog pins that (`compose-multiplatform-material3`). Re-check when CMP is bumped.
- **0.2 — Native runtime.** `compose.desktop.currentOs` (`ComposePlugin.DesktopDependencies.currentOs`)
  is still host-specific; that is the standard Compose Desktop setup (packages are built on each
  OS, Phase 9). The hand-written OS detection is gone.
