package app.komikku.desktop.di

import android.app.Application
import tachiyomi.core.common.desktop.DesktopDirs
import tachiyomi.data.DesktopDatabase
import uy.kohesive.injekt.Injekt
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Desktop dependency graph (Injekt), the counterpart of the module imports in the Android `App`.
 *
 * Everything lives under [dataDir]: `preferences/` (preference stores), `databases/tachiyomi.db`,
 * `files/`, `cache/` (compat `Context`), `storage/` (default storage folder: local source,
 * downloads, backups), `extensions/` (installed extension APKs, converted jars). Call [init] once per process, before anything resolves from [Injekt].
 */
object DesktopInjekt {

    /** How to stop the delayed writers into the data folder created so far ([onCreatedWriter]). */
    private val writerStops = CopyOnWriteArrayList<suspend () -> Unit>()

    fun init(dataDir: File = DesktopDirs.dataDir): Application {
        val app = Application(dataDir)
        Injekt.importModule(DesktopPreferenceModule(app, dataDir))
        Injekt.importModule(DesktopAppModule(app, DesktopDatabase.defaultFile(dataDir)))
        Injekt.importModule(DesktopDomainModule(app, dataDir))
        return app
    }

    /**
     * A singleton that writes into the data folder from its own coroutines after the call that asked for it returned
     * (e.g. `DownloadCache`'s index, written a second later) was created; [stop] cancels that work and waits for it.
     */
    internal fun onCreatedWriter(stop: suspend () -> Unit) {
        writerStops += stop
    }

    /**
     * Stops the delayed writers created so far and waits for them, so nothing writes into the data folder afterwards
     * (tests: before their temporary data folder is deleted). Those singletons are unusable after this.
     */
    suspend fun stopDelayedWriters() {
        while (writerStops.isNotEmpty()) writerStops.removeAt(0)()
    }
}
