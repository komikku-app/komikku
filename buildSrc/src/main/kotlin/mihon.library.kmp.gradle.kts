import mihon.buildlogic.configureKmpLibrary

/*
 * KMK: Android library + desktop (JVM) Kotlin Multiplatform module, path preserving
 * (docs/kmp-desktop/PLAN.md, D1). Replaces `id("mihon.library")` + `kotlin("android")` (or
 * `kotlin("multiplatform")`) in a module's `plugins {}` block; see `configureKmpLibrary` for the
 * source set layout and how existing `dependencies {}` blocks are handled.
 */
plugins {
    id("mihon.library")
    kotlin("multiplatform")
}

configureKmpLibrary()
