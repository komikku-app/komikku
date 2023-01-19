buildscript {
    dependencies {
        classpath(libs.android.shortcut.gradle)
        classpath(libs.google.services.gradle)
        classpath(libs.aboutLibraries.gradle)
        classpath(libs.sqldelight.gradle)
        classpath(sylibs.firebase.crashlytics.gradle)
        classpath(sylibs.versionsx)
    }
}

plugins {
    alias(kotlinx.plugins.serialization) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
