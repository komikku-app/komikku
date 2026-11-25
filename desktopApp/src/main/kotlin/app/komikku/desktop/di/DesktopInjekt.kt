package app.komikku.desktop.di

import android.app.Application
import app.komikku.desktop.compat.stopCreatingDataFolders
import exh.log.xLogE
import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.desktop.DesktopDirs
import tachiyomi.data.DesktopDatabase
import uy.kohesive.injekt.Injekt
import java.io.File

/**
 * Desktop dependency graph (Injekt), the counterpart of the module imports in the Android `App`.
 *
 * Everything lives under [dataDir]: `preferences/` (preference stores), `databases/tachiyomi.db`,
 * `files/`, `cache/` (compat `Context`), `storage/` (default storage folder: local source,
 * downloads, backups), `extensions/` (installed extension APKs, converted jars). Call [init] once per process, before anything resolves from [Injekt].
 */
object DesktopInjekt {

    private val lock = Any()

    /** How to stop the delayed writers into the data folder created so far ([onCreatedWriter]); under [lock]. */
    private val writerStops = ArrayList<suspend () -> Unit>()

    /** Set by [stopDelayedWriters], reset by [init]: a writer created in between is stopped at once. Under [lock]. */
    private var stopped = false

    /** The application of the last [init], whose folder getters [stopDelayedWriters] closes. Under [lock]. */
    private var application: Application? = null

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
     * After [stopDelayedWriters] (and before the next [init]) [stop] runs at once instead; its failure is logged.
     */
    internal fun onCreatedWriter(stop: suspend () -> Unit) {
        val stopNow = synchronized(lock) { stopped.also { if (!it) writerStops += stop } }
        if (stopNow) {
            try {
                runBlocking { stop() }
            } catch (e: Exception) {
                xLogE("Could not stop a data folder writer created after the teardown", e)
            }
        }
    }

    /**
     * Stops the delayed writers created so far and waits for them, so nothing writes into the data folder afterwards
     * (tests: before their temporary data folder is deleted). From now on until the next [init] the compat folder
     * getters of the [init] application no longer create folders (`stopCreatingDataFolders`), and a writer created
     * later is stopped when it registers. Every stop is attempted; the first failure is rethrown at the end (the
     * others added as suppressed). Those singletons are unusable after this. Not called in normal use.
     */
    suspend fun stopDelayedWriters() {
        val app = synchronized(lock) {
            stopped = true
            application
        }
        app?.stopCreatingDataFolders()
        var failure: Throwable? = null
        while (true) {
            val stop = synchronized(lock) { writerStops.removeFirstOrNull() } ?: break
            try {
                stop()
            } catch (e: Throwable) {
                failure?.addSuppressed(e) ?: run { failure = e }
            }
        }
        failure?.let { throw it }
    }
}
