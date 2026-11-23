package mihon.buildlogic

import org.gradle.api.Project

/**
 * KMK: quickjs-kt's Android AAR carries natives for Android ABIs only, so the Android host unit tests of a module
 * that uses it (`*UnitTestRuntimeClasspath`) get the JVM artifact (Linux / macOS / Windows natives) instead;
 * [configuration] is the module's Android unit-test `runtimeOnly` configuration.
 */
fun Project.useQuickJsKtJvmInAndroidHostTests(configuration: String) {
    configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
        exclude(mapOf("group" to "io.github.dokar3", "module" to "quickjs-kt-android"))
    }
    dependencies.add(configuration, libs.quickjs.kt.jvm)
}
