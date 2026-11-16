package mihon.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMK: Compose for a `mihon.library.kmp` module (PLAN.md D6, "BOM wins"), applied by the
 * `mihon.library.kmp.compose` convention plugin.
 *
 * Compose is resolved **per target**, never through the shared `jvmSharedMain` dependencies:
 *
 * - `androidMain` gets `platform(compose.bom)` and the module declares the usual androidx
 *   coordinates of the `compose` catalog there (`compose.foundation`, `compose.material3.core`, …).
 *   The Android side of a converted module therefore resolves exactly what the androidx Compose BOM
 *   dictates, like the Android-only `mihon.library.compose` modules and the app do. JetBrains
 *   Compose Multiplatform coordinates are deliberately *not* used on Android: since CMP 1.12 their
 *   Android variants are separate `*-android` artifacts that require newer androidx Compose
 *   versions than the BOM (e.g. `org.jetbrains.compose.ui:ui:1.12.2` → `androidx.compose.ui:ui:1.12.1`
 *   while the BOM pins 1.11.4), so they would upgrade the app's Compose; a strict constraint would
 *   instead put JetBrains wrapper artifacts built against a newer androidx on the app's classpath.
 * - `desktopMain` gets the JetBrains runtime (`libs.compose.multiplatform.runtime`); modules add
 *   the other `libs.compose.multiplatform.*` coordinates (or the `libs.bundles.compose.multiplatform`
 *   bundle) there. Material 3 is pinned to the JetBrains build of the BOM's material3 version.
 *
 * Shared code in `jvmSharedMain` is compiled once per target (there is no shared metadata
 * compilation in this JVM-only hierarchy), against androidx Compose on Android and JetBrains Compose
 * on desktop; both expose the same `androidx.compose.*` packages, so shared sources need no changes.
 * Never put Compose dependencies on the top-level `implementation` / `api` configurations of such a
 * module: they are mirrored to `jvmSharedMain` and would reach the desktop target with androidx
 * (Android-only or stub) artifacts.
 *
 * The compose compiler plugin, the Android `buildFeatures.compose` flag and the optional compiler
 * metrics / reports (`-PenableComposeCompilerMetrics`, `-PenableComposeCompilerReports`) are set up
 * as in `configureCompose`.
 */
internal fun Project.configureKmpCompose() {
    pluginManager.apply(kotlinx.plugins.compose.compiler.get().pluginId)

    extensions.configure<LibraryExtension> {
        buildFeatures {
            compose = true
        }
    }

    val kmp = extensions.getByType<KotlinMultiplatformExtension>()
    val bom = dependencies.platform(compose.bom)
    val desktopRuntime = libs.compose.multiplatform.runtime
    kmp.sourceSets.named("androidMain") {
        dependencies { implementation(bom) }
    }
    kmp.sourceSets.named("${DESKTOP_TARGET}Main") {
        dependencies { implementation(desktopRuntime) }
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        val enableMetrics = providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()
        val enableReports = providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()

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
