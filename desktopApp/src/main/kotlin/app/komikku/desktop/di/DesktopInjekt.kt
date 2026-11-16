package app.komikku.desktop.di

import android.app.Application
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

    fun init(dataDir: File = DesktopDirs.dataDir): Application {
        val app = Application(dataDir)
        Injekt.importModule(DesktopPreferenceModule(app, dataDir))
        Injekt.importModule(DesktopAppModule(app, DesktopDatabase.defaultFile(dataDir)))
        Injekt.importModule(DesktopDomainModule(app, dataDir))
        return app
    }
}
