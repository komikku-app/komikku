import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    kotlin("multiplatform")
}

kotlin {
    androidTarget()
    // KMK --> Desktop (JVM) target sharing the OkHttp implementation with Android via `jvmShared`
    jvm("desktop")
    // KMK <--

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project.dependencies.platform(kotlinx.coroutines.bom))
                api(kotlinx.coroutines.core)
            }
        }

        // Intermediate source set shared by the two JVM targets (Android + desktop). OkHttp is a
        // JVM library, so its usage lives here rather than in commonMain. An iOS target would add a
        // separate `iosMain` actual (e.g. backed by Ktor) without touching this code.
        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                api(libs.okhttp.core)
            }
        }

        getByName("androidMain").dependsOn(jvmShared)
        getByName("desktopMain").dependsOn(jvmShared)
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

android {
    namespace = "tachiyomi.core.network"
}
