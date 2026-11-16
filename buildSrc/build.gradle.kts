plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(androidx.gradle)
    implementation(kotlinx.gradle)
    implementation(kotlinx.compose.compiler.gradle)
    implementation(libs.spotless.gradle)
    implementation(gradleApi())

    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(androidx.javaClass.superclass.protectionDomain.codeSource.location))
    // KMK -->
    implementation(files(composeLibs.javaClass.superclass.protectionDomain.codeSource.location))
    // KMK <--
    implementation(files(kotlinx.javaClass.superclass.protectionDomain.codeSource.location))
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
