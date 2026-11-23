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
    // Swing `Dispatchers.Main` (Compose Desktop brings it transitively; declared explicitly because shared
    // code relies on it: `launchUI`, `withUIContext`, Voyager `screenModelScope`).
    implementation(kotlinx.coroutines.swing)
    // Shared Kotlin Multiplatform modules that also target Android.
    implementation(projects.i18n)
    implementation(projects.i18nSy)
    implementation(projects.i18nKmk)
    implementation(projects.core.preference)
    // Shared non-UI stack: network, logging, i18n helpers (desktop variant + :desktop:android-compat).
    implementation(projects.core.common)
    // Domain + data layer (SQLDelight over the bundled SQLite driver), sources, local source.
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.sourceLocal)
    // Shared UI: presentation-core components, app theme, Voyager host, desktop Coil setup.
    implementation(projects.presentationCore)
    implementation(projects.appShared)
    // Extensions: APK reading, dex2jar conversion, class loading (Phase 5).
    implementation(projects.desktop.extensionRuntime)
    // Mangayomi JavaScript sources: host + Mihon source adapter (PLAN.md 8, I3).
    implementation(projects.sourceJs)
    implementation(kotlinx.serialization.json)
    // Borrowed backup / restore option lists (PersistentList)
    implementation(kotlinx.immutables)
    implementation(kotlinx.serialization.protobuf)
    implementation(kotlinx.serialization.xml)
    implementation(kotlinx.serialization.xml.core.jvm)

    testImplementation(libs.bundles.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(kotlinx.coroutines.test)
    testImplementation(libs.compose.multiplatform.ui.test)
    // Fixture extension APKs (generated, signed with test keys) for the extension manager tests.
    testImplementation(testFixtures(projects.desktop.extensionRuntime))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Injekt is process-global: every test class that boots `DesktopInjekt` gets its own JVM.
    forkEvery = 1
    // Keep tests out of the user's real data dir (preferences, cookies, caches).
    val testDataDir = layout.buildDirectory.dir("tmp/test-data").get().asFile
    systemProperty("komikku.dataDir", testDataDir.absolutePath)
    doFirst { testDataDir.deleteRecursively() }
}

/*
 * Version: the Android app's `versionName` / `versionCode` in app/build.gradle.kts are the single source of truth
 * (read here, not duplicated). jpackage needs MAJOR[.MINOR[.BUILD]] numbers (MSI: MAJOR, MINOR <= 255,
 * BUILD <= 65535; DMG: MAJOR > 0), which Komikku's `x.y.z` names satisfy.
 */
