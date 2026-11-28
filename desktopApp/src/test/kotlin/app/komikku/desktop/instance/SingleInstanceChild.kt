package app.komikku.desktop.instance

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * A second app start for [DesktopSingleInstanceTest], run in its own JVM: the app's start-up check
 * ([DesktopSingleInstance.ensure]) on the data folder given as the first argument; a second argument `unlockable` makes
 * `tryLock` throw like a file system that cannot lock. It exits like the app (0 when it signalled a running instance, 1
 * when that did not answer, after printing `BUSY`); when it owns the folder it prints `LOCKED`, prints `SHOW` for each
 * show request and runs until it is killed.
 */
object SingleInstanceChild {

    const val UNLOCKABLE = "unlockable"

    @JvmStatic
    fun main(args: Array<String>) {
        val instance = DesktopSingleInstance.ensure(
            File(args.first()),
            showBusy = { say("BUSY") },
            tryLock = if (UNLOCKABLE in args.drop(1)) unlockable else lockable,
        )
        instance.onShowRequest = { say("SHOW") }
        say("LOCKED")
        Thread.sleep(Long.MAX_VALUE)
    }

    private val lockable: (FileChannel) -> FileLock? = { it.tryLock() }

    /** `tryLock` on a file system without locking (some NFS / SMB shares). */
    val unlockable: (FileChannel) -> FileLock? =
        { throw IOException("Operation not supported") }

    private fun say(line: String) = synchronized(System.out) {
        println(line)
        System.out.flush()
    }
}
