package app.komikku.desktop

import app.komikku.desktop.di.DesktopInjekt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.time.Duration.Companion.seconds

/**
 * Registered for every test class (`junit-platform.properties`, `META-INF/services`): after a class's `@AfterAll`
 * methods, and before JUnit deletes the class's `@TempDir` that [DesktopInjekt.init] was given, stops the app's delayed
 * writers into it and waits for them ([DesktopInjekt.stopDelayedWriters]). Without this the download index's
 * write a second after the last change could recreate `cache/` while the folder is deleted (`DirectoryNotEmptyException`
 * on its root, CI run 45655979842). Classes that boot [DesktopInjekt] are `PER_CLASS` and run in their own JVM.
 *
 * A stop that takes longer than [TIMEOUT] is logged, not failed: the class's tests have passed, and the folder getters
 * no longer create folders once the stop has begun, so the clean-up can still succeed.
 */
class DesktopDataDirTeardown : AfterAllCallback {

    override fun afterAll(context: ExtensionContext) {
        val done = runBlocking { withTimeoutOrNull(TIMEOUT) { DesktopInjekt.stopDelayedWriters() } }
        if (done == null) {
            System.err.println(
                "DesktopDataDirTeardown: stopping the delayed data folder writers of ${context.displayName} " +
                    "took longer than $TIMEOUT; going on with the clean-up",
            )
        }
    }

    private companion object {
        val TIMEOUT = 10.seconds
    }
}
