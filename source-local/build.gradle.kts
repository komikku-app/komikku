import mihon.buildlogic.AndroidConfig
import mihon.buildlogic.configureTest
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("mihon.code.lint")
    kotlin("multiplatform")
}

kotlin {
    android {
        compileSdk { version = release(AndroidConfig.COMPILE_SDK) }
        namespace = "tachiyomi.source.local"
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.sourceApi)
                api(projects.i18n)
                // SY -->
                api(projects.i18nSy)
                // SY <--

                implementation(libs.unifile)
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
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(AndroidConfig.JvmTarget)
    }
}

configureTest()
