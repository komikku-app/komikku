import mihon.buildlogic.Config
import mihon.buildlogic.getCommitCount
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
    // Extensions use host-provided OkHttp modules like on Android (libs.bundles.okhttp): Keiyoushi's client adds
    // okhttp3.zstd.Zstd. Native zstd-kmp library inside the jar (macOS / Linux / Windows, x64 + arm64).
    implementation(libs.okhttp.zstd)
    // Mangayomi JavaScript sources: host + Mihon source adapter (PLAN.md 8, I3).
    implementation(projects.sourceJs)
    implementation(kotlinx.serialization.json)
    // Borrowed backup / restore option lists (PersistentList)
    implementation(kotlinx.immutables)
    implementation(kotlinx.serialization.protobuf)
    implementation(kotlinx.serialization.xml)
    implementation(kotlinx.serialization.xml.core.jvm)
    // M2: the packaged app's self-test (`--self-test`) opens the bundled SQLite driver and the QuickJS engine that
    // extensions use directly (both are on the runtime class path already, through :data and :core:common)
    implementation(androidx.sqlite.bundled)
    implementation(projects.core.quickjs)

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
 * M2: the JDK modules of the packaged app's runtime, one per line in runtime-modules.txt (`#` comments). The first Mac
 * test build lacked jdk.zipfs, so dex2jar could not write the converted jar of any APK extension; the full JDK of
 * `run` and the tests hid it.
 */
val runtimeModulesFile: File = project.file("runtime-modules.txt")
val desktopRuntimeModules: List<String> = runtimeModulesFile.readLines()
    .map { it.substringBefore('#').trim() }
    .filter { it.isNotEmpty() }
    .also { modules -> check(modules.toSet().size == modules.size) { "runtime-modules.txt lists a module twice" } }

