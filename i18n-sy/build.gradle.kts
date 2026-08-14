import mihon.buildlogic.AndroidConfig
import mihon.buildlogic.configureTest

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("mihon.code.lint")
    kotlin("multiplatform")
    alias(libs.plugins.moko)
    id("com.github.ben-manes.versions")
}

kotlin {
    android {
        compileSdk { version = release(AndroidConfig.COMPILE_SDK) }
        namespace = "tachiyomi.i18n.sy"
        androidResources {
            enable = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }
    }
}

multiplatformResources {
    resourcesClassName.set("SYMR")
    resourcesPackage.set("tachiyomi.i18n.sy")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(AndroidConfig.JvmTarget)
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
}

configureTest()
