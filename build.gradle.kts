// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.github.ben-manes.versions") version "0.28.0"
}

buildscript {
    repositories {
        google()
        jcenter()
        maven { setUrl("https://maven.fabric.io/public") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.5.3")
        classpath("com.github.ben-manes:gradle-versions-plugin:0.22.0")
        classpath("com.github.zellius:android-shortcut-gradle-plugin:0.1.2")
        classpath("com.google.gms:google-services:4.3.3")
        classpath("org.jmailen.gradle:kotlinter-gradle:2.3.1")
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files

        // Realm (EH)
        classpath("io.realm:realm-gradle-plugin:5.13.1")

        // Firebase (EH)
        classpath("io.fabric.tools:gradle:1.31.0")
    }
}

allprojects {
    repositories {
        google()
        maven { setUrl("https://www.jitpack.io") }
        maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { setUrl("https://dl.bintray.com/ibm-cloud-sdks/ibm-cloud-sdk-repo") }
        maven { setUrl("https://plugins.gradle.org/m2/") }
        jcenter()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
