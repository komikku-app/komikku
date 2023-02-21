plugins {
    id("com.android.library")
    kotlin("android")
    id("com.squareup.sqldelight")
    kotlin("plugin.serialization")
}

android {
    namespace = "tachiyomi.data"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    sqldelight {
        database("Database") {
            packageName = "tachiyomi.data"
            dialect = "sqlite:3.24"
        }
    }
}

dependencies {
    implementation(project(":source-api"))
    implementation(project(":domain"))
    implementation(project(":core"))

    api(libs.sqldelight.android.driver)
    api(libs.sqldelight.coroutines)
    api(libs.sqldelight.android.paging)
}
