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
    namespace = "tachiyomi.core.metadata"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

// KMK --> xmlutil core: Android keeps `core-android` (adds Android's XML parser), desktop uses
// the JVM artifact (PROGRESS.md, 1.2 xmlutil).
kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(kotlinx.serialization.xml.core)
        }
        named("desktopMain") {
            dependencies {
                implementation(kotlinx.serialization.xml.core.jvm)
            }
        }
        // ComicInfo XML round trip on the JVM xmlutil artifact.
        named("desktopTest") {
            dependencies {
                implementation(libs.bundles.test)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}
// KMK <--

dependencies {
    implementation(projects.sourceApi)

    // KMK --> bundle split: `serialization-xml-core` is Android-only (see kotlin.sourceSets above)
    // implementation(kotlinx.bundles.serialization)
    implementation(kotlinx.serialization.json)
    implementation(kotlinx.serialization.json.okio)
    implementation(kotlinx.serialization.protobuf)
    implementation(kotlinx.serialization.xml)
    // KMK <--
}
