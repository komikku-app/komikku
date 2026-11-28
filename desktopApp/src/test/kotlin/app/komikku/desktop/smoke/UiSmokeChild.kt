package app.komikku.desktop.smoke

import kotlin.system.exitProcess

/** Child JVM of [UiSmokeStartTest]: `main()`'s first call of the smoke mode; exits 0 when it was not refused. */
object UiSmokeChild {
    @JvmStatic
    fun main(args: Array<String>) {
        UiSmoke.startIfRequested(args)
        println("STARTED")
        exitProcess(0)
    }
}
