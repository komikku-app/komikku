package app.komikku.desktop.instance

import java.io.File

/**
 * A second app start for [DesktopSingleInstanceTest], run in its own JVM: the app's start-up check
 * ([DesktopSingleInstance.ensure]) on the data folder given as the argument. It exits like the app (0 when it signalled
 * a running instance, 1 when that did not answer, after printing `BUSY`); when it owns the folder it prints `LOCKED`,
 * prints `SHOW` for each show request and runs until it is killed.
 */
object SingleInstanceChild {

    @JvmStatic
    fun main(args: Array<String>) {
        val instance = DesktopSingleInstance.ensure(File(args.single()), showBusy = { say("BUSY") })
        instance.onShowRequest = { say("SHOW") }
        say("LOCKED")
        Thread.sleep(Long.MAX_VALUE)
    }

    private fun say(line: String) = synchronized(System.out) {
        println(line)
        System.out.flush()
    }
}
