import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("mihon.code.lint")
    kotlin("jvm")
    // Compose compiler plugin is already on the classpath (via buildSrc); apply by id without a version.
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.multiplatform)
}

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
    // Compose Desktop runtime for the host OS (Skiko native included); packaging per OS is handled
    // by `nativeDistributions` below.
    implementation(ComposePlugin.DesktopDependencies.currentOs)
    implementation(libs.compose.multiplatform.material3)
    // Shared Kotlin Multiplatform modules that also target Android.
    implementation(projects.i18n)
    implementation(projects.core.preference)
}

// `compose` in build scripts resolves to the version catalog (see buildSrc ComposeCatalogAccessor.kt),
// so the JetBrains Compose extension is accessed by type.
extensions.configure<ComposeExtension> {
    extensions.configure<DesktopExtension> {
        application {
            mainClass = "app.komikku.desktop.MainKt"

            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                packageName = "Komikku"
                packageVersion = "1.0.0"
            }
        }
    }
}
