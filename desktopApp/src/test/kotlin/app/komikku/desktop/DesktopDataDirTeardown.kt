package app.komikku.desktop

import app.komikku.desktop.di.DesktopInjekt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.time.Duration.Companion.seconds

/**
 * Registered for every test class (`junit-platform.properties`, `META-INF/services`): after a class's `@AfterAll`
 * methods, and before JUnit deletes the class's `@TempDir` that [DesktopInjekt.init] was given, stops the app's delayed
 * writers into it and waits for them ([DesktopInjekt.stopDelayedWriters]). Without this the download index's
 * write a second after the last change could recreate `cache/` while the folder is deleted (`DirectoryNotEmptyException`
 * on its root, CI run 45655979842). Classes that boot [DesktopInjekt] are `PER_CLASS` and run in their own JVM.
 */
class DesktopDataDirTeardown : AfterAllCallback {

    override fun afterAll(context: ExtensionContext) {
        runBlocking { withTimeout(10.seconds) { DesktopInjekt.stopDelayedWriters() } }
    }
}
