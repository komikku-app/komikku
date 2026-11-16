// KMK -->
// import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
// KMK <--

plugins {
    // KMK --> Android + desktop (JVM) through the path-preserving convention plugin: src/commonMain/kotlin
    // is compiled through `jvmSharedMain`, src/androidMain/kotlin stays the Android source set (and is
    // also compiled for desktop, see `desktopMain` below). See buildSrc mihon/buildlogic/KmpLibrary.kt.
    id("mihon.library.kmp")
    // id("mihon.library")
    // kotlin("multiplatform")
    // KMK <--
}

kotlin {
    // KMK --> targets come from mihon.library.kmp
    // androidTarget()
    // KMK <--
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.sourceApi)
                api(projects.i18n)
                // SY -->
                api(projects.i18nSy)
                // SY <--

                // KMK --> moved to androidMain (Android-only AAR); desktop gets UniFile from
                // :desktop:android-compat through core:common
                // implementation(libs.unifile)
                // KMK <--
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.archive)
                implementation(projects.core.common)
                implementation(projects.coreMetadata)

                // Move ChapterRecognition to separate module?
                implementation(projects.domain)

                implementation(kotlinx.bundles.serialization)

                // KMK -->
                implementation(libs.unifile)
                // KMK <--
            }
        }
        // KMK --> desktop compiles the same platform files as Android (src/androidMain/kotlin: the
        // `actual` LocalCoverManager / LocalSourceFileSystem, LocalSource, OrderBy, epub metadata)
        // against the compat layer (Context, UniFile, AndroidXmlReader) and core:archive's desktop
        // API, plus desktop-only files from src/desktopMain/kotlin.
        named("desktopMain") {
            kotlin.srcDir("src/androidMain/kotlin")
            dependencies {
                implementation(projects.core.archive)
                implementation(projects.core.common)
                implementation(projects.coreMetadata)
                implementation(projects.domain)

                // `kotlinx.bundles.serialization` minus the Android-only xmlutil core
                implementation(kotlinx.serialization.json)
                implementation(kotlinx.serialization.json.okio)
                implementation(kotlinx.serialization.protobuf)
                implementation(kotlinx.serialization.xml)
                implementation(kotlinx.serialization.xml.core.jvm)
            }
        }
        named("desktopTest") {
            dependencies {
                implementation(libs.bundles.test)
                implementation(kotlinx.coroutines.test)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
        // KMK <--
    }

    // KMK --> `@OptIn` no longer needed (the plugin configures the targets)
    // @OptIn(ExperimentalKotlinGradlePluginApi::class)
    // KMK <--
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

android {
    namespace = "tachiyomi.source.local"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

// KMK --> desktop tests keep their files under build/, not in the user's data dir.
tasks.named<Test>("desktopTest") {
    systemProperty("komikku.dataDir", layout.buildDirectory.dir("tmp/test-data").get().asFile.absolutePath)
}
// KMK <--
