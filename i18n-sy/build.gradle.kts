plugins {
    id("mihon.library")
    kotlin("multiplatform")
    alias(libs.plugins.moko)
    id("com.github.ben-manes.versions")
}

kotlin {
    androidTarget()
    // KMK --> Desktop (JVM) target for Compose Multiplatform builds
    jvm("desktop")
    // KMK <--

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }
    }
}

android {
    namespace = "tachiyomi.i18n.sy"

    sourceSets {
        named("main") {
            res.srcDir("src/commonMain/resources")
        }
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
    }
}

multiplatformResources {
    resourcesClassName.set("SYMR")
    resourcesPackage.set("tachiyomi.i18n.sy")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.freeCompilerArgs.addAll(
        "-Xexpect-actual-classes",
    )
}
