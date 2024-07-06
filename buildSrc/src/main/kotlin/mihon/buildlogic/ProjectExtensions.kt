package mihon.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.accessors.dm.LibrariesForAndroidx
import org.gradle.accessors.dm.LibrariesForCompose
import org.gradle.accessors.dm.LibrariesForKotlinx
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val Project.androidx get() = the<LibrariesForAndroidx>()
val Project.compose get() = the<LibrariesForCompose>()
val Project.kotlinx get() = the<LibrariesForKotlinx>()
val Project.libs get() = the<LibrariesForLibs>()

internal fun Project.configureAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = AndroidConfig.COMPILE_SDK

        defaultConfig {
            minSdk = AndroidConfig.MIN_SDK
            ndk {
                version = AndroidConfig.NDK
            }
        }

        compileOptions {
            sourceCompatibility = AndroidConfig.JavaVersion
            targetCompatibility = AndroidConfig.JavaVersion
            isCoreLibraryDesugaringEnabled = true
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(AndroidConfig.JvmTarget)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xcontext-receivers",
            )

            // Treat all Kotlin warnings as errors (disabled by default)
            // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
            val warningsAsErrors: String? by project
            allWarningsAsErrors.set(warningsAsErrors.toBoolean())

        }
    }

    dependencies {
        "coreLibraryDesugaring"(libs.desugar)
    }
}

internal fun Project.configureCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    pluginManager.apply(kotlinx.plugins.compose.compiler.get().pluginId)

    commonExtension.apply {
        buildFeatures {
            compose = true
        }

        dependencies {
            "implementation"(platform(compose.bom))
        }
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        // Enable strong skipping mode
        enableStrongSkippingMode.set(true)

        // Enable experimental compiler opts
        // https://developer.android.com/jetpack/androidx/releases/compose-compiler#1.5.9
        enableNonSkippingGroupOptimization.set(true)

        val enableMetrics = project.providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()
        val enableReports = project.providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()

        val rootProjectDir = rootProject.layout.buildDirectory.asFile.get()
        val relativePath = projectDir.relativeTo(rootDir)
        if (enableMetrics) {
            val buildDirPath = rootProjectDir.resolve("compose-metrics").resolve(relativePath)
            metricsDestination.set(buildDirPath)
        }
        if (enableReports) {
            val buildDirPath = rootProjectDir.resolve("compose-reports").resolve(relativePath)
            reportsDestination.set(buildDirPath)
        }
    }

}

internal fun Project.configureTest() {
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
}
