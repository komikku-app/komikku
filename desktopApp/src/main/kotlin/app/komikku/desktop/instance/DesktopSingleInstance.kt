package app.komikku.desktop.instance

import exh.log.xLogE
import exh.log.xLogI
import exh.log.xLogW
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * KMK: one running Komikku per data folder (two processes on one database and one download folder lose data).
 *
 * At start-up the app takes an exclusive lock on [LOCK_FILE] in its data folder (`FileChannel.tryLock`), held until the
 * process ends; the system releases it when the process dies, also after a crash or `kill -9`. The instance that holds
 * it listens for "show your window" requests ([InstanceSignal]) and writes where to reach it to [PORT_FILE].
 *
 * A start that finds the lock taken asks the running instance to show its window, or to open the address it was started
 * with (an OAuth tracker redirect on Windows / Linux, `OAuthRedirectInbox`), and exits with code 0 ([ensure]); when
 * no answer comes within [ANSWER_TIMEOUT] (it keeps retrying until then: the running instance may only just have taken
 * the lock, or may be quitting, which stops answering at once ([stopListening]) and releases the lock when it exits), a
 * dialog says another Komikku is using the data folder and it exits with code 1. A port file left by a process that died is never trusted: the next holder of the lock replaces it.
 * Different data folders (`-Dkomikku.dataDir`) do not affect each other.
 *
 * Within one JVM the lock is also tracked in [held]: `tryLock` there throws [OverlappingFileLockException], and on
 * POSIX systems closing a second channel on the lock file would release the first one's lock.
 *
 * A data folder whose file system cannot lock (`tryLock` throws, e.g. some NFS / SMB shares; ANSWER 013) falls back to
 * the port file as an advisory lock ([lockUnsupported]): a start signals the instance it names when that answers, and
 * otherwise takes the folder over. It cannot stop two starts at the same moment, or on two computers, so the app warns
 * about it in a banner ([UnlockableFolderBanner]) and in the log on every start.
 */
