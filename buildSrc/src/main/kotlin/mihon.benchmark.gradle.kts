import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("com.android.test")

    id("mihon.code.lint")
}

android {
    configureAndroid(this)
    configureTest()
}
