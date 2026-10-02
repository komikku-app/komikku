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
import java.io.File

val Project.androidx get() = the<LibrariesForAndroidx>()
val Project.compose get() = the<LibrariesForCompose>()
val Project.kotlinx get() = the<LibrariesForKotlinx>()
val Project.libs get() = the<LibrariesForLibs>()

internal fun Project.configureAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = AndroidConfig.COMPILE_SDK

        defaultConfig {
            minSdk = AndroidConfig.MIN_SDK
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
                "-Xcontext-parameters",
                "-opt-in=kotlin.RequiresOptIn",
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

            constraints {
                // Keep every Compose module on one material3 version. A module resolves its own
                // graph, so a transitive dependency can lift one module above another: a wrapper
                // that one module compiles then fails at runtime in the next. materialkolor is the
                // concrete case, because it requests material3 through
                // org.jetbrains.compose.material3. Raise the catalog version and this constraint
                // with it.
                add("implementation", compose.material3.core) {
                    version { strictly(compose.versions.material3.get()) }
                }
            }
        }
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        val enableMetrics = project.providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()
        val enableReports = project.providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()

        val rootBuildDir = rootProject.layout.buildDirectory.asFile.get()
        val relativePath = projectDir.relativeTo(rootDir)

        if (enableMetrics) {
            rootBuildDir.resolve("compose-metrics").resolve(relativePath).let(metricsDestination::set)
        }

        if (enableReports) {
            rootBuildDir.resolve("compose-reports").resolve(relativePath).let(reportsDestination::set)
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

val Project.generatedBuildDir: File get() = project.layout.buildDirectory.asFile.get().resolve("generated/mihon")
