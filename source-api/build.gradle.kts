import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    // KMK --> Android + desktop (JVM), path preserving: shared sources stay in src/commonMain/kotlin
    // (compiled through `jvmSharedMain`), see buildSrc mihon/buildlogic/KmpLibrary.kt.
    id("mihon.library.kmp")
    // id("mihon.library")
    // kotlin("multiplatform")
    // KMK <--
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
}

kotlin {
    // KMK -->
    // androidTarget()
    // KMK <--
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinx.serialization.json)
                api(libs.injekt)
                api(libs.rxjava)
                api(libs.jsoup)

                // SY -->
                api(projects.i18n)
                api(projects.i18nSy)
                api(kotlinx.reflect)
                // SY <--

                implementation(project.dependencies.platform(compose.bom))
                implementation(compose.runtime)
            }
        }
        // KMK --> core:common is shared (network, Context helpers, logging); on desktop it also
        // re-exports :desktop:android-compat (Application, SharedPreferences, Uri, androidx.preference).
        named("jvmSharedMain") {
            dependencies {
                api(projects.core.common)
            }
        }
        // KMK <--
        val androidMain by getting {
            dependencies {
                // KMK -->
                // implementation(projects.core.common)
                // KMK <--
                api(libs.preferencektx)

                // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
                implementation(kotlinx.coroutines.android)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFile("consumer-proguard.pro")
    }
}
