import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    kotlin("multiplatform")
}

kotlin {
    androidTarget()
    // KMK --> Desktop (JVM) target so the preference abstraction is usable from Compose Desktop
    jvm("desktop")
    // KMK <--

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(project.dependencies.platform(kotlinx.coroutines.bom))
                api(kotlinx.coroutines.core)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(kotlinx.serialization.json)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.bundles.test)
                implementation(kotlinx.coroutines.test)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

android {
    namespace = "tachiyomi.core.preference"
}
