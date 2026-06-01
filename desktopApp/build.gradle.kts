import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("mihon.code.lint")
    kotlin("jvm")
    // Compose compiler plugin is already on the classpath (via buildSrc); apply by id without a version.
    id("org.jetbrains.kotlin.plugin.compose")
    application
}

// NOTE: The `org.jetbrains.compose` Gradle plugin is intentionally NOT applied here. It registers a
// project extension named `compose`, which collides with this repo's `compose` version catalog
// (also exposed as a `compose` extension). For Phase 0 we depend on the Compose Desktop artifact
// directly and use the Compose compiler plugin, which is enough to compile and run the app. The full
// Compose Multiplatform plugin (with native packaging DSL) can be adopted in Phase 3 once the catalog
// name clash is resolved project-wide.

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Without the org.jetbrains.compose plugin we must select the host-specific Compose Desktop
// artifact ourselves so the matching Skiko native library is on the runtime classpath.
val composeMultiplatformVersion = the<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("compose-multiplatform")
    .get()
    .requiredVersion

val hostComposeDesktopTarget: String = run {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val isArm = osArch.contains("aarch64") || osArch.contains("arm")
    when {
        osName.contains("mac") || osName.contains("darwin") -> if (isArm) "macos-arm64" else "macos-x64"
        osName.contains("win") -> if (isArm) "windows-arm64" else "windows-x64"
        else -> if (isArm) "linux-arm64" else "linux-x64"
    }
}

dependencies {
    // Host-specific Compose Desktop UI stack (runtime/ui/foundation/material + Skiko native).
    implementation("org.jetbrains.compose.desktop:desktop-jvm-$hostComposeDesktopTarget:$composeMultiplatformVersion")
    // Shared Kotlin Multiplatform modules that also target Android.
    implementation(projects.i18n)
    implementation(projects.core.preference)
}

application {
    mainClass.set("app.komikku.desktop.MainKt")
}
