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
 *   always. Their shared code (`OkHttpHostHttp`, the engine thread, the Mihon source adapter in
 *   `app.komikku.sourcejs.mihon` over `source-api`) lives in the `jvmShared` intermediate source set
 *   (`src/jvmSharedMain`, tests in `src/jvmSharedTest`); `commonMain` stays free of JVM APIs for iOS.
 * - `iosArm64` / `iosSimulatorArm64`: only when the build host is macOS, so Linux / Windows builds and CI are
 *   unaffected. `-Pios-targets` declares them on any host: Kotlin/Native compiles iOS klibs (main and test) on Linux
 *   too, only linking and running the simulator tests need macOS (docs/kmp-desktop/IOS_CI.md). Their HTTP is Ktor's
 *   Darwin engine (`DarwinHostHttp`), their preferences `NSUserDefaults` (`IosJsSourceRuntime`).
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
val withIosTargets = isMacOsHost || providers.gradleProperty("ios-targets").isPresent

kotlin {
    androidTarget()
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    if (withIosTargets) {
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
            // Public API: JsFilter.raw and the result models are kotlinx-serialization JSON types.
            api(kotlinx.serialization.json)
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
                // Public API: OkHttpHostHttp(OkHttpClient).
                api(libs.okhttp.core)
                // Public API: the source adapter (`app.komikku.sourcejs.mihon.MangayomiJsSource` is an `HttpSource`).
                // Through it also `core:common` (NetworkHelper, logcat) and, on desktop, `:desktop:android-compat`.
                api(projects.sourceApi)
            }
        }
        named("desktopTest").configure {
            dependencies {
                implementation(libs.okhttp.mockwebserver)
            }
        }
        if (withIosTargets) {
            named("iosMain").configure {
                dependencies {
                    // `DarwinHostHttp`: Ktor on NSURLSession (docs/kmp-desktop/IOS_CI.md, I5a).
                    api(libs.ktor.client.core)
                    implementation(libs.ktor.client.darwin)
                }
            }
            named("iosTest").configure {
                dependencies {
                    implementation(libs.ktor.client.mock)
                }
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

// iOS simulator tests: each test's start and end in the CI log, so a hanging test is named there.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    testLogging {
        events("started", "passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
