import mihon.buildlogic.configureKmpCompose

/*
 * KMK: `mihon.library.kmp` + Compose (docs/kmp-desktop/PLAN.md, D5 / D6). Replaces
 * `id("mihon.library")` + `id("mihon.library.compose")` + `kotlin("android")` in a Compose module's
 * `plugins {}` block; see `configureKmpCompose` for how Compose is resolved per target.
 */
plugins {
    id("mihon.library.kmp")
}

configureKmpCompose()
