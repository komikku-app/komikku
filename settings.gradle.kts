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
        create("compose") {
            from(files("gradle/compose.versions.toml"))
        }
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
include(":core-metadata")
include(":core:archive")
include(":core:common")
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

// Can either create symlink or set project path like below
include(":ad-filter")
project(":ad-filter").projectDir = file("AdblockAndroid/ad-filter")
include(":adblock-client")
project(":adblock-client").projectDir = file("AdblockAndroid/adblock-client")

/*
// Composite build. Would need to manually create local.properties inside AdblockAndroid for it to sync.
includeBuild("AdblockAndroid") {
    dependencySubstitution {
        substitute(module("io.github.edsuns:adblock-client"))
            .using(project(":adblock-client"))
    }

    dependencySubstitution {
        substitute(module("io.github.edsuns:ad-filter"))
            .using(project(":ad-filter"))
    }
}
*/
