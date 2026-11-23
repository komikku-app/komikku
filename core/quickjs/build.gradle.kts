/*
 * KMK: `:core:quickjs` (docs/kmp-desktop/PLAN.md, section 8, step I2b).
 *
 * `app.cash.quickjs.QuickJs` / `QuickJsException` — the JavaScript API Mihon extensions link against — implemented
 * on QuickJS through `io.github.dokar3:quickjs-kt` for Android and desktop. Replaces the zhanghai
 * `quickjs-android` AAR (both ship `libquickjs.so`, so they cannot be packaged together) and the desktop copy that
 * lived in `:desktop:android-compat`. Binary compatible with the old classes (javap diff in PROGRESS.md).
 *
 * Tests (`src/test/kotlin`) run on desktop and on the Android host: quickjs-kt's Android AAR carries Android
 * natives only, so the Android unit-test runtime classpath gets the JVM artifact (host natives) instead.
 */
import mihon.buildlogic.useQuickJsKtJvmInAndroidHostTests

plugins {
    id("mihon.library.kmp")
}

android {
    namespace = "app.komikku.core.quickjs"
}

dependencies {
    // Multiplatform coordinate: `quickjs-kt-android` (AAR) for Android, `quickjs-kt-jvm` for desktop.
    implementation(libs.quickjs.kt)
    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.coroutines.core)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Android host unit tests: swap the Android AAR (natives for Android ABIs only) for the JVM artifact.
useQuickJsKtJvmInAndroidHostTests("testRuntimeOnly")
