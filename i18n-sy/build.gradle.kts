plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("dev.icerock.mobile.multiplatform-resources")
    id("com.github.ben-manes.versions")
}

kotlin {
    androidTarget()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.moko.core)
            }
        }
        val androidMain by getting {
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
    kotlinOptions.freeCompilerArgs += listOf(
        "-Xexpect-actual-classes",
    )
}
