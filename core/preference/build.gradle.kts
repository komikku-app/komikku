plugins {
    // Android + desktop (JVM), see buildSrc mihon/buildlogic/KmpLibrary.kt.
    // Shared sources stay in src/commonMain/kotlin (compiled through `jvmSharedMain`).
    id("mihon.library.kmp")
}

android {
    namespace = "tachiyomi.core.preference"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }

    sourceSets {
        named("desktopMain") {
            dependencies {
                implementation(kotlinx.serialization.json)
            }
        }
        named("desktopTest") {
            dependencies {
                implementation(kotlinx.coroutines.test)
            }
        }
    }
}

dependencies {
    api(platform(kotlinx.coroutines.bom))
    api(kotlinx.coroutines.core)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
