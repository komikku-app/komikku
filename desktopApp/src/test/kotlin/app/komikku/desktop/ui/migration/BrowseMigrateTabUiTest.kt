package app.komikku.desktop.ui.migration

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.browse.SimpleSearchSource
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import java.io.File

/** P20, headless: the whole app's Browse screen has Android's Migrate tab, listing the sources with library entries. */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrowseMigrateTabUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(SimpleSearchSource(SOURCE, "Delta source"))) }
        TestLibrary.insertManga(SOURCE, "/iota", "Iota")
    }

    @Test
    fun `the Browse screen has the Migrate tab`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.browse.localized()).performClick()
        val tab = MR.strings.label_migration.localized()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(tab).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(tab).performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("migrate-source-$SOURCE").fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val SOURCE = 9_091L
    }
}
