package app.komikku.desktop.di

import android.app.Application
import app.komikku.desktop.compat.stopCreatingDataFolders
import exh.log.xLogE
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.desktop.DesktopDirs
import tachiyomi.data.DesktopDatabase
import uy.kohesive.injekt.Injekt
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop dependency graph (Injekt), the counterpart of the module imports in the Android `App`.
 *
 * Everything lives under [dataDir]: `preferences/` (preference stores), `databases/tachiyomi.db`,
 * `files/`, `cache/` (compat `Context`), `storage/` (default storage folder: local source,
 * downloads, backups), `extensions/` (installed extension APKs, converted jars). Call [init] once per process, before anything resolves from [Injekt].
 */
object DesktopInjekt {

    private val lock = Any()

    /** A delayed writer created so far ([onCreatedWriter]): [name] for reports, [stop] cancels its work and waits. */
    private class Writer(val name: String, val stop: suspend () -> Unit)

    /** The delayed writers into the data folder created so far; under [lock]. */
    private val writers = ArrayList<Writer>()

    /** Set by [stopDelayedWriters], reset by [init]: a writer created in between is stopped at once. Under [lock]. */
    private var stopped = false

    /** The application of the last [init], whose folder getters [stopDelayedWriters] closes. Under [lock]. */
    private var application: Application? = null

    /** How long one writer's stop may take before it is reported as not finished. */
    internal val STOP_TIMEOUT = 10.seconds

    /**
     * Builds the graph over [dataDir]. A second call (tests only) resets the teardown state ([stopDelayedWriters]) and
     * registers the modules again, but Injekt keeps the singletons already created: they stay bound to the first
     * [dataDir] (and stay stopped after [stopDelayedWriters]). So a process is not re-initialisable after a stop except
     * for objects not created yet; production calls this once.
     */
    fun init(dataDir: File = DesktopDirs.dataDir): Application {
        val app = Application(dataDir)
        synchronized(lock) {
            stopped = false
            application = app
        }
        Injekt.importModule(DesktopPreferenceModule(app, dataDir))
        Injekt.importModule(DesktopAppModule(app, DesktopDatabase.defaultFile(dataDir)))
        Injekt.importModule(DesktopDomainModule(app, dataDir))
        return app
    }

    /**
     * A singleton that writes into the data folder from its own coroutines after the call that asked for it returned
     * (e.g. `DownloadCache`'s index, written a second later) was created; [stop] cancels that work and waits for it.
     * After [stopDelayedWriters] (and before the next [init]) [stop] runs at once instead, for at most [STOP_TIMEOUT];
     * a failure or a stop that did not finish is logged, an interrupt of the calling thread is kept.
     */
    internal fun onCreatedWriter(name: String, stop: suspend () -> Unit) {
        val stopNow = synchronized(lock) { stopped.also { if (!it) writers += Writer(name, stop) } }
        if (!stopNow) return
        try {
            if (runBlocking { withTimeoutOrNull(STOP_TIMEOUT) { stop() } } == null) {
                xLogE("Data folder writer $name created after the teardown did not stop within $STOP_TIMEOUT")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            xLogE("Interrupted while stopping data folder writer $name created after the teardown", e)
        } catch (e: Exception) {
            xLogE("Could not stop data folder writer $name created after the teardown", e)
        }
    }

    /**
     * Stops the delayed writers created so far and waits for them, so nothing writes into the data folder afterwards
     * (tests: before their temporary data folder is deleted). From now on until the next [init] the compat folder
     * getters of the [init] application no longer create folders (`stopCreatingDataFolders`), and a writer created
     * later is stopped when it registers. Every stop runs to its end (at most [perStop] each) even when the caller is
     * cancelled; the caller's cancellation is rethrown after the last one. Returns the names of the writers whose stop
     * did not finish within [perStop] (they may still write); the first failure of a stop is rethrown at the end (the
     * others added as suppressed). Those singletons are unusable after this. Not called in normal use.
     */
    suspend fun stopDelayedWriters(perStop: Duration = STOP_TIMEOUT): List<String> {
        val app = synchronized(lock) {
            stopped = true
            application
        }
        app?.stopCreatingDataFolders()
        val unfinished = ArrayList<String>()
        var failure: Throwable? = null
        while (true) {
            val writer = synchronized(lock) { writers.removeFirstOrNull() } ?: break
            try {
                // NonCancellable: the caller's cancellation does not cut a stop short; only its own bound does
                val finished = withContext(NonCancellable) { withTimeoutOrNull(perStop) { writer.stop() } != null }
                if (!finished) unfinished += writer.name
            } catch (e: Throwable) {
                failure?.addSuppressed(e) ?: run { failure = e }
            }
        }
        currentCoroutineContext().ensureActive()
        failure?.let { throw it }
        return unfinished
    }
}
