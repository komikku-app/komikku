import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * KMK: `:desktop:android-compat` (docs/kmp-desktop/PLAN.md, D3).
 *
 * Desktop-only JVM library that provides, under the real Android / AndroidX / library FQNs, the
 * subset of APIs that shared Komikku code (and later extensions) needs to compile and run on a
 * desktop JVM. Written from scratch for Komikku (Apache-2.0); no AOSP or Suwayomi code is copied.
 * Members that cannot be supported throw `UnsupportedOperationException("android-compat: …")`.
 *
 * Never a dependency of the Android app: only desktop targets of shared modules depend on it.
 */
plugins {
    id("mihon.code.lint")
    kotlin("jvm")
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
    // `SharedPreferences` is backed by the desktop preference store (one JSON file per name).
    api(projects.core.preference)

    // `CookieManager` stores cookies as OkHttp cookies (parsing, domain/path matching, expiry).
    implementation(libs.okhttp.core)
    // `Html.fromHtml` converts markup to text with jsoup.
    implementation(libs.jsoup)
    // Cookie persistence.
    implementation(kotlinx.serialization.json)
    // `AppCompatDelegate.defaultNightModeFlow`. (`app.cash.quickjs.QuickJs` moved to :core:quickjs in I2b.)
    implementation(kotlinx.coroutines.core)
    // `nl.adaptivity.xmlutil.core.AndroidXmlReader` (xmlutil `core-android`) over the JVM xmlutil core.
    api(kotlinx.serialization.xml.core.jvm)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