// P17: the system tray icon (`DesktopTray.ICON_RESOURCE`) is the packaging icon, not a second copy in the sources
tasks.named<ProcessResources>("processResources") {
    from("icons/komikku.png") { into("app/komikku/desktop") }
    // M2: the bundled runtime's module list, which the packaged app's `--self-test` checks the runtime against
    from(runtimeModulesFile) { into("app/komikku/desktop/selftest") }
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

// The version the release assets are named with (`build_desktop.yml`, P9): the full Android `versionName`, printed as
// one marked line `desktopVersionName=<versionName>` that the workflow extracts. The packages use only the part before
// a `-` suffix (`packageVersionName`); desktop releases do not support suffixes, so the workflow refuses a suffixed
// name before it builds (review fixes P9).
tasks.register("printVersionName") {
    group = "help"
    description = "Prints the Android versionName as desktopVersionName=<versionName> (packages use it without a suffix)."
    val version = appVersionName
    doLast { println("desktopVersionName=$version") }
}

/*
 * Signing hooks (P9, BUILDING.md "Signing"): off unless set, so a build without them is exactly the unsigned one. Each
 * setting is a Gradle property (`-Pkomikku.desktop.<name>=…`, or ~/.gradle/gradle.properties) or an environment
 * variable (CI: from repository secrets); blank = not set. Values are never printed.
 *
 * - macOS signing (Compose `macOS.signing`): the Developer ID Application identity, optionally the keychain that
 *   holds it (CI imports the certificate into a temporary one).
 * - macOS notarization (Compose `macOS.notarization`, task `notarizeDmg`): Apple ID, app-specific password and team
 *   ID, all three or none; only together with signing.
 * - Windows: the Compose plugin has no signing configuration; `build_desktop.yml` signs the MSI / EXE with signtool
 *   after packaging when its secrets are set.
 */
fun desktopSetting(property: String, environment: String): String? =
    (providers.gradleProperty(property).orNull ?: providers.environmentVariable(environment).orNull)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

val macSigningIdentity = desktopSetting("komikku.desktop.macSigningIdentity", "KOMIKKU_MAC_SIGNING_IDENTITY")
val macSigningKeychain = desktopSetting("komikku.desktop.macSigningKeychain", "KOMIKKU_MAC_SIGNING_KEYCHAIN")
val macNotarizationAppleId = desktopSetting("komikku.desktop.macNotarizationAppleId", "KOMIKKU_MAC_NOTARIZATION_APPLE_ID")
val macNotarizationPassword = desktopSetting("komikku.desktop.macNotarizationPassword", "KOMIKKU_MAC_NOTARIZATION_PASSWORD")
val macNotarizationTeamId = desktopSetting("komikku.desktop.macNotarizationTeamId", "KOMIKKU_MAC_NOTARIZATION_TEAM_ID")
val macNotarizationSettings = listOf(macNotarizationAppleId, macNotarizationPassword, macNotarizationTeamId)
check(macNotarizationSettings.all { it == null } || macNotarizationSettings.all { it != null }) {
    "macOS notarization needs all of KOMIKKU_MAC_NOTARIZATION_APPLE_ID, _PASSWORD and _TEAM_ID (or the " +
        "komikku.desktop.macNotarization* Gradle properties), or none of them"
}
check(macNotarizationAppleId == null || macSigningIdentity != null) {
    "macOS notarization needs signing: set KOMIKKU_MAC_SIGNING_IDENTITY (or komikku.desktop.macSigningIdentity) too"
}

/*
 * The commit count of a preview build (`-Pdesktop-preview`), compared with the `r<N>` preview release tags. A shallow
 * clone counts only its own commits (1 with CI's default checkout depth), which would make every release look newer, so
 * the build fails instead of packaging a preview that offers every release as an update.
 */
fun previewCommitCount(): Int {
    val shallow = providers.exec {
        commandLine("git", "rev-parse", "--is-shallow-repository")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
    check(shallow != "true") {
        "-Pdesktop-preview needs the full Git history for the commit count, but this clone is shallow " +
            "(git fetch --unshallow, or actions/checkout with fetch-depth: 0)"
    }
    val count = runCatching { getCommitCount().toInt() }.getOrNull()
    check(count != null && count > 0) {
        "-Pdesktop-preview needs the commit count (git rev-list --count HEAD), got ${count ?: "none"}: build from a Git clone"
    }
    return count
}

// `compose` in build scripts resolves to the version catalog (see buildSrc ComposeCatalogAccessor.kt),
// so the JetBrains Compose extension is accessed by type.
extensions.configure<ComposeExtension> {
    extensions.configure<DesktopExtension> {
        application {
            mainClass = "app.komikku.desktop.MainKt"
            // Read by app-shared's desktop BuildConfig (About screen, backups); also applies to `run`.
            jvmArgs += listOf("-Dkomikku.version=$appVersionName", "-Dkomikku.versionCode=$appVersionCode")
            // App update check (P6, DesktopUpdaterConfig): off unless built with Android's `-Penable-updater`;
            // `-Pdesktop-preview` selects the preview channel (komikku-preview releases, compared by commit count).
            if (Config.enableUpdater) jvmArgs += "-Dkomikku.updater=true"
            if (project.hasProperty("desktop-preview")) {
                jvmArgs += listOf("-Dkomikku.preview=true", "-Dkomikku.commitCount=${previewCommitCount()}")
            }

            // Packaging (Phase 9). How to build / what is not validated: docs/kmp-desktop/BUILDING.md.
            nativeDistributions {
                // No TargetFormat.AppImage (M1, first CI run): its `packageAppImage` writes the same directory as
                // `createDistributable` (binaries/main/app), which the DMG / DEB / RPM tasks read, and Gradle refuses
                // to run them together. The app image directory (packed as the Linux .tar.gz) is
                // `createDistributable`'s output.
                targetFormats(
                    TargetFormat.Msi,
                    TargetFormat.Exe,
                    TargetFormat.Dmg,
                    TargetFormat.Deb,
                    TargetFormat.Rpm,
                )
                packageName = "Komikku"
                packageVersion = packageVersionName
                description = "Manga reader for Android and desktop, based on Mihon and TachiyomiSY"
                vendor = "Komikku"
                copyright = "Copyright (C) Komikku contributors. Licensed under the Apache License 2.0."
                licenseFile.set(rootProject.file("LICENSE"))

                /*
                 * JDK modules of the bundled runtime (jlink): the list in runtime-modules.txt (M2; why each one is
                 * there is noted in the file). Static dependencies from jdeps (`./gradlew :desktopApp:suggestRuntimeModules`
                 * plus a per-jar pass) are a small part of it; the rest is what code loaded at run time needs and jdeps
                 * cannot see (service loaders, reflection, extensions). The packaged app's `--self-test` (run by
                 * build_desktop.yml) checks the built runtime against the same file.
                 */
                modules(*desktopRuntimeModules.toTypedArray())

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
                    // Signing / notarization only when their settings are there (see "Signing hooks" above).
                    if (macSigningIdentity != null) {
                        signing {
                            sign.set(true)
                            identity.set(macSigningIdentity)
                            if (macSigningKeychain != null) keychain.set(macSigningKeychain)
                        }
                    }
                    if (macNotarizationAppleId != null) {
                        notarization {
                            appleID.set(macNotarizationAppleId)
                            password.set(macNotarizationPassword)
                            teamID.set(macNotarizationTeamId)
                        }
                    }
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
