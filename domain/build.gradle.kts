plugins {
    // KMK --> Android + desktop (JVM), path preserving: shared sources stay in src/main/java
    // (compiled through `jvmSharedMain`), see buildSrc mihon/buildlogic/KmpLibrary.kt.
    id("mihon.library.kmp")
    // id("mihon.library")
    // kotlin("android")
    // KMK <--
    kotlin("plugin.serialization")
}

android {
    namespace = "tachiyomi.domain"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
        // KMK -->
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // KMK <--
    }

    // KMK --> Android-only libraries (AARs, Android main dispatcher) and the Android xmlutil core; desktop gets UniFile from
    // :desktop:android-compat (through core:common) and the JVM xmlutil core (PROGRESS.md, 1.2 xmlutil).
    sourceSets {
        androidMain.dependencies {
            implementation(libs.unifile)
            implementation(kotlinx.serialization.xml.core)
            implementation(kotlinx.coroutines.android)
        }
        named("desktopMain") {
            dependencies {
                implementation(kotlinx.serialization.xml.core.jvm)
                // Version of the compileOnly `runtime-annotation` (`@Immutable`); on Android it comes
                // from consistent resolution with the runtime classpath.
                compileOnly(project.dependencies.platform(compose.bom))
            }
        }
    }
    // KMK <--
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.common)

    implementation(platform(kotlinx.coroutines.bom))
    // KMK --> bundle split: `coroutines-android` (Android main dispatcher) is in androidMain
    // implementation(kotlinx.bundles.coroutines)
    implementation(kotlinx.coroutines.core)
    implementation(kotlinx.coroutines.guava)
    // KMK <--
    // KMK --> bundle split: `serialization-xml-core` is Android-only (see kotlin.sourceSets above)
    // implementation(kotlinx.bundles.serialization)
    implementation(kotlinx.serialization.json)
    implementation(kotlinx.serialization.json.okio)
    implementation(kotlinx.serialization.protobuf)
    implementation(kotlinx.serialization.xml)
    // KMK <--

    // KMK --> moved to androidMain (Android-only AAR)
    // implementation(libs.unifile)
    // KMK <--

    api(libs.sqldelight.android.paging)

    compileOnly(compose.runtime.annotation)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
