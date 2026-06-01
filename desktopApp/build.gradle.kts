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

dependencies {
    // Brings the host-specific Compose Desktop UI stack (runtime/ui/foundation/material + Skiko).
    implementation(libs.composemp.desktop)
    // Shared Kotlin Multiplatform module that also targets Android.
    implementation(projects.i18n)
}

application {
    mainClass.set("app.komikku.desktop.MainKt")
}