val appBuildFile = rootProject.file("app/build.gradle.kts").readText()
val appVersionName: String = Regex("""versionName\s*=\s*"([^"]+)"""").find(appBuildFile)?.groupValues?.get(1)
    ?: error("versionName not found in app/build.gradle.kts")
val appVersionCode: Int = Regex("""versionCode\s*=\s*(\d+)""").find(appBuildFile)?.groupValues?.get(1)?.toInt()
    ?: error("versionCode not found in app/build.gradle.kts")
val packageVersionName: String = appVersionName.substringBefore('-').also {
    require(Regex("""\d+(\.\d+){0,2}""").matches(it)) { "versionName $it is not a jpackage version (x[.y[.z]])" }
}

// `compose` in build scripts resolves to the version catalog (see buildSrc ComposeCatalogAccessor.kt),
// so the JetBrains Compose extension is accessed by type.
extensions.configure<ComposeExtension> {
    extensions.configure<DesktopExtension> {
        application {
            mainClass = "app.komikku.desktop.MainKt"
            // Read by app-shared's desktop BuildConfig (About screen, backups); also applies to `run`.
            jvmArgs += listOf("-Dkomikku.version=$appVersionName", "-Dkomikku.versionCode=$appVersionCode")

            // Packaging (Phase 9). How to build / what is not validated: docs/kmp-desktop/BUILDING.md.
            nativeDistributions {
                // AppImage is a directory image for appimagetool, not a single .AppImage file (BUILDING.md).
                targetFormats(
                    TargetFormat.Msi,
                    TargetFormat.Exe,
                    TargetFormat.Dmg,
                    TargetFormat.Deb,
                    TargetFormat.Rpm,
                    TargetFormat.AppImage,
                )
                packageName = "Komikku"
                packageVersion = packageVersionName
                description = "Manga reader for Android and desktop, based on Mihon and TachiyomiSY"
                vendor = "Komikku"
                copyright = "Copyright (C) Komikku contributors. Licensed under the Apache License 2.0."
                licenseFile.set(rootProject.file("LICENSE"))

                /*
                 * JDK modules of the bundled runtime (jlink). Static dependencies from jdeps over the runtime
                 * class path (`./gradlew :desktopApp:suggestRuntimeModules`, plus a per-jar jdeps pass):
                 * java.compiler, java.desktop, java.instrument, java.logging, java.management, java.xml,
                 * jdk.unsupported, jdk.xml.dom. Added for code loaded at run time that jdeps cannot see:
                 * java.net.http (jsoup's optional HttpClient path; the plugin's suggestion), jdk.crypto.ec
                 * (TLS with EC keys / ECDHE on JDK 21), jdk.charsets (Shift_JIS / GBK / ... in archives and
                 * pages), jdk.localedata (non-English date / number formats), jdk.accessibility (screen
                 * readers on Windows). Extensions run in the same JVM and may need more: if a packaged build
                 * hits NoClassDefFoundError for a java.* / jdk.* class, add the module here or switch to
                 * `includeAllModules = true` (bigger, always complete) as a fallback.
                 */
                modules(
                    "java.compiler",
                    "java.desktop",
                    "java.instrument",
                    "java.logging",
                    "java.management",
                    "java.net.http",
                    "java.xml",
                    "jdk.accessibility",
                    "jdk.charsets",
                    "jdk.crypto.ec",
                    "jdk.localedata",
                    "jdk.unsupported",
                    "jdk.xml.dom",
                )

                // Generated from the Android launcher icon (desktopApp/icons/generate_icons.py).
                linux {
                    packageName = "komikku"
                    iconFile.set(project.file("icons/komikku.png"))
                    // jpackage: the menu group becomes the `.desktop` file's Categories (freedesktop.org main +
                    // additional categories); the app category is the DEB Section / RPM Group.
                    menuGroup = "Graphics;Viewer;"
                    appCategory = "graphics"
                    shortcut = true
                    debPackageVersion = packageVersionName
                    rpmPackageVersion = packageVersionName
                    rpmLicenseType = "ASL 2.0"
                }
                windows {
                    iconFile.set(project.file("icons/komikku.ico"))
                    menuGroup = "Komikku"
                    // Fixed so that newer MSI packages upgrade older installs; never change it.
                    upgradeUuid = "4b3008ca-6650-44e4-a404-1adb723fb2ef"
                    shortcut = true
                    menu = true
                    dirChooser = true
                    perUserInstall = true
                    msiPackageVersion = packageVersionName
                    exePackageVersion = packageVersionName
                }
                macOS {
                    iconFile.set(project.file("icons/komikku.icns"))
                    bundleID = "app.komikku"
                    appCategory = "public.app-category.entertainment"
                    dockName = "Komikku"
                    packageVersion = packageVersionName
                    dmgPackageVersion = packageVersionName
                    // TODO(signing): `signing { sign.set(true); identity.set(...) }` and `notarization { ... }`
                    // need an Apple Developer ID; not configured (no secrets), see BUILDING.md.
                }
            }

            // Release build type (ProGuard) disabled initially: extensions are loaded reflectively and use
            // host classes (source-api, OkHttp, kotlinx, compat layer) that shrinking would remove or rename.
            // Enabling it needs keep rules for that whole shared surface (BUILDING.md).
            buildTypes.release.proguard {
                isEnabled.set(false)
            }
        }
    }
}
