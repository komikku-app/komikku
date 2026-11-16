plugins {
    // KMK --> Android + desktop (JVM), path preserving: sources stay in src/main/java (compiled through
    // `jvmSharedMain`); Compose resolved per target (androidx + BOM on Android, JetBrains on desktop), see
    // buildSrc mihon/buildlogic/KmpLibrary.kt and KmpCompose.kt.
    id("mihon.library.kmp.compose")
    // id("mihon.library")
    // id("mihon.library.compose")
    // kotlin("android")
    // KMK <--
}

android {
    namespace = "tachiyomi.presentation.core"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }

    // KMK --> Compose per target ("BOM wins", PLAN.md D6): androidx coordinates under the Compose BOM on
    // Android, JetBrains Compose Multiplatform on desktop. Android-only APIs used by shared files
    // (`LocalContext`, `BackHandler`, `systemGestureExclusion`, `ViewConfiguration`) come from
    // :desktop:compose-compat / :desktop:android-compat on desktop.
    sourceSets {
        androidMain.dependencies {
            implementation(compose.activity)
            implementation(compose.foundation)
            implementation(compose.material3.core)
            implementation(compose.material.icons)
            implementation(compose.animation)
            implementation(compose.animation.graphics)
            implementation(compose.ui.tooling.preview)
            implementation(compose.ui.util)

            implementation(androidx.paging.runtime)
        }
        named("desktopMain") {
            dependencies {
                implementation(libs.bundles.compose.multiplatform)
                implementation(projects.desktop.composeCompat)
            }
        }
    }
    // KMK <--
}

dependencies {
    api(projects.core.common)
    api(projects.i18n)
    // SY -->
    api(projects.i18nSy)
    // SY <--

    // KMK --> Compose libraries moved to kotlin.sourceSets (androidMain / desktopMain) above
    // Compose
    // implementation(compose.activity)
    // implementation(compose.foundation)
    // implementation(compose.material3.core)
    // implementation(compose.material.icons)
    // implementation(compose.animation)
    // implementation(compose.animation.graphics)
    // KMK <--
    debugImplementation(compose.ui.tooling)
    // KMK -->
    // implementation(compose.ui.tooling.preview)
    // implementation(compose.ui.util)

    // implementation(androidx.paging.runtime)
    // KMK <--
    implementation(androidx.paging.compose)
    implementation(kotlinx.immutables)
}
