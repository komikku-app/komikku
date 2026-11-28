package app.komikku.desktop.di

import app.komikku.desktop.extension.dex.DexJarConverter
import app.komikku.desktop.extensions.DesktopExtensionManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * The app exit ([DesktopInjekt.runExitActions], from `Main.kt`) through the real wiring: it does not create the
 * extension manager (which would load every extension), and a manager that was created deletes at exit the converted
 * jars still pending. Alone in its class: Injekt keeps a singleton created by an earlier test of the same JVM.
 */
class DesktopExitActionsTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `the exit does not create the extension manager, and a created one deletes its pending jars`() {
        DesktopInjekt.init(dir.resolve("data"))
        val extensions = dir.resolve("data/extensions")

        DesktopInjekt.runExitActions()
        // The manager's first load creates the folder
        extensions.exists() shouldBe false

        val manager = Injekt.get<DesktopExtensionManager>()
        runBlocking { withTimeout(30.seconds) { manager.isInitialized.first { it } } }
        extensions.exists() shouldBe true
        val pending = extensions.resolve("jars/${"0".repeat(64)}-v${DexJarConverter.CACHE_VERSION}.jar")
        pending.parentFile.mkdirs()
        pending.writeBytes(ByteArray(1))

        DesktopInjekt.runExitActions()

        pending.exists() shouldBe false
    }
}
