package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * The local source is browsable: since the source API of TachiyomiX 1.6 (d972c02ce2) it is a plain `Source`, no longer
 * a `CatalogueSource`, and the catalogue and the source feed showed "not installed" for it (found by the UI smoke run).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalSourceBrowseUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        val local = File(checkNotNull(Injekt.get<StorageManager>().getLocalSourceDirectory()?.filePath))
        File(local, "$TITLE/Chapter 1").mkdirs()
    }

    private fun ComposeUiTest.show(screen: Screen) = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme { DesktopToastHost { Navigator(screen) } }
        }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the local source's catalogue lists its manga`() = runDesktopComposeUiTest(width = 1000, height = 700) {
        show(CatalogueScreen(LocalSource.ID))
        waitForText(TITLE)
    }

    @Test
    fun `the local source's feed lists its manga`() = runDesktopComposeUiTest(width = 1000, height = 700) {
        show(SourceFeedScreen(LocalSource.ID))
        waitForText(TITLE)
    }

    private companion object {
        const val TITLE = "Local browse manga"
    }
}
