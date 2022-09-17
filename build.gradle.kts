buildscript {
    dependencies {
        classpath(libs.android.shortcut.gradle)
        classpath(libs.google.services.gradle)
        classpath(libs.aboutLibraries.gradle)
        classpath(kotlinx.serialization.gradle)
        classpath(libs.sqldelight.gradle)
        classpath(sylibs.firebase.crashlytics.gradle)
    }
}

plugins {
    alias(androidx.plugins.application) apply false
    alias(androidx.plugins.library) apply false
    alias(kotlinx.plugins.android) apply false
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.versionsx)
}

subprojects {
    apply<org.jmailen.gradle.kotlinter.KotlinterPlugin>()

    kotlinter {
        experimentalRules = true

        disabledRules = arrayOf(
            "experimental:argument-list-wrapping", // Doesn't play well with Android Studio
            "experimental:comment-wrapping", // Doesn't play nice with SY specifiers
            "filename", // Often broken to give a more general name
        )
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
