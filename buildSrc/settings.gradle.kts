dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
        create("androidx") {
            from(files("../gradle/androidx.versions.toml"))
        }
        // KMK --> Must match the catalog name in the root settings.gradle.kts
        create("composeLibs") {
            from(files("../gradle/compose.versions.toml"))
        }
        // KMK <--
        create("kotlinx") {
            from(files("../gradle/kotlinx.versions.toml"))
        }
    }
}

rootProject.name = "komikku-buildSrc"
