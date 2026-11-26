package app.komikku.desktop

import app.komikku.desktop.di.DesktopInjekt
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Registered for every test class (`junit-platform.properties`, `META-INF/services`): after a class's `@AfterAll`
 * methods, and before JUnit deletes the class's `@TempDir` that [DesktopInjekt.init] was given, stops the app's delayed
 * writers into it and waits for them ([DesktopInjekt.stopDelayedWriters]). Without this the download index's
 * write a second after the last change could recreate `cache/` while the folder is deleted (`DirectoryNotEmptyException`
 * on its root, CI run 45655979842). Classes that boot [DesktopInjekt] are `PER_CLASS` and run in their own JVM.
 *
 * Each stop is bounded by [DesktopInjekt.STOP_TIMEOUT]. A writer whose stop did not finish fails the class, naming it:
 * the folder getters no longer create folders, but such a writer can still write a file into a folder that exists, so
 * the clean-up (or the next class's data) is not safe.
 */
class DesktopDataDirTeardown : AfterAllCallback {

    override fun afterAll(context: ExtensionContext) {
        val unfinished = runBlocking { DesktopInjekt.stopDelayedWriters() }
        check(unfinished.isEmpty()) {
            "DesktopDataDirTeardown: the data folder writer(s) ${unfinished.joinToString()} of ${context.displayName} " +
                "did not stop within ${DesktopInjekt.STOP_TIMEOUT} and may still write into its data folder"
        }
    }
}
