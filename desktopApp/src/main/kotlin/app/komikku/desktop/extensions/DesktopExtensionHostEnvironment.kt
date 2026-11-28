package app.komikku.desktop.extensions

import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.extension.linkcheck.ExtensionHostEnvironment
import java.io.File

/**
 * The link check's `--create-sources` on the desktop app's class path (`META-INF/services`): the app's Injekt graph
 * over the run's data folder, as extensions resolve the `Application` and the network helper when their sources are
 * created (like the self-test's extension checks).
 */
class DesktopExtensionHostEnvironment : ExtensionHostEnvironment {
    override fun prepare(dataDir: File) {
        DesktopInjekt.init(dataDir)
    }
}
