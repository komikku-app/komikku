package mihon.buildlogic

import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.api.AndroidSourceDirectorySet
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Name of the desktop (JVM) target; its source sets are `desktopMain` / `desktopTest`. */
const val DESKTOP_TARGET = "desktop"

/** Intermediate source set group shared by the Android and desktop targets. */
const val JVM_SHARED_GROUP = "jvmShared"

/**
 * KMK: path-preserving Kotlin Multiplatform setup for an Android library module (PLAN.md, D1),
 * applied by the `mihon.library.kmp` convention plugin on top of `mihon.library` +
 * `kotlin("multiplatform")`.
 *
 * Targets: `androidTarget()` + `jvm("desktop")`. Source sets:
 *
 * ```
 * commonMain   (no sources: JDK APIs are not visible there and there is no non-JVM target)
 *   └ jvmSharedMain   <- src/main/java, src/main/kotlin, src/commonMain/kotlin (the module's
 *       │                existing source dirs, whichever exist) + src/jvmSharedMain/kotlin
 *       ├ androidMain <- src/androidMain/kotlin (Android-only files, same package)
 *       └ desktopMain <- src/desktopMain/kotlin (desktop counterparts)
 * commonTest
 *   └ jvmSharedTest   <- src/test/java, src/test/kotlin, src/commonTest/kotlin
 *       ├ androidUnitTest <- src/androidUnitTest/kotlin
 *       └ desktopTest     <- src/desktopTest/kotlin
 * ```
 *
 * Android-only inputs stay where they are: `src/main/AndroidManifest.xml`, `src/main/res`,
 * `src/main/assets` and `src/main/resources` remain on the Android `main` source set (Java resources
 * from `src/main/resources` / `src/test/resources` are also added to the desktop target).
 * The Android `main` / `test` Java + Kotlin dirs are moved off `src/main` and `src/test` so the
 * shared sources are compiled exactly once per target (through `jvmSharedMain` / `jvmSharedTest`).
 * Only Kotlin sources are shared; `.java` files under `src/main/java` would not be compiled.
 *
 * Dependencies: what a module declares directly on the top-level `api`, `implementation`,
 * `compileOnly`, `runtimeOnly` and `test*` configurations is mirrored onto the matching
 * `jvmShared*` configuration, so an existing `dependencies {}` block keeps working for both targets
 * unchanged. Android-only libraries (AARs) must be declared through `androidMain` instead, e.g.
 * `kotlin { sourceSets { androidMain.dependencies { implementation(libs.unifile) } } }`; anything
 * left in the top-level block is also put on the desktop classpath and fails resolution there.
 * `debug*` / `release*` configurations stay Android-only.
 *
 * Visibility: code in `jvmSharedMain` cannot call declarations from `androidMain` / `desktopMain`
 * of the same module (K2 enforces source set boundaries). A platform-split symbol used by shared
 * code of the same module needs `expect`/`actual` or an interface implemented per platform. Other
 * modules can use same-package `androidMain` + `desktopMain` declarations directly, because each
 * of their targets compiles against this module's platform artifact.
 *
 * IDE caveat: a source set shared by Android and JVM targets is a known Kotlin limitation in the
 * IDE (analysis goes through the Android target; desktop-incompatible API use in `jvmSharedMain`
 * may only show up when running `compileKotlinDesktop`). Gradle compiles each target correctly.
 */
internal fun Project.configureKmpLibrary() {
    val kmp = extensions.getByType<KotlinMultiplatformExtension>()

    val mainDirs = existingDirs("src/main/java", "src/main/kotlin", "src/commonMain/kotlin")
    val testDirs = existingDirs("src/test/java", "src/test/kotlin", "src/commonTest/kotlin")

    kmp.androidTarget()
    kmp.jvm(DESKTOP_TARGET)

    // Android `main` / `test` source sets: keep manifest and resources, drop the shared code dirs
    // (KGP feeds the Android java/kotlin dirs into `androidMain` / `androidUnitTest`). Must run after
    // `androidTarget()`, which points the manifest to src/androidMain/AndroidManifest.xml.
    extensions.configure<LibraryExtension> {
        sourceSets.named("main") {
            file("src/main/AndroidManifest.xml").takeIf { it.isFile }?.let(manifest::srcFile)
            res.srcDir("src/main/res")
            assets.srcDir("src/main/assets")
            resources.srcDir("src/main/resources")
            java.setSrcDirs(listOf("src/androidMain/java"))
            (kotlin as AndroidSourceDirectorySet).setSrcDirs(listOf("src/androidMain/kotlin"))
        }
        sourceSets.named("test") {
            resources.srcDir("src/test/resources")
            java.setSrcDirs(listOf("src/androidUnitTest/java"))
            (kotlin as AndroidSourceDirectorySet).setSrcDirs(listOf("src/androidUnitTest/kotlin"))
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    kmp.applyDefaultHierarchyTemplate {
        common {
            group(JVM_SHARED_GROUP) {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    kmp.sourceSets.configureEach {
        when (name) {
            // KGP also adds the "Android style" src/main/kotlin and src/test/kotlin dirs.
            "androidMain" -> kotlin.setSrcDirs(kotlin.srcDirs - mainDirs.toSet())
            "androidUnitTest" -> kotlin.setSrcDirs(kotlin.srcDirs - testDirs.toSet())
            "commonMain", "commonTest" -> kotlin.setSrcDirs(emptyList<Any>())
            "${JVM_SHARED_GROUP}Main" -> {
                kotlin.srcDirs(mainDirs)
                mirrorDependencies(
                    "api" to apiConfigurationName,
                    "implementation" to implementationConfigurationName,
                    "compileOnly" to compileOnlyConfigurationName,
                    "runtimeOnly" to runtimeOnlyConfigurationName,
                )
            }
            "${JVM_SHARED_GROUP}Test" -> {
                kotlin.srcDirs(testDirs)
                mirrorDependencies(
                    "testImplementation" to implementationConfigurationName,
                    "testCompileOnly" to compileOnlyConfigurationName,
                    "testRuntimeOnly" to runtimeOnlyConfigurationName,
                )
            }
            "${DESKTOP_TARGET}Main" -> resources.srcDir("src/main/resources")
            "${DESKTOP_TARGET}Test" -> resources.srcDir("src/test/resources")
        }
    }
}

/**
 * Adds every dependency declared directly on each `from` configuration to its `to` configuration.
 *
 * `extendsFrom` cannot be used: KGP makes the top-level Android configurations extend the
 * `androidMain*` ones, which would leak Android-only dependencies to desktop. The copy is lazy
 * (`addAllLater`) because version catalog dependencies are themselves added lazily and are not
 * seen by `configureEach` until the source collection is realized.
 */
private fun Project.mirrorDependencies(vararg mapping: Pair<String, String>) {
    mapping.forEach { (from, to) ->
        val source = configurations.getByName(from)
        configurations.getByName(to).dependencies.addAllLater(
            objects.listProperty(Dependency::class.java).value(provider { source.dependencies.toList() }),
        )
    }
}

private fun Project.existingDirs(vararg paths: String) = paths.map(::file).filter { it.isDirectory }
