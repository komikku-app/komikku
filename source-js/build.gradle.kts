import mihon.buildlogic.useQuickJsKtJvmInAndroidHostTests
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
 * - `androidTarget()` (namespace `app.komikku.sourcejs`, SDK settings from `mihon.library`) and `jvm("desktop")`:
 *   always. Their shared code (`OkHttpHostHttp`, the engine thread) lives in the `jvmShared` intermediate source
 *   set (`src/jvmSharedMain`); `commonMain` stays free of JVM APIs for iOS.
 * - `iosArm64` / `iosSimulatorArm64`: only when the build host is macOS, so Linux / Windows builds and CI are
 *   unaffected.
 *
 * Android uses `quickjs-kt`'s Android AAR (I2b replaced the zhanghai `quickjs-android` AAR that clashed with it,
 * PLAN.md 8.7 item 5). Its native library only exists for Android ABIs, so the Android host unit tests
 * (`testDebugUnitTest` / `testReleaseUnitTest`, which run `commonTest`) get the JVM artifact with host natives
 * instead.
 */
plugins {
    id("mihon.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val isMacOsHost = providers.systemProperty("os.name").get().startsWith("Mac", ignoreCase = true)

kotlin {
    androidTarget()
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    if (isMacOsHost) {
        iosArm64()
        iosSimulatorArm64()
    }

    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
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
        named("jvmSharedMain").configure {
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

android {
    namespace = "app.komikku.sourcejs"
}

// Android host unit tests: swap quickjs-kt's Android AAR (natives for Android ABIs only) for the JVM artifact.
useQuickJsKtJvmInAndroidHostTests("androidUnitTestRuntimeOnly")

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
