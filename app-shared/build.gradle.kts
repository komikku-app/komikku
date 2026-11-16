import mihon.buildlogic.applyAppSourceBorrowing

/*
 * KMK: `:app-shared` (docs/kmp-desktop/PLAN.md, D5). Android + desktop home of platform-neutral app
 * services and UI: new Komikku code lives in src/{main,androidMain,desktopMain}/kotlin, existing `app`
 * files are compiled from their upstream paths through the allowlist in borrowed-app-sources.gradle.kts
 * (`app` excludes the same files and depends on this module).
 */
plugins {
    id("mihon.library.kmp.compose")
}

// Compiles the `app` files listed in borrowed-app-sources.gradle.kts.
applyAppSourceBorrowing()

android {
    namespace = "app.komikku.shared"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        // Same opt-ins as `app`, so borrowed files compile unchanged.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-Xannotation-default-target=param-property",
        )
    }

    sourceSets {
        // Compose per target ("BOM wins", see buildSrc mihon/buildlogic/KmpCompose.kt).
        androidMain.dependencies {
            implementation(compose.activity)
            implementation(compose.foundation)
            implementation(compose.material3.core)
            implementation(compose.material.icons)
            implementation(compose.animation)
            implementation(compose.animation.graphics)
            implementation(compose.ui.tooling.preview)
            implementation(compose.ui.util)

            // ThemeMode.kt (setAppCompatDelegateThemeMode)
            implementation(androidx.appcompat)
        }
        named("desktopMain") {
            dependencies {
                api(libs.bundles.compose.multiplatform)
                api(projects.desktop.composeCompat)

                // Image loading (covers) on desktop; Android keeps the app's Coil setup.
                api(project.dependencies.platform(libs.coil.bom))
                api(libs.coil.core)
                api(libs.coil.compose)
                implementation(libs.coil.network.okhttp)

                // Borrowed ChapterCache (desktopCopy, reader page cache)
                implementation(libs.disklrucache)

                // What the desktopCopy borrowed files reference (Android: `app` has these itself)
                api(projects.data)
                api(projects.sourceLocal)
                api(projects.core.archive)
                api(projects.coreMetadata)
            }
        }
    }
}

dependencies {
    api(projects.i18n)
    api(projects.i18nSy)
    api(projects.i18nKmk)
    api(projects.core.common)
    api(projects.sourceApi)
    api(projects.domain)
    api(projects.presentationCore)

    api(libs.injekt)
    api(libs.materialKolor)
    api(libs.bundles.voyager)
    api(libs.compose.materialmotion)
    implementation(kotlinx.immutables)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
