import mihon.buildlogic.configureCompose

plugins {
    id("com.android.application")

    id("mihon.code.lint")
}

android {
    configureCompose(this)
}