class DesktopSingleInstance private constructor(
    private val lockFile: Path,
    private val channel: FileChannel?,
    private val lock: FileLock?,
    private val server: InstanceSignal.Server?,
    private val token: String?,
    /** The data folder cannot be locked: only the port file guards it, see the class description. */
    val lockUnsupported: Boolean,
) : AutoCloseable {

    /**
     * Called on a background thread when another start on this data folder asks to show the window; a request that
     * comes before this is set is delivered when it is set.
     */
    var onShowRequest: (() -> Unit)?
        get() = server?.onShowRequest
        set(value) {
            server?.onShowRequest = value
        }

    /**
     * Called on a background thread with the address another start on this data folder was asked to open; like
     * [onShowRequest], one that comes before this is set is delivered when it is set.
     */
    var onOpenRequest: ((String) -> Unit)?
        get() = server?.onOpenRequest
        set(value) {
            server?.onOpenRequest = value
        }

    /**
     * Stops answering other starts and removes the port file, but keeps the lock: for the shutdown after the window
     * closed. A start in that time then keeps retrying the lock (and gets it once this process ends) instead of being
     * told its window was shown by a process that is about to exit.
     */
    fun stopListening() {
        server?.close()
        val portFile = lockFile.resolveSibling(PORT_FILE)
        try {
            // Without the lock another start may have taken the folder over and written its own
            if (!lockUnsupported || InstancePortFile.read(portFile)?.token == token) Files.deleteIfExists(portFile)
        } catch (_: IOException) {
        }
    }

    /** Logs the [lockUnsupported] warning again, for the log file that is only set up after the check. */
    fun warnIfUnlockable() {
        if (lockUnsupported) xLogW(unlockableWarning(lockFile))
    }

    /** Stops listening, removes the port file and releases the lock. */
    override fun close() {
        stopListening()
        synchronized(held) {
            try {
                channel?.use { lock?.release() }
            } catch (_: IOException) {
            }
            held.remove(lockFile)
        }
    }

    sealed interface Outcome {
        /** This process owns the data folder now. */
        class Primary(val instance: DesktopSingleInstance) : Outcome

        /** Another process owns it and was asked to show its window (or to open the address). */
        data object ShownOther : Outcome

        /** Another process owns it and did not answer in time. */
        data object OtherNotAnswering : Outcome
    }

    private sealed interface LockAttempt {
        class Locked(val channel: FileChannel, val lock: FileLock) : LockAttempt
        data object Taken : LockAttempt
        class Unsupported(val error: IOException) : LockAttempt
    }

    /** Named: the desktop log tags lines with the logging class's name, which would be `Companion`. */
    companion object SingleInstance {
        const val LOCK_FILE = "instance.lock"
        const val PORT_FILE = "instance.port"
        val ANSWER_TIMEOUT = 2.seconds
        private val REQUEST_READ_TIMEOUT = 1.seconds
        private val RETRY_DELAY = 100.milliseconds

        private val held = mutableSetOf<Path>()

        /**
         * The start-up check: returns this process's instance when it owns [dataDir]; otherwise asks the running one to
         * show its window (to open [openAddress] when there is one) and calls [exit] with 0, or shows [showBusy] and calls
         * [exit] with 1 when it does not answer.
         */
        fun ensure(
            dataDir: File,
            openAddress: String? = null,
            exit: (Int) -> Nothing = { exitProcess(it) },
            showBusy: (File) -> Unit = ::showBusyDialog,
            tryLock: (FileChannel) -> FileLock? = { it.tryLock() },
        ): DesktopSingleInstance = when (val outcome = acquire(dataDir, openAddress = openAddress, tryLock = tryLock)) {
            is Outcome.Primary -> outcome.instance
            Outcome.ShownOther -> {
                val asked = if (openAddress != null) "to open an address" else "to show its window"
                xLogI("Single instance: Komikku already runs on this data folder; asked it $asked")
                exit(0)
            }
            Outcome.OtherNotAnswering -> {
                xLogW("Single instance: Komikku already runs on this data folder and did not answer within $ANSWER_TIMEOUT")
                showBusy(dataDir)
                exit(1)
            }
        }

        /**
         * Takes [dataDir]'s lock, or asks its holder to show its window (to open [openAddress] when there is one); retries
         * both until [answerTimeout] has passed. [requestReadTimeout] bounds how long the holder waits for a request line.
         * [tryLock] takes the lock (tests: a file system that cannot lock).
         */
        fun acquire(
            dataDir: File,
            answerTimeout: Duration = ANSWER_TIMEOUT,
            requestReadTimeout: Duration = REQUEST_READ_TIMEOUT,
            openAddress: String? = null,
            tryLock: (FileChannel) -> FileLock? = { it.tryLock() },
        ): Outcome {
            val signal: (Path, Duration) -> Boolean = if (openAddress != null) {
                { portFile, timeout -> InstanceSignal.requestOpen(portFile, openAddress, timeout) }
            } else {
                InstanceSignal::requestShow
            }
            val directory = try {
                dataDir.toPath().toRealPath()
            } catch (_: IOException) {
                dataDir.toPath().toAbsolutePath().normalize()
            }
            val lockFile = directory.resolve(LOCK_FILE)
            val portFile = directory.resolve(PORT_FILE)
            val deadline = System.nanoTime() + answerTimeout.inWholeNanoseconds
            while (true) {
                when (val attempt = lock(lockFile, tryLock)) {
                    is LockAttempt.Locked -> {
                        return Outcome.Primary(becomePrimary(lockFile, attempt.channel, attempt.lock, requestReadTimeout))
                    }
                    is LockAttempt.Unsupported -> {
                        val remaining = (deadline - System.nanoTime()).coerceAtLeast(1)
                        return advisory(lockFile, attempt.error, remaining.nanoseconds, requestReadTimeout, signal)
                    }
                    LockAttempt.Taken -> {
                        val remaining = (deadline - System.nanoTime()).coerceAtLeast(0)
                        if (signal(portFile, remaining.nanoseconds)) return Outcome.ShownOther
                    }
                }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) return Outcome.OtherNotAnswering
                Thread.sleep(minOf(RETRY_DELAY.inWholeNanoseconds, remaining) / 1_000_000)
            }
        }

        /**
         * The lock cannot be taken on this file system: the port file is the lock, an advisory one (ANSWER 013). The
         * instance it names is asked to show its window once, within [answerTimeout]; when none answers (no port file,
         * or one left by a process that died) this start takes the folder over and writes its own. An instance that is
         * quitting has removed its port file already, so a start then does not wait for it to exit.
         */
        private fun advisory(
            lockFile: Path,
            error: IOException,
            answerTimeout: Duration,
            readTimeout: Duration,
            signal: (Path, Duration) -> Boolean,
        ): Outcome {
            xLogW(unlockableWarning(lockFile), error)
            val portFile = lockFile.resolveSibling(PORT_FILE)
            if (signal(portFile, answerTimeout)) return Outcome.ShownOther
            synchronized(held) {
                if (lockFile in held) return Outcome.OtherNotAnswering
                held.add(lockFile)
            }
            return Outcome.Primary(becomePrimary(lockFile, null, null, readTimeout))
        }

        private fun unlockableWarning(lockFile: Path) =
            "Single instance: the data folder ${lockFile.parent} cannot be locked; $PORT_FILE is an advisory lock only, " +
                "do not open the folder from two computers at the same time"

        private fun lock(lockFile: Path, tryLock: (FileChannel) -> FileLock?): LockAttempt = synchronized(held) {
            if (lockFile in held) return LockAttempt.Taken
            val channel = try {
                FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            } catch (e: IOException) {
                return LockAttempt.Unsupported(e)
            }
            val lock = try {
                tryLock(channel)
            } catch (_: OverlappingFileLockException) {
                null
            } catch (e: IOException) {
                channel.close()
                return LockAttempt.Unsupported(e)
            }
            if (lock == null) {
                channel.close()
                return LockAttempt.Taken
            }
            held.add(lockFile)
            LockAttempt.Locked(channel, lock)
        }

        /** Listens for other starts; [lock] is `null` when the file system cannot lock ([advisory]). */
        private fun becomePrimary(
            lockFile: Path,
            channel: FileChannel?,
            lock: FileLock?,
            readTimeout: Duration,
        ): DesktopSingleInstance {
            val portFile = lockFile.resolveSibling(PORT_FILE)
            val token = InstanceSignal.newToken()
            val server = try {
                Files.deleteIfExists(portFile)
                InstanceSignal.Server(token, readTimeout).also { server ->
                    try {
                        InstancePortFile.write(portFile, InstancePortFile.Endpoint(server.port, token))
                    } catch (e: IOException) {
                        server.close()
                        throw e
                    }
                    server.start()
                }
            } catch (e: IOException) {
                xLogE("Single instance: cannot listen for other starts; they will report the data folder as busy", e)
                null
            }
            return DesktopSingleInstance(lockFile, channel, lock, server, token, lockUnsupported = lock == null)
        }

        /** Asks the OS to bring this app to the front (macOS; elsewhere showing the window is enough). */
        fun requestForeground() {
            try {
                if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()) {
                    val desktop = Desktop.getDesktop()
                    if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) desktop.requestForeground(true)
                }
            } catch (e: Exception) {
                xLogW("Single instance: could not bring the app to the front", e)
            }
        }

        private fun showBusyDialog(dataDir: File) {
            if (GraphicsEnvironment.isHeadless()) return
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                JOptionPane.showMessageDialog(
                    null,
                    stringResource(KMR.strings.desktop_single_instance_busy, dataDir.absolutePath),
                    stringResource(MR.strings.app_name),
                    JOptionPane.WARNING_MESSAGE,
                )
            } catch (e: Exception) {
                xLogE("Single instance: could not show the busy dialog", e)
            }
        }
    }
}
