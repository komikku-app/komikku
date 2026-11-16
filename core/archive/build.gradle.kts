plugins {
    // KMK --> Android + desktop (JVM), path preserving: shared sources stay in src/main/kotlin
    // (compiled through `jvmSharedMain`), see buildSrc mihon/buildlogic/KmpLibrary.kt.
    id("mihon.library.kmp")
    // id("mihon.library")
    // kotlin("android")
    // KMK <--
    kotlin("plugin.serialization")
}

android {
    namespace = "mihon.core.archive"
}

// KMK --> Android reads / writes archives with libarchive (JNI); desktop implements the same API on
// zip4j, junrar, commons-compress and xz (src/desktopMain).
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.libarchive)
            implementation(libs.unifile)
        }
        named("desktopMain") {
            dependencies {
                implementation(libs.bundles.archive.desktop)
            }
        }
        named("desktopTest") {
            dependencies {
                implementation(libs.bundles.test)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}

// Desktop tests keep the CBZ key and preferences under build/, not in the user's data dir.
tasks.named<Test>("desktopTest") {
    systemProperty("komikku.dataDir", layout.buildDirectory.dir("tmp/test-data").get().asFile.absolutePath)
}
// KMK <--

dependencies {
    implementation(libs.jsoup)
    // KMK --> moved to androidMain (Android-only AARs)
    // implementation(libs.libarchive)
    // implementation(libs.unifile)
    // KMK <--
    // KMK -->
    implementation(projects.core.common)
    implementation(libs.injekt)
    // KMK <--
}
