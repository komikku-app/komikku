pluginManagement {
    resolutionStrategy {
        eachPlugin {
            val regex = "com.android.(library|application)".toRegex()
            if (regex matches requested.id.id) {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("kotlinx") {
            from(files("gradle/kotlinx.versions.toml"))
        }
        create("androidx") {
            from(files("gradle/androidx.versions.toml"))
        }
        // KMK --> Named `composeLibs` (not `compose`) so the `org.jetbrains.compose` plugin can register its
        // own `compose` extension. Build scripts keep using `compose.*` through the `Project.compose`
        // accessor in buildSrc (ComposeCatalogAccessor.kt).
        create("composeLibs") {
            from(files("gradle/compose.versions.toml"))
        }
        // KMK <--
        create("sylibs") {
            from(files("gradle/sy.versions.toml"))
        }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven(url = "https://www.jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Komikku"
include(":app")
// KMK --> Android + desktop app services and UI shared with the desktop app (docs/kmp-desktop/PLAN.md, D5)
include(":app-shared")
// KMK <--
// KMK --> Compose Multiplatform desktop application (Phase 0 KMP scaffolding)
include(":desktopApp")
// Desktop-only Android API surface (docs/kmp-desktop/PLAN.md, D3)
include(":desktop:android-compat")
// Desktop-only Android Compose API surface (LocalContext, BackHandler, …) for shared UI code
include(":desktop:compose-compat")
// Desktop extension runtime: APK reader, dex2jar conversion, extension class loader (Phase 5)
include(":desktop:extension-runtime")
// KMK <--
include(":core-metadata")
include(":core:archive")
include(":core:common")
// KMK --> Multiplatform preference abstraction shared with desktop/iOS (Phase 1)
include(":core:preference")
// KMK <--
// KMK --> `app.cash.quickjs.QuickJs` on quickjs-kt for Android and desktop (docs/kmp-desktop/PLAN.md, I2b)
include(":core:quickjs")
// KMK <--
include(":data")
include(":domain")
include(":i18n")
// KMK -->
include(":i18n-kmk")
include(":flagkit")
// KMK <--
// SY -->
include(":i18n-sy")
// SY <--
include(":macrobenchmark")
include(":presentation-core")
include(":presentation-widget")
include(":source-api")
include(":source-local")
// KMK --> Host for Mangayomi JavaScript manga extensions (docs/kmp-desktop/PLAN.md, section 8)
include(":source-js")
// KMK <--
include(":telemetry")
