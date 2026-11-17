import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * KMK: host for Mangayomi JavaScript manga extensions (docs/kmp-desktop/PLAN.md, section 8;
 * specification: docs/kmp-desktop/MANGAYOMI_CONTRACT.md).
 *
 * Real Kotlin Multiplatform module: the host, the JS prelude, the bridge interfaces, the DOM (Ksoup), the
 * crypto helpers and the result models live in `commonMain` and may only use multiplatform libraries, so the
 * same code compiles for Kotlin/Native iOS later.
 *
 * Targets:
 * - `jvm("desktop")`: always.
 * - `iosArm64` / `iosSimulatorArm64`: only when the build host is macOS, so Linux / Windows builds and CI are
 *   unaffected.
 * - Android: not yet. `quickjs-kt`'s Android AAR clashes with the zhanghai `quickjs-android` AAR the app uses for
 *   Mihon extensions (both ship `libquickjs.so`), see PLAN.md 8.7 item 5. To add it later: apply
 *   `id("mihon.library")` (Android library + lint), call `androidTarget()` below, set `android { namespace =
 *   "app.komikku.sourcejs" }`, and give `androidMain` an `OkHttpHostHttp` (the `desktopMain` one is plain OkHttp
 *   and can move to a shared `jvmMain` group).
 */
plugins {
    id("mihon.code.lint")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val isMacOsHost = providers.systemProperty("os.name").get().startsWith("Mac", ignoreCase = true)

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    if (isMacOsHost) {
        iosArm64()
        iosSimulatorArm64()
    }

    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            implementation(kotlinx.coroutines.core)
            implementation(kotlinx.serialization.json)
            // QuickJS (same engine version as Mangayomi) behind `JsEngine`; multiplatform coordinate.
            implementation(libs.quickjs.kt)
            implementation(libs.ksoup)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlinx.coroutines.test)
        }
        named("desktopMain").configure {
            dependencies {
                implementation(libs.okhttp.core)
            }
        }
        named("desktopTest").configure {
            dependencies {
                implementation(libs.okhttp.mockwebserver)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
