import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * KMK: `:desktop:compose-compat` (docs/kmp-desktop/PLAN.md, D3 applied to Compose).
 *
 * Desktop-only JVM library that provides, under their Android FQNs, the few Android-only Compose /
 * AndroidX Compose APIs that shared UI code (`presentation-core`, borrowed `app` files) uses:
 * `LocalContext`, `androidx.activity.compose.BackHandler`, `Modifier.systemGestureExclusion`,
 * `WindowInsets.isImeVisible`.
 * Upstream files that use them compile unchanged on desktop. Kept apart from
 * `:desktop:android-compat` so the non-UI desktop stack does not depend on Compose.
 *
 * Never a dependency of the Android app: only desktop targets of shared modules depend on it.
 */
plugins {
    id("mihon.code.lint")
    kotlin("jvm")
    // Compose compiler plugin is on the classpath via buildSrc; apply by id without a version.
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // `LocalContext` exposes the compat `android.content.Context`.
    api(projects.desktop.androidCompat)

    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.ui)
    implementation(libs.compose.multiplatform.ui.backhandler)
    implementation(libs.compose.multiplatform.foundation)
}
