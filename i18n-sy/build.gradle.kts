plugins {
    id("mihon.library")
    id("dev.icerock.mobile.multiplatform-resources")
    kotlin("multiplatform")
    id("com.github.ben-manes.versions")
}

kotlin {
    androidTarget()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.moko.core)
            }
        }
        androidMain {
            dependsOn(commonMain) // https://github.com/icerockdev/moko-resources/issues/562
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
    multiplatformResourcesClassName = "SYMR"
    multiplatformResourcesPackage = "tachiyomi.i18n.sy"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.freeCompilerArgs.addAll(
        "-Xexpect-actual-classes",
    )
}
