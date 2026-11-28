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
import androidx.compose.ui.test.hasText
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
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
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
 * packaged app's `--ui-smoke`, driven headless through the Compose test framework instead of the real window: the
 * window the mode chooses on a screen of [screenWidth] × [screenHeight], without a title bar of [titleBar] px. One
 * subclass per size: each needs its own app (the script reads a chapter and turns the lock on).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class SmokeScriptTest(screenWidth: Int, screenHeight: Int, private val titleBar: Int) {

    private val window = SmokeWindowSize.choose(screenWidth, screenHeight) as SmokeWindowSize.Choice.Fits

    private lateinit var app: Application
    private val navigators = SmokeNavigators()

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        UiSmoke.fixLocale()
        app = DesktopInjekt.init(dataDir)
        UiSmokeSession.prepareApp(window.width, window.height)
        NavigatorRegistry.install(navigators)
    }

    @AfterAll
    fun uninstall() = NavigatorRegistry.uninstall()

    @Test
    fun `every step of the smoke script passes`(@TempDir reportFolder: File) = runDesktopComposeUiTest(window.width, window.height - titleBar) {
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
        // The window's minimum height (SmokeWindowSize) keeps the details screen's chapters whole on screen
        val contentHeight = onRoot().fetchSemanticsNode().size.height.toFloat()
        SmokeFixture.CHAPTERS.forEach { chapter ->
            withClue("$chapter whole on screen") {
                val row = onNode(hasText(chapter)).fetchSemanticsNode()
                row.positionInRoot.y + row.size.height shouldBeLessThanOrEqual contentHeight
            }
        }
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

/** The title bar's height on macOS; the most a window's frame takes from its height is about 40 px. */
private const val MAC_TITLE_BAR = 28
private const val MAX_TITLE_BAR = 40

/** The preferred window, as on the Linux and Windows runners. */
class SmokeScriptPreferredSizeTest : SmokeScriptTest(1280, 800, MAC_TITLE_BAR)

/** The macOS runner's screen (CI run 46139150350): a 1024x684 window, 1024x656 without its title bar. */
class SmokeScriptSmallScreenTest : SmokeScriptTest(1024, 684, MAC_TITLE_BAR)

/** The smallest window the mode accepts, with the tallest frame. */
class SmokeScriptMinimumSizeTest : SmokeScriptTest(960, 600, MAX_TITLE_BAR)
