plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("com.github.ben-manes.versions")
}

kotlin {
    android()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinx.serialization.json)
                api(libs.injekt.core)
                api(libs.rxjava)
                api(libs.jsoup)
                // SY -->
                api(project(":i18n"))
                api(kotlinx.reflect)
                // SY <--
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(project(":core"))
                api(libs.preferencektx)
            }
        }
    }
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFile("consumer-proguard.pro")
    }
}
