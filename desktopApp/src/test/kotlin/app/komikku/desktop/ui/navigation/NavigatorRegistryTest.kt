package app.komikku.desktop.ui.navigation

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.smoke.SmokeNavigators
import app.komikku.desktop.ui.browse.BrowseHomeScreen
import app.komikku.desktop.ui.homeTabs
import app.komikku.desktop.ui.more.MoreHomeScreen
import app.komikku.desktop.window.DesktopShortcuts
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The navigator registry costs nothing in a normal run (ANSWER 011, condition 1): nothing is installed and nothing is
 * kept after a start that visits every destination; installed by the smoke mode, the same start hands it the tabs and
 * the stacks.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NavigatorRegistryTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
    }

    @AfterEach
    fun uninstall() = NavigatorRegistry.uninstall()

    private fun visitEveryDestination(install: SmokeNavigators?) = runDesktopComposeUiTest(width = 1280, height = 800) {
        install?.let(NavigatorRegistry::install)
        val shortcuts = DesktopShortcuts()
        setContent { KomikkuApp(context = app, shortcuts = shortcuts) }
        waitForIdle()
        homeTabs.indices.forEach { index ->
            runOnIdle { checkNotNull(shortcuts.handler)(DesktopShortcuts.Action.SelectTab(index)) }
            waitForIdle()
        }
    }

    @Test
    fun `a normal start registers nothing`() {
        visitEveryDestination(install = null)

        NavigatorRegistry.installed.shouldBeNull()
    }

    @Test
    fun `the smoke mode's registry gets the tabs and the stacks`() {
        val navigators = SmokeNavigators()

        visitEveryDestination(install = navigators)

        navigators.hasTabs shouldBe true
        navigators.currentTab() shouldBe homeTabs.lastIndex
        navigators.top(MoreHomeScreen).shouldNotBeNull().key shouldBe MoreHomeScreen.key
        navigators.top(BrowseHomeScreen).shouldNotBeNull().key shouldBe BrowseHomeScreen.key
    }
}
