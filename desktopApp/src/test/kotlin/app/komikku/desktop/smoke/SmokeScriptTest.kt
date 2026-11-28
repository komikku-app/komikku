package app.komikku.desktop.smoke

import android.app.Application
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.navigation.NavigatorRegistry
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.imageio.ImageIO

/**
 * The smoke script on every test run (UI_SMOKE_DESIGN.md section 4): the same steps, presets and fixture as the
 * packaged app's `--ui-smoke`, driven headless through the Compose test framework instead of the real window.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmokeScriptTest {

    private lateinit var app: Application
    private val navigators = SmokeNavigators()

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        UiSmoke.fixLocale()
        app = DesktopInjekt.init(dataDir)
        UiSmokeSession.prepareApp()
        NavigatorRegistry.install(navigators)
    }

    @AfterAll
    fun uninstall() = NavigatorRegistry.uninstall()

    @Test
    fun `every step of the smoke script passes`(@TempDir reportFolder: File) = runDesktopComposeUiTest(width = 1280, height = 772) {
        setContent { KomikkuApp(context = app) }
        waitForIdle()
        val driver = TestDriver(this, navigators)
        val script = SmokeScript(driver)
        val report = SmokeReport(reportFolder)

        // Real time for the script's timeouts, not the test's virtual clock
        val failure = runBlocking { script.run(report, reportFolder) }

        withClue({ runBlocking { driver.accessibilityTree() } }) { failure.shouldBeNull() }
        report.steps().map { it.name } shouldBe script.steps.map { it.name }
        report.steps().map { it.status }.shouldContainOnly(SmokeReport.Status.PASS)
        script.steps.forEach { File(reportFolder, "${it.name}.png").isFile shouldBe true }
    }

    /** [SmokeDriver] over the Compose test framework: its key injection, text input and captures. */
    private class TestDriver(private val test: ComposeUiTest, override val navigators: SmokeNavigators) : SmokeDriver {

        override suspend fun <T> onUi(block: () -> T): T = test.runOnIdle(block)

        /** The focused node, where the keyboard's input goes, else the root. */
        private fun target(): SemanticsNodeInteraction =
            if (test.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()) test.onNode(isFocused()) else test.onRoot()

        override suspend fun press(key: SmokeKey) {
            val composeKey = when (key) {
                SmokeKey.PageDown -> Key.PageDown
                SmokeKey.Escape -> Key.Escape
                SmokeKey.Enter -> Key.Enter
            }
            target().performKeyInput { pressKey(composeKey) }
        }

        override suspend fun type(text: String) {
            target().performTextInput(text)
        }

        override suspend fun texts(): List<String> = buildList {
            test.waitForIdle()
            fun visit(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
                node.children.forEach(::visit)
            }
            test.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
        }

        override suspend fun awaitIdle(timeoutMillis: Long): Boolean {
            test.waitForIdle()
            return true
        }

        override suspend fun screenshot(file: File): Screenshot {
            val root = test.onAllNodes(isRoot()).onFirst()
            val image = root.captureToImage().toAwtImage()
            val size = root.fetchSemanticsNode().size
            file.parentFile?.mkdirs()
            ImageIO.write(image, "png", file)
            val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            return Screenshot(image.width, image.height, size.width, size.height, Screenshot.countColours(pixels))
        }

        override suspend fun accessibilityTree(): String = test.onAllNodes(isRoot()).printToString()
    }
}
