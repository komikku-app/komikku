import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * KMK: `:desktop:extension-runtime` (docs/kmp-desktop/PLAN.md, D4 / Phase 5).
 *
 * Desktop-only JVM library that turns a Tachiyomi / Mihon extension APK into loaded `Source`s:
 * APK reading (zip, binary `AndroidManifest.xml`, `resources.arsc`), APK signing certificate
 * extraction and verification (v3.1 / v3 / v2 signing blocks, v1 JAR signatures), dex → jar
 * conversion with dex2jar (cached by APK SHA-256) and a child-first class loader whose parent shares
 * the host's compat / source-api / network classes with the extension. Also hosts the link-check
 * tool (step 5.3). Written from scratch for Komikku (Apache-2.0).
 *
 * Never a dependency of the Android app.
 */
plugins {
    id("mihon.code.lint")
    kotlin("jvm")
    // Fixture builders (binary manifest / resource table writers, signed APKs, generated classes)
    // shared with the desktop app's extension manager tests.
    `java-test-fixtures`
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
    // `Source` / `SourceFactory` (desktop variant), which also brings core:common and the compat layer.
    api(projects.sourceApi)
    // dex → class files. Brings ASM (core from d2j-external, tree/util/commons from org.ow2.asm),
    // used by the link checker as well.
    implementation(libs.dex2jar.translator)

    // Test fixtures: APKs are signed by apksig (an independent implementation of the schemes the
    // runtime verifies) with BouncyCastle-made certificates; classes are generated with ASM and dexed
    // with the AOSP dx that dex2jar bundles.
    testFixturesApi(androidx.apksig)
    testFixturesApi(libs.bouncycastle.pkix)
    testFixturesImplementation(libs.dex2jar.translator)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
