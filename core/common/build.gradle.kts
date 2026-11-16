plugins {
    // KMK --> Android + desktop (JVM), path preserving: shared sources stay in src/main/kotlin
    // (compiled through `jvmSharedMain`), see buildSrc mihon/buildlogic/KmpLibrary.kt.
    id("mihon.library.kmp")
    // KMK <--
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            // KMK -->
            "-Xexpect-actual-classes",
            // KMK <--
        )
    }

    // KMK --> Android-only libraries (AARs); desktop gets the same FQNs from :desktop:android-compat.
    sourceSets {
        androidMain.dependencies {
            api(libs.preferencektx)

            implementation(libs.image.decoder)

            implementation(libs.unifile)
            implementation(libs.libarchive)

            // JavaScript engine
            implementation(libs.bundles.js.engine)

            // SY -->
            implementation(sylibs.xlog)
            implementation(sylibs.exifinterface)
            // SY <--
        }
        named("desktopMain") {
            dependencies {
                // Android API surface on desktop (Context, Uri, CookieManager, UniFile, QuickJs, xlog, …).
                // `api`: core:common's public API exposes these types to every desktop consumer.
                api(projects.desktop.androidCompat)
            }
        }
    }
    // KMK <--
}

dependencies {
    implementation(projects.i18n)
    // SY -->
    implementation(projects.i18nSy)
    // SY <--

    // KMK --> Multiplatform preference abstraction (re-exported so existing
    // tachiyomi.core.common.preference.* imports keep resolving)
    api(projects.core.preference)
    // KMK <--

    api(libs.logcat)

    api(libs.rxjava)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okio)

    // KMK --> moved to androidMain (Android-only AARs)
    // implementation(libs.image.decoder)

    // implementation(libs.unifile)
    // implementation(libs.libarchive)
    // KMK <--

    api(kotlinx.coroutines.core)
    api(kotlinx.serialization.json)
    api(kotlinx.serialization.json.okio)

    // KMK --> moved to androidMain (Android-only AAR)
    // api(libs.preferencektx)
    // KMK <--

    implementation(libs.jsoup)

    // Sort
    implementation(libs.natural.comparator)

    // KMK --> moved to androidMain (Android-only AAR)
    // JavaScript engine
    // implementation(libs.bundles.js.engine)
    // KMK <--

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // SY -->
    // KMK --> moved to androidMain (Android-only AARs)
    // implementation(sylibs.xlog)
    // implementation(sylibs.exifinterface)
    // KMK <--
    // SY <--
}
