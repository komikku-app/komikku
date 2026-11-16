plugins {
    // KMK --> Android + desktop (JVM), path preserving: shared sources stay in src/main/java
    // (compiled through `jvmSharedMain`), see buildSrc mihon/buildlogic/KmpLibrary.kt.
    id("mihon.library.kmp")
    // id("mihon.library")
    // kotlin("android")
    // KMK <--
    kotlin("plugin.serialization")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "tachiyomi.data"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    sqldelight {
        databases {
            create("Database") {
                packageName.set("tachiyomi.data")
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
                // KMK --> in a Kotlin Multiplatform module SQLDelight defaults to src/commonMain/sqldelight
                srcDirs("src/main/sqldelight")
                // KMK <--
            }
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }

    // KMK --> desktop database: the same AndroidX SQLite driver stack as the app, over the bundled
    // SQLite JVM driver (Android gets the driver from `app`).
    sourceSets {
        named("desktopMain") {
            dependencies {
                implementation(androidx.sqlite.bundled)
            }
        }
        named("desktopTest") {
            dependencies {
                implementation(libs.bundles.test)
                implementation(kotlinx.coroutines.test)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
    // KMK <--
}

// KMK --> desktop tests keep their files under build/, not in the user's data dir; the migration
// test starts from the SQLDelight schema snapshot in src/main/sqldelight.
tasks.named<Test>("desktopTest") {
    systemProperty("komikku.dataDir", layout.buildDirectory.dir("tmp/test-data").get().asFile.absolutePath)
    systemProperty("komikku.sqldelightDir", file("src/main/sqldelight").absolutePath)
    inputs.dir("src/main/sqldelight")
}
// KMK <--

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)

    implementation(kotlinx.serialization.json)
    implementation(kotlinx.serialization.json.okio)
    implementation(kotlinx.serialization.protobuf)

    api(libs.bundles.sqldelight)
}
